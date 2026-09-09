package com.blank.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import com.blank.app.ui.nav.BlankNav
import com.blank.app.ui.theme.BlankTheme

class MainActivity : ComponentActivity() {

    private val pendingRoundId = mutableStateOf<Long?>(null)

    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // targetSdk 35 · Android 15 부터는 edge-to-edge 가 강제라
        // setDecorFitsSystemWindows(true) 는 무시된다. 여백은 Scaffold 가 준다.
        enableEdgeToEdge()
        pendingRoundId.value = intent.roundId()
        requestNotificationPermission()
        setContent {
            BlankTheme {
                BlankNav(
                    openRoundId = pendingRoundId.value,
                    onOpenRoundConsumed = { pendingRoundId.value = null }
                )
            }
        }
    }

    /** 알림을 탭해서 들어온 경우 — 오늘 화면을 거치지 않고 재현 화면으로 직행한다. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingRoundId.value = intent.roundId()
    }

    private fun Intent.roundId(): Long? =
        getLongExtra(EXTRA_ROUND_ID, -1L).takeIf { it > 0 }

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
