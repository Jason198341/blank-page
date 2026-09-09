package com.blank.app.ui.recall

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.blank.app.core.AppContainer
import com.blank.app.data.local.RoundEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString

data class RecallState(
    val loading: Boolean = true,
    val round: RoundEntity? = null,
    val title: String = "",
    /** 힌트 1회. 요소 하나의 라벨만 준다 — 원본을 여는 게 아니다. */
    val hintText: String? = null,
    val hintAvailable: Boolean = true,
    val text: String = "",
    val imagePath: String? = null,
    val startedAt: Long = System.currentTimeMillis(),
    val submitting: Boolean = false
)

class RecallViewModel(app: Application) : AndroidViewModel(app) {

    private val container = AppContainer.get(app)
    private val _state = MutableStateFlow(RecallState())
    val state = _state.asStateFlow()

    private var loadedRoundId: Long = 0

    fun load(roundId: Long) {
        if (loadedRoundId == roundId) return
        loadedRoundId = roundId
        viewModelScope.launch {
            val round = container.db.rounds().byId(roundId)
            val item = round?.let { container.db.items().byId(it.itemId) }
            val settings = container.settings.get()
            _state.value = RecallState(
                loading = false,
                round = round,
                title = item?.title.orEmpty(),
                hintAvailable = settings.allowHint,
                startedAt = System.currentTimeMillis()
            )
        }
    }

    fun onTextChange(v: String) = _state.update { it.copy(text = v) }

    fun onPhoto(path: String?) = _state.update { it.copy(imagePath = path) }

    /**
     * 힌트는 요소 하나의 라벨만 보여준다. 원본을 여는 경로는 오직 제출 뒤뿐이다.
     * 감점하지 않고 "힌트 사용" 표시만 남긴다 — 벌주면 몰래 원본을 찾아본다.
     */
    fun useHint() {
        val round = _state.value.round ?: return
        viewModelScope.launch {
            val item = container.db.items().byId(round.itemId) ?: return@launch
            val revisionId = if (round.revisionId != 0L) round.revisionId else item.currentRevisionId
            val sheet = container.db.revisions().byId(revisionId)?.sheetJson.orEmpty()
            val labels = runCatching {
                com.blank.app.domain.BlankJson
                    .decodeFromString<com.blank.app.domain.ElementSheet>(sheet)
                    .elements.map { it.label.ifBlank { it.canonical.take(16) } }
            }.getOrDefault(emptyList())
            _state.update {
                it.copy(
                    hintText = if (labels.isEmpty()) "힌트를 만들 수 없습니다"
                    else "항목 ${labels.size}개 · 첫 항목은 “${labels.first()}”",
                    hintAvailable = false
                )
            }
        }
    }

    fun submit(gaveUp: Boolean, onDone: (Long) -> Unit) {
        val s = _state.value
        val round = s.round ?: return
        if (s.submitting) return
        _state.update { it.copy(submitting = true) }
        viewModelScope.launch {
            val id = container.repository.submit(
                roundId = round.id,
                text = s.text.trim(),
                imagePath = s.imagePath,
                usedHint = s.hintText != null,
                elapsedMs = System.currentTimeMillis() - s.startedAt,
                gaveUp = gaveUp
            )
            onDone(id)
        }
    }
}
