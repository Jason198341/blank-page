package com.blank.app.domain

import com.blank.app.data.local.Grade
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 앱 전체에서 쓰는 JSON 설정. CLI 가 필드를 더 붙여 보내도 죽지 않게 관대하게 읽는다. */
val BlankJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
    explicitNulls = false
}

// ---------------------------------------------------------------- 정규화 (등록 시 1회)

@Serializable
data class Numeric(
    @SerialName("has_value") val hasValue: Boolean = false,
    val value: Double = 0.0,
    val unit: String = "",
    val comparator: String = "none",
    @SerialName("tolerance_abs") val toleranceAbs: Double = 0.0,
    @SerialName("tolerance_pct") val tolerancePct: Double = 0.0
)

@Serializable
data class Element(
    val id: String,
    val label: String = "",
    val canonical: String,
    val aliases: List<String> = emptyList(),
    val kind: String = "fact",
    val required: Boolean = true,
    /** 순서가 의미를 갖는 요소만 0 이상. -1 이면 어디 있든 순서를 안 따진다. */
    @SerialName("order_index") val orderIndex: Int = -1,
    @SerialName("parent_id") val parentId: String = "",
    val numeric: Numeric = Numeric()
)

/**
 * 채점 기준이 되는 요소표.
 *
 * 등록 시 한 번 만들어 리비전에 붙여 둔다. 채점 때마다 원문을 다시 쪼개면 회차마다
 * 요소가 갈라져서 "3일차엔 맞았는데 30일차엔 빠졌다" 를 계산할 수 없다. 요소 id 가
 * 고정돼 있어야 5회차를 가로질러 같은 것을 추적할 수 있다.
 */
@Serializable
data class ElementSheet(
    @SerialName("schema_version") val schemaVersion: Int = 1,
    val title: String = "",
    val kind: String = "mixed",
    @SerialName("order_sensitive") val orderSensitive: Boolean = false,
    val elements: List<Element> = emptyList()
) {
    companion object {
        /**
         * CLI 가 아직 안 돌았거나 실패했을 때 쓰는 임시 요소표.
         * 줄 단위로 자르기만 한다 — 등록이 터미널 상태에 인질로 잡히면 안 된다.
         */
        fun fromLines(title: String, body: String): ElementSheet {
            val elements = body.lines()
                .map { it.trim().removePrefix("-").removePrefix("*").trim() }
                .filter { it.isNotEmpty() }
                .mapIndexed { i, line ->
                    Element(
                        id = "e${i + 1}",
                        label = line.take(20),
                        canonical = line.replace(Regex("^\\d+[.)]\\s*"), "")
                    )
                }
            return ElementSheet(title = title, elements = elements)
        }
    }
}

// ---------------------------------------------------------------- 채점 결과

enum class Verdict { CORRECT, MISSING, WRONG, ORDER_ERROR, UNKNOWN }

@Serializable
data class Judgement(
    @SerialName("element_id") val elementId: String,
    val verdict: String = "missing",
    val reason: String = "",
    val quote: String = ""
) {
    fun verdictEnum(): Verdict = when (verdict.lowercase()) {
        "correct" -> Verdict.CORRECT
        "missing" -> Verdict.MISSING
        "wrong" -> Verdict.WRONG
        "order_error" -> Verdict.ORDER_ERROR
        else -> Verdict.UNKNOWN
    }
}

@Serializable
data class Extra(
    val text: String = "",
    /** correct_addition / harmless / contradiction */
    val kind: String = "harmless",
    val reason: String = ""
)

@Serializable
data class NextAction(
    val interval: String = "keep_schedule",
    @SerialName("focus_element_ids") val focusElementIds: List<String> = emptyList(),
    val advice: String = ""
)

@Serializable
data class GradingResult(
    @SerialName("schema_version") val schemaVersion: Int = 1,
    @SerialName("submission_type") val submissionType: String = "partial",
    /** 사진을 읽어 옮겨 적은 글. 글로 제출했으면 비어 있다. */
    val transcript: String = "",
    val judgements: List<Judgement> = emptyList(),
    val extras: List<Extra> = emptyList(),
    val score: Int = 0,
    val grade: String = "F",
    val summary: String = "",
    @SerialName("review_original") val reviewOriginal: Boolean = false,
    @SerialName("next_action") val nextAction: NextAction = NextAction()
) {
    /** 맞힌 요소 수 (순서만 틀린 것은 반쯤 맞은 것이라 세지 않는다) */
    fun hits(): Int = judgements.count { it.verdictEnum() == Verdict.CORRECT }

    /**
     * 모델이 매긴 A~F 를 앱의 3등급으로 접는다. 3단계 이상은 자기채점 노이즈만 늘린다.
     * A = 완전재현, B·C = 골격은 섰다, D·F = 무너졌다.
     */
    fun toGrade(): Grade = when (grade.uppercase()) {
        "A" -> Grade.GREEN
        "B", "C" -> Grade.AMBER
        else -> Grade.RED
    }
}
