package com.blank.app.domain

import com.blank.app.data.local.Grade
import com.blank.app.data.local.RoundEntity
import com.blank.app.data.local.RoundState
import com.blank.app.util.Dates
import java.time.LocalDate

/**
 * 인출 일정 규칙. 여기 말고 다른 데서 날짜를 만들지 않는다.
 *
 * 뼈대는 D0 / D+3 / D+10 / D+30 / D+90 딱 5회. 끝이 있다는 게 이 앱의 정체성이라
 * 무한 반복 큐로 번지지 않게 한다. 다만 두 가지 예외가 있다:
 *
 *  - red(실패) 를 받으면 **다음 날 재시도**를 같은 회차에 끼워 넣는다. 실패한 인출 뒤에
 *    간격을 그대로 늘리면 간격효과의 전제(직전 인출 성공)가 깨지기 때문이다.
 *    재시도가 통과하면 다음 회차는 원래 캘린더 날짜 그대로 간다 — 전체 리셋은 하지 않는다.
 *  - amber(부분) 는 통과시키되 **다음 간격을 30% 줄인다**. 겨우 버틴 기억을 원래 간격으로
 *    밀면 다음 회차에서 무너진다.
 */
object ReviewSchedule {

    val OFFSETS = intArrayOf(0, 3, 10, 30, 90)
    const val LAST_INDEX = 4

    fun label(roundIndex: Int): String = if (roundIndex == 0) "D0" else "D+${OFFSETS[roundIndex]}"

    /** 등록 시 5회차를 한 번에 만든다. D0 은 지금 당장(알림 시각을 기다리지 않는다). */
    fun plan(noteId: String, now: Long, notifyHour: Int): List<RoundEntity> {
        val base = Dates.toDate(now)
        return OFFSETS.mapIndexed { index, offset ->
            RoundEntity(
                noteId = noteId,
                roundIndex = index,
                offsetDays = offset,
                dueAt = if (index == 0) now else Dates.atHour(base.plusDays(offset.toLong()), notifyHour)
            )
        }
    }

    /**
     * 채점 직후의 일정 조정.
     *
     * @return [retry] 새로 끼워 넣을 재시도 회차(없으면 null), [shifted] 날짜가 당겨진 뒤 회차들
     */
    data class Adjustment(val retry: RoundEntity?, val shifted: List<RoundEntity>)

    fun adjust(
        round: RoundEntity,
        grade: Grade,
        future: List<RoundEntity>,
        now: Long,
        notifyHour: Int
    ): Adjustment = when (grade) {
        Grade.RED -> {
            // 내일 같은 회차를 다시. 최소 하루는 비운다 — 당일 재시도는 인출이 아니라 암기다.
            val tomorrow = Dates.atHour(Dates.toDate(now).plusDays(1), notifyHour)
            Adjustment(
                retry = round.copy(
                    id = 0,
                    attempt = round.attempt + 1,
                    dueAt = tomorrow,
                    revisionId = 0,
                    state = RoundState.PENDING,
                    notifiedAt = null,
                    completedAt = null
                ),
                shifted = emptyList()
            )
        }
        Grade.AMBER -> {
            // 다음 회차만 30% 당긴다. 그 뒤 회차는 건드리지 않는다 —
            // 연쇄 이동은 캘린더를 못 믿게 만든다.
            val next = future.minByOrNull { it.dueAt }
            if (next == null) Adjustment(null, emptyList())
            else {
                val gapDays = (Dates.toDate(next.dueAt).toEpochDay() - Dates.toDate(now).toEpochDay())
                val pulled = (gapDays * 0.7).toLong().coerceAtLeast(1)
                val newDate: LocalDate = Dates.toDate(now).plusDays(pulled)
                Adjustment(null, listOf(next.copy(dueAt = Dates.atHour(newDate, notifyHour))))
            }
        }
        else -> Adjustment(null, emptyList())
    }

    /** 5회차를 green/amber 로 끝냈으면 졸업이다. */
    fun graduates(round: RoundEntity, grade: Grade): Boolean =
        round.roundIndex == LAST_INDEX && (grade == Grade.GREEN || grade == Grade.AMBER)
}
