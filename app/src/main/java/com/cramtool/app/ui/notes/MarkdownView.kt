package com.cramtool.app.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.cramtool.app.notes.MdBlock

/** 把一個區塊畫出來。scale = 使用者調整的字體倍率 */
@Composable
fun MdBlockView(block: MdBlock, scale: Float, modifier: Modifier = Modifier) {
    val t = MaterialTheme.typography
    val scheme = MaterialTheme.colorScheme
    val codeBg = scheme.surfaceVariant
    fun TextStyle.s() = copy(fontSize = fontSize * scale, lineHeight = lineHeight * scale)
    val body = t.bodyLarge.s()

    when (block) {
        is MdBlock.Heading -> {
            val style = when (block.level) {
                1 -> t.headlineSmall
                2 -> t.titleLarge
                3 -> t.titleMedium
                else -> t.titleSmall
            }.s().copy(fontWeight = FontWeight.Bold, color = if (block.level <= 2) scheme.primary else scheme.onSurface)
            Column(modifier.padding(top = if (block.level <= 2) 20.dp else 12.dp, bottom = 4.dp)) {
                Text(inline(block.text, codeBg), style = style)
                if (block.level == 1) HorizontalDivider(Modifier.padding(top = 6.dp))
            }
        }
        is MdBlock.Paragraph -> Text(inline(block.text, codeBg), style = body, modifier = modifier.padding(vertical = 4.dp))
        is MdBlock.ListItem -> Row(modifier.padding(start = (block.depth * 18).dp, top = 2.dp, bottom = 2.dp)) {
            val marker = when {
                block.checked == true -> "☑"
                block.checked == false -> "☐"
                block.marker != null -> block.marker
                else -> listOf("•", "◦", "▪")[block.depth % 3]
            }
            Text(marker, style = body, color = scheme.primary, modifier = Modifier.width((22 * scale).dp))
            Text(inline(block.text, codeBg), style = body, modifier = Modifier.weight(1f))
        }
        is MdBlock.Quote -> Row(modifier.padding(vertical = 6.dp).height(IntrinsicSize.Min)) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(scheme.primary.copy(alpha = .5f), RoundedCornerShape(2.dp)))
            Text(
                inline(block.text, codeBg), style = body.copy(color = scheme.onSurfaceVariant),
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        is MdBlock.Code -> Column(
            modifier.padding(vertical = 6.dp).fillMaxWidth()
                .background(codeBg, RoundedCornerShape(8.dp)).padding(12.dp)
        ) {
            if (block.language.isNotBlank()) {
                Text(block.language, style = t.labelSmall, color = scheme.onSurfaceVariant)
            }
            Text(
                block.code,
                style = t.bodyMedium.s().copy(fontFamily = FontFamily.Monospace),
                softWrap = false,
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            )
        }
        is MdBlock.Math -> Box(
            modifier.padding(vertical = 6.dp).fillMaxWidth()
                .background(codeBg.copy(alpha = .5f), RoundedCornerShape(8.dp)).padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(block.tex, style = body.copy(fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic))
        }
        is MdBlock.Table -> TableView(block, body, codeBg, modifier)
        MdBlock.Rule -> HorizontalDivider(modifier.padding(vertical = 12.dp))
        is MdBlock.Image -> ImageBlock(block, modifier)
    }
}

@Composable
private fun TableView(table: MdBlock.Table, style: TextStyle, codeBg: Color, modifier: Modifier) {
    val border = MaterialTheme.colorScheme.outlineVariant
    // 依內容長度估算欄寬
    val widths = table.header.indices.map { c ->
        val maxLen = (listOf(table.header[c]) + table.rows.map { it[c] }).maxOf { it.length }
        (maxLen * 14 + 24).coerceIn(72, 260).dp
    }
    Column(
        modifier.padding(vertical = 6.dp).horizontalScroll(rememberScrollState())
            .border(1.dp, border, RoundedCornerShape(6.dp))
    ) {
        (listOf(table.header) + table.rows).forEachIndexed { r, row ->
            Row(
                Modifier.height(IntrinsicSize.Min)
                    .background(if (r == 0) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            ) {
                row.forEachIndexed { c, cell ->
                    Box(Modifier.width(widths[c]).fillMaxHeight().border(0.5.dp, border).padding(8.dp)) {
                        Text(
                            inline(cell, codeBg),
                            style = if (r == 0) style.copy(fontWeight = FontWeight.Bold) else style,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 行內 Markdown：**粗體**、*斜體*、`code`、~~刪除線~~、[連結](url)、$數學$、<br>
 */
fun inline(src: String, codeBg: Color): AnnotatedString = buildAnnotatedString {
    val s = src.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
    var i = 0
    val bold = SpanStyle(fontWeight = FontWeight.Bold)
    val italic = SpanStyle(fontStyle = FontStyle.Italic)
    val strike = SpanStyle(textDecoration = TextDecoration.LineThrough)
    val code = SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg, fontSize = 0.92.em)
    val math = SpanStyle(fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic)
    val link = SpanStyle(textDecoration = TextDecoration.Underline, color = Color(0xFF1E88E5))

    fun wrap(open: String, close: String = open, style: SpanStyle, recurse: Boolean = true): Boolean {
        if (!s.startsWith(open, i)) return false
        val end = s.indexOf(close, i + open.length)
        if (end <= i + open.length) return false
        val inner = s.substring(i + open.length, end)
        if (inner.startsWith(" ") || inner.endsWith(" ")) return false
        pushStyle(style)
        if (recurse) append(inline(inner, codeBg)) else append(inner)
        pop()
        i = end + close.length
        return true
    }

    while (i < s.length) {
        val c = s[i]
        val handled = when (c) {
            '\\' -> if (i + 1 < s.length) { append(s[i + 1]); i += 2; true } else false
            '`' -> wrap("`", style = code, recurse = false)
            '*' -> wrap("**", style = bold) || wrap("*", style = italic)
            '_' -> (i == 0 || !s[i - 1].isLetterOrDigit()) && (wrap("__", style = bold) || wrap("_", style = italic))
            '~' -> wrap("~~", style = strike)
            '$' -> wrap("$", style = math, recurse = false)
            '[' -> {
                val m = Regex("""^\[([^\]]+)]\(([^)\s]+)[^)]*\)""").find(s.substring(i))
                if (m != null) {
                    pushStyle(link); append(inline(m.groupValues[1], codeBg)); pop()
                    i += m.value.length
                    true
                } else false
            }
            else -> false
        }
        if (!handled) { append(c); i++ }
    }
}
