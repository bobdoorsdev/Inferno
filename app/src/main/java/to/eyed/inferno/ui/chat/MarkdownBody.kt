package to.eyed.inferno.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import to.eyed.inferno.ui.theme.pressScale
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import to.eyed.inferno.ui.components.CopyGlyph
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swmansion.enriched.markdown.compose.CodeBlockLayout
import com.swmansion.enriched.markdown.compose.EnrichedMarkdownText
import com.swmansion.enriched.markdown.compose.markdownStyle
import com.swmansion.enriched.markdown.math.LatexMathPlugin
import com.swmansion.enriched.markdown.math.compose.inlineMath
import com.swmansion.enriched.markdown.math.compose.math
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import java.text.BreakIterator
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
import to.eyed.inferno.ui.S
import to.eyed.inferno.ui.components.Hairline
import to.eyed.inferno.ui.components.copyToClipboard
import to.eyed.inferno.ui.theme.Ink
import to.eyed.inferno.ui.theme.MonoBody
import to.eyed.inferno.ui.theme.Radii
import to.eyed.inferno.ui.theme.RowMeta
import to.eyed.inferno.ui.theme.Typography
import to.eyed.inferno.ui.theme.rememberHaptics
import to.eyed.inferno.ui.theme.whiteA

/**
 * Assistant markdown in the monochrome language: white headings, I100 body, mono code on I900 cards,
 * white underlined links (never blue). Streaming strategy: the source is split into stable block-level
 * chunks so only the growing last chunk re-parses while tokens arrive; `key(index)` keeps every earlier
 * chunk's parse alive. `TextDirection.Content` keeps RTL answers readable inside the LTR chrome.
 */
@Composable
fun MarkdownBody(
    text: String,
    modifier: Modifier = Modifier,
    streaming: Boolean = false,
    selectable: Boolean = true,
    textColor: Color = Ink.I100,
    fillWidth: Boolean = true,
) {
    val chunks = remember(text) { MarkdownChunker.chunks(text) }
    val content: @Composable () -> Unit = {
        Column(if (fillWidth) modifier.fillMaxWidth() else modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            chunks.forEachIndexed { index, chunk ->
                key(index) {
                    StreamingMarkdownChunk(
                        chunk = chunk,
                        isTail = index == chunks.lastIndex,
                        streamActive = streaming,
                        textColor = textColor,
                        selectable = selectable,
                        fillWidth = fillWidth,
                    )
                }
            }
        }
    }
    if (selectable) SelectionContainer { content() } else content()
}

/**
 * Keeps each paragraph's reveal animation alive when a later paragraph arrives. A closed block
 * becomes rich Markdown only after its own reveal has caught up, never merely because it stopped
 * being the current streaming tail.
 */
@Composable
private fun StreamingMarkdownChunk(
    chunk: String,
    isTail: Boolean,
    streamActive: Boolean,
    textColor: Color,
    selectable: Boolean,
    fillWidth: Boolean,
) {
    var revealed by remember { mutableStateOf(!streamActive) }
    if (revealed) {
        MarkdownChunkView(chunk, textColor = textColor, selectable = selectable, fillWidth = fillWidth)
    } else {
        StreamingMarkdownTail(
            chunk = chunk,
            completeWhenCaughtUp = !streamActive || !isTail,
            onRevealComplete = { revealed = true },
        )
    }
}

/**
 * The live tail is revealed at a fixed cadence, independently of native token arrival and markdown
 * parsing. A fractional final grapheme creates the soft scan boundary; no visible scan-line is drawn.
 */
@Composable
private fun StreamingRevealText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    completeWhenCaughtUp: Boolean = false,
    onRevealComplete: () -> Unit = {},
) {
    val boundaries = remember(text) {
        val iterator = BreakIterator.getCharacterInstance()
        iterator.setText(text)
        buildList {
            var boundary = iterator.first()
            add(boundary)
            boundary = iterator.next()
            while (boundary != BreakIterator.DONE) {
                add(boundary)
                boundary = iterator.next()
            }
        }.ifEmpty { listOf(0) }
    }
    val targetCount = boundaries.size - 1
    val latestTarget by rememberUpdatedState(targetCount)
    val latestCompleteWhenCaughtUp by rememberUpdatedState(completeWhenCaughtUp)
    val latestOnRevealComplete by rememberUpdatedState(onRevealComplete)
    var progress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        while (true) {
            if (progress < latestTarget) {
                progress = (progress + 0.5f).coerceAtMost(latestTarget.toFloat())
                delay(8)
            } else {
                if (latestCompleteWhenCaughtUp) {
                    latestOnRevealComplete()
                    return@LaunchedEffect
                }
                snapshotFlow { latestTarget to latestCompleteWhenCaughtUp }.first { (target, finish) ->
                    target.toFloat() > progress || finish
                }
            }
        }
    }

    val whole = progress.toInt().coerceIn(0, targetCount)
    val fraction = (progress - whole).coerceIn(0f, 1f)
    val shownEnd = boundaries.getOrElse(whole) { text.length }
    val nextEnd = boundaries.getOrElse(whole + 1) { text.length }
    val revealed = remember(text, shownEnd, nextEnd, fraction, color) {
        buildAnnotatedString {
            if (shownEnd > 0) append(text.substring(0, shownEnd))
            if (shownEnd < text.length) {
                withStyle(SpanStyle(color = color.copy(alpha = fraction))) {
                    append(text.substring(shownEnd, nextEnd))
                }
                if (nextEnd < text.length) {
                    withStyle(SpanStyle(color = color.copy(alpha = 0f))) {
                        append(text.substring(nextEnd))
                    }
                }
            }
        }
    }

    Text(revealed, modifier = modifier, style = style.copy(color = color), color = color)
}

/** Lightweight live tail: code fences retain their card; prose avoids reparsing incomplete Markdown. */
@Composable
private fun StreamingMarkdownTail(
    chunk: String,
    completeWhenCaughtUp: Boolean,
    onRevealComplete: () -> Unit,
) {
    val first = chunk.lineSequence().firstOrNull()?.trim().orEmpty()
    val opener = Regex("""^(\x60{3,}|~{3,})([A-Za-z0-9_+.-]*)$""").matchEntire(first)
    if (opener != null) {
        val marker = opener.groupValues[1]
        val closeIndex = chunk.lines().withIndex().firstOrNull { (index, line) ->
            index > 0 && line.trim().let { it.length >= marker.length && it.all { c -> c == marker[0] } }
        }?.index ?: -1
        val codeLines = chunk.lines().drop(1).let { lines -> if (closeIndex > 0) lines.take(closeIndex - 1) else lines }
        MonoCodeBlock(
            codeLines.joinToString("\n"),
            opener.groupValues[2],
            streaming = true,
            completeWhenCaughtUp = completeWhenCaughtUp,
            onRevealComplete = onRevealComplete,
        )
    } else {
        StreamingRevealText(
            text = chunk,
            style = Typography.bodyLarge.copy(textDirection = TextDirection.Content),
            color = Ink.I100,
            completeWhenCaughtUp = completeWhenCaughtUp,
            onRevealComplete = onRevealComplete,
        )
    }
}

@Composable
private fun MarkdownChunkView(chunk: String, textColor: Color, selectable: Boolean, fillWidth: Boolean) {
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
        val body = Typography.bodyLarge.copy(color = textColor, textDirection = TextDirection.Content)
        // Parse synchronously and retain the last good tree: no blank placeholder flash while streaming.
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

/** Code card: language bar with a copy action, hairline seam, horizontally scrolling mono code. */
@Composable
fun MonoCodeBlock(
    code: String,
    language: String?,
    modifier: Modifier = Modifier,
    streaming: Boolean = false,
    completeWhenCaughtUp: Boolean = false,
    onRevealComplete: () -> Unit = {},
) {
    val shape = RoundedCornerShape(Radii.row)
    val context = LocalContext.current
    val trimmed = code.trimEnd('\n')
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Ink.I900)
            .border(1.dp, whiteA(0.08f), shape),
    ) {
        Row(
            Modifier.fillMaxWidth().background(whiteA(0.03f)).padding(start = 14.dp, end = 6.dp, top = 3.dp, bottom = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                language?.trim()?.takeIf { it.isNotBlank() } ?: "text",
                style = RowMeta.copy(fontFamily = MonoBody.fontFamily),
                color = Ink.I500,
                modifier = Modifier.weight(1f),
            )
            CopyChip(onCopy = { copyToClipboard(context, trimmed, "Code") })
        }
        Hairline()
        Box(Modifier.horizontalScroll(rememberScrollState())) {
            if (streaming) {
                StreamingRevealText(
                    text = trimmed,
                    style = MonoBody.copy(color = Ink.I100),
                    color = Ink.I100,
                    modifier = Modifier.padding(14.dp),
                    completeWhenCaughtUp = completeWhenCaughtUp,
                    onRevealComplete = onRevealComplete,
                )
            } else {
                Text(highlightCode(trimmed, language), style = MonoBody.copy(color = CodePalette.Text), softWrap = false, modifier = Modifier.padding(14.dp))
            }
        }
    }
}

/** "Copy" -> "Copied" for 1.6 s; the system clipboard overlay on API 33+ is the only other feedback. */
@Composable
private fun CopyChip(onCopy: () -> Unit) {
    var copied by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    LaunchedEffect(copied) { if (copied) { delay(1_600); copied = false } }
    Row(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(whiteA(0.06f))
            .pressScale(interaction, 0.96f)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
            ) { haptics.tap(); onCopy(); copied = true }
            .padding(horizontal = 11.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        CopyGlyph(checked = copied, size = 16.dp, tint = if (copied) Ink.White else Ink.I500)
        Text(if (copied) S.copied else S.copy, style = RowMeta, color = if (copied) Ink.White else Ink.I500)
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
