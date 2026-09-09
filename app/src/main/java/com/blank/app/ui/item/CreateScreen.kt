package com.blank.app.ui.item

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blank.app.data.local.ItemKind
import com.blank.app.ui.common.BlankCard
import com.blank.app.ui.theme.Accent
import com.blank.app.ui.theme.Bg
import com.blank.app.ui.theme.CardBg
import com.blank.app.ui.theme.Outline
import com.blank.app.ui.theme.TextPrimary
import com.blank.app.ui.theme.TextSecondary
import com.blank.app.ui.theme.TextTertiary

private val KIND_LABELS = listOf(
    ItemKind.STRUCTURE to "구조도",
    ItemKind.LIST to "목록",
    ItemKind.SEQUENCE to "순서",
    ItemKind.VALUE to "수치·컷오프"
)

/** 필드는 최대 3개. 오늘 등록하는 마찰이 이 앱의 목숨이다. */
@Composable
fun CreateScreen(onDone: () -> Unit, vm: CreateViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().background(Bg).imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDone) {
                Icon(Icons.Filled.Close, contentDescription = "닫기", tint = TextSecondary)
            }
            Text("새 지식", style = MaterialTheme.typography.titleMedium,
                color = TextPrimary, modifier = Modifier.weight(1f))
            TextButton(onClick = { vm.save(onDone) }, enabled = state.canSave) {
                Text("저장", color = if (state.canSave) Accent else TextTertiary)
            }
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
        ) {
            state.blockedReason?.let {
                BlankCard {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
                Spacer(Modifier.height(20.dp))
            }

            Label("제목 (재현할 때 유일한 단서)")
            Field(state.title, vm::onTitle, "5필러 막대와 컷오프 선", singleLine = true)

            Spacer(Modifier.height(20.dp))
            Label("원본")
            Field(state.body, vm::onBody, "한 줄에 하나씩", minHeight = 200.dp)
            Text(
                "이 원본이 채점 기준이 됩니다. 등록 직후 터미널의 claude 가 채점 가능한 요소로 쪼갭니다.",
                style = MaterialTheme.typography.labelMedium, color = TextTertiary,
                modifier = Modifier.padding(top = 6.dp)
            )

            Spacer(Modifier.height(20.dp))
            Label("종류 — 무엇을 '맞았다'고 볼지가 달라집니다")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KIND_LABELS.forEach { (kind, label) ->
                    FilterChip(
                        selected = state.kind == kind,
                        onClick = { vm.onKind(kind) },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = CardBg,
                            labelColor = TextSecondary,
                            selectedContainerColor = Accent,
                            selectedLabelColor = androidx.compose.ui.graphics.Color.White
                        )
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Label("태그 (선택)")
            Field(state.tags, vm::onTags, "쉼표로 구분", singleLine = true)

            Spacer(Modifier.height(28.dp))
            Text(
                "저장하면 오늘 · 3일 · 10일 · 30일 · 90일, 딱 5회 일정이 자동으로 잡힙니다.",
                style = MaterialTheme.typography.bodyMedium, color = TextTertiary
            )
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = TextSecondary,
        modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    singleLine: Boolean = false,
    minHeight: androidx.compose.ui.unit.Dp = 0.dp
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().let { if (minHeight > 0.dp) it.heightIn(min = minHeight) else it },
        singleLine = singleLine,
        placeholder = { Text(placeholder, color = TextTertiary) },
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Accent,
            unfocusedBorderColor = Outline,
            focusedContainerColor = CardBg,
            unfocusedContainerColor = CardBg,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            cursorColor = Accent
        )
    )
}
