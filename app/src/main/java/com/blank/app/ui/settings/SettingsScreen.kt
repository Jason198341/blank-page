package com.blank.app.ui.settings

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blank.app.cli.TermuxBridge
import com.blank.app.core.AppContainer
import com.blank.app.data.prefs.Settings
import com.blank.app.notify.ScheduleSyncer
import com.blank.app.ui.common.BlankCard
import com.blank.app.ui.theme.Accent
import com.blank.app.ui.theme.Bg
import com.blank.app.ui.theme.StateDone
import com.blank.app.ui.theme.StateMissed
import com.blank.app.ui.theme.TextPrimary
import com.blank.app.ui.theme.TextSecondary
import com.blank.app.ui.theme.TextTertiary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val container = AppContainer.get(app)
    val settings = container.settings.flow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Settings())

    fun setHour(hour: Int) = viewModelScope.launch {
        container.settings.setNotifyTime(hour, 0)
        // 시각을 바꾸면 이미 걸린 알람이 옛 시각을 가리킨다. 즉시 다시 건다.
        ScheduleSyncer.resync(getApplication())
    }

    fun setCap(v: Int) = viewModelScope.launch { container.settings.setDailyCap(v) }
    fun setExpire(v: Boolean) = viewModelScope.launch { container.settings.setExpireMissed(v) }
    fun setHint(v: Boolean) = viewModelScope.launch { container.settings.setAllowHint(v) }
    fun setLimit(v: Int) = viewModelScope.launch { container.settings.setActiveLimit(v) }
}

@Composable
fun SettingsScreen(onBack: () -> Unit, vm: SettingsViewModel = viewModel()) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val termuxNote = TermuxBridge.readiness(context)
    val exactOk = ScheduleSyncer.canScheduleExact(context)

    Column(Modifier.fillMaxSize().background(Bg)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "뒤로", tint = TextSecondary)
            }
            Text("설정", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                BlankCard {
                    Column {
                        SectionTitle("알림 시각")
                        Text(
                            "매일 ${settings.notifyHour}시에 그 날 인출할 것을 알립니다. " +
                                "등록 당일(D0)은 시각과 상관없이 바로 시작합니다.",
                            style = MaterialTheme.typography.bodyMedium, color = TextSecondary
                        )
                        Spacer(Modifier.height(8.dp))
                        Slider(
                            value = settings.notifyHour.toFloat(),
                            onValueChange = { vm.setHour(it.toInt()) },
                            valueRange = 5f..23f,
                            steps = 17
                        )
                        if (!exactOk) {
                            Text(
                                "정확 알람이 꺼져 있습니다. 알림이 최대 15분 밀릴 수 있습니다.",
                                style = MaterialTheme.typography.labelMedium, color = StateMissed
                            )
                            TextButton(
                                onClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                        runCatching {
                                            context.startActivity(
                                                Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                                    .setData(Uri.parse("package:${context.packageName}"))
                                            )
                                        }
                                    }
                                },
                                contentPadding = PaddingValues(0.dp)
                            ) { Text("정확 알람 허용하기", color = Accent) }
                        }
                    }
                }
            }

            item {
                BlankCard {
                    Column {
                        SectionTitle("하루 상한")
                        Text(
                            "하루에 ${settings.dailyCap}개까지만 펼쳐 보여줍니다. " +
                                "넘치는 것은 사라지지 않고 다음 날로 넘어갑니다.",
                            style = MaterialTheme.typography.bodyMedium, color = TextSecondary
                        )
                        Slider(
                            value = settings.dailyCap.toFloat(),
                            onValueChange = { vm.setCap(it.toInt()) },
                            valueRange = 3f..20f,
                            steps = 16
                        )
                    }
                }
            }

            item {
                BlankCard {
                    Column {
                        ToggleRow(
                            "놓친 회차를 만료 처리",
                            "끄면 밀린 회차가 계속 오늘 목록에 남습니다(기본). 켜면 그 날이 지나면 사라집니다.",
                            settings.expireMissed, vm::setExpire
                        )
                        Spacer(Modifier.height(16.dp))
                        ToggleRow(
                            "힌트 1회 허용",
                            "재현 화면에서 항목 수와 첫 항목 이름만 보여줍니다. 감점하지 않고 기록만 남깁니다.",
                            settings.allowHint, vm::setHint
                        )
                    }
                }
            }

            item {
                BlankCard {
                    Column {
                        SectionTitle("동시 항목 상한")
                        Text(
                            "${settings.activeLimit}개. 이 앱이 플래시카드 더미로 퇴화하지 않게 막는 장치입니다. " +
                                "상한에 닿으면 하나를 졸업시켜야 새로 담을 수 있습니다.",
                            style = MaterialTheme.typography.bodyMedium, color = TextSecondary
                        )
                        Slider(
                            value = settings.activeLimit.toFloat(),
                            onValueChange = { vm.setLimit(it.toInt()) },
                            valueRange = 5f..60f,
                            steps = 54
                        )
                    }
                }
            }

            item {
                BlankCard {
                    Column {
                        SectionTitle("채점기 (Termux)")
                        Text(
                            termuxNote ?: "Termux 연결됨 · 폰의 claude 로 무료 채점합니다",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (termuxNote == null) StateDone else StateMissed
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "터미널에서 한 번만: ln -sf ~/blank-page/tools/blank ~/bin/blank\n" +
                                "채점이 밀리면: blank drain",
                            style = MaterialTheme.typography.labelMedium, color = TextTertiary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = TextPrimary,
        modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
private fun ToggleRow(title: String, desc: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!value) }) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text(desc, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
        Switch(
            checked = value, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Accent)
        )
    }
}
