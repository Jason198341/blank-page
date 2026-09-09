package com.blank.app.ui.item

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blank.app.data.local.Grade
import com.blank.app.data.local.RoundState
import com.blank.app.ui.common.BlankCard
import com.blank.app.ui.common.GradeChip
import com.blank.app.ui.common.RoundBadge
import com.blank.app.ui.theme.Accent
import com.blank.app.ui.theme.Bg
import com.blank.app.ui.theme.TextPrimary
import com.blank.app.ui.theme.TextSecondary
import com.blank.app.ui.theme.TextTertiary
import com.blank.app.util.Dates

@Composable
fun KnowledgeScreen(
    itemId: Long,
    onOpenResult: (Long) -> Unit,
    onBack: () -> Unit,
    vm: KnowledgeViewModel = viewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(itemId) { vm.load(itemId) }

    Column(Modifier.fillMaxSize().background(Bg)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "뒤로", tint = TextSecondary)
            }
            Text(
                state.item?.title.orEmpty(), style = MaterialTheme.typography.titleMedium,
                color = TextPrimary, modifier = Modifier.weight(1f)
            )
            state.item?.let { item ->
                TextButton(onClick = { vm.archive(!item.archived) }) {
                    Text(if (item.archived) "되살리기" else "접기", color = TextTertiary)
                }
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                if (state.originalOpen) {
                    BlankCard {
                        Column {
                            Text("원본", style = MaterialTheme.typography.labelLarge, color = TextTertiary)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                state.item?.body.orEmpty(),
                                style = MaterialTheme.typography.bodyLarge, color = TextPrimary
                            )
                        }
                    }
                } else {
                    BlankCard {
                        Column {
                            Text(
                                "원본은 예정된 회차가 남아 있는 동안 잠깁니다.",
                                style = MaterialTheme.typography.bodyMedium, color = TextSecondary
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "지금 열면 다음 인출은 연습이 아니라 확인이 됩니다.",
                                style = MaterialTheme.typography.labelMedium, color = TextTertiary
                            )
                            Spacer(Modifier.height(10.dp))
                            TextButton(onClick = vm::openOriginal, contentPadding = PaddingValues(0.dp)) {
                                Text("그래도 열기", color = TextTertiary)
                            }
                        }
                    }
                }
            }

            item {
                Text("회차", style = MaterialTheme.typography.labelLarge, color = TextTertiary)
            }

            items(state.rounds, key = { it.id }) { round ->
                val submissionId = state.submissionByRound[round.id]
                BlankCard(
                    Modifier.clickable(enabled = submissionId != null) {
                        submissionId?.let(onOpenResult)
                    },
                    padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RoundBadge(round.roundIndex, round.attempt)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            Dates.formatDay(Dates.toDate(round.dueAt)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary, modifier = Modifier.weight(1f)
                        )
                        when {
                            round.state == RoundState.DONE ->
                                GradeChip(state.gradeByRound[round.id] ?: Grade.UNGRADED)
                            round.dueAt <= System.currentTimeMillis() ->
                                Text("오늘 할 것", style = MaterialTheme.typography.labelMedium, color = Accent)
                            else ->
                                Text("예정", style = MaterialTheme.typography.labelMedium, color = TextTertiary)
                        }
                    }
                }
            }
        }
    }
}
