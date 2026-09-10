package com.blank.app.ui.result

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.blank.app.core.AppContainer
import com.blank.app.data.local.ComparisonEntity
import com.blank.app.data.local.Grade
import com.blank.app.data.local.SubmissionEntity
import com.blank.app.domain.BlankJson
import com.blank.app.domain.Element
import com.blank.app.domain.ElementSheet
import com.blank.app.domain.Extra
import com.blank.app.domain.Judgement
import com.blank.app.domain.Verdict
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

data class DiffRow(val element: Element, val judgement: Judgement?) {
    val verdict: Verdict get() = judgement?.verdictEnum() ?: Verdict.UNKNOWN
}

data class DiffState(
    val loading: Boolean = true,
    val title: String = "",
    val roundIndex: Int = 0,
    val submission: SubmissionEntity? = null,
    val comparison: ComparisonEntity? = null,
    val rows: List<DiffRow> = emptyList(),
    val extras: List<Extra> = emptyList(),
    val nextDue: LocalDate? = null,
    val nextRoundIndex: Int? = null
) {
    val grading: Boolean get() = comparison?.status == "PENDING"
    val failed: Boolean get() = comparison?.status == "FAILED" || comparison?.status == "TIMEOUT"
    val hit: Int get() = rows.count { it.verdict == Verdict.CORRECT }
    val total: Int get() = rows.size
}

class DiffViewModel(app: Application) : AndroidViewModel(app) {

    private val container = AppContainer.get(app)
    private val _state = MutableStateFlow(DiffState())
    val state = _state.asStateFlow()

    private var submissionId: Long = 0

    fun load(id: Long) {
        submissionId = id
        viewModelScope.launch { reload() }
        // 채점은 터미널에서 1분 안팎 걸린다. 결과가 들어오면 화면을 갈아끼운다.
        viewModelScope.launch {
            com.blank.app.cli.TermuxBridge.events.collect { reload() }
        }
    }

    fun refresh() { viewModelScope.launch { reload() } }

    private suspend fun reload() {
        val db = container.db
        val submission = db.submissions().byId(submissionId) ?: return
        val round = db.rounds().byId(submission.roundId)
        val note = db.notes().byId(submission.noteId)
        val comparison = db.comparisons().bySubmission(submissionId)

        val revisionId = round?.revisionId?.takeIf { it != 0L } ?: 0L
        val sheetJson = if (revisionId != 0L)
            db.revisions().byId(revisionId)?.sheetJson.orEmpty()
        else note?.sheetJson.orEmpty()
        val sheet = runCatching { BlankJson.decodeFromString<ElementSheet>(sheetJson) }
            .getOrDefault(ElementSheet())

        val judgements = runCatching {
            BlankJson.decodeFromString<List<Judgement>>(comparison?.judgementsJson ?: "[]")
        }.getOrDefault(emptyList()).associateBy { it.elementId }

        val extras = runCatching {
            BlankJson.decodeFromString<List<Extra>>(comparison?.extrasJson ?: "[]")
        }.getOrDefault(emptyList())

        val next = round?.let {
            db.rounds().futureOf(it.noteId, System.currentTimeMillis()).minByOrNull { r -> r.dueAt }
        }

        _state.update {
            DiffState(
                loading = false,
                title = note?.title.orEmpty(),
                roundIndex = round?.roundIndex ?: 0,
                submission = submission,
                comparison = comparison,
                rows = sheet.elements.map { e -> DiffRow(e, judgements[e.id]) },
                extras = extras,
                nextDue = next?.let { r -> com.blank.app.util.Dates.toDate(r.dueAt) },
                nextRoundIndex = next?.roundIndex
            )
        }
    }

    /**
     * 등급 뒤집기. 표시 등급만 바뀐다 — 다음 일정은 이미 원 판정 기준으로 잡혔고
     * 바꾸지 않는다. 그래야 스스로를 후하게 봐도 이득이 없다.
     */
    fun override(grade: Grade) {
        viewModelScope.launch {
            container.repository.overrideGrade(submissionId, grade)
            reload()
        }
    }
}
