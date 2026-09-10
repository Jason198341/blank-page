package com.blank.app.ui.note

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.blank.app.core.AppContainer
import com.blank.app.data.local.ItemKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NoteEditState(
    val loading: Boolean = true,
    val editingId: String? = null,
    val title: String = "",
    val body: String = "",
    val kind: ItemKind = ItemKind.LIST,
    val tags: String = "",
    val referenceOnly: Boolean = false,
    val folder: String = "",
    val saving: Boolean = false,
    val blockedReason: String? = null
) {
    val canSave: Boolean get() = title.isNotBlank() && !saving && blockedReason == null
}

class NoteEditViewModel(app: Application) : AndroidViewModel(app) {

    private val container = AppContainer.get(app)
    private val _state = MutableStateFlow(NoteEditState())
    val state = _state.asStateFlow()

    fun load(noteId: String?) {
        viewModelScope.launch {
            if (noteId == null) {
                // 새 노트 — 복습 상한을 넘겼으면 복습 등록을 막고 참조용만 허용
                val limit = container.settings.get().activeLimit
                val active = container.db.notes().activeReviewCount()
                _state.value = NoteEditState(
                    loading = false, referenceOnly = false,
                    blockedReason = if (active >= limit)
                        "복습 노트가 ${active}개입니다. ${limit}개까지만 동시에 답니다 — " +
                            "참조용으로는 얼마든지 만들 수 있습니다." else null
                )
            } else {
                val note = container.db.notes().byId(noteId)
                _state.value = NoteEditState(
                    loading = false, editingId = noteId,
                    title = note?.title.orEmpty(), body = note?.body.orEmpty(),
                    kind = note?.kind ?: ItemKind.LIST, tags = note?.tags.orEmpty(),
                    referenceOnly = note?.referenceOnly ?: false
                )
            }
        }
    }

    fun onTitle(v: String) = _state.update { it.copy(title = v) }
    fun onBody(v: String) = _state.update { it.copy(body = v) }
    fun onKind(v: ItemKind) = _state.update { it.copy(kind = v) }
    fun onTags(v: String) = _state.update { it.copy(tags = v) }
    fun onReferenceOnly(v: Boolean) = _state.update {
        // 상한 초과로 막혀 있어도 참조용은 항상 허용
        it.copy(referenceOnly = v, blockedReason = if (v) null else it.blockedReason)
    }

    fun save(onDone: (String) -> Unit) {
        val s = _state.value
        // 복습으로 저장하려는데 막혀 있으면 막는다 (참조용이면 통과)
        if (!s.canSave && !(s.referenceOnly && s.title.isNotBlank() && !s.saving)) return
        _state.update { it.copy(saving = true) }
        val tags = s.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        viewModelScope.launch {
            val id = if (s.editingId != null) {
                container.repository.editNote(s.editingId, s.title.trim(), s.body.trim(), s.kind, tags)
                s.editingId
            } else {
                container.repository.createNote(s.title.trim(), s.body.trim(), s.kind, tags, s.referenceOnly, s.folder)
            }
            if (id != null) onDone(id) else _state.update { it.copy(saving = false) }
        }
    }
}
