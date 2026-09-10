package com.blank.app.ui.note

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.ClickableText
import com.blank.app.domain.MdBlock
import com.blank.app.domain.Markdown
import com.blank.app.ui.theme.Accent
import com.blank.app.ui.theme.CardBg
import com.blank.app.ui.theme.Outline
import com.blank.app.ui.theme.TextPrimary
import com.blank.app.ui.theme.TextSecondary

/**
 * 마크다운 렌더 뷰. [[위키링크]] 탭은 [onWikiLink], [텍스트](url) 탭은 [onUrl].
 */
@Composable
fun MarkdownView(
    body: String,
    onWikiLink: (String) -> Unit,
    onUrl: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Markdown.blocks(body).forEach { block ->
            when (block) {
                is MdBlock.Heading -> {
                    Spacer(Modifier.height(if (block.level <= 2) 14.dp else 8.dp))
                    InlineText(block.text, onWikiLink, onUrl,
                        size = when (block.level) { 1 -> 26; 2 -> 21; 3 -> 18; else -> 16 }.sp,
                        weight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                }
                is MdBlock.Paragraph -> {
                    InlineText(block.text, onWikiLink, onUrl)
                    Spacer(Modifier.height(8.dp))
                }
                is MdBlock.Bullet -> {
                    Row(Modifier.padding(vertical = 2.dp)) {
                        Text(if (block.ordered) "${block.number}. " else "•  ",
                            color = TextSecondary, fontSize = 15.sp)
                        InlineText(block.text, onWikiLink, onUrl, modifier = Modifier.weight(1f))
                    }
                }
                is MdBlock.Quote -> {
                    Row(Modifier.padding(vertical = 4.dp)) {
                        Box(Modifier.width(3.dp).height(20.dp).clip(RoundedCornerShape(2.dp)).background(Outline))
                        Spacer(Modifier.width(10.dp))
                        InlineText(block.text, onWikiLink, onUrl, color = TextSecondary)
                    }
                }
                is MdBlock.Code -> {
                    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp)
                        .clip(RoundedCornerShape(10.dp)).background(CardBg).padding(12.dp)) {
                        Text(block.text, fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = TextPrimary)
                    }
                }
                MdBlock.Divider -> {
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Outline))
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun InlineText(
    text: String,
    onWikiLink: (String) -> Unit,
    onUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.TextUnit = 16.sp,
    weight: FontWeight = FontWeight.Normal,
    color: androidx.compose.ui.graphics.Color = TextPrimary
) {
    val annotated = Markdown.inline(text, Accent)
    ClickableText(
        text = annotated,
        modifier = modifier,
        style = MaterialTheme.typography.bodyLarge.copy(color = color, fontSize = size, fontWeight = weight),
        onClick = { offset ->
            annotated.getStringAnnotations("wiki", offset, offset).firstOrNull()?.let { onWikiLink(it.item); return@ClickableText }
            annotated.getStringAnnotations("url", offset, offset).firstOrNull()?.let { onUrl(it.item) }
        }
    )
}
