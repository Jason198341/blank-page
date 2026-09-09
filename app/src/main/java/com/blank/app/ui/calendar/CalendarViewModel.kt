package com.blank.app.ui.calendar

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.blank.app.core.AppContainer
import com.blank.app.data.local.DueRound
import com.blank.app.data.local.RoundState
import com.blank.app.util.Dates
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth

enum class DayState { PLANNED, DONE, MISSED }

data class DayMark(val roundIndex: Int, val state: DayState)

data class CalendarState(
    val month: YearMonth = YearMonth.now(),
    val selected: LocalDate = LocalDate.now(),
    val marks: Map<LocalDate, List<DayMark>> = emptyMap(),
    val selectedRounds: List<DueRound> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(app: Application) : AndroidViewModel(app) {

    private val container = AppContainer.get(app)
    private val month = MutableStateFlow(YearMonth.now())
    private val selected = MutableStateFlow(LocalDate.now())

    private val roundsFlow = month.flatMapLatest { ym ->
        // 그리드가 앞뒤 달을 물고 있으므로 여유 있게 6주치를 가져온다
        val from = Dates.startOfDay(ym.atDay(1).minusDays(7))
        val to = Dates.endOfDay(ym.atEndOfMonth().plusDays(14))
        container.db.rounds().flowBetween(from, to)
    }

    val state = combine(month, selected, roundsFlow) { ym, day, rounds ->
        val today = Dates.today()
        val marks = rounds.groupBy { Dates.toDate(it.round.dueAt) }
            .mapValues { (date, list) ->
                list.map { due ->
                    DayMark(
                        roundIndex = due.round.roundIndex,
                        state = when {
                            due.round.state == RoundState.DONE -> DayState.DONE
                            date.isBefore(today) -> DayState.MISSED
                            else -> DayState.PLANNED
                        }
                    )
                }
            }
        CalendarState(
            month = ym,
            selected = day,
            marks = marks,
            selectedRounds = rounds.filter { Dates.toDate(it.round.dueAt) == day }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CalendarState())

    fun prevMonth() { month.value = month.value.minusMonths(1) }
    fun nextMonth() { month.value = month.value.plusMonths(1) }
    fun goToday() {
        month.value = YearMonth.now()
        selected.value = Dates.today()
    }
    fun select(date: LocalDate) {
        selected.value = date
        if (YearMonth.from(date) != month.value) month.value = YearMonth.from(date)
    }
}
