package com.blank.app.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.blank.app.core.AppContainer
import com.blank.app.data.local.DueRound
import com.blank.app.util.Dates
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class TodayState(
    val due: List<DueRound> = emptyList(),
    val doneToday: List<DueRound> = emptyList(),
    val nextDueDate: LocalDate? = null,
    val dailyCap: Int = 7,
    val collapsed: Boolean = false
) {
    val visible: List<DueRound> get() = if (collapsed) due.take(3) else due
    val total: Int get() = due.size + doneToday.size
}

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(app: Application) : AndroidViewModel(app) {

    private val container = AppContainer.get(app)
    private val db = container.db
    private val collapsed = MutableStateFlow(false)
    /** 자정을 넘겨도 화면이 어제에 머물지 않게 날짜를 상태로 들고 있는다 */
    private val today = MutableStateFlow(Dates.today())

    private val dueFlow: Flow<List<DueRound>> = today.flatMapLatest { day ->
        db.rounds().flowDueUntil(Dates.endOfDay(day))
    }

    private val doneFlow: Flow<List<DueRound>> = today.flatMapLatest { day ->
        db.rounds().flowCompletedBetween(Dates.startOfDay(day), Dates.endOfDay(day))
    }

    private val upcomingFlow: Flow<LocalDate?> = today.flatMapLatest { day ->
        db.rounds().flowBetween(Dates.endOfDay(day) + 1, Dates.endOfDay(day.plusDays(400)))
            .map { list -> list.minByOrNull { it.round.dueAt }?.let { Dates.toDate(it.round.dueAt) } }
    }

    val state = combine(
        dueFlow, doneFlow, upcomingFlow, collapsed, container.settings.flow
    ) { due, done, next, isCollapsed, settings ->
        TodayState(
            due = due,
            doneToday = done,
            nextDueDate = next,
            dailyCap = settings.dailyCap,
            // 상한을 넘겨 몰린 날만 접는다. 평소엔 전부 보인다.
            collapsed = isCollapsed && due.size > settings.dailyCap
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TodayState())

    fun refreshDay() { today.value = Dates.today() }

    fun collapse(v: Boolean) { collapsed.value = v }

    fun onOverflow(size: Int, cap: Int) {
        // 상한을 넘겨 처음 들어오면 자동으로 접어 준다 — 배지 100개는 사람을 죽인다
        if (size > cap && !collapsed.value) collapsed.value = true
    }
}
