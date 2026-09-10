package com.blank.app.domain

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 손으로 짠 마크다운 렌더러. 마크다운 라이브러리를 안 쓰는 이유는 로컬에 컴파일러가
 * 없어 의존성 하나가 늘 때마다 빌드 실패의 왕복 비용이 크기 때문이다. 필요한 문법
 * (제목·굵게·기울임·인라인코드·목록·인용·코드블록·링크·[[위키링크]])만 직접 그린다.
 */
sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Bullet(val text: String, val ordered: Boolean, val number: Int) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Code(val text: String) : MdBlock
    data object Divider : MdBlock
}

object Markdown {

    fun blocks(body: String): List<MdBlock> {
        val out = mutableListOf<MdBlock>()
        val lines = body.lines()
        var i = 0
        var orderCounter = 0
        while (i < lines.size) {
            val line = lines[i]
            val t = line.trim()
            when {
                t.startsWith("```") -> {
                    val sb = StringBuilder()
                    i++
                    while (i < lines.size && !lines[i].trim().startsWith("```")) {
                        sb.appendLine(lines[i]); i++
                    }
                    out += MdBlock.Code(sb.toString().trimEnd('\n'))
                }
                t.isEmpty() -> orderCounter = 0
                t == "---" || t == "***" -> out += MdBlock.Divider
                t.startsWith("#") -> {
                    val level = t.takeWhile { it == '#' }.length.coerceIn(1, 4)
                    out += MdBlock.Heading(level, t.drop(level).trim())
                }
                t.startsWith("> ") -> out += MdBlock.Quote(t.removePrefix("> "))
                t.startsWith("- ") || t.startsWith("* ") ->
                    out += MdBlock.Bullet(t.drop(2), ordered = false, number = 0)
                t.matches(Regex("""^\d+\.\s.*""")) -> {
                    orderCounter++
                    out += MdBlock.Bullet(t.substringAfter(". "), ordered = true, number = orderCounter)
                }
                else -> out += MdBlock.Paragraph(t)
            }
            i++
        }
        return out
    }

    private val WIKILINK = Regex("""\[\[([^\]]+)]]""")
    private val LINK = Regex("""\[([^\]]+)]\(([^)]+)\)""")

    /**
     * 인라인 마크다운을 AnnotatedString 으로. [[위키링크]] 와 [텍스트](url) 에는
     * annotation 을 달아 탭을 잡을 수 있게 한다. 링크 태그: "wiki"=제목, "url"=주소.
     */
    fun inline(text: String, accent: androidx.compose.ui.graphics.Color): AnnotatedString =
        buildAnnotatedString {
            var rest = text
            // 위키링크·링크를 먼저 자리표시자로 빼내면 굵게/기울임 처리와 안 섞인다
            val tokens = mutableListOf<Triple<IntRange, String, String>>() // range, kind, payload/display
            WIKILINK.findAll(text).forEach {
                val shown = it.groupValues[1].substringAfter('|', it.groupValues[1].substringBefore('|'))
                    .ifBlank { it.groupValues[1] }
                tokens += Triple(it.range, "wiki:${it.groupValues[1].substringBefore('|').trim()}", shown)
            }
            LINK.findAll(text).forEach {
                tokens += Triple(it.range, "url:${it.groupValues[2]}", it.groupValues[1])
            }
            tokens.sortBy { it.first.first }

            var cursor = 0
            fun emphasize(seg: String) { appendEmphasized(seg) }
            for ((range, kind, shown) in tokens) {
                if (range.first < cursor) continue
                if (range.first > cursor) emphasize(text.substring(cursor, range.first))
                pushStringAnnotation(if (kind.startsWith("wiki")) "wiki" else "url",
                    kind.substringAfter(':'))
                withStyle(SpanStyle(color = accent, fontWeight = FontWeight.Medium)) { append(shown) }
                pop()
                cursor = range.last + 1
            }
            if (cursor < text.length) emphasize(text.substring(cursor))
            rest = ""
        }

    private fun androidx.compose.ui.text.AnnotatedString.Builder.appendEmphasized(seg: String) {
        var i = 0
        while (i < seg.length) {
            when {
                seg.startsWith("**", i) -> {
                    val end = seg.indexOf("**", i + 2)
                    if (end > 0) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(seg.substring(i + 2, end)) }
                        i = end + 2; continue
                    }
                }
                seg[i] == '*' || seg[i] == '_' -> {
                    val ch = seg[i]
                    val end = seg.indexOf(ch, i + 1)
                    if (end > 0) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(seg.substring(i + 1, end)) }
                        i = end + 1; continue
                    }
                }
                seg[i] == '`' -> {
                    val end = seg.indexOf('`', i + 1)
                    if (end > 0) {
                        withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(seg.substring(i + 1, end)) }
                        i = end + 1; continue
                    }
                }
            }
            append(seg[i]); i++
        }
    }
}
