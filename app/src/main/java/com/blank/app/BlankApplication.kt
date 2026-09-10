package com.blank.app

import android.app.Application
import com.blank.app.core.AppContainer
import com.blank.app.notify.Notifications
import com.blank.app.notify.ReviewDispatcher
import com.blank.app.data.vault.VaultStore
import com.blank.app.data.vault.VaultSync
import com.blank.app.notify.ScheduleSyncer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BlankApplication : Application() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        AppContainer.get(this)
        Notifications.ensureChannel(this)
        scope.launch {
            // 옵시디언에서 고친 게 있을 수 있으니 볼트를 먼저 훑고,
            // 앱이 꺼져 있는 동안 지나간 회차를 주워 담고 다음 알람을 다시 건다
            if (VaultStore.hasVault(this@BlankApplication))
                runCatching { VaultSync.sync(this@BlankApplication) }
            runCatching { ReviewDispatcher.fireDue(this@BlankApplication) }
            runCatching { ScheduleSyncer.resync(this@BlankApplication) }
        }
    }
}
