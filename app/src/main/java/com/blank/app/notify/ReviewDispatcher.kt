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
            val note = db.notes().byId(round.noteId) ?: continue
            if (note.archived || note.referenceOnly) continue
            // 알림이 나가는 순간 이 회차의 채점 기준(원본 스냅샷)을 얼린다.
            // 이후 옵시디언에서든 앱에서든 원본이 바뀌어도 이 회차는 지금 이 원본으로 채점한다.
            val revisionId = if (round.revisionId != 0L) round.revisionId else {
                val rev = (db.revisions().maxRevision(note.id) ?: 0) + 1
                db.revisions().insert(
                    com.blank.app.data.local.RevisionEntity(
                        noteId = note.id, revision = rev, title = note.title, body = note.body,
                        kind = note.kind, sheetJson = note.sheetJson, createdAt = now
                    )
                )
            }
            db.rounds().update(round.copy(state = RoundState.NOTIFIED, revisionId = revisionId, notifiedAt = now))
            Notifications.show(context, round.id, note.title, round.roundIndex)
        }
    }
}
