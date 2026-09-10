package com.blank.app.ui.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blank.app.data.local.NoteEntity
import com.blank.app.ui.common.BlankCard
import com.blank.app.ui.theme.Accent
import com.blank.app.ui.theme.Bg
import com.blank.app.ui.theme.CardBg
import com.blank.app.ui.theme.Outline
import com.blank.app.ui.theme.StateDone
import com.blank.app.ui.theme.TextPrimary
import com.blank.app.ui.theme.TextSecondary
import com.blank.app.ui.theme.TextTertiary

@Composable
fun VaultBrowserScreen(
    onOpenNote: (String) -> Unit,
    onCreate: () -> Unit,
    onSettings: () -> Unit,
    vm: VaultViewModel = viewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize().background(Bg)) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("볼트", style = MaterialTheme.typography.displaySmall,
                        color = TextPrimary, modifier = Modifier.weight(1f))
                    IconButton(onClick = vm::resync) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "동기화",
                            tint = if (state.syncing) Accent else TextSecondary)
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "설정", tint = TextSecondary)
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = vm::onQuery,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("노트 검색", color = TextTertiary) },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = TextTertiary) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions.Default,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent, unfocusedBorderColor = Outline,
                        focusedContainerColor = CardBg, unfocusedContainerColor = CardBg,
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Accent
                    )
                )
            }

            if (state.allTags.isNotEmpty()) {
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.allTags.forEach { t ->
                            FilterChip(
                                selected = state.tag == t,
                                onClick = { vm.onTag(if (state.tag == t) null else t) },
                                label = { Text("#$t") },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = CardBg, labelColor = TextSecondary,
                                    selectedContainerColor = Accent, selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }
            }

            if (state.filtered.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 64.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (state.notes.isEmpty()) "볼트가 비어 있습니다" else "검색 결과 없음",
                            color = TextTertiary)
                    }
                }
            }

            items(state.filtered, key = { it.id }) { note -> NoteRow(note) { onOpenNote(note.id) } }
        }

        FloatingActionButton(
            onClick = onCreate, containerColor = Accent,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)
        ) { Icon(Icons.Filled.Add, contentDescription = "새 노트", tint = Color.White) }
    }
}

@Composable
private fun NoteRow(note: NoteEntity, onClick: () -> Unit) {
    BlankCard(Modifier.clickable(onClick = onClick)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(note.title, style = MaterialTheme.typography.titleMedium,
                    color = if (note.missing) TextTertiary else TextPrimary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (note.referenceOnly) {
                    Text("참조", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                } else if (!note.archived) {
                    Box(Modifier.width(8.dp).height(8.dp)
                        .background(StateDone, RoundedCornerShape(4.dp)))
                }
            }
            val preview = note.body.lineSequence()
                .map { it.trim().trimStart('#', '-', '*', '>').trim() }
                .firstOrNull { it.isNotEmpty() && !it.startsWith("---") }.orEmpty()
            if (preview.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(preview, style = MaterialTheme.typography.bodyMedium, color = TextSecondary,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (note.tags.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(note.tags.split(",").filter { it.isNotBlank() }.joinToString(" ") { "#${it.trim()}" },
                    style = MaterialTheme.typography.labelMedium, color = TextTertiary)
            }
        }
    }
}
