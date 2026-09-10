package com.blank.app.ui.result

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blank.app.data.local.Grade
import com.blank.app.domain.ReviewSchedule
import com.blank.app.domain.Verdict
import com.blank.app.ui.common.BlankCard
import com.blank.app.ui.common.GradeChip
import com.blank.app.ui.common.RecallBar
import com.blank.app.ui.common.RoundBadge
import com.blank.app.ui.theme.Accent
import com.blank.app.ui.theme.PillShape
import com.blank.app.ui.theme.Bg
import com.blank.app.ui.theme.CardBg
import com.blank.app.ui.theme.DiffExtra
import com.blank.app.ui.theme.DiffHit
import com.blank.app.ui.theme.DiffMissed
import com.blank.app.ui.theme.DiffWrong
import com.blank.app.ui.theme.Outline
import com.blank.app.ui.theme.TextPrimary
import com.blank.app.ui.theme.TextSecondary
import com.blank.app.ui.theme.TextTertiary
import com.blank.app.util.Dates

/**
 * 비교결과. 원본이 처음 열리는 화면이다.
 *
 * 백분율을 쓰지 않는다("3 / 4 재현"). 축포도 없다. 빠뜨린 것은 원본 안에서 보여야
 * 그게 어디에 있던 것인지 이해된다.
 */
@Composable
fun DiffScreen(
    submissionId: Long,
    onDone: () -> Unit,
    vm: DiffViewModel = viewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(submissionId) { vm.load(submissionId) }

    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(state.grading, state.rows.size) {
        if (!state.grading) revealed = true
    }

    Column(Modifier.fillMaxSize().background(Bg)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDone) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "뒤로", tint = TextSecondary)
            }
            Text(
                state.title, style = MaterialTheme.typography.titleMedium,
                color = TextPrimary, modifier = Modifier.weight(1f)
            )
            RoundBadge(state.roundIndex)
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f)
        ) {
            item {
                when {
                    state.grading -> GradingCard()
                    state.failed -> FailedCard(state.comparison?.failReason) { vm.refresh() }
                    else -> Summary(state)
                }
            }

            if (!state.grading) {
                item {
                    AnimatedVisibility(visible = revealed, enter = fadeIn() + expandVertically()) {
                        Column {
                            SectionHeader("원본", unlocked = true)
                            Spacer(Modifier.height(8.dp))
                            BlankCard {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    state.rows.forEach { row -> ElementRow(row) }
                                    if (state.rows.isEmpty()) {
                                        Text("요소표가 아직 없습니다", color = TextTertiary)
                                    }
                                }
                            }
                        }
                    }
                }

                if (state.extras.isNotEmpty()) {
                    item {
                        Column {
                            SectionHeader("내가 덧붙인 것", unlocked = false)
                            Spacer(Modifier.height(8.dp))
                            BlankCard {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    state.extras.forEach { extra ->
                                        val color = when (extra.kind) {
                                            "contradiction" -> DiffWrong
                                            "correct_addition" -> DiffHit
                                            else -> DiffExtra
                                        }
                                        Row {
                                            Text("+ ", color = color, fontWeight = FontWeight.Bold)
                                            Column {
                                                Text(extra.text, color = TextPrimary,
                                                    style = MaterialTheme.typography.bodyLarge)
                                                if (extra.reason.isNotBlank()) {
                                                    Text(extra.reason, color = TextTertiary,
                                                        style = MaterialTheme.typography.labelMedium)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                state.comparison?.takeIf { it.advice.isNotBlank() }?.let { comparison ->
                    item {
                        BlankCard {
                            Column {
                                Text("다음에 할 것", style = MaterialTheme.typography.labelLarge, color = TextTertiary)
                                Spacer(Modifier.height(6.dp))
                                Text(comparison.advice, color = TextSecondary,
                                    style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }

                state.comparison?.takeIf { it.status == "OK" }?.let { comparison ->
                    item { OverrideRow(comparison.grade, comparison.suggestedGrade, vm::override) }
                }
            }
        }

        Column(Modifier.padding(16.dp)) {
            val next = state.nextDue
            Text(
                if (next == null) "5회차까지 끝났습니다 · 졸업"
                else "다음 인출 · ${Dates.formatDay(next)}" +
                    (state.nextRoundIndex?.let { " (${ReviewSchedule.label(it)})" } ?: ""),
                style = MaterialTheme.typography.bodyMedium, color = TextTertiary
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = PillShape,
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.White)
            ) { Text("확인") }
        }
    }
}

@Composable
private fun Summary(state: DiffState) {
    Column {
        RecallBar(state.hit, state.total)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            state.comparison?.let { GradeChip(it.grade) }
            Spacer(Modifier.width(10.dp))
            state.submission?.let {
                Text(Dates.formatElapsed(it.elapsedMs), fontSize = 13.sp, color = TextTertiary)
            }
            if (state.submission?.usedHint == true) {
                Spacer(Modifier.width(10.dp))
                Text("힌트 사용", fontSize = 13.sp, color = TextTertiary)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Tally("맞음", state.rows.count { it.verdict == Verdict.CORRECT }, DiffHit)
            Tally("빠뜨림", state.rows.count { it.verdict == Verdict.MISSING }, DiffMissed)
            Tally("틀림", state.rows.count { it.verdict == Verdict.WRONG }, DiffWrong)
            Tally("덧붙임", state.extras.size, DiffExtra)
        }
        val summary = state.comparison?.summary.orEmpty()
        Spacer(Modifier.height(14.dp))
        if (summary.isNotBlank()) {
            Text(summary, style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
        } else {
            // 판정은 이미 나왔다. 설명은 뒤에서 채워지는 중이라 화면을 붙잡아 두지 않는다.
            Text("설명을 채우는 중…", style = MaterialTheme.typography.bodyMedium, color = TextTertiary)
        }
    }
}

@Composable
private fun Tally(label: String, count: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text("$label $count", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
    }
}

@Composable
private fun SectionHeader(title: String, unlocked: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = TextTertiary)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f).height(1.dp).background(Outline))
        if (unlocked) {
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Outlined.LockOpen, contentDescription = null, tint = TextTertiary,
                modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun ElementRow(row: DiffRow) {
    val (mark, color) = when (row.verdict) {
        Verdict.CORRECT -> "✓" to DiffHit
        Verdict.MISSING -> "⊘" to DiffMissed
        Verdict.WRONG -> "✕" to DiffWrong
        Verdict.ORDER_ERROR -> "↕" to DiffMissed
        Verdict.UNKNOWN -> "·" to TextTertiary
    }
    Row {
        Text(mark, color = color, fontWeight = FontWeight.Bold, modifier = Modifier.width(22.dp))
        Column {
            Text(
                row.element.canonical,
                style = MaterialTheme.typography.bodyLarge,
                color = if (row.verdict == Verdict.CORRECT) TextPrimary else TextSecondary
            )
            row.judgement?.reason?.takeIf { it.isNotBlank() && row.verdict != Verdict.CORRECT }?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = TextTertiary)
            }
        }
    }
}

@Composable
private fun GradingCard() {
    BlankCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(color = Accent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(14.dp))
            Column {
                Text("제출 완료 · 채점 중", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text(
                    "터미널의 claude 가 원본과 대조하고 있습니다 (1분 안팎)",
                    style = MaterialTheme.typography.bodyMedium, color = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun FailedCard(reason: String?, onRetry: () -> Unit) {
    Box(
        Modifier.fillMaxWidth()
            .background(CardBg, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column {
            Text("채점하지 못했습니다", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Spacer(Modifier.height(6.dp))
            Text(
                reason ?: "터미널의 claude 를 부르지 못했습니다.",
                style = MaterialTheme.typography.bodyMedium, color = TextSecondary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "재현물은 저장돼 있습니다. Termux 에서 `blank drain` 을 실행하면 밀린 채점을 처리합니다.",
                style = MaterialTheme.typography.labelMedium, color = TextTertiary
            )
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onRetry, contentPadding = PaddingValues(0.dp)) {
                Text("다시 확인", color = Accent)
            }
        }
    }
}

@Composable
private fun OverrideRow(current: Grade, suggested: Grade, onPick: (Grade) -> Unit) {
    Column {
        Text(
            "채점이 틀렸다면 등급만 바꿀 수 있습니다. 다음 일정은 원래 판정대로 갑니다.",
            style = MaterialTheme.typography.labelMedium, color = TextTertiary
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Grade.GREEN, Grade.AMBER, Grade.RED).forEach { grade ->
                TextButton(
                    onClick = { onPick(grade) },
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = if (grade == current) CardBg else Color.Transparent
                    )
                ) { GradeChip(grade) }
            }
        }
        if (current != suggested) {
            Text(
                "앱이 매긴 등급과 다릅니다",
                style = MaterialTheme.typography.labelMedium, color = TextTertiary
            )
        }
    }
}
