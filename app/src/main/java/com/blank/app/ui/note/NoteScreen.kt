package com.blank.app.ui.note

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blank.app.ui.common.BlankCard
import com.blank.app.ui.common.RoundBadge
import com.blank.app.ui.theme.Accent
import com.blank.app.ui.theme.BgReading
import com.blank.app.ui.theme.TextPrimary
import com.blank.app.ui.theme.TextSecondary
import com.blank.app.ui.theme.TextTertiary
import com.blank.app.util.Dates
import kotlinx.coroutines.launch

@Composable
fun NoteScreen(
    noteId: String,
    onOpenNote: (String) -> Unit,
    onEdit: (String) -> Unit,
    onBack: () -> Unit,
    vm: NoteViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(noteId) { vm.load(noteId) }
    val note = state.note

    Column(Modifier.fillMaxSize().background(BgReading)) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "뒤로", tint = TextSecondary)
            }
            Text(note?.title.orEmpty(), style = MaterialTheme.typography.titleMedium,
                color = TextPrimary, modifier = Modifier.weight(1f))
            IconButton(onClick = { onEdit(noteId) }) {
                Icon(Icons.Outlined.Edit, contentDescription = "편집", tint = TextSecondary)
            }
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)) {

            note ?: return@Column

            // 복습/참조 전환
            BlankCard(padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (note.referenceOnly) "단순 참조용" else "복습 대상",
                            style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text(if (note.referenceOnly) "복습 스케줄에 올라가지 않습니다"
                            else "오늘·3·10·30·90일 인출로 각인합니다",
                            style = MaterialTheme.typography.labelMedium, color = TextTertiary)
                    }
                    // 스위치 ON = 복습 대상 (참조용의 반대)
                    Switch(
                        checked = !note.referenceOnly,
                        onCheckedChange = { vm.setReferenceOnly(!it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = Accent)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // 복습 진행 (복습 노트일 때만)
            if (!note.referenceOnly && state.rounds.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.rounds.take(5).forEach { RoundBadge(it.roundIndex, it.attempt) }
                }
                Text("다음 인출 · ${state.rounds.minByOrNull { it.dueAt }?.let { Dates.formatDay(Dates.toDate(it.dueAt)) } ?: "-"}",
                    style = MaterialTheme.typography.labelMedium, color = TextTertiary,
                    modifier = Modifier.padding(top = 6.dp))
                Spacer(Modifier.height(12.dp))
            }

            // 본문 (마크다운)
            MarkdownView(
                body = note.body,
                onWikiLink = { title ->
                    scope.launch {
                        val id = vm.resolveLink(title)
                        if (id != null) onOpenNote(id)
                        else vm.createLinked(title) { onOpenNote(it) }
                    }
                },
                onUrl = { url ->
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
                }
            )

            // 백링크
            if (state.backlinks.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                Text("이 노트를 가리키는 곳", style = MaterialTheme.typography.labelLarge, color = TextTertiary)
                Spacer(Modifier.height(8.dp))
                state.backlinks.forEach { bl ->
                    BlankCard(Modifier.clickable { onOpenNote(bl.id) }.padding(bottom = 8.dp),
                        padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(bl.title, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                    }
                }
            }
            Spacer(Modifier.height(48.dp))
        }
    }
}
