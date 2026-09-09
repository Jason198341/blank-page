package com.blank.app.cli

import android.content.Context
import android.content.Intent
import com.blank.app.core.AppContainer

/**
 * 터미널이 보낸 명령의 실행부.
 *
 * 요청 id 가 어디로 갈지를 정한다:
 *   n<itemId>_<ts>       → 정규화 결과
 *   g<submissionId>_<ts> → 채점 결과
 */
object CliCommands {

    suspend fun run(context: Context, cmd: String, intent: Intent): String {
        val req = intent.getStringExtra("req").orEmpty()
        val repo = AppContainer.get(context).repository
        return when (cmd) {
            "status" -> {
                val text = intent.getStringExtra("text").orEmpty()
                targetSubmission(req)?.let { repo.markStatus(it, text) }
                TermuxBridge.emit(TermuxBridge.Event.Status(req, text))
                "ok"
            }

            "result" -> {
                val part = intent.getIntExtra("part", 0)
                val parts = intent.getIntExtra("parts", 1)
                val piece = intent.getStringExtra("json").orEmpty()
                val whole = TermuxBridge.collect(req, part, parts, piece)
                    ?: return "ok: part $part/$parts"
                when (val target = route(req)) {
                    is Route.Normalize -> repo.applyNormalization(target.itemId, whole)
                    is Route.Grade -> repo.applyGrading(target.submissionId, whole, whole)
                    null -> return "error: 알 수 없는 요청 id ($req)"
                }
                TermuxBridge.emit(TermuxBridge.Event.Done(req, whole))
                "ok"
            }

            "failed" -> {
                val reason = intent.getStringExtra("reason").orEmpty().ifBlank { "채점 실패" }
                targetSubmission(req)?.let { repo.markFailed(it, reason) }
                TermuxBridge.emit(TermuxBridge.Event.Failed(req, reason))
                "ok"
            }

            "ping" -> "pong"

            else -> "error: 모르는 명령 ($cmd)"
        }
    }

    private sealed class Route {
        data class Normalize(val itemId: Long) : Route()
        data class Grade(val submissionId: Long) : Route()
    }

    private fun route(req: String): Route? {
        val id = req.drop(1).substringBefore('_').toLongOrNull() ?: return null
        return when (req.firstOrNull()) {
            'n' -> Route.Normalize(id)
            'g' -> Route.Grade(id)
            else -> null
        }
    }

    private fun targetSubmission(req: String): Long? =
        (route(req) as? Route.Grade)?.submissionId
}
