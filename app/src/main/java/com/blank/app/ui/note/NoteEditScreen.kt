package com.blank.app.ui.note

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

private val KINDS = listOf(
    ItemKind.STRUCTURE to "구조도", ItemKind.LIST to "목록",
    ItemKind.SEQUENCE to "순서", ItemKind.VALUE to "수치"
)

@Composable
fun NoteEditScreen(
    noteId: String?,
    onDone: (String) -> Unit,
    onCancel: () -> Unit,
    vm: NoteEditViewModel = viewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(noteId) { vm.load(noteId) }

    Column(Modifier.fillMaxSize().background(Bg).imePadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCancel) {
                Icon(Icons.Filled.Close, contentDescription = "닫기", tint = TextSecondary)
            }
            Text(if (state.editingId == null) "새 노트" else "편집",
                style = MaterialTheme.typography.titleMedium, color = TextPrimary,
                modifier = Modifier.weight(1f))
            TextButton(onClick = { vm.save(onDone) }, enabled = state.canSave) {
                Text("저장", color = if (state.canSave) Accent else TextTertiary)
            }
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {

            Field(state.title, vm::onTitle, "제목", singleLine = true)
            Spacer(Modifier.height(16.dp))
            Field(state.body, vm::onBody, "마크다운으로 작성 · [[다른 노트]] 로 연결", minHeight = 240.dp)
            Spacer(Modifier.height(16.dp))

            Text("종류", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KINDS.forEach { (kind, label) ->
                    FilterChip(
                        selected = state.kind == kind, onClick = { vm.onKind(kind) },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = CardBg, labelColor = TextSecondary,
                            selectedContainerColor = Accent, selectedLabelColor = Color.White
                        )
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Field(state.tags, vm::onTags, "태그 (쉼표로 구분)", singleLine = true)

            Spacer(Modifier.height(20.dp))
            // 새 노트일 때만 참조/복습 선택 (편집은 노트 화면 스위치로)
            if (state.editingId == null) {
                BlankCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("단순 참조용", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Text("켜면 복습 스케줄에 올리지 않습니다. 끄면 오늘부터 5회 인출.",
                                style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        }
                        Switch(checked = state.referenceOnly, onCheckedChange = vm::onReferenceOnly,
                            colors = SwitchDefaults.colors(checkedTrackColor = Accent))
                    }
                }
                state.blockedReason?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = TextTertiary)
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun Field(
    value: String, onChange: (String) -> Unit, placeholder: String,
    singleLine: Boolean = false, minHeight: androidx.compose.ui.unit.Dp = 0.dp
) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().let { if (minHeight > 0.dp) it.heightIn(min = minHeight) else it },
        singleLine = singleLine,
        placeholder = { Text(placeholder, color = TextTertiary) },
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Accent, unfocusedBorderColor = Outline,
            focusedContainerColor = CardBg, unfocusedContainerColor = CardBg,
            focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Accent
        )
    )
}
