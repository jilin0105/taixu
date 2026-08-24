package top.wkbin.taixu.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import top.wkbin.taixu.ui.components.RuntimeIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import top.wkbin.taixu.feature.chat.R
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import top.wkbin.taixu.ui.components.RuntimeCircularProgressIndicator as CircularProgressIndicator
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.SyntaxHighlighter

/**
 * 轻量 markdown 渲染组件：支持标题、段落、粗体/斜体、行内代码、
 * 代码块（带语言徽章、一键复制与语法高亮）、无序/有序列表、引用、链接、
 * 远程图片与表格。远程图片由 Coil 加载，网页链接由 Compose LinkAnnotation 打开。
 */
@Composable
fun MarkdownText(markdown: String, modifier: Modifier = Modifier) {
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }
    SelectionContainer(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            blocks.forEach { block ->
                when (block) {
                    is MdParagraph -> InlineText(block.text, MaterialTheme.typography.bodyMedium)
                    is MdHeading -> InlineText(
                        block.text,
                        when (block.level) {
                            1 -> MaterialTheme.typography.headlineSmall
                            2 -> MaterialTheme.typography.titleLarge
                            3 -> MaterialTheme.typography.titleMedium
                            else -> MaterialTheme.typography.titleSmall
                        },
                        bold = true,
                    )
                    is MdCodeBlock -> CodeBlock(block)
                    is MdList -> ListBlock(block)
                    is MdQuote -> QuoteBlock(block)
                    is MdTable -> TableBlock(block)
                    is MdRemoteMedia -> RemoteMediaBlock(block)
                    is MdHr -> HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

// ---------- 解析 ----------

private sealed interface MdBlock

private data class MdParagraph(val text: String) : MdBlock
private data class MdHeading(val level: Int, val text: String) : MdBlock
private data class MdCodeBlock(val language: String, val code: String) : MdBlock
private data class MdList(val ordered: Boolean, val items: List<String>) : MdBlock
private data class MdQuote(val lines: List<String>) : MdBlock
private data class MdTable(val headers: List<String>, val rows: List<List<String>>) : MdBlock
private data class MdRemoteMedia(val url: String, val description: String) : MdBlock
private object MdHr : MdBlock

private val headingRegex = Regex("^\\s*(#{1,6})\\s+(.+?)\\s*#*\\s*$")
private val orderedListRegex = Regex("^\\s*\\d+\\.\\s+(.*)$")
private val unorderedListRegex = Regex("^\\s*[-*+]\\s+(.*)$")
private val hrRegex = Regex("^\\s*(?:-{3,}|\\*{3,}|_{3,})\\s*$")
private val markdownImageRegex = Regex(
    pattern = "^\\s*!\\[([^]\\n]*)]\\((https?://[^)\\s]+)\\)\\s*$",
    option = RegexOption.IGNORE_CASE,
)
private val standaloneWebUrlRegex = Regex("^https?://\\S+$", RegexOption.IGNORE_CASE)
private val inlineMarkdownLinkRegex = Regex("\\[([^]\\n]+)]\\((https?://[^)\\s]+)\\)", RegexOption.IGNORE_CASE)
private val inlineWebUrlRegex = Regex("https?://[^\\s<>\\[\\]{}\"']+", RegexOption.IGNORE_CASE)

private fun parseMarkdownBlocks(md: String): List<MdBlock> {
    val lines = md.replace("\r\n", "\n").replace("\r", "\n").split("\n")
    val blocks = mutableListOf<MdBlock>()
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trimStart()
        when {
            trimmed.startsWith("```") -> {
                val lang = trimmed.removePrefix("```").trim()
                val code = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                    code.add(lines[i]); i++
                }
                i++ // skip closing fence
                blocks.add(MdCodeBlock(lang, code.joinToString("\n").trim('\n')))
            }
            markdownImageRegex.matches(line) -> {
                val match = markdownImageRegex.matchEntire(line)!!
                blocks.add(
                    MdRemoteMedia(
                        url = match.groupValues[2],
                        description = match.groupValues[1],
                    ),
                )
                i++
            }
            standaloneWebUrlRegex.matches(line.trim()) -> {
                val url = trimUrlPunctuation(line.trim())
                blocks.add(MdRemoteMedia(url = url, description = ""))
                i++
            }
            headingRegex.matches(line.trim()) -> {
                val m = headingRegex.find(line.trim())!!
                blocks.add(MdHeading(m.groupValues[1].length, m.groupValues[2]))
                i++
            }
            isTableStart(lines, i) -> {
                val headers = splitRow(lines[i])
                i += 2
                val rows = mutableListOf<List<String>>()
                while (i < lines.size) {
                    val t = lines[i].trimStart()
                    if (lines[i].isBlank() || !t.startsWith("|")) break
                    rows.add(splitRow(lines[i]))
                    i++
                }
                blocks.add(MdTable(headers, rows))
            }
            hrRegex.matches(line) -> { blocks.add(MdHr); i++ }
            trimmed.startsWith(">") -> {
                val quoted = mutableListOf<String>()
                while (i < lines.size && lines[i].trimStart().startsWith(">")) {
                    quoted.add(lines[i].trimStart().removePrefix(">").removePrefix(" "))
                    i++
                }
                blocks.add(MdQuote(quoted))
            }
            orderedListRegex.matches(line) || unorderedListRegex.matches(line) -> {
                val ordered = orderedListRegex.matches(lines[i])
                val items = mutableListOf<String>()
                while (i < lines.size && (orderedListRegex.matches(lines[i]) || unorderedListRegex.matches(lines[i]))) {
                    val l = lines[i]
                    val m = orderedListRegex.find(l) ?: unorderedListRegex.find(l)
                    items.add(m!!.groupValues[1])
                    i++
                }
                blocks.add(MdList(ordered, items))
            }
            line.isBlank() -> i++
            else -> {
                val para = mutableListOf<String>()
                while (i < lines.size) {
                    val l = lines[i]
                    val t = l.trimStart()
                    if (l.isBlank() ||
                        headingRegex.matches(l.trim()) ||
                        hrRegex.matches(l) ||
                        isTableStart(lines, i) ||
                        t.startsWith("```") ||
                        t.startsWith(">") ||
                        markdownImageRegex.matches(l) ||
                        standaloneWebUrlRegex.matches(l.trim()) ||
                        orderedListRegex.matches(l) ||
                        unorderedListRegex.matches(l)
                    ) break
                    para.add(l); i++
                }
                blocks.add(MdParagraph(para.joinToString(" ").trim()))
            }
        }
    }
    return blocks
}

// ---------- 行内渲染 ----------

@Composable
private fun InlineText(
    text: String,
    style: TextStyle,
    bold: Boolean = false,
    color: Color = Color.Unspecified,
    modifier: Modifier = Modifier,
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated = remember(text, bold, linkColor) { buildInline(text, bold, linkColor) }
    Text(annotated, style = style, color = color, modifier = modifier)
}

private fun buildInline(
    text: String,
    forceBold: Boolean,
    linkColor: Color,
): AnnotatedString = buildAnnotatedString {
    if (forceBold) pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
    val linkStyles = TextLinkStyles(
        style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
        hoveredStyle = SpanStyle(color = linkColor.copy(alpha = 0.8f)),
        pressedStyle = SpanStyle(color = linkColor.copy(alpha = 0.65f)),
    )
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("~~", i) -> {
                val end = text.indexOf("~~", i + 2)
                if (end != -1) {
                    pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                    append(text, i + 2, end)
                    pop()
                    i = end + 2
                } else { append(text[i]); i++ }
            }
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end != -1) {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    append(text, i + 2, end)
                    pop()
                    i = end + 2
                } else { append(text[i]); i++ }
            }
            text[i] == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end != -1) {
                    pushStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                    append(text, i + 1, end)
                    pop()
                    i = end + 1
                } else { append(text[i]); i++ }
            }
            text.startsWith("[", i) -> {
                val link = inlineMarkdownLinkRegex.find(text, i)
                if (link != null && link.range.first == i) {
                    withLink(LinkAnnotation.Url(link.groupValues[2], linkStyles)) {
                        append(link.groupValues[1])
                    }
                    i = link.range.last + 1
                } else { append(text[i]); i++ }
            }
            text.regionMatches(i, "https://", 0, 8, ignoreCase = true) ||
                text.regionMatches(i, "http://", 0, 7, ignoreCase = true) -> {
                val match = inlineWebUrlRegex.find(text, i)
                if (match != null && match.range.first == i) {
                    val url = trimUrlPunctuation(match.value)
                    withLink(LinkAnnotation.Url(url, linkStyles)) { append(url) }
                    i += url.length
                } else { append(text[i]); i++ }
            }
            text[i] == '*' -> {
                val end = text.indexOf('*', i + 1)
                if (end != -1 && text.getOrNull(end + 1) != '*') {
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    append(text, i + 1, end)
                    pop()
                    i = end + 1
                } else { append(text[i]); i++ }
            }
            else -> { append(text[i]); i++ }
        }
    }
    if (forceBold) pop()
}

private fun trimUrlPunctuation(value: String): String {
    var result = value.trimEnd('.', ',', ';', ':', '!', '?')
    while (result.endsWith(')') && result.count { it == ')' } > result.count { it == '(' }) {
        result = result.dropLast(1)
    }
    return result
}

@Composable
private fun RemoteMediaBlock(block: MdRemoteMedia) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val openSource = {
        runCatching { uriHandler.openUri(block.url) }
            .onFailure { Toast.makeText(context, context.getString(R.string.chat_open_link_failed), Toast.LENGTH_SHORT).show() }
        Unit
    }
    val shape = RoundedCornerShape(12.dp)

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = shape,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            SubcomposeAsyncImage(
                model = remember(block.url) {
                    ImageRequest.Builder(context)
                        .data(block.url)
                        .crossfade(true)
                        .build()
                },
                contentDescription = block.description.ifBlank { stringResource(R.string.chat_web_content) },
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .clip(shape)
                    .clickable(onClick = openSource),
                loading = {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                    }
                },
                error = {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        RuntimeIcon(
                            RuntimeIconName.Image,
                            Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.chat_image_load_failed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = openSource)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RuntimeIcon(
                    RuntimeIconName.OpenInNew,
                    Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = block.url,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun CodeBlock(block: MdCodeBlock) {
    val context = LocalContext.current
    val copyCode = {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.chat_code_clipboard_label), block.code))
        Toast.makeText(context, context.getString(R.string.chat_code_copied), Toast.LENGTH_SHORT).show()
    }

    val highlightedText = remember(block.code, block.language) {
        SyntaxHighlighter.highlight(block.code, block.language)
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            // 代码块顶部信息栏：语言徽章 + 复制按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = if (block.language.isNotBlank()) block.language.uppercase() else "CODE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                )
                Surface(
                    color = Color.Transparent,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.clickable(onClick = copyCode),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    ) {
                        RuntimeIcon(
                            RuntimeIconName.Copy,
                            Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(R.string.chat_copy),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // 代码高亮显示区
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp),
            ) {
                Text(
                    text = highlightedText,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                    ),
                )
            }
        }
    }
}

private fun splitRow(line: String): List<String> =
    line.trim().trim('|').split("|").map { it.trim() }

private fun isTableSeparator(line: String): Boolean {
    val t = line.trim()
    if (t.isEmpty()) return false
    val cells = t.trim('|').split("|")
    if (cells.size < 2) return false
    return cells.all { c ->
        val s = c.trim()
        s.isEmpty() || s.all { ch -> ch == '-' || ch == ':' || ch.isWhitespace() }
    }
}

private fun isTableStart(lines: List<String>, i: Int): Boolean =
    i + 1 < lines.size && lines[i].trimStart().startsWith("|") && isTableSeparator(lines[i + 1])

@Composable
private fun TableBlock(table: MdTable) {
    val colCount = (listOf(table.headers) + table.rows).maxOf { it.size }
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            (0 until colCount).forEach { c ->
                InlineText(
                    table.headers.getOrNull(c) ?: "",
                    MaterialTheme.typography.bodySmall,
                    bold = true,
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        table.rows.forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                (0 until colCount).forEach { c ->
                    InlineText(
                        row.getOrNull(c) ?: "",
                        MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ListBlock(block: MdList) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        block.items.forEachIndexed { index, item ->
            Row(Modifier.fillMaxWidth()) {
                Text(
                    if (block.ordered) "${index + 1}." else "\u2022",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp),
                )
                InlineText(item, MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 0.dp))
            }
        }
    }
}

@Composable
private fun QuoteBlock(block: MdQuote) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        block.lines.forEach { line ->
            InlineText(line, MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
