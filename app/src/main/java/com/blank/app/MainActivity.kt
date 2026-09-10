package com.blank.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.blank.app.data.vault.VaultStore
import com.blank.app.data.vault.VaultSync
import com.blank.app.ui.nav.BlankNav
import com.blank.app.ui.theme.BlankTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val pendingRoundId = mutableStateOf<Long?>(null)
    private val hasVault = mutableStateOf(false)

    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    /** SAF 폴더 선택. 고른 트리를 영구 권한으로 붙들고 첫 동기화를 돌린다. */
    private val pickVault =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            if (uri != null) {
                VaultStore.remember(this, uri)
                hasVault.value = true
                lifecycleScope.launch(Dispatchers.IO) { runCatching { VaultSync.sync(this@MainActivity) } }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hasVault.value = VaultStore.hasVault(this)
        pendingRoundId.value = intent.roundId()
        requestNotificationPermission()
        setContent {
            BlankTheme {
                BlankNav(
                    hasVault = hasVault.value,
                    onPickVault = { pickVault.launch(null) },
                    openRoundId = pendingRoundId.value,
                    onOpenRoundConsumed = { pendingRoundId.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingRoundId.value = intent.roundId()
    }

    private fun Intent.roundId(): Long? = getLongExtra(EXTRA_ROUND_ID, -1L).takeIf { it > 0 }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    companion object {
        const val EXTRA_ROUND_ID = "round_id"
    }
}
