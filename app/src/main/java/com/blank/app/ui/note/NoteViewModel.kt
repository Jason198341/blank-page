package com.blank.app.ui.note

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.blank.app.core.AppContainer
import com.blank.app.data.local.ItemKind
import com.blank.app.data.local.NoteEntity
import com.blank.app.data.local.RoundEntity
import com.blank.app.domain.BlankJson
import com.blank.app.domain.Frontmatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NoteState(
    val loading: Boolean = true,
    val note: NoteEntity? = null,
    val rounds: List<RoundEntity> = emptyList(),
    val backlinks: List<NoteEntity> = emptyList()
)

class NoteViewModel(app: Application) : AndroidViewModel(app) {

    private val container = AppContainer.get(app)
    private val _state = MutableStateFlow(NoteState())
    val state = _state.asStateFlow()
    private var noteId: String = ""

    fun load(id: String) {
        noteId = id
        viewModelScope.launch { reload() }
    }

    fun refresh() { viewModelScope.launch { reload() } }

    private suspend fun reload() {
        val note = container.db.notes().byId(noteId)
        val rounds = if (note != null && !note.referenceOnly)
            container.db.rounds().futureOf(noteId, 0) else emptyList()
        val all = container.db.notes().allNow()
        val title = note?.title
        val backlinks = if (title == null) emptyList() else all.filter { other ->
            other.id != noteId && runCatching {
                BlankJson.decodeFromString<List<String>>(other.linkTitlesJson)
            }.getOrDefault(emptyList()).any { it.equals(title, true) }
        }
        _state.value = NoteState(false, note, rounds.sortedBy { it.roundIndex }, backlinks)
    }

    fun setReferenceOnly(v: Boolean) {
        viewModelScope.launch { container.repository.setReferenceOnly(noteId, v); reload() }
    }

    /** [[제목]] 을 노트 id 로. 없으면 null (호출부가 새 노트 만들지 물어본다) */
    suspend fun resolveLink(title: String): String? =
        container.db.notes().titleIndex().firstOrNull { it.title.equals(title, true) }?.id

    fun createLinked(title: String, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val id = container.repository.createNote(
                title = title, body = "", kind = ItemKind.LIST, tags = emptyList(),
                referenceOnly = true, folder = ""
            )
            if (id != null) onCreated(id)
        }
    }
}
