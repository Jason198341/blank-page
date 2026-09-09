package com.blank.app.notify

import android.content.Context
import com.blank.app.data.local.AppDatabase
import com.blank.app.data.local.RoundState

/** 시각이 지난 회차를 알림으로 내보내고 리비전을 못박는 한 곳. */
object ReviewDispatcher {

    suspend fun fireDue(context: Context) {
        val db = AppDatabase.get(context)
        val now = System.currentTimeMillis()
        val due = db.rounds().overdue(now)
        for (round in due) {
            val item = db.items().byId(round.itemId) ?: continue
            if (item.archived) continue
            db.rounds().update(
                round.copy(
                    state = RoundState.NOTIFIED,
                    // 알림이 나가는 순간 채점 기준이 되는 원본을 고정한다
                    revisionId = if (round.revisionId == 0L) item.currentRevisionId else round.revisionId,
                    notifiedAt = now
                )
            )
            Notifications.show(context, round.id, item.title, round.roundIndex)
        }
    }
}
