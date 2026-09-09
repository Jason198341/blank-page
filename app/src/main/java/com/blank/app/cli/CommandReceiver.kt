package com.blank.app.cli

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 터미널 → 앱 입구.
 *
 *   am broadcast -n com.blank.app/.cli.CommandReceiver -a com.blank.app.CMD \
 *       --include-stopped-packages -e cmd result -e req g12_1757... \
 *       --ei part 0 --ei parts 1 -e json '{...}'
 *
 * 매니페스트가 이 클래스를 가리키므로 지우면 브로드캐스트가 올 때마다 앱이 죽는다.
 * 실행부는 [CliCommands] 에 있고 여기서는 받아 넘기기만 한다.
 */
class CommandReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val cmd = intent.getStringExtra("cmd").orEmpty().ifBlank { "status" }
        val pending = goAsync()
        val app = context.applicationContext
        scope.launch {
            val out = runCatching { CliCommands.run(app, cmd, intent) }
                .getOrElse { "error: ${it.message}" }
            runCatching { pending.setResult(if (out.startsWith("error")) 1 else 0, out, null) }
            runCatching { pending.finish() }
        }
    }
}
