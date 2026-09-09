package com.blank.app.ui.item

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blank.app.ui.common.BlankCard
import com.blank.app.ui.common.ProgressDots
import com.blank.app.ui.theme.Accent
import com.blank.app.ui.theme.Bg
import com.blank.app.ui.theme.TextPrimary
import com.blank.app.ui.theme.TextSecondary
import com.blank.app.ui.theme.TextTertiary

@Composable
fun LibraryScreen(
    onOpenItem: (Long) -> Unit,
    onCreate: () -> Unit,
    onSettings: () -> Unit,
    vm: LibraryViewModel = viewModel()
) {
    val rows by vm.rows.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize().background(Bg)) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("서재", style = MaterialTheme.typography.displaySmall,
                        color = TextPrimary, modifier = Modifier.weight(1f))
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "설정", tint = TextSecondary)
                    }
                }
            }

            if (rows.isEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 64.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("아직 등록한 지식이 없습니다", color = TextTertiary)
                    }
                }
            }

            items(rows, key = { it.item.id }) { row ->
                BlankCard(Modifier.clickable { onOpenItem(row.item.id) }) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                row.item.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (row.item.archived) TextTertiary else TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            if (row.item.archived) {
                                Text("졸업", style = MaterialTheme.typography.labelMedium, color = TextTertiary)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ProgressDots(row.done)
                            Spacer(Modifier.weight(1f))
                            Text("${row.done} / 5 회차",
                                style = MaterialTheme.typography.labelMedium, color = TextTertiary)
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = onCreate,
            containerColor = Accent,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)
        ) { Icon(Icons.Filled.Add, contentDescription = "새 지식", tint = Color.White) }
    }
}
