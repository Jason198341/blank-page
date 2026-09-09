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
                val stageOf = intent.getIntExtra("stage", 1)
                val whole = TermuxBridge.collect(req, part, parts, piece, stageOf)
                    ?: return "ok: part $part/$parts"
                // stage 1 = 판정(점수·일정이 여기서 정해진다), stage 2 = 설명만 채우기
                when (val target = route(req)) {
                    is Route.Normalize -> repo.applyNormalization(target.itemId, whole)
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
                targetSubmission(req)?.let { repo.markFailed(it, reason) }
                TermuxBridge.emit(TermuxBridge.Event.Failed(req, reason))
                "ok"
            }

            "ping" -> {
                TermuxBridge.writeStatus(context, "pong ${System.currentTimeMillis()}\n")
                "pong"
            }

            // 터미널에서 앱 안을 들여다보는 유일한 창. 브로드캐스트가 실제로 앱에
            // 닿았는지, 채점 결과가 DB 에 들어갔는지를 여기서 확인한다.
            "dump" -> {
                val db = AppContainer.get(context).db
                val now = System.currentTimeMillis()
                val items = db.items().activeCount()
                val due = db.rounds().overdue(now)
                val pending = db.comparisons().pending()
                val body = buildString {
                    append("blank app status @ ").append(now).append('\n')
                    append("살아 있는 지식: ").append(items).append('\n')
                    append("시각 지난 회차: ").append(due.size).append('\n')
                    append("채점 대기: ").append(pending.size).append('\n')
                    due.take(10).forEach {
                        append("  round#").append(it.id)
                            .append(" item=").append(it.itemId)
                            .append(" 회차=").append(it.roundIndex)
                            .append(" 상태=").append(it.state)
                            .append(" 리비전=").append(it.revisionId).append('\n')
                    }
                    pending.take(10).forEach {
                        append("  cmp#").append(it.id)
                            .append(" sub=").append(it.submissionId)
                            .append(" 상태=").append(it.status)
                            .append(" 사유=").append(it.failReason ?: "-").append('\n')
                    }
                }
                TermuxBridge.writeStatus(context, body)
                "ok"
            }

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
