package to.eyed.inferno.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.AnnotatedString
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swmansion.enriched.markdown.compose.CodeBlockLayout
import com.swmansion.enriched.markdown.compose.EnrichedMarkdownText
import com.swmansion.enriched.markdown.compose.markdownStyle
import com.swmansion.enriched.markdown.math.LatexMathPlugin
import com.swmansion.enriched.markdown.math.compose.inlineMath
import com.swmansion.enriched.markdown.math.compose.math
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownCodeBlock
import com.mikepenz.markdown.compose.elements.MarkdownCodeFence
import com.mikepenz.markdown.compose.elements.MarkdownTable
import com.mikepenz.markdown.compose.elements.MarkdownTableHeader
import com.mikepenz.markdown.compose.elements.MarkdownTableRow
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.markdownDimens
import com.mikepenz.markdown.model.rememberMarkdownState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import to.eyed.inferno.data.LocalToolCommandParser
import to.eyed.inferno.ui.S
import to.eyed.inferno.ui.components.CopyIconButton
import to.eyed.inferno.ui.components.Hairline
import to.eyed.inferno.ui.components.copyToClipboard
import to.eyed.inferno.ui.theme.Ink
import to.eyed.inferno.ui.theme.MonoBody
import to.eyed.inferno.ui.theme.Radii
import to.eyed.inferno.ui.theme.RowMeta
import to.eyed.inferno.ui.theme.Typography
import to.eyed.inferno.ui.theme.whiteA

/**
 * Render every incoming chunk as real Markdown immediately. Streaming uses Compose's text-motion
 * interpolation, not a timer-driven reveal cursor; new tokens never wait behind an animation queue.
 */
@Composable
fun MarkdownBody(
    text: String,
    modifier: Modifier = Modifier,
    streaming: Boolean = false,
    selectable: Boolean = true,
    textColor: androidx.compose.ui.graphics.Color = Ink.I100,
    fillWidth: Boolean = true,
) {
    val visibleText = remember(text) { LocalToolCommandParser.visibleText(text) }
    val chunks = remember(visibleText) { MarkdownChunker.chunks(visibleText) }
    val content: @Composable () -> Unit = {
        Column(
            if (fillWidth) modifier.fillMaxWidth() else modifier,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            chunks.forEachIndexed { index, chunk ->
                key(index) {
                    MarkdownChunkView(
                        chunk = chunk,
                        textColor = textColor,
                        selectable = selectable,
                        fillWidth = fillWidth,
                        streaming = streaming,
                    )
                }
            }
        }
    }
    if (selectable) SelectionContainer { content() } else content()
}

@Composable
private fun MarkdownChunkView(
    chunk: String,
    textColor: androidx.compose.ui.graphics.Color,
    selectable: Boolean,
    fillWidth: Boolean,
    streaming: Boolean,
) {
    if (SafeMathMarkdown.containsMath(chunk)) {
        val uriHandler = LocalUriHandler.current
        val style = remember(textColor) {
            markdownStyle {
                paragraph {
                    color = textColor
                    fontSize = 16.sp
                    lineHeight = 23.sp
                    marginBottom = 8.dp
                }
                strong { color = textColor }
                emphasis { color = textColor }
                list { color = textColor }
                h1 { color = Ink.White; fontSize = 22.sp; lineHeight = 28.sp }
                h2 { color = Ink.White; fontSize = 19.sp; lineHeight = 25.sp }
                h3 { color = Ink.White; fontSize = 16.sp; lineHeight = 22.sp }
                h4 { color = Ink.I100; fontSize = 15.sp; lineHeight = 21.sp }
                h5 { color = Ink.White; fontWeight = FontWeight.SemiBold }
                h6 { color = Ink.I300; fontWeight = FontWeight.Medium }
                code {
                    fontFamily = MonoBody.fontFamily
                    fontSize = 13.sp
                    color = Ink.White
                    backgroundColor = whiteA(0.10f)
                }
                codeBlock {
                    fontFamily = MonoBody.fontFamily
                    fontSize = 13.sp
                    lineHeight = 19.sp
                    color = Ink.I100
                    backgroundColor = Ink.I900
                    borderColor = whiteA(0.08f)
                    cornerRadius = Radii.row
                    padding = 14.dp
                }
                link { color = Ink.White }
                math {
                    fontSize = 20.sp
                    color = textColor
                    backgroundColor = Ink.I900
                    padding = 12.dp
                    marginBottom = 8.dp
                    alignment = Alignment.Start
                }
                inlineMath { color = textColor }
            }
        }
        LatexMathPlugin {
            EnrichedMarkdownText(
                markdown = SafeMathMarkdown.normalize(chunk),
                modifier = if (fillWidth) Modifier.fillMaxWidth() else Modifier,
                style = style,
                selectable = selectable,
                codeBlockLayout = CodeBlockLayout.Wrapping,
                onLinkClick = { uri -> runCatching { uriHandler.openUri(uri) } },
            )
        }
    } else {
        val body = Typography.bodyLarge.copy(
            color = textColor,
            textDirection = TextDirection.Content,
            textMotion = if (streaming) TextMotion.Animated else TextMotion.Static,
        )
        // Immediate first parse + retained prior tree avoid placeholder flashes as the tail grows.
        val state = rememberMarkdownState(content = chunk, retainState = true, immediate = true)
        Markdown(
            markdownState = state,
            colors = markdownColor(
                text = textColor,
                codeBackground = Ink.I900,
                inlineCodeBackground = whiteA(0.10f),
                dividerColor = whiteA(0.10f),
                tableBackground = whiteA(0.03f),
            ),
            typography = markdownTypography(
                h1 = body.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, color = Ink.White, letterSpacing = (-0.2).sp),
                h2 = body.copy(fontSize = 19.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold, color = Ink.White),
                h3 = body.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, color = Ink.White),
                h4 = body.copy(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold, color = Ink.I100),
                h5 = body.copy(fontWeight = FontWeight.SemiBold, color = Ink.White),
                h6 = body.copy(fontWeight = FontWeight.Medium, color = Ink.I300),
                text = body,
                code = MonoBody.copy(color = Ink.I100),
                inlineCode = MonoBody.copy(color = Ink.White, fontSize = 13.sp),
                quote = body.copy(color = Ink.I300),
                paragraph = body,
                ordered = body,
                bullet = body,
                list = body,
                textLink = TextLinkStyles(
                    style = SpanStyle(color = Ink.White, textDecoration = TextDecoration.Underline),
                    pressedStyle = SpanStyle(color = Ink.I300, textDecoration = TextDecoration.Underline),
                ),
                table = body.copy(fontSize = 14.sp, lineHeight = 20.sp),
            ),
            dimens = markdownDimens(codeBackgroundCornerSize = Radii.row, tableCellWidth = 110.dp, tableCellPadding = 12.dp),
            components = markdownComponents(
                codeFence = { model ->
                    MarkdownCodeFence(model.content, model.node) { code, language, _ -> MonoCodeBlock(code, language) }
                },
                codeBlock = { model ->
                    MarkdownCodeBlock(model.content, model.node) { code, language, _ -> MonoCodeBlock(code, language) }
                },
                table = { model ->
                    MarkdownTable(
                        model.content, model.node, model.typography.table,
                        headerBlock = { content, header, width, style -> MarkdownTableHeader(content, header, width, style, maxLines = 3) },
                        rowBlock = { content, row, width, style -> MarkdownTableRow(content, row, width, style, verticalAlignment = Alignment.Top, maxLines = Int.MAX_VALUE) },
                    )
                },
            ),
            modifier = if (fillWidth) Modifier.fillMaxWidth() else Modifier,
        )
    }
}

/** The code card keeps its previous syntax spans while only the unfinished line is re-highlighted. */
@Composable
fun MonoCodeBlock(
    code: String,
    language: String?,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(Radii.row)
    val context = LocalContext.current
    val trimmed = code.trimEnd('\n').take(32_000)
    var highlightedSource by remember { mutableStateOf("") }
    var highlighted by remember { mutableStateOf<AnnotatedString?>(null) }

    LaunchedEffect(trimmed, language) {
        val previousSource = highlightedSource
        val previousHighlight = highlighted
        val result = withContext(Dispatchers.Default) {
            highlightCodeIncremental(previousSource, previousHighlight, trimmed, language)
        }
        highlightedSource = trimmed
        highlighted = result
    }

    val displayed = remember(trimmed, highlightedSource, highlighted) {
        val current = highlighted
        when {
            current == null -> AnnotatedString(trimmed)
            current.text == trimmed -> current
            trimmed.startsWith(highlightedSource) -> AnnotatedString.Builder().apply {
                append(current)
                append(trimmed.substring(highlightedSource.length))
            }.toAnnotatedString()
            else -> AnnotatedString(trimmed)
        }
    }

    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Ink.I900)
            .border(1.dp, whiteA(0.08f), shape),
    ) {
        Row(
            Modifier.fillMaxWidth().background(whiteA(0.03f)).padding(start = 14.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                language?.trim()?.takeIf { it.isNotBlank() } ?: "text",
                style = RowMeta.copy(fontFamily = MonoBody.fontFamily),
                color = Ink.I500,
                modifier = Modifier.weight(1f),
            )
            CopyIconButton(S.copy, onCopy = { copyToClipboard(context, trimmed, "Code") }, size = 32.dp, iconSize = 15.dp, tint = Ink.I500, backgroundEnabled = false)
        }
        Hairline()
        Box(Modifier.horizontalScroll(rememberScrollState())) {
            Text(displayed, style = MonoBody.copy(color = CodePalette.Text), softWrap = false, modifier = Modifier.padding(14.dp))
        }
    }
}

/**
 * Splits markdown into independently renderable chunks on blank lines, keeping fenced code blocks
 * (even unterminated, mid-stream) and list runs intact so numbering and fences never break across
 * chunks. Pure Kotlin: covered by MarkdownChunkerTest.
 */
object MarkdownChunker {
    private val fenceStart = Regex("^(`{3,}|~{3,})(.*)$")

    fun chunks(source: String): List<String> {
        val chunks = mutableListOf<String>()
        val current = mutableListOf<String>()
        // CommonMark fences: the opener is 3+ of one char (a backtick opener's info string may not contain a
        // backtick, so "```ls``` lists files" is inline code); the closer is the same char, at least as long, alone.
        // A plain toggle would flip on such lines and glue the rest of the answer into one re-parsed chunk.
        var fenceChar: Char? = null
        var fenceLen = 0
        val lines = source.split("\n")

        fun flush() {
            if (current.any { it.isNotBlank() }) chunks.add(current.joinToString("\n"))
            current.clear()
        }

        for ((index, line) in lines.withIndex()) {
            val trimmed = line.trim()
            val m = fenceStart.matchEntire(trimmed)
            if (m != null) {
                val marker = m.groupValues[1]
                val rest = m.groupValues[2]
                if (fenceChar == null) {
                    if (marker[0] == '~' || !rest.contains('`')) {
                        fenceChar = marker[0]; fenceLen = marker.length
                        current.add(line); continue
                    }
                } else if (marker[0] == fenceChar && marker.length >= fenceLen && rest.isBlank()) {
                    fenceChar = null; fenceLen = 0
                    current.add(line); continue
                }
                // Otherwise ordinary content (inside a fence, or inline code text).
            }
            if (trimmed.isEmpty() && fenceChar == null) {
                val prevIsItem = current.lastOrNull()?.let(::isListItem) ?: false
                var nextIsItem = false
                for (i in (index + 1) until lines.size) {
                    if (lines[i].isNotBlank()) { nextIsItem = isListItem(lines[i]); break }
                }
                if (prevIsItem && nextIsItem) current.add(line) else flush()
                continue
            }
            current.add(line)
        }
        flush()
        return chunks
    }

    /** "- ", "* ", "+ ", "> " or "1." / "12)" style markers (an indented continuation counts too). */
    fun isListItem(line: String): Boolean {
        val t = line.trim()
        if (t.startsWith("- ") || t.startsWith("* ") || t.startsWith("+ ") || t.startsWith("> ")) return true
        if (line.startsWith("  ") && t.isNotEmpty()) return true
        val mark = t.indexOfFirst { it == '.' || it == ')' }
        if (mark <= 0 || mark > 3) return false
        return t.substring(0, mark).all { it.isDigit() }
    }
}
