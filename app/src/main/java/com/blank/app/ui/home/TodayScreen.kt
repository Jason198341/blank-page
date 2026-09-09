package com.blank.app.ui.home

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blank.app.data.local.DueRound
import com.blank.app.data.local.Grade
import com.blank.app.ui.common.BlankCard
import com.blank.app.ui.common.RoundBadge
import com.blank.app.ui.theme.Accent
import com.blank.app.ui.theme.Bg
import com.blank.app.ui.theme.Outline
import com.blank.app.ui.theme.StateDone
import com.blank.app.ui.theme.TextPrimary
import com.blank.app.ui.theme.TextSecondary
import com.blank.app.ui.theme.TextTertiary
import com.blank.app.util.Dates
import java.time.temporal.ChronoUnit

@Composable
fun TodayScreen(
    onOpenRound: (Long) -> Unit,
    onOpenResult: (Long) -> Unit,
    onCreate: () -> Unit,
    vm: TodayViewModel = viewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refreshDay() }
    LaunchedEffect(state.due.size, state.dailyCap) { vm.onOverflow(state.due.size, state.dailyCap) }

    Box(Modifier.fillMaxSize().background(Bg)) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Header(state) }

            if (state.due.isEmpty() && state.doneToday.isEmpty()) {
                item { EmptyToday(state, onCreate) }
            }

            if (state.collapsed) {
                item { OverflowNotice(state.due.size) { vm.collapse(false) } }
            }

            items(state.visible, key = { it.round.id }) { due ->
                DueCard(due) { onOpenRound(due.round.id) }
            }

            if (state.collapsed && state.due.size > 3) {
                item {
                    TextButton(onClick = { vm.collapse(false) }, modifier = Modifier.fillMaxWidth()) {
                        Text("나머지 ${state.due.size - 3}개 마저 보기", color = TextSecondary)
                    }
                }
            }

            if (state.doneToday.isNotEmpty()) {
                item {
                    Text(
                        "오늘 끝낸 것",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextTertiary,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
                items(state.doneToday, key = { "done_${it.round.id}" }) { done ->
                    DoneCard(done) { onOpenResult(done.round.id) }
                }
            }
        }

        FloatingActionButton(
            onClick = onCreate,
            containerColor = Accent,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)
        ) { Icon(Icons.Filled.Add, contentDescription = "새 지식", tint = androidx.compose.ui.graphics.Color.White) }
    }
}

@Composable
private fun Header(state: TodayState) {
    val done = state.doneToday.size
    val total = state.total
    Column(Modifier.padding(bottom = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("오늘", style = MaterialTheme.typography.displaySmall, color = TextPrimary)
            Text(
                Dates.formatDayDow(Dates.today()),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
        if (total > 0) {
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier.fillMaxWidth().height(6.dp)
                    .clip(RoundedCornerShape(3.dp)).background(Outline)
            ) {
                Box(
                    Modifier.fillMaxWidth(if (total == 0) 0f else done.toFloat() / total)
                        .height(6.dp).clip(RoundedCornerShape(3.dp)).background(StateDone)
                )
            }
            Spacer(Modifier.height(6.dp))
            Text("$done / $total 완료", style = MaterialTheme.typography.labelMedium, color = TextTertiary)
        }
    }
}

/** 0개일 때는 "없음" 이 아니라 "끝냄" 으로 읽혀야 한다. */
@Composable
private fun EmptyToday(state: TodayState, onCreate: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(20.dp).clip(CircleShape).background(Outline))
        Spacer(Modifier.height(20.dp))
        Text("오늘은 백지 없음", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        Spacer(Modifier.height(8.dp))
        val next = state.nextDueDate
        Text(
            if (next == null) "등록된 지식이 없습니다"
            else "다음 인출 · ${Dates.formatDay(next)} (${ChronoUnit.DAYS.between(Dates.today(), next)}일 뒤)",
            style = MaterialTheme.typography.bodyMedium, color = TextTertiary
        )
        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onCreate) { Text("새 지식 등록하기", color = Accent) }
    }
}

@Composable
private fun OverflowNotice(count: Int, onExpand: () -> Unit) {
    BlankCard {
        Column {
            Text("오늘 ${count}개가 몰렸습니다", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Spacer(Modifier.height(6.dp))
            Text(
                "한 번에 다 하지 않아도 됩니다. 남은 것은 내일로 넘어갑니다.",
                style = MaterialTheme.typography.bodyMedium, color = TextSecondary
            )
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onExpand, contentPadding = PaddingValues(0.dp)) {
                Text("전부 보기", color = Accent)
            }
        }
    }
}

/** 카드에 제목만 보인다. 본문·힌트·지난 점수는 전부 숨긴다 — 제목이 유일한 인출 단서다. */
@Composable
private fun DueCard(due: DueRound, onClick: () -> Unit) {
    val late = Dates.daysLate(due.round.dueAt)
    BlankCard(Modifier.clickable(onClick = onClick)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoundBadge(due.round.roundIndex, due.round.attempt)
                if (due.tags.isNotBlank()) {
                    Text(
                        "  #${due.tags.split(",").first().trim()}",
                        style = MaterialTheme.typography.labelMedium, color = TextTertiary
                    )
                }
                Spacer(Modifier.weight(1f))
                if (late > 0) Text("${late}일 지연", fontSize = 11.sp, color = TextTertiary)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    due.title, style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary, modifier = Modifier.weight(1f)
                )
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = TextTertiary)
            }
        }
    }
}

@Composable
private fun DoneCard(due: DueRound, onClick: () -> Unit) {
    BlankCard(
        Modifier.clickable(onClick = onClick),
        padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("✓", color = StateDone, fontWeight = FontWeight.Bold)
            Spacer(Modifier.size(10.dp))
            Text(
                due.title, style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary, modifier = Modifier.weight(1f)
            )
            Text(
                when {
                    due.gradeOrNull().let { it == null || it == Grade.UNGRADED } -> "채점 중"
                    (due.total ?: 0) > 0 -> "${due.hit} / ${due.total} 재현"
                    else -> "제출됨"
                },
                style = MaterialTheme.typography.labelMedium, color = TextTertiary
            )
        }
    }
}
