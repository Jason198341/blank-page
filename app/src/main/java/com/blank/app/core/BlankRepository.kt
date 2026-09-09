package com.blank.app.core

import android.content.Context
import com.blank.app.cli.TermuxBridge
import com.blank.app.data.local.AppDatabase
import com.blank.app.data.local.ComparisonEntity
import com.blank.app.data.local.Engine
import com.blank.app.data.local.Grade
import com.blank.app.data.local.ItemEntity
import com.blank.app.data.local.ItemKind
import com.blank.app.data.local.RevisionEntity
import com.blank.app.data.local.RoundState
import com.blank.app.data.local.SubmissionEntity
import com.blank.app.data.local.SubmissionKind
import com.blank.app.data.prefs.SettingsStore
import com.blank.app.domain.BlankJson
import com.blank.app.domain.ElementSheet
import com.blank.app.domain.GradingResult
import com.blank.app.domain.ReviewSchedule
import com.blank.app.notify.Notifications
import com.blank.app.notify.ScheduleSyncer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

/**
 * 앱의 모든 쓰기가 지나는 곳. 화면은 여기 말고 DB 를 직접 건드리지 않는다.
 *
 * 특히 "회차 상태 변경 → 알람 재무장" 은 늘 짝으로 일어나야 한다. 한쪽만 하면
 * 알림이 안 오거나 이미 끝낸 회차의 알림이 뜬다.
 */
/** 사용자가 고른 종류를 정규화기에 그대로 넘긴다 — 무엇을 "맞았다" 로 볼지가 여기서 갈린다. */
private fun ItemKind.promptLabel(): String = when (this) {
    ItemKind.STRUCTURE -> "diagram"
    ItemKind.LIST -> "list"
    ItemKind.SEQUENCE -> "procedure"
    ItemKind.VALUE -> "numeric"
}

class BlankRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val settings: SettingsStore
) {

    // ------------------------------------------------------------------ 등록

    /**
     * 지식을 등록하고 5회차를 한 번에 깐다.
     *
     * 요소표는 먼저 줄 단위로 대충 만들어 두고, CLI 정규화 결과가 돌아오면 갈아끼운다.
     * 등록이 터미널 상태에 인질로 잡히면 안 된다 — 오늘 입력하는 마찰이 이 앱의 목숨이다.
     */
    suspend fun createItem(title: String, body: String, kind: ItemKind, tags: String): Long {
        val now = System.currentTimeMillis()
        val itemId = db.items().insert(
            ItemEntity(title = title, body = body, kind = kind, tags = tags, createdAt = now, updatedAt = now)
        )
        val revisionId = db.revisions().insert(
            RevisionEntity(
                itemId = itemId, revision = 1, title = title, body = body, kind = kind,
                sheetJson = BlankJson.encodeToString(ElementSheet.fromLines(title, body)),
                normalized = false, createdAt = now
            )
        )
        db.items().byId(itemId)?.let { db.items().update(it.copy(currentRevisionId = revisionId)) }

        val notifyHour = settings.get().notifyHour
        db.rounds().insertAll(ReviewSchedule.plan(itemId, now, notifyHour))
        ScheduleSyncer.resync(context)

        TermuxBridge.requestNormalize(context, itemId, title, body, kind.promptLabel())
        return itemId
    }

    /** 원본 수정. 기존 리비전은 절대 건드리지 않고 한 줄 새로 쌓는다. */
    suspend fun editItem(itemId: Long, title: String, body: String, kind: ItemKind, tags: String) {
        val item = db.items().byId(itemId) ?: return
        val now = System.currentTimeMillis()
        val revision = (db.revisions().maxRevision(itemId) ?: 0) + 1
        val revisionId = db.revisions().insert(
            RevisionEntity(
                itemId = itemId, revision = revision, title = title, body = body, kind = kind,
                sheetJson = BlankJson.encodeToString(ElementSheet.fromLines(title, body)),
                normalized = false, createdAt = now
            )
        )
        db.items().update(
            item.copy(
                title = title, body = body, kind = kind, tags = tags,
                currentRevisionId = revisionId, updatedAt = now
            )
        )
        TermuxBridge.requestNormalize(context, itemId, title, body, kind.promptLabel())
    }

    /** CLI 정규화 결과를 현재 리비전에 붙인다. 이미 알림이 나간 회차의 기준은 안 바꾼다. */
    suspend fun applyNormalization(itemId: Long, json: String) {
        val sheet = runCatching { BlankJson.decodeFromString<ElementSheet>(json) }.getOrNull() ?: return
        if (sheet.elements.isEmpty()) return
        val item = db.items().byId(itemId) ?: return
        val revision = db.revisions().byId(item.currentRevisionId) ?: return
        if (revision.normalized) return
        // 리비전 행은 불변이라 UPDATE 하지 않는다. 정규화본을 다음 리비전으로 쌓는다.
        val now = System.currentTimeMillis()
        val next = (db.revisions().maxRevision(itemId) ?: 0) + 1
        val newId = db.revisions().insert(
            revision.copy(
                id = 0, revision = next,
                sheetJson = BlankJson.encodeToString(sheet), normalized = true, createdAt = now
            )
        )
        db.items().update(item.copy(currentRevisionId = newId, updatedAt = now))
    }

    // ------------------------------------------------------------------ 제출

    /**
     * 재현물 제출. 채점을 기다리지 않고 먼저 로컬에 박아 둔다 —
     * 터미널이 죽어 있어도 "제출했다" 는 사실은 남아야 한다.
     */
    suspend fun submit(
        roundId: Long,
        text: String,
        imagePath: String?,
        usedHint: Boolean,
        elapsedMs: Long,
        gaveUp: Boolean
    ): Long {
        val round = db.rounds().byId(roundId) ?: return -1
        val now = System.currentTimeMillis()
        val kind = when {
            gaveUp -> SubmissionKind.GAVE_UP
            imagePath != null && text.isNotBlank() -> SubmissionKind.BOTH
            imagePath != null -> SubmissionKind.IMAGE
            else -> SubmissionKind.TEXT
        }
        val submissionId = db.submissions().insert(
            SubmissionEntity(
                roundId = roundId, itemId = round.itemId, kind = kind, text = text,
                imagePath = imagePath, usedHint = usedHint, elapsedMs = elapsedMs, submittedAt = now
            )
        )
        db.rounds().update(round.copy(state = RoundState.DONE, completedAt = now))
        Notifications.cancel(context, roundId)
        ScheduleSyncer.resync(context)

        // 채점 기준은 이 회차가 붙든 리비전. 알림 전에 들어온 회차면 현재 리비전을 쓴다.
        val item = db.items().byId(round.itemId)
        val revisionId = if (round.revisionId != 0L) round.revisionId else item?.currentRevisionId ?: 0
        val sheetJson = db.revisions().byId(revisionId)?.sheetJson.orEmpty()

        db.comparisons().insert(
            ComparisonEntity(
                submissionId = submissionId, roundId = roundId,
                total = runCatching { BlankJson.decodeFromString<ElementSheet>(sheetJson).elements.size }
                    .getOrDefault(0),
                engine = Engine.CLI, status = "PENDING", createdAt = now
            )
        )

        val recall = if (gaveUp) "모르겠습니다. 아무것도 기억나지 않습니다." else text
        val dispatch = TermuxBridge.requestGrade(context, submissionId, sheetJson, recall, imagePath)
        dispatch.getOrNull()?.note?.let { note ->
            db.comparisons().bySubmission(submissionId)?.let {
                db.comparisons().update(it.copy(failReason = note))
            }
        }
        return submissionId
    }

    // ------------------------------------------------------------------ 채점 결과 반영

    suspend fun applyGrading(submissionId: Long, json: String, raw: String) {
        val comparison = db.comparisons().bySubmission(submissionId) ?: return
        val result = runCatching { BlankJson.decodeFromString<GradingResult>(json) }.getOrNull()
            ?: run { markFailed(submissionId, "채점 결과를 읽지 못했습니다"); return }

        // 채점 기준이 된 그 회차의 요소표로 점수를 다시 센다 (모델의 산수는 믿지 않는다)
        val round = db.rounds().byId(comparison.roundId)
        val revisionId = round?.revisionId?.takeIf { it != 0L }
            ?: db.items().byId(round?.itemId ?: 0)?.currentRevisionId ?: 0
        val sheet = runCatching {
            BlankJson.decodeFromString<ElementSheet>(
                db.revisions().byId(revisionId)?.sheetJson.orEmpty()
            )
        }.getOrDefault(ElementSheet())

        val grade = result.gradeFor(sheet)
        db.comparisons().update(
            comparison.copy(
                hit = result.hits(),
                total = if (sheet.elements.isNotEmpty()) sheet.elements.size
                    else result.judgements.size.coerceAtLeast(comparison.total),
                score = result.computeScore(sheet),
                grade = grade,
                suggestedGrade = grade,
                judgementsJson = BlankJson.encodeToString(result.judgements),
                extrasJson = BlankJson.encodeToString(result.extras),
                transcript = result.transcript,
                summary = result.summary,
                advice = result.nextAction.advice,
                reviewOriginal = result.reviewOriginal,
                status = "OK",
                failReason = null,
                rawText = raw
            )
        )
        applyScheduleAdjustment(comparison.roundId, grade)
    }

    suspend fun markFailed(submissionId: Long, reason: String) {
        db.comparisons().bySubmission(submissionId)?.let {
            db.comparisons().update(it.copy(status = "FAILED", failReason = reason))
        }
    }

    suspend fun markStatus(submissionId: Long, text: String) {
        db.comparisons().bySubmission(submissionId)?.let {
            db.comparisons().update(it.copy(failReason = text))
        }
    }

    /**
     * 사용자가 등급을 뒤집는다. 표시 등급만 바뀌고 **일정 계산은 원래 판정 기준**으로
     * 이미 끝나 있다 — 그래야 자기기만에 이득이 없다.
     */
    suspend fun overrideGrade(submissionId: Long, grade: Grade) {
        db.comparisons().bySubmission(submissionId)?.let {
            db.comparisons().update(it.copy(grade = grade))
        }
    }

    private suspend fun applyScheduleAdjustment(roundId: Long, grade: Grade) {
        val round = db.rounds().byId(roundId) ?: return
        val now = System.currentTimeMillis()
        val notifyHour = settings.get().notifyHour
        val future = db.rounds().futureOf(round.itemId, now)
        val adjustment = ReviewSchedule.adjust(round, grade, future, now, notifyHour)

        adjustment.retry?.let { db.rounds().insert(it) }
        adjustment.shifted.forEach { db.rounds().update(it) }

        if (ReviewSchedule.graduates(round, grade)) {
            db.items().setArchived(round.itemId, true, now)
        }
        ScheduleSyncer.resync(context)
    }
}
