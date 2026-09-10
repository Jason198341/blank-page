package com.blank.app.cli

import android.content.Context
import android.content.Intent
import com.blank.app.core.AppContainer

/**
 * 터미널이 보낸 명령의 실행부.
 *
 *   n<noteId(uuid)>_<ts>    → 정규화 결과 (noteId 는 String)
 *   g<submissionId>_<ts>    → 채점 결과   (submissionId 는 Long)
 */
object CliCommands {

    suspend fun run(context: Context, cmd: String, intent: Intent): String {
        val req = intent.getStringExtra("req").orEmpty()
        val repo = AppContainer.get(context).repository
        return when (cmd) {
            "status" -> {
                val text = intent.getStringExtra("text").orEmpty()
                (route(req) as? Route.Grade)?.let { repo.markStatus(it.submissionId, text) }
                TermuxBridge.emit(TermuxBridge.Event.Status(req, text))
                "ok"
            }

            "result" -> {
                val part = intent.getIntExtra("part", 0)
                val parts = intent.getIntExtra("parts", 1)
                val piece = intent.getStringExtra("json").orEmpty()
                val stageOf = intent.getIntExtra("stage", 1)
                val whole = TermuxBridge.collect(req, part, parts, piece, stageOf)
                    ?: return "ok: part $part/$parts"
                when (val target = route(req)) {
                    is Route.Normalize -> repo.applyNormalization(target.noteId, whole)
                    is Route.Grade ->
                        if (stageOf >= 2) repo.applyGradingDetails(target.submissionId, whole)
                        else repo.applyGrading(target.submissionId, whole, whole)
                    null -> return "error: 알 수 없는 요청 id ($req)"
                }
                TermuxBridge.emit(TermuxBridge.Event.Done(req, whole))
                "ok"
            }

            "failed" -> {
                val reason = intent.getStringExtra("reason").orEmpty().ifBlank { "채점 실패" }
                (route(req) as? Route.Grade)?.let { repo.markFailed(it.submissionId, reason) }
                TermuxBridge.emit(TermuxBridge.Event.Failed(req, reason))
                "ok"
            }

            "ping" -> {
                TermuxBridge.writeStatus(context, "pong ${System.currentTimeMillis()}\n")
                "pong"
            }

            "dump" -> {
                val db = AppContainer.get(context).db
                val now = System.currentTimeMillis()
                val notes = db.notes().activeReviewCount()
                val due = db.rounds().overdue(now)
                val pending = db.comparisons().pending()
                val body = buildString {
                    append("blank app status @ ").append(now).append('\n')
                    append("복습 노트: ").append(notes).append('\n')
                    append("시각 지난 회차: ").append(due.size).append('\n')
                    append("채점 대기: ").append(pending.size).append('\n')
                    due.take(10).forEach {
                        append("  round#").append(it.id).append(" note=").append(it.noteId.take(8))
                            .append(" 회차=").append(it.roundIndex).append(" 상태=").append(it.state).append('\n')
                    }
                    pending.take(10).forEach {
                        append("  cmp#").append(it.id).append(" sub=").append(it.submissionId)
                            .append(" 상태=").append(it.status).append(" 사유=").append(it.failReason ?: "-").append('\n')
                    }
                }
                TermuxBridge.writeStatus(context, body)
                "ok"
            }

            else -> "error: 모르는 명령 ($cmd)"
        }
    }

    private sealed class Route {
        data class Normalize(val noteId: String) : Route()
        data class Grade(val submissionId: Long) : Route()
    }

    private fun route(req: String): Route? {
        if (req.length < 2) return null
        val payload = req.drop(1).substringBeforeLast('_')
        return when (req.firstOrNull()) {
            'n' -> payload.takeIf { it.isNotBlank() }?.let { Route.Normalize(it) }
            'g' -> payload.toLongOrNull()?.let { Route.Grade(it) }
            else -> null
        }
    }
}
