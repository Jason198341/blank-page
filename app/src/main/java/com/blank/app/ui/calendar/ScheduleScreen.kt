package com.blank.app.ui.calendar

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
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blank.app.data.local.DueRound
import com.blank.app.data.local.Grade
import com.blank.app.data.local.RoundState
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

@Composable
fun ScheduleScreen(
    onOpenRound: (Long) -> Unit,
    onOpenResult: (Long) -> Unit,
    vm: CalendarViewModel = viewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val today = Dates.today()

    Column(Modifier.fillMaxSize().background(Bg)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = vm::prevMonth) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "이전 달", tint = TextSecondary)
            }
            Text(
                "${state.month.year}년 ${state.month.monthValue}월",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            IconButton(onClick = vm::nextMonth) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "다음 달", tint = TextSecondary)
            }
            TextButton(onClick = vm::goToday) { Text("오늘", color = Accent) }
        }

        MonthGrid(
            month = state.month,
            selected = state.selected,
            today = today,
            marks = state.marks,
            onSelect = vm::select,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${Dates.formatDayDow(state.selected)} · ${state.selectedRounds.size}개",
                style = MaterialTheme.typography.labelLarge, color = TextSecondary
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(Outline)) {}

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (state.selectedRounds.isEmpty()) {
                item {
                    Text(
                        "이 날은 비어 있습니다",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextTertiary,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            }
            items(state.selectedRounds, key = { it.round.id }) { due ->
                DayRow(due, today, onOpenRound, onOpenResult)
            }
        }
    }
}

@Composable
private fun DayRow(
    due: DueRound,
    today: java.time.LocalDate,
    onOpenRound: (Long) -> Unit,
    onOpenResult: (Long) -> Unit
) {
    val date = Dates.toDate(due.round.dueAt)
    val done = due.round.state == RoundState.DONE
    // 미래 회차는 열 수 없다. 여는 순간 원본 잠금이 무의미해진다.
    val openable = done || !date.isAfter(today)

    BlankCard(
        Modifier.clickable(enabled = openable) {
            if (done) onOpenResult(due.round.id) else onOpenRound(due.round.id)
        },
        padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundBadge(due.round.roundIndex, due.round.attempt)
            Spacer(Modifier.width(12.dp))
            Text(
                due.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (openable) TextPrimary else TextTertiary,
                modifier = Modifier.weight(1f)
            )
            Text(
                when {
                    done && due.gradeOrNull().let { it != null && it != Grade.UNGRADED } &&
                        (due.total ?: 0) > 0 -> "${due.hit} / ${due.total} 재현"
                    done -> "제출됨"
                    date.isBefore(today) -> "지남"
                    date == today -> "오늘"
                    else -> "예정"
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (done) StateDone else TextTertiary
            )
        }
    }
}
