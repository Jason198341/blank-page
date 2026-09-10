package com.blank.app.ui.vault

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.blank.app.core.AppContainer
import com.blank.app.data.local.NoteEntity
import com.blank.app.data.vault.VaultSync
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class VaultState(
    val notes: List<NoteEntity> = emptyList(),
    val query: String = "",
    val tag: String? = null,
    val syncing: Boolean = false
) {
    val allTags: List<String> = notes.flatMap { it.tags.split(",") }
        .map { it.trim() }.filter { it.isNotEmpty() }.distinct().sorted()

    val filtered: List<NoteEntity> = notes.filter { n ->
        (query.isBlank() || n.title.contains(query, true) || n.body.contains(query, true)) &&
            (tag == null || n.tags.split(",").map { it.trim() }.contains(tag))
    }
}

class VaultViewModel(app: Application) : AndroidViewModel(app) {

    private val container = AppContainer.get(app)
    private val query = MutableStateFlow("")
    private val tag = MutableStateFlow<String?>(null)
    private val syncing = MutableStateFlow(false)

    val state = combine(container.db.notes().all(), query, tag, syncing) { notes, q, t, sy ->
        VaultState(notes = notes, query = q, tag = t, syncing = sy)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), VaultState())

    fun onQuery(v: String) { query.value = v }
    fun onTag(v: String?) { tag.value = v }

    fun resync() {
        viewModelScope.launch {
            syncing.value = true
            runCatching { VaultSync.sync(getApplication()) }
            syncing.value = false
        }
    }
}
