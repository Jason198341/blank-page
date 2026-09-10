package com.blank.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blank.app.data.local.Grade
import com.blank.app.domain.ReviewSchedule
import com.blank.app.ui.theme.CardBg
import com.blank.app.ui.theme.Outline
import com.blank.app.ui.theme.RoundBadgeBg
import com.blank.app.ui.theme.RoundBadgeFg
import com.blank.app.ui.theme.StateDone
import com.blank.app.ui.theme.StateMissed
import com.blank.app.ui.theme.TextSecondary
import com.blank.app.ui.theme.TextTertiary

/** D0 / D+3 … 배지. 회차가 멀수록 색이 가라앉는다. */
@Composable
fun RoundBadge(roundIndex: Int, attempt: Int = 0, modifier: Modifier = Modifier) {
    val i = roundIndex.coerceIn(0, RoundBadgeBg.lastIndex)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(RoundBadgeBg[i])
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = ReviewSchedule.label(roundIndex) + if (attempt > 0) " 재시도" else "",
            color = RoundBadgeFg[i],
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.4.sp
        )
    }
}

@Composable
fun BlankCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(16.dp),
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CardBg)
            .border(1.dp, Outline, RoundedCornerShape(20.dp))
            .padding(padding)
    ) { content() }
}

/** 5회차 진행 점. 스트릭 대신 이걸 쓴다 — 이 앱에는 "매일" 이 없다. */
@Composable
fun ProgressDots(done: Int, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        repeat(5) { i ->
            Box(
                Modifier
                    .padding(end = 4.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (i < done) StateDone else Outline)
            )
        }
    }
}

/** 백분율을 쓰지 않는다. "3 / 4 재현" 이 사람을 성적표 모드로 밀지 않는다. */
@Composable
fun RecallBar(hit: Int, total: Int, modifier: Modifier = Modifier) {
    val ratio = if (total <= 0) 0f else hit.toFloat() / total
    Column(modifier) {
        Text(
            "$hit / $total 재현",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Box(
            Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Outline)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(ratio)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(gradeColor(hit, total))
            )
        }
    }
}

private fun gradeColor(hit: Int, total: Int): Color {
    if (total <= 0) return TextTertiary
    val r = hit.toFloat() / total
    return when {
        r >= 0.9f -> StateDone
        r >= 0.6f -> Color(0xFFE5A03D)
        else -> StateMissed
    }
}

@Composable
fun GradeChip(grade: Grade, modifier: Modifier = Modifier) {
    val (label, color) = when (grade) {
        Grade.GREEN -> "완전재현" to StateDone
        Grade.AMBER -> "부분재현" to Color(0xFFE5A03D)
        Grade.RED -> "무너짐" to StateMissed
        Grade.UNGRADED -> "채점 대기" to TextTertiary
    }
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, modifier = modifier)
}
