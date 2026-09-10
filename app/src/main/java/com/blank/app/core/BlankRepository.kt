package com.blank.app.core

import android.content.Context
import com.blank.app.cli.TermuxBridge
import com.blank.app.data.local.AppDatabase
import com.blank.app.data.local.ComparisonEntity
import com.blank.app.data.local.Engine
import com.blank.app.data.local.Grade
import com.blank.app.data.local.ItemKind
import com.blank.app.data.local.NoteEntity
import com.blank.app.data.local.RevisionEntity
import com.blank.app.data.local.RoundState
import com.blank.app.data.local.SubmissionEntity
import com.blank.app.data.local.SubmissionKind
import com.blank.app.data.prefs.SettingsStore
import com.blank.app.data.vault.Frontmatter
import com.blank.app.data.vault.VaultStore
import com.blank.app.data.vault.VaultSync
import com.blank.app.domain.BlankJson
import com.blank.app.domain.ElementSheet
import com.blank.app.domain.GradingDetails
import com.blank.app.domain.GradingStage1
import com.blank.app.domain.Judgement
import com.blank.app.domain.ReviewSchedule
import com.blank.app.notify.Notifications
import com.blank.app.notify.ScheduleSyncer
import kotlinx.serialization.encodeToString

/**
 * 앱의 모든 쓰기가 지나는 곳. 노트 본문은 **볼트의 .md 파일이 진실**이고, Room 은 색인 +
 * 복습 상태만 든다. 그래서 노트를 고치면 파일을 먼저 쓰고 색인을 맞춘다.
 */
class BlankRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val settings: SettingsStore
) {

    init {
        // 볼트 동기화가 새 복습 노트를 발견하면 CLI 정규화를 걸도록 연결
        VaultSync.onNeedsNormalize = { noteId, title, body, kind ->
            TermuxBridge.requestNormalize(context, noteId, title, body, kind)
        }
    }

    // ------------------------------------------------------------------ 노트 만들기·고치기

    /**
     * 새 노트를 볼트에 .md 로 만든다. 앱에서 만든 노트는 복습이 기본(review=true) —
     * 참조용으로 두려면 [referenceOnly] 를 켠다.
     */
    suspend fun createNote(
        title: String,
        body: String,
        kind: ItemKind,
        tags: List<String>,
        referenceOnly: Boolean,
        folder: String
    ): String? {
        val now = System.currentTimeMillis()
        val id = java.util.UUID.randomUUID().toString()
        val content = Frontmatter.serialize(id, !referenceOnly, tags, kind, now, ensureHeading(title, body))
        val fileName = sanitize(title)
        val vf = VaultStore.create(context, folder, fileName, content) ?: return null

        db.notes().upsert(
            NoteEntity(
                id = id, relPath = vf.relPath, title = title, body = body, kind = kind,
                tags = tags.joinToString(","), referenceOnly = referenceOnly,
                linkTitlesJson = BlankJson.encodeToString(Frontmatter.linkTitles(body)),
                fileMtime = vf.lastModified, contentHash = ("---$body").hashCode().toString(),
                createdAt = now, updatedAt = now
            )
        )
        if (!referenceOnly) {
            db.rounds().insertAll(ReviewSchedule.plan(id, now, settings.get().notifyHour))
            db.notes().update(db.notes().byId(id)!!.copy(scheduled = true))
            ScheduleSyncer.resync(context)
            TermuxBridge.requestNormalize(context, id, title, body, kind.name.lowercase())
        }
        return id
    }

    /** 노트 본문을 고쳐 파일에 되쓴다. 색인도 갱신하고 정규화를 다시 건다. */
    suspend fun editNote(noteId: String, title: String, body: String, kind: ItemKind, tags: List<String>) {
        val note = db.notes().byId(noteId) ?: return
        val now = System.currentTimeMillis()
        val content = Frontmatter.serialize(noteId, !note.referenceOnly, tags, kind, note.createdAt, ensureHeading(title, body))
        VaultStore.findByPath(context, note.relPath)?.let { VaultStore.write(context, it.uri, content) }
        db.notes().update(
            note.copy(
                title = title, body = body, kind = kind, tags = tags.joinToString(","),
                normalized = false,
                linkTitlesJson = BlankJson.encodeToString(Frontmatter.linkTitles(body)),
                contentHash = content.hashCode().toString(), updatedAt = now
            )
        )
        if (!note.referenceOnly && body.isNotBlank()) {
            TermuxBridge.requestNormalize(context, noteId, title, body, kind.name.lowercase())
        }
    }

    /**
     * 단순 참조용 ↔ 복습 대상 전환.
     *  - 참조용으로: 예정 회차를 접는다. 파일 프론트매터 review:false.
     *  - 복습으로: 회차가 없으면 오늘부터 새 5회차. 파일 review:true + 정규화.
     */
    suspend fun setReferenceOnly(noteId: String, referenceOnly: Boolean) {
        val note = db.notes().byId(noteId) ?: return
        val now = System.currentTimeMillis()
        db.notes().update(note.copy(referenceOnly = referenceOnly, updatedAt = now))

        // 파일 프론트매터 되쓰기
        VaultStore.findByPath(context, note.relPath)?.let { doc ->
            val content = Frontmatter.serialize(
                noteId, !referenceOnly, note.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                note.kind, note.createdAt, ensureHeading(note.title, note.body)
            )
            VaultStore.write(context, doc.uri, content)
        }

        if (referenceOnly) {
            db.rounds().skipPendingOf(noteId)
        } else if (db.rounds().countForNote(noteId) == 0) {
            db.rounds().insertAll(ReviewSchedule.plan(noteId, now, settings.get().notifyHour))
            db.notes().update(db.notes().byId(noteId)!!.copy(scheduled = true))
            if (note.body.isNotBlank())
                TermuxBridge.requestNormalize(context, noteId, note.title, note.body, note.kind.name.lowercase())
        }
        ScheduleSyncer.resync(context)
    }

    // ------------------------------------------------------------------ 정규화 결과

    suspend fun applyNormalization(noteId: String, json: String) {
        val sheet = runCatching { BlankJson.decodeFromString<ElementSheet>(json) }.getOrNull() ?: return
        if (sheet.elements.isEmpty()) return
        val note = db.notes().byId(noteId) ?: return
        db.notes().update(note.copy(sheetJson = BlankJson.encodeToString(sheet), normalized = true))
    }

    // ------------------------------------------------------------------ 제출

    suspend fun submit(
        roundId: Long, text: String, imagePath: String?,
        usedHint: Boolean, elapsedMs: Long, gaveUp: Boolean
    ): Long {
        val round = db.rounds().byId(roundId) ?: return -1
        val now = System.currentTimeMillis()
        val kind = when {
            gaveUp -> SubmissionKind.GAVE_UP
            imagePath != null && text.isNotBlank() -> SubmissionKind.BOTH
            imagePath != null -> SubmissionKind.IMAGE
            else -> SubmissionKind.TEXT
        }
        // 알림 전에 들어온 회차면 지금 원본을 스냅샷으로 얼린다
        val revisionId = if (round.revisionId != 0L) round.revisionId else freeze(round.noteId, now)
        val submissionId = db.submissions().insert(
            SubmissionEntity(
                roundId = roundId, noteId = round.noteId, kind = kind, text = text,
                imagePath = imagePath, usedHint = usedHint, elapsedMs = elapsedMs, submittedAt = now
            )
        )
        db.rounds().update(round.copy(state = RoundState.DONE, revisionId = revisionId, completedAt = now))
        Notifications.cancel(context, roundId)
        ScheduleSyncer.resync(context)

        val sheetJson = db.revisions().byId(revisionId)?.sheetJson.orEmpty()
        db.comparisons().insert(
            ComparisonEntity(
                submissionId = submissionId, roundId = roundId,
                total = runCatching { BlankJson.decodeFromString<ElementSheet>(sheetJson).elements.size }.getOrDefault(0),
                engine = Engine.CLI, status = "PENDING", createdAt = now
            )
        )
        val recall = if (gaveUp) "모르겠습니다. 아무것도 기억나지 않습니다." else text
        TermuxBridge.requestGrade(context, submissionId, sheetJson, recall, imagePath)
            .getOrNull()?.note?.let { note ->
                db.comparisons().bySubmission(submissionId)?.let { db.comparisons().update(it.copy(failReason = note)) }
            }
        return submissionId
    }

    /** 현재 노트 본문·요소표를 리비전으로 얼린다 */
    private suspend fun freeze(noteId: String, now: Long): Long {
        val note = db.notes().byId(noteId) ?: return 0
        val rev = (db.revisions().maxRevision(noteId) ?: 0) + 1
        return db.revisions().insert(
            RevisionEntity(noteId = noteId, revision = rev, title = note.title, body = note.body,
                kind = note.kind, sheetJson = note.sheetJson, createdAt = now)
        )
    }

    // ------------------------------------------------------------------ 채점 결과

    suspend fun applyGrading(submissionId: Long, json: String, raw: String) {
        val comparison = db.comparisons().bySubmission(submissionId) ?: return
        val result = runCatching { BlankJson.decodeFromString<GradingStage1>(json) }.getOrNull()
            ?: run { markFailed(submissionId, "채점 결과를 읽지 못했습니다"); return }
        if (result.judgements.isEmpty()) { markFailed(submissionId, "판정이 비어 있습니다"); return }

        val sheet = sheetFor(comparison.roundId)
        val grade = result.gradeFor(sheet)
        db.comparisons().update(
            comparison.copy(
                hit = result.hits(),
                total = if (sheet.elements.isNotEmpty()) sheet.elements.size else result.judgements.size,
                score = result.computeScore(sheet), grade = grade, suggestedGrade = grade,
                judgementsJson = BlankJson.encodeToString(result.judgements),
                extrasJson = BlankJson.encodeToString(result.extras),
                status = "OK", failReason = null, rawText = raw
            )
        )
        applyScheduleAdjustment(comparison.roundId, grade)
    }

    suspend fun applyGradingDetails(submissionId: Long, json: String) {
        val comparison = db.comparisons().bySubmission(submissionId) ?: return
        val details = runCatching { BlankJson.decodeFromString<GradingDetails>(json) }.getOrNull() ?: return
        val judgements = runCatching {
            BlankJson.decodeFromString<List<Judgement>>(comparison.judgementsJson)
        }.getOrDefault(emptyList())
        val byId = details.details.associateBy { it.elementId }
        val merged = judgements.map { j -> byId[j.elementId]?.let { j.copy(reason = it.reason, quote = it.quote) } ?: j }
        db.comparisons().update(
            comparison.copy(
                judgementsJson = BlankJson.encodeToString(merged),
                transcript = details.transcript, summary = details.summary,
                advice = details.advice, reviewOriginal = details.reviewOriginal
            )
        )
    }

    suspend fun markFailed(submissionId: Long, reason: String) {
        db.comparisons().bySubmission(submissionId)?.let { db.comparisons().update(it.copy(status = "FAILED", failReason = reason)) }
    }

    suspend fun markStatus(submissionId: Long, text: String) {
        db.comparisons().bySubmission(submissionId)?.let { db.comparisons().update(it.copy(failReason = text)) }
    }

    suspend fun overrideGrade(submissionId: Long, grade: Grade) {
        db.comparisons().bySubmission(submissionId)?.let { db.comparisons().update(it.copy(grade = grade)) }
    }

    private suspend fun sheetFor(roundId: Long): ElementSheet {
        val round = db.rounds().byId(roundId)
        val revisionId = round?.revisionId?.takeIf { it != 0L }
        val sheetJson = revisionId?.let { db.revisions().byId(it)?.sheetJson }
            ?: db.notes().byId(round?.noteId ?: "")?.sheetJson.orEmpty()
        return runCatching { BlankJson.decodeFromString<ElementSheet>(sheetJson) }.getOrDefault(ElementSheet())
    }

    private suspend fun applyScheduleAdjustment(roundId: Long, grade: Grade) {
        val round = db.rounds().byId(roundId) ?: return
        val now = System.currentTimeMillis()
        val notifyHour = settings.get().notifyHour
        val future = db.rounds().futureOf(round.noteId, now)
        val adj = ReviewSchedule.adjust(round, grade, future, now, notifyHour)
        adj.retry?.let { db.rounds().insert(it) }
        adj.shifted.forEach { db.rounds().update(it) }
        if (ReviewSchedule.graduates(round, grade)) db.notes().setArchived(round.noteId, true, now)
        ScheduleSyncer.resync(context)
    }

    // ------------------------------------------------------------------ 유틸

    private fun ensureHeading(title: String, body: String): String =
        if (body.lineSequence().any { it.trimStart().startsWith("#") }) body else "# $title\n\n$body"

    private fun sanitize(title: String): String =
        title.replace(Regex("""[/\\:*?"<>|]"""), " ").trim().ifBlank { "note" }.take(60)

}
