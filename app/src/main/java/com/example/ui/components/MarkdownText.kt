package com.example.ui.components

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AuraViolet
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.EmeraldLossless
import com.example.ui.theme.RubyAccent
import com.example.ui.theme.adaptiveGoldColor
import java.net.URI
import java.net.URLDecoder

internal enum class TableAlign { START, CENTER, END }

internal enum class ToolTraceKind { SEARCH, FETCH, RESULT, TOOL }

internal sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data class BulletItem(val text: String, val indentLevel: Int = 0) : MarkdownBlock
    data class TaskItem(val checked: Boolean, val text: String, val indentLevel: Int = 0) : MarkdownBlock
    data class NumberedItem(val number: String, val text: String) : MarkdownBlock
    data class Blockquote(val text: String) : MarkdownBlock
    data class ToolTrace(val kind: ToolTraceKind, val label: String, val detail: String) : MarkdownBlock
    data class ReferenceLink(val title: String, val url: String) : MarkdownBlock
    data class Table(
        val headers: List<String>,
        val alignments: List<TableAlign>,
        val rows: List<List<String>>
    ) : MarkdownBlock
    data class CodeBlock(val language: String, val code: String) : MarkdownBlock
    data object Divider : MarkdownBlock
}

/**
 * Opens an external http(s) URL in the device's system browser with chooser fallback.
 */
fun openExternalUrl(context: Context, rawUrl: String): Boolean {
    val trimmed = rawUrl.trim()
    if (!trimmed.startsWith("http://", ignoreCase = true) &&
        !trimmed.startsWith("https://", ignoreCase = true)
    ) {
        return false
    }
    val uri = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return false
    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
        if (context !is Activity) {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
    val opened = runCatching {
        context.startActivity(intent)
        true
    }.getOrElse {
        runCatching {
            val chooser = Intent.createChooser(intent, "选择浏览器打开链接").apply {
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        }.getOrDefault(false)
    }
    if (!opened) {
        Toast.makeText(context, "未找到可打开此网页链接的浏览器应用", Toast.LENGTH_SHORT).show()
    }
    return opened
}

/**
 * Lightweight, high-craft Jetpack Compose Markdown renderer tailored for Auralis AI responses.
 * Supports H1-H4 headings, GFM tables, iconified tool/search trace cards, clickable reference
 * source link cards, inline links, bold, italic, strikethrough, inline code, task lists,
 * bullet & numbered lists, blockquotes, horizontal dividers, and fenced code blocks.
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    baseColor: Color = MaterialTheme.colorScheme.onSurface,
    baseStyle: TextStyle = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
    accentColor: Color = adaptiveGoldColor(),
    onOpenUrl: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val urlOpener: (String) -> Unit = remember(context, onOpenUrl) {
        { url: String ->
            if (onOpenUrl != null) {
                onOpenUrl(url)
            } else {
                openExternalUrl(context, url)
            }
            Unit
        }
    }
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }
    val codeBg = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.85f)
    val quoteBg = accentColor.copy(alpha = 0.08f)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> {
                    val (fontSize, lineH, color, topPad) = when (block.level) {
                        1 -> Quad(20.sp, 27.sp, accentColor, 6.dp)
                        2 -> Quad(18.sp, 25.sp, baseColor, 5.dp)
                        3 -> Quad(16.sp, 23.sp, accentColor.copy(alpha = 0.92f), 4.dp)
                        else -> Quad(15.sp, 21.sp, baseColor.copy(alpha = 0.95f), 2.dp)
                    }
                    Text(
                        text = parseInlineMarkdown(block.text, accentColor, codeBg, urlOpener),
                        style = baseStyle.copy(
                            fontSize = fontSize,
                            lineHeight = lineH,
                            fontWeight = FontWeight.Bold,
                            color = color
                        ),
                        modifier = Modifier.padding(top = topPad)
                    )
                }

                is MarkdownBlock.Paragraph -> {
                    Text(
                        text = parseInlineMarkdown(block.text, accentColor, codeBg, urlOpener),
                        style = baseStyle.copy(color = baseColor)
                    )
                }

                is MarkdownBlock.BulletItem -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = (block.indentLevel * 14).dp)
                    ) {
                        Text(
                            text = "•",
                            style = baseStyle.copy(
                                color = accentColor,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = parseInlineMarkdown(block.text, accentColor, codeBg, urlOpener),
                            style = baseStyle.copy(color = baseColor),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                is MarkdownBlock.TaskItem -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = (block.indentLevel * 14).dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (block.checked) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
                            contentDescription = if (block.checked) "已完成" else "待办",
                            tint = if (block.checked) EmeraldLossless else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(17.dp)
                        )
                        Text(
                            text = parseInlineMarkdown(block.text, accentColor, codeBg, urlOpener),
                            style = baseStyle.copy(
                                color = if (block.checked) baseColor.copy(alpha = 0.72f) else baseColor,
                                textDecoration = if (block.checked) TextDecoration.LineThrough else TextDecoration.None
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                is MarkdownBlock.NumberedItem -> {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "${block.number}.",
                            style = baseStyle.copy(
                                color = accentColor,
                                fontWeight = FontWeight.SemiBold
                            ),
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = parseInlineMarkdown(block.text, accentColor, codeBg, urlOpener),
                            style = baseStyle.copy(color = baseColor),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                is MarkdownBlock.ToolTrace -> {
                    ToolTraceCard(
                        block = block,
                        baseColor = baseColor,
                        baseStyle = baseStyle,
                        accentColor = accentColor,
                        codeBg = codeBg,
                        onOpenUrl = urlOpener
                    )
                }

                is MarkdownBlock.ReferenceLink -> {
                    ReferenceLinkRow(
                        block = block,
                        accentColor = accentColor,
                        onClick = { urlOpener(block.url) }
                    )
                }

                is MarkdownBlock.Table -> {
                    MarkdownTableCard(
                        table = block,
                        baseColor = baseColor,
                        baseStyle = baseStyle,
                        accentColor = accentColor,
                        codeBg = codeBg,
                        onOpenUrl = urlOpener
                    )
                }

                is MarkdownBlock.Blockquote -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .clip(RoundedCornerShape(10.dp))
                            .background(quoteBg)
                            .padding(vertical = 8.dp, horizontal = 10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(2.dp))
                                .background(accentColor)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = parseInlineMarkdown(block.text, accentColor, codeBg, urlOpener),
                            style = baseStyle.copy(
                                color = baseColor.copy(alpha = 0.88f),
                                fontStyle = FontStyle.Italic
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                is MarkdownBlock.CodeBlock -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(codeBg)
                            .border(0.6.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (block.language.isNotBlank()) {
                            Text(
                                text = block.language.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = accentColor
                            )
                        }
                        Text(
                            text = block.code,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        )
                    }
                }

                MarkdownBlock.Divider -> {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolTraceCard(
    block: MarkdownBlock.ToolTrace,
    baseColor: Color,
    baseStyle: TextStyle,
    accentColor: Color,
    codeBg: Color,
    onOpenUrl: (String) -> Unit
) {
    val (icon, tint) = when (block.kind) {
        ToolTraceKind.SEARCH -> Icons.Rounded.TravelExplore to accentColor
        ToolTraceKind.FETCH -> Icons.Rounded.Language to adaptiveGoldColor()
        ToolTraceKind.RESULT -> Icons.Rounded.LibraryBooks to EmeraldLossless
        ToolTraceKind.TOOL -> Icons.Rounded.Build to AuraViolet
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(tint.copy(alpha = 0.09f))
            .border(0.6.dp, tint.copy(alpha = 0.28f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Box(
            modifier = Modifier
                .padding(top = 1.dp)
                .size(24.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(tint.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = block.label,
                tint = tint,
                modifier = Modifier.size(14.dp)
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = block.label,
                style = MaterialTheme.typography.labelSmall,
                color = tint,
                fontWeight = FontWeight.Bold
            )
            if (block.detail.isNotBlank()) {
                Text(
                    text = parseInlineMarkdown(block.detail, tint, codeBg, onOpenUrl),
                    style = baseStyle.copy(
                        color = baseColor.copy(alpha = 0.90f),
                        lineHeight = 18.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun ReferenceLinkRow(
    block: MarkdownBlock.ReferenceLink,
    accentColor: Color,
    onClick: () -> Unit
) {
    val hostLabel = remember(block.url) {
        runCatching { URI(block.url).host?.removePrefix("www.").orEmpty() }
            .getOrDefault("")
            .ifBlank { block.url.take(48) }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.85f))
            .border(0.6.dp, accentColor.copy(alpha = 0.30f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(accentColor.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Public,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(15.dp)
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = block.title,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Medium,
                    lineHeight = 17.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = hostLabel,
                style = MaterialTheme.typography.labelSmall,
                color = accentColor.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
            contentDescription = "在浏览器打开",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun MarkdownTableCard(
    table: MarkdownBlock.Table,
    baseColor: Color,
    baseStyle: TextStyle,
    accentColor: Color,
    codeBg: Color,
    onOpenUrl: (String) -> Unit
) {
    val colCount = table.headers.size.coerceAtLeast(1)
    val useFullWidthFlex = colCount <= 2
    val flexWeights = remember(table) {
        if (!useFullWidthFlex) emptyList()
        else {
            val avgLens = (0 until colCount).map { col ->
                val headerLen = table.headers.getOrElse(col) { "" }.length
                val rowAvg = table.rows.map { it.getOrElse(col) { "" }.length }.average().takeIf { !it.isNaN() } ?: 0.0
                maxOf(headerLen.toDouble(), rowAvg).coerceIn(4.0, 40.0).toFloat()
            }
            val total = avgLens.sum().coerceAtLeast(1f)
            avgLens.map { (it / total).coerceIn(0.28f, 0.72f) }
        }
    }

    val colWidthsDp = remember(table) {
        if (useFullWidthFlex) emptyList()
        else {
            (0 until colCount).map { col ->
                val maxLen = maxOf(
                    table.headers.getOrElse(col) { "" }.length,
                    table.rows.maxOfOrNull { it.getOrElse(col) { "" }.length } ?: 0
                )
                when {
                    maxLen <= 8 -> 96.dp
                    maxLen <= 16 -> 132.dp
                    else -> 184.dp
                }
            }
        }
    }

    val tableShape = RoundedCornerShape(12.dp)
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)
    val headerBg = accentColor.copy(alpha = 0.14f)
    val evenRowBg = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.65f)
    val oddRowBg = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.45f)
    val cellStyle = baseStyle.copy(fontSize = 13.sp, lineHeight = 19.sp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(tableShape)
            .border(0.75.dp, borderColor, tableShape)
    ) {
        val containerModifier = if (useFullWidthFlex) {
            Modifier.fillMaxWidth()
        } else {
            Modifier.horizontalScroll(rememberScrollState())
        }

        Column(modifier = containerModifier) {
            // Header Row
            Row(
                modifier = (if (useFullWidthFlex) Modifier.fillMaxWidth() else Modifier)
                    .background(headerBg)
                    .height(IntrinsicSize.Min)
            ) {
                for (col in 0 until colCount) {
                    val align = when (table.alignments.getOrElse(col) { TableAlign.START }) {
                        TableAlign.START -> TextAlign.Start
                        TableAlign.CENTER -> TextAlign.Center
                        TableAlign.END -> TextAlign.End
                    }
                    val cellMod = if (useFullWidthFlex) {
                        Modifier.weight(flexWeights.getOrElse(col) { 1f })
                    } else {
                        Modifier.width(colWidthsDp.getOrElse(col) { 128.dp })
                    }
                    Box(
                        modifier = cellMod
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        contentAlignment = when (align) {
                            TextAlign.Center -> Alignment.Center
                            TextAlign.End -> Alignment.CenterEnd
                            else -> Alignment.CenterStart
                        }
                    ) {
                        Text(
                            text = parseInlineMarkdown(table.headers.getOrElse(col) { "" }, accentColor, codeBg, onOpenUrl),
                            style = cellStyle.copy(
                                fontWeight = FontWeight.Bold,
                                color = accentColor,
                                textAlign = align
                            )
                        )
                    }
                    if (col < colCount - 1) {
                        Box(
                            modifier = Modifier
                                .width(0.5.dp)
                                .fillMaxHeight()
                                .background(borderColor)
                        )
                    }
                }
            }

            // Data Rows
            table.rows.forEachIndexed { rowIdx, row ->
                HorizontalDivider(thickness = 0.5.dp, color = borderColor)
                Row(
                    modifier = (if (useFullWidthFlex) Modifier.fillMaxWidth() else Modifier)
                        .background(if (rowIdx % 2 == 0) evenRowBg else oddRowBg)
                        .height(IntrinsicSize.Min)
                ) {
                    for (col in 0 until colCount) {
                        val align = when (table.alignments.getOrElse(col) { TableAlign.START }) {
                            TableAlign.START -> TextAlign.Start
                            TableAlign.CENTER -> TextAlign.Center
                            TableAlign.END -> TextAlign.End
                        }
                        val cellMod = if (useFullWidthFlex) {
                            Modifier.weight(flexWeights.getOrElse(col) { 1f })
                        } else {
                            Modifier.width(colWidthsDp.getOrElse(col) { 128.dp })
                        }
                        Box(
                            modifier = cellMod
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            contentAlignment = when (align) {
                                TextAlign.Center -> Alignment.Center
                                TextAlign.End -> Alignment.CenterEnd
                                else -> Alignment.CenterStart
                            }
                        ) {
                            Text(
                                text = parseInlineMarkdown(row.getOrElse(col) { "" }, accentColor, codeBg, onOpenUrl),
                                style = cellStyle.copy(
                                    color = baseColor,
                                    fontWeight = if (col == 0) FontWeight.Medium else FontWeight.Normal,
                                    textAlign = align
                                )
                            )
                        }
                        if (col < colCount - 1) {
                            Box(
                                modifier = Modifier
                                    .width(0.5.dp)
                                    .fillMaxHeight()
                                    .background(borderColor.copy(alpha = 0.65f))
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

private val HEADING_REGEX = Regex("""^(#{1,6})\s+(.+)$""")
private val ORDERED_LIST_REGEX = Regex("""^\s*(\d+)[.)]\s+(.+)$""")
private val TASK_LIST_REGEX = Regex("""^(\s*)[-*+•]\s+\[([ xX])\]\s+(.+)$""")
private val BULLET_LIST_REGEX = Regex("""^(\s*)[-*+•]\s+(.+)$""")
private val DIVIDER_REGEX = Regex("""^\s*([-*_])\1{2,}\s*$""")
private val TABLE_SEPARATOR_REGEX = Regex("""^\s*\|?\s*:?-{2,}:?\s*(\|\s*:?-{2,}:?\s*)+\|?\s*$""")
private val STANDALONE_LINK_REGEX = Regex("""^\[([^\]\n]+)\]\((https?://[^\s)\n]+)\)$""")

internal fun parseMarkdownBlocks(raw: String): List<MarkdownBlock> {
    val lines = raw.replace("\r\n", "\n").split("\n")
    val blocks = mutableListOf<MarkdownBlock>()
    val paragraphBuffer = mutableListOf<String>()

    fun flushParagraph() {
        if (paragraphBuffer.isNotEmpty()) {
            blocks.add(MarkdownBlock.Paragraph(paragraphBuffer.joinToString("\n")))
            paragraphBuffer.clear()
        }
    }

    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        // Fenced code block ```
        if (trimmed.startsWith("```")) {
            flushParagraph()
            val lang = trimmed.removePrefix("```").trim()
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                codeLines.add(lines[i])
                i++
            }
            blocks.add(MarkdownBlock.CodeBlock(lang, codeLines.joinToString("\n")))
            i++
            continue
        }

        if (trimmed.isEmpty()) {
            flushParagraph()
            i++
            continue
        }

        // GFM Table detection (header row with '|' + separator row matching |---|---|)
        if (trimmed.contains('|') && i + 1 < lines.size && TABLE_SEPARATOR_REGEX.matches(lines[i + 1].trim())) {
            flushParagraph()
            val headers = splitTableRowCells(trimmed)
            val sepCells = splitTableRowCells(lines[i + 1].trim())
            val colCount = maxOf(headers.size, sepCells.size).coerceAtLeast(1)
            val alignments = (0 until colCount).map { col ->
                val token = sepCells.getOrElse(col) { "---" }.trim()
                when {
                    token.startsWith(":") && token.endsWith(":") -> TableAlign.CENTER
                    token.endsWith(":") -> TableAlign.END
                    else -> TableAlign.START
                }
            }
            val normalizedHeaders = (0 until colCount).map { headers.getOrElse(it) { "" } }
            val rows = mutableListOf<List<String>>()
            i += 2

            while (i < lines.size) {
                val curTrimmed = lines[i].trim()
                if (curTrimmed.isEmpty()) break
                if (!curTrimmed.contains('|')) break
                if (HEADING_REGEX.matches(curTrimmed) || curTrimmed.startsWith("```")) break

                // Merge accidental soft-wrapped row lines (e.g. model emits `|col1|text\n|` or `|col1|text\nmore|`)
                val rowBuilder = StringBuilder(curTrimmed)
                while (
                    i + 1 < lines.size &&
                    countPipes(rowBuilder.toString()) < colCount + 1
                ) {
                    val nextTrim = lines[i + 1].trim()
                    if (nextTrim.isEmpty()) break
                    if (nextTrim == "|") {
                        rowBuilder.append("|")
                        i++
                        break
                    }
                    // If next line starts a brand new row (begins with `|` and has >= 2 pipes), stop merging
                    if (nextTrim.startsWith("|") && countPipes(nextTrim) >= 2) break
                    rowBuilder.append(" ").append(nextTrim)
                    i++
                }

                // Consume orphan trailing `|` line emitted right after a complete row
                if (i + 1 < lines.size && lines[i + 1].trim() == "|") {
                    i++
                }

                val parsedCells = splitTableRowCells(rowBuilder.toString())
                if (parsedCells.any { it.isNotBlank() }) {
                    rows.add((0 until colCount).map { parsedCells.getOrElse(it) { "" } })
                }
                i++
            }

            blocks.add(MarkdownBlock.Table(normalizedHeaders, alignments, rows))
            continue
        }

        if (DIVIDER_REGEX.matches(trimmed)) {
            flushParagraph()
            blocks.add(MarkdownBlock.Divider)
            i++
            continue
        }

        val headingMatch = HEADING_REGEX.matchEntire(trimmed)
        if (headingMatch != null) {
            flushParagraph()
            blocks.add(
                MarkdownBlock.Heading(
                    level = headingMatch.groupValues[1].length.coerceIn(1, 4),
                    text = headingMatch.groupValues[2].trim()
                )
            )
            i++
            continue
        }

        if (trimmed.startsWith(">")) {
            flushParagraph()
            val quoteLines = mutableListOf<String>()
            while (i < lines.size && lines[i].trim().startsWith(">")) {
                quoteLines.add(lines[i].trim().removePrefix(">").trim())
                i++
            }
            val joined = quoteLines.joinToString("\n").trim()
            val toolTrace = parseToolTraceBlock(joined)
            if (toolTrace != null) {
                blocks.add(toolTrace)
            } else {
                blocks.add(MarkdownBlock.Blockquote(joined))
            }
            continue
        }

        val taskMatch = TASK_LIST_REGEX.matchEntire(line)
        if (taskMatch != null) {
            flushParagraph()
            val indent = (taskMatch.groupValues[1].length / 2).coerceAtMost(3)
            val checked = taskMatch.groupValues[2].equals("x", ignoreCase = true)
            blocks.add(MarkdownBlock.TaskItem(checked, taskMatch.groupValues[3].trim(), indent))
            i++
            continue
        }

        val bulletMatch = BULLET_LIST_REGEX.matchEntire(line)
        if (bulletMatch != null) {
            flushParagraph()
            val indent = (bulletMatch.groupValues[1].length / 2).coerceAtMost(3)
            val body = bulletMatch.groupValues[2].trim()
            val standaloneLink = STANDALONE_LINK_REGEX.matchEntire(body)
            if (standaloneLink != null && indent == 0) {
                blocks.add(
                    MarkdownBlock.ReferenceLink(
                        title = standaloneLink.groupValues[1].trim(),
                        url = standaloneLink.groupValues[2].trim()
                    )
                )
            } else {
                blocks.add(MarkdownBlock.BulletItem(body, indent))
            }
            i++
            continue
        }

        val orderedMatch = ORDERED_LIST_REGEX.matchEntire(line)
        if (orderedMatch != null) {
            flushParagraph()
            blocks.add(MarkdownBlock.NumberedItem(orderedMatch.groupValues[1], orderedMatch.groupValues[2].trim()))
            i++
            continue
        }

        paragraphBuffer.add(line)
        i++
    }
    flushParagraph()
    return blocks
}

private fun countPipes(line: String): Int = line.count { it == '|' }

private fun splitTableRowCells(rawRow: String): List<String> {
    var s = rawRow.trim()
    if (s.startsWith("|")) s = s.substring(1)
    if (s.endsWith("|")) s = s.dropLast(1)
    return s.split("|").map { it.trim() }
}

private fun parseToolTraceBlock(content: String): MarkdownBlock.ToolTrace? {
    val trimmed = content.trim()
    var kind: ToolTraceKind? = null
    var body = trimmed

    when {
        trimmed.startsWith("[[SEARCH]]") -> {
            kind = ToolTraceKind.SEARCH
            body = trimmed.removePrefix("[[SEARCH]]").trim()
        }
        trimmed.startsWith("[[FETCH]]") -> {
            kind = ToolTraceKind.FETCH
            body = trimmed.removePrefix("[[FETCH]]").trim()
        }
        trimmed.startsWith("[[RESULT]]") -> {
            kind = ToolTraceKind.RESULT
            body = trimmed.removePrefix("[[RESULT]]").trim()
        }
        trimmed.startsWith("[[TOOL]]") -> {
            kind = ToolTraceKind.TOOL
            body = trimmed.removePrefix("[[TOOL]]").trim()
        }
        // Backward compatibility for already-saved messages containing emojis
        trimmed.startsWith("🔍") -> {
            kind = ToolTraceKind.SEARCH
            body = trimmed.removePrefix("🔍").trim()
        }
        trimmed.startsWith("🌐") || trimmed.startsWith("📄") -> {
            kind = ToolTraceKind.FETCH
            body = trimmed.removePrefix("🌐").removePrefix("📄").trim()
        }
        trimmed.startsWith("📚") -> {
            kind = ToolTraceKind.RESULT
            body = trimmed.removePrefix("📚").trim()
        }
        trimmed.startsWith("🛠️") || trimmed.startsWith("🛠") -> {
            kind = ToolTraceKind.TOOL
            body = trimmed.removePrefix("🛠️").removePrefix("🛠").trim()
        }
    }
    if (kind == null) return null

    // Extract bold prefix label like "**联网搜索**：..." or "**已检索到 42 条网页资料**（...）"
    val cleanNoBoldPrefix = body
    val colonRegex = Regex("""^\*\*([^*]+)\*\*\s*[：:]\s*(.*)$""", RegexOption.DOT_MATCHES_ALL)
    val colonMatch = colonRegex.matchEntire(cleanNoBoldPrefix)
    val (rawLabel, rawDetail) = if (colonMatch != null) {
        colonMatch.groupValues[1].trim() to colonMatch.groupValues[2].trim()
    } else {
        val boldHeadRegex = Regex("""^\*\*([^*]+)\*\*\s*(.*)$""", RegexOption.DOT_MATCHES_ALL)
        val boldMatch = boldHeadRegex.matchEntire(cleanNoBoldPrefix)
        if (boldMatch != null) {
            boldMatch.groupValues[1].trim() to boldMatch.groupValues[2].trim()
        } else {
            val defaultLabel = when (kind) {
                ToolTraceKind.SEARCH -> "联网搜索"
                ToolTraceKind.FETCH -> "读取网页"
                ToolTraceKind.RESULT -> "检索完成"
                ToolTraceKind.TOOL -> "工具调用"
            }
            defaultLabel to cleanNoBoldPrefix
        }
    }

    // Reclassify legacy "联网搜索: https://..." traces as 读取网页 + decode percent-encoding
    val isPureUrl = rawDetail.startsWith("http://", true) || rawDetail.startsWith("https://", true)
    val finalKind = if (kind == ToolTraceKind.SEARCH && isPureUrl) ToolTraceKind.FETCH else kind
    val finalLabel = if (kind == ToolTraceKind.SEARCH && isPureUrl) "读取网页" else rawLabel
    val finalDetail = if (isPureUrl) {
        runCatching {
            val uri = URI(rawDetail.trim())
            val host = uri.host?.removePrefix("www.").orEmpty()
            val decodedPath = runCatching { URLDecoder.decode(uri.rawPath.orEmpty(), "UTF-8") }
                .getOrDefault(uri.rawPath.orEmpty())
            val compact = if (decodedPath.isNotEmpty() && decodedPath != "/") "$host$decodedPath" else host
            if (compact.isNotBlank()) "[$compact](${rawDetail.trim()})" else rawDetail
        }.getOrDefault(rawDetail)
    } else {
        rawDetail
    }

    return MarkdownBlock.ToolTrace(kind = finalKind, label = finalLabel, detail = finalDetail)
}

private val INLINE_TOKEN_REGEX = Regex(
    """(\[[^\]\n]+\]\(https?://[^\s)\n]+\)|`[^`\n]+`|\*\*\*[^*\n]+?\*\*\*|\*\*[^*\n]+?\*\*|__[^_\n]+?__|~~[^~\n]+?~~|\*[^*\n]+?\*)"""
)
private val MARKDOWN_LINK_REGEX = Regex("""^\[([^\]\n]+)\]\((https?://[^\s)\n]+)\)$""")

internal fun parseInlineMarkdown(
    text: String,
    accentColor: Color = ChampagneGold,
    codeBg: Color = Color(0xFF14141F),
    onOpenUrl: ((String) -> Unit)? = null
): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    for (match in INLINE_TOKEN_REGEX.findAll(text)) {
        if (match.range.first > cursor) {
            append(text.substring(cursor, match.range.first))
        }
        val token = match.value
        val linkMatch = MARKDOWN_LINK_REGEX.matchEntire(token)
        when {
            linkMatch != null -> {
                val label = linkMatch.groupValues[1]
                val url = linkMatch.groupValues[2]
                val listener = onOpenUrl?.let { handler ->
                    LinkInteractionListener { handler(url) }
                }
                withLink(
                    LinkAnnotation.Url(
                        url = url,
                        styles = TextLinkStyles(
                            style = SpanStyle(
                                color = accentColor,
                                textDecoration = TextDecoration.Underline,
                                fontWeight = FontWeight.Medium
                            )
                        ),
                        linkInteractionListener = listener
                    )
                ) {
                    append(label)
                }
            }
            token.startsWith("`") && token.endsWith("`") && token.length >= 2 -> {
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = codeBg,
                        color = accentColor,
                        fontSize = 13.sp
                    )
                ) {
                    append(" ${token.substring(1, token.length - 1)} ")
                }
            }
            token.startsWith("***") && token.endsWith("***") && token.length >= 6 -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)) {
                    append(token.substring(3, token.length - 3))
                }
            }
            (token.startsWith("**") && token.endsWith("**") && token.length >= 4) ||
                (token.startsWith("__") && token.endsWith("__") && token.length >= 4) -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(token.substring(2, token.length - 2))
                }
            }
            token.startsWith("~~") && token.endsWith("~~") && token.length >= 4 -> {
                withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough, color = RubyAccent.copy(alpha = 0.7f))) {
                    append(token.substring(2, token.length - 2))
                }
            }
            token.startsWith("*") && token.endsWith("*") && token.length >= 2 -> {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(token.substring(1, token.length - 1))
                }
            }
            else -> append(token)
        }
        cursor = match.range.last + 1
    }
    if (cursor < text.length) {
        append(text.substring(cursor))
    }
}
