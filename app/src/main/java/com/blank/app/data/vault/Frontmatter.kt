package com.blank.app.data.vault

import com.blank.app.data.local.ItemKind
import java.util.UUID

/**
 * .md 파일의 YAML 프론트매터 + 본문.
 *
 * 표준 옵시디언 프론트매터와 호환되게 최소 YAML 만 다룬다 — `key: value` 와
 * tags 의 인라인 배열/블록 리스트. 우리가 쓰는 키: blank_id, review, tags, kind, created.
 * `review: false` 면 단순 참조용(복습 제외). 키가 없으면 [defaultReview] 를 따른다.
 */
data class ParsedNote(
    val blankId: String,
    val review: Boolean,
    val tags: List<String>,
    val kind: ItemKind,
    val created: Long,
    val body: String,
    /** 프론트매터에 blank_id 가 없어 새로 붙였는가 → 파일에 되써야 한다 */
    val needsWriteback: Boolean
)

object Frontmatter {

    private val WIKILINK = Regex("""\[\[([^\]]+)]]""")

    fun parse(raw: String, defaultReview: Boolean, now: Long): ParsedNote {
        var review: Boolean? = null
        var id: String? = null
        var kind = ItemKind.LIST
        var created = now
        val tags = mutableListOf<String>()
        var body = raw

        if (raw.startsWith("---")) {
            val end = raw.indexOf("\n---", 3)
            if (end > 0) {
                val fm = raw.substring(3, end).trim('\n')
                body = raw.substring(end + 4).removePrefix("\n")
                var inTagBlock = false
                fm.lines().forEach { line ->
                    val t = line.trim()
                    if (inTagBlock && t.startsWith("-")) {
                        tags += t.removePrefix("-").trim().trim('"', '\'')
                    } else {
                        inTagBlock = false
                        val k = t.substringBefore(':', "").trim().lowercase()
                        val v = t.substringAfter(':', "").trim()
                        when (k) {
                            "blank_id" -> id = v.trim('"', '\'').ifBlank { null }
                            "review" -> review = v.equals("true", true) || v == "1"
                            "kind" -> runCatching { kind = ItemKind.valueOf(v.uppercase()) }
                            "created" -> created = parseTime(v) ?: now
                            "tags" -> when {
                                v.startsWith("[") -> v.trim('[', ']').split(",")
                                    .map { it.trim().trim('"', '\'') }.filter { it.isNotEmpty() }
                                    .forEach { tags += it }
                                v.isBlank() -> inTagBlock = true
                                else -> tags += v.trim('"', '\'')
                            }
                        }
                    }
                }
            }
        }

        val assigned = id == null
        return ParsedNote(
            blankId = id ?: UUID.randomUUID().toString(),
            review = review ?: defaultReview,
            tags = tags,
            kind = kind,
            created = created,
            body = body.trim('\n'),
            needsWriteback = assigned
        )
    }

    /** 프론트매터를 노트 상태에 맞게 다시 써서 파일 전체 문자열을 만든다. */
    fun serialize(
        blankId: String,
        review: Boolean,
        tags: List<String>,
        kind: ItemKind,
        created: Long,
        body: String
    ): String = buildString {
        append("---\n")
        append("blank_id: ").append(blankId).append('\n')
        append("review: ").append(if (review) "true" else "false").append('\n')
        if (tags.isNotEmpty()) append("tags: [").append(tags.joinToString(", ")).append("]\n")
        append("kind: ").append(kind.name.lowercase()).append('\n')
        append("created: ").append(formatTime(created)).append('\n')
        append("---\n\n")
        append(body.trim('\n')).append('\n')
    }

    /** 본문에서 [[링크]] 제목들을 뽑는다 (별칭 [[제목|별칭]] 은 제목만) */
    fun linkTitles(body: String): List<String> =
        WIKILINK.findAll(body).map { it.groupValues[1].substringBefore('|').trim() }
            .filter { it.isNotEmpty() }.distinct().toList()

    private fun parseTime(v: String): Long? =
        runCatching {
            val s = v.trim().take(19).let { if (it.length == 16) "$it:00" else it }
            java.time.LocalDateTime.parse(s)
                .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.getOrNull()

    private fun formatTime(ms: Long): String =
        java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault())
            .toLocalDateTime().withNano(0).toString()
}
