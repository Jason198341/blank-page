package com.blank.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 알람이 울리면 밀린 회차를 전부 알림으로 띄우고, 다음 알람을 다시 건다.
 *
 * 알림을 띄우는 순간 그 회차가 붙들 원본 리비전이 확정된다 — 이후 사용자가 원본을
 * 고쳐도 이 회차의 채점 기준은 지금 이 원본이다.
 */
class ReviewAlarmReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        scope.launch {
            runCatching { ReviewDispatcher.fireDue(app) }
            runCatching { ScheduleSyncer.resync(app) }
            runCatching { pending.finish() }
        }
    }
}
