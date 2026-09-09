package com.blank.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** 재부팅·앱 업데이트·시간 변경으로 알람이 날아간다. 전부 여기서 다시 건다. */
class BootReceiver : BroadcastReceiver() {

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
