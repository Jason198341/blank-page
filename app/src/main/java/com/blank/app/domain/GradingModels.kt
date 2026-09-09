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
data class GradingStage1(
    val judgements: List<Judgement> = emptyList(),
    val extras: List<Extra> = emptyList()
) {
    /** 맞힌 요소 수 (순서만 틀린 것은 반쯤 맞은 것이라 세지 않는다) */
    fun hits(): Int = judgements.count { it.verdictEnum() == Verdict.CORRECT }

    /**
     * 점수를 앱에서 계산한다. 모델에게 시키지 않는다 —
     * 판정이 여섯 개 모두 똑같은데도 33점과 50점을 오갔다(실측).
     * 스키마는 형식을 보장하지 산수를 보장하지 않는다.
     *
     * 가중치는 required 2 / 그 외 1. 요소 점수는 correct 1.0, order_error 0.5,
     * wrong·missing 0. 원본과 어긋나는 덧붙임(contradiction)은 하나당 5점을 뺀다.
     */
    fun computeScore(sheet: ElementSheet): Int {
        val byId = judgements.associateBy { it.elementId }
        var weightSum = 0.0
        var earned = 0.0
        sheet.elements.forEach { element ->
            val weight = if (element.required) 2.0 else 1.0
            weightSum += weight
            earned += weight * when (byId[element.id]?.verdictEnum()) {
                Verdict.CORRECT -> 1.0
                Verdict.ORDER_ERROR -> 0.5
                else -> 0.0
            }
        }
        if (weightSum <= 0.0) return 0
        val penalty = extras.count { it.kind == "contradiction" } * 5
        return (Math.round(100 * earned / weightSum).toInt() - penalty).coerceIn(0, 100)
    }

    /**
     * 3등급으로 접는다. 필수 요소를 하나라도 **틀리게** 기억하고 있으면 완전재현으로
     * 보지 않는다 — 빠뜨린 것보다 틀리게 아는 쪽이 위험하다.
     */
    fun gradeFor(sheet: ElementSheet): Grade {
        val score = computeScore(sheet)
        val requiredWrong = sheet.elements.any { element ->
            element.required &&
                judgements.firstOrNull { it.elementId == element.id }?.verdictEnum() == Verdict.WRONG
        }
        return when {
            score >= 90 && !requiredWrong -> Grade.GREEN
            score >= 60 -> Grade.AMBER
            else -> Grade.RED
        }
    }
}

@Serializable
data class Detail(
    @SerialName("element_id") val elementId: String,
    val reason: String = "",
    val quote: String = ""
)

/** 2단계 — 판정은 그대로 두고 설명만 채운다. 늦게 와도 화면이 이미 서 있다. */
@Serializable
data class GradingDetails(
    val details: List<Detail> = emptyList(),
    val summary: String = "",
    val advice: String = "",
    /** 사진으로 낸 종이를 읽어 옮겨 적은 글. 글로 냈으면 비어 있다. */
    val transcript: String = "",
    @SerialName("review_original") val reviewOriginal: Boolean = false
)
