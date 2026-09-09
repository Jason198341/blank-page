package com.blank.app.ui.item

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.blank.app.core.AppContainer
import com.blank.app.data.local.Grade
import com.blank.app.data.local.ItemEntity
import com.blank.app.data.local.ItemKind
import com.blank.app.data.local.RoundEntity
import com.blank.app.data.local.RoundState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CreateState(
    val title: String = "",
    val body: String = "",
    val kind: ItemKind = ItemKind.LIST,
    val tags: String = "",
    val saving: Boolean = false,
    val blockedReason: String? = null
) {
    val canSave: Boolean get() = title.isNotBlank() && body.isNotBlank() && !saving && blockedReason == null
}

class CreateViewModel(app: Application) : AndroidViewModel(app) {

    private val container = AppContainer.get(app)
    private val _state = MutableStateFlow(CreateState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // 동시에 살아 있는 항목이 너무 많으면 이 앱은 플래시카드 더미로 퇴화한다.
            // 졸업시켜 자리를 내야 새로 담을 수 있다.
            val limit = container.settings.get().activeLimit
            val active = container.db.items().activeCount()
            if (active >= limit) {
                _state.update {
                    it.copy(blockedReason = "지금 살아 있는 지식이 ${active}개입니다. " +
                        "${limit}개까지만 동시에 답니다 — 하나를 졸업시키거나 접은 뒤 등록하세요.")
                }
            }
        }
    }

    fun onTitle(v: String) = _state.update { it.copy(title = v) }
    fun onBody(v: String) = _state.update { it.copy(body = v) }
    fun onKind(v: ItemKind) = _state.update { it.copy(kind = v) }
    fun onTags(v: String) = _state.update { it.copy(tags = v) }

    fun save(onDone: () -> Unit) {
        val s = _state.value
        if (!s.canSave) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            container.repository.createItem(s.title.trim(), s.body.trim(), s.kind, s.tags.trim())
            onDone()
        }
    }
}

data class LibraryRow(val item: ItemEntity, val done: Int)

class LibraryViewModel(app: Application) : AndroidViewModel(app) {

    private val container = AppContainer.get(app)

    @OptIn(ExperimentalCoroutinesApi::class)
    val rows = container.db.items().all()
        .map { items ->
            items.map { item ->
                LibraryRow(item, container.db.rounds().futureOf(item.id, 0).let { all ->
                    // futureOf 는 미완료만 준다. 5회 중 끝낸 수 = 5 - 남은 수 (재시도는 세지 않는다)
                    (5 - all.count { r -> r.attempt == 0 }).coerceIn(0, 5)
                })
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

data class KnowledgeState(
    val item: ItemEntity? = null,
    val rounds: List<RoundEntity> = emptyList(),
    /** 원본을 지금 봐도 되는가 — 예정된 회차가 없을 때만 연다 */
    val originalOpen: Boolean = false,
    val submissionByRound: Map<Long, Long> = emptyMap(),
    val gradeByRound: Map<Long, Grade> = emptyMap()
)

class KnowledgeViewModel(app: Application) : AndroidViewModel(app) {

    private val container = AppContainer.get(app)
    private val _state = MutableStateFlow(KnowledgeState())
    val state = _state.asStateFlow()
    private var itemId = 0L
    private val forceOpen = MutableStateFlow(false)

    fun load(id: Long) {
        if (itemId == id) return
        itemId = id
        viewModelScope.launch {
            combine(
                container.db.items().flowById(id),
                container.db.rounds().flowByItem(id),
                forceOpen
            ) { item, rounds, opened ->
                val subs = mutableMapOf<Long, Long>()
                val grades = mutableMapOf<Long, Grade>()
                rounds.forEach { round ->
                    container.db.submissions().byRound(round.id)?.let { submission ->
                        subs[round.id] = submission.id
                        container.db.comparisons().bySubmission(submission.id)?.let { c ->
                            grades[round.id] = c.grade
                        }
                    }
                }
                KnowledgeState(
                    item = item,
                    rounds = rounds,
                    // 예정된 회차가 남아 있으면 원본은 잠긴 채로 둔다 — 미리 보면 다음 인출이 죽는다
                    originalOpen = opened || rounds.none { it.state != RoundState.DONE },
                    submissionByRound = subs,
                    gradeByRound = grades
                )
            }.collect { _state.value = it }
        }
    }

    /** 사용자가 명시적으로 잠금을 깬다. 다음 회차가 오염된다는 걸 화면이 먼저 말한다. */
    fun openOriginal() { forceOpen.value = true }

    fun archive(archived: Boolean) {
        viewModelScope.launch {
            container.db.items().setArchived(itemId, archived, System.currentTimeMillis())
        }
    }
}
