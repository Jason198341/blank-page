package com.blank.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blank.app.ui.theme.Accent
import com.blank.app.ui.theme.RoundDotAlpha
import com.blank.app.ui.theme.RoundDotSize
import com.blank.app.ui.theme.StateDone
import com.blank.app.ui.theme.StateMissed
import com.blank.app.ui.theme.StatePlanned
import com.blank.app.ui.theme.TextPrimary
import com.blank.app.ui.theme.TextSecondary
import com.blank.app.ui.theme.TextTertiary
import java.time.LocalDate
import java.time.YearMonth

private val DOW = listOf("일", "월", "화", "수", "목", "금", "토")

/**
 * 한 달 스케줄표.
 *
 * 색은 **상태**(예정/완료/놓침)로 나눈다. 캘린더에서 던지는 질문은 "언제 뭐가 있나 /
 * 뭘 놓쳤나" 지 "이게 몇 회차냐" 가 아니다. 회차는 점의 크기·명도로만 암시하고,
 * 정확한 회차는 날짜를 눌러 아래 목록에서 읽는다.
 *
 * 셀에 제목 텍스트를 절대 넣지 않는다 — 360dp 폭에서 셀 하나가 44dp 라 두 글자도
 * 안 들어가고 말줄임표만 남는다. 그게 이런 캘린더가 무너지는 1순위 원인이다.
 */
@Composable
fun MonthGrid(
    month: YearMonth,
    selected: LocalDate,
    today: LocalDate,
    marks: Map<LocalDate, List<DayMark>>,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val first = month.atDay(1)
    // 일요일 시작. DayOfWeek 는 월=1 … 일=7 이라 7 을 0 으로 접는다.
    val lead = first.dayOfWeek.value % 7
    val start = first.minusDays(lead.toLong())

    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            DOW.forEachIndexed { i, label ->
                Text(
                    label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 11.sp,
                    color = if (i == 0) StateMissed.copy(alpha = 0.7f) else TextTertiary
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        // 6행 고정. 달마다 높이가 튀면 아래 목록이 덜컹거린다.
        repeat(6) { week ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { dow ->
                    val date = start.plusDays((week * 7 + dow).toLong())
                    DayCell(
                        date = date,
                        inMonth = YearMonth.from(date) == month,
                        isToday = date == today,
                        isSelected = date == selected,
                        marks = marks[date].orEmpty(),
                        modifier = Modifier.weight(1f),
                        onClick = { onSelect(date) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    inMonth: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    marks: List<DayMark>,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier
            .heightIn(min = 48.dp)
            .padding(1.dp)
            .clip(RoundedCornerShape(10.dp))
            .then(if (isSelected) Modifier.background(Color.White.copy(alpha = 0.05f)) else Modifier)
            .then(if (isToday) Modifier.border(1.5.dp, Accent, RoundedCornerShape(10.dp)) else Modifier)
            .clickable(onClick = onClick)
            .padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            date.dayOfMonth.toString(),
            fontSize = 13.sp,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            color = when {
                !inMonth -> TextTertiary.copy(alpha = 0.35f)
                isToday -> TextPrimary
                else -> TextSecondary
            }
        )
        Spacer(Modifier.height(3.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.height(8.dp)
        ) {
            // 점은 한 줄만. 넘치면 +N 으로 접는다.
            marks.take(3).forEach { mark ->
                val size = RoundDotSize[mark.roundIndex.coerceIn(0, 4)].dp
                Box(
                    Modifier
                        .size(size)
                        .clip(CircleShape)
                        .background(
                            dayColor(mark.state)
                                .copy(alpha = RoundDotAlpha[mark.roundIndex.coerceIn(0, 4)])
                        )
                )
            }
            if (marks.size > 3) {
                Text("+${marks.size - 3}", fontSize = 9.sp, color = TextTertiary)
            }
        }
    }
}

private fun dayColor(state: DayState): Color = when (state) {
    DayState.PLANNED -> StatePlanned
    DayState.DONE -> StateDone
    DayState.MISSED -> StateMissed
}
