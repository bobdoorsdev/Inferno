package to.eyed.inferno.ui.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.composables.icons.lucide.Braces
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Play
import kotlinx.coroutines.delay
import me.rerere.bettertext.latex.LatexBlock
import me.rerere.bettertext.latex.LatexInline
import me.rerere.bettertext.markdown.LocalMarkdownTextStyle
import me.rerere.bettertext.markdown.Markdown
import me.rerere.bettertext.markdown.MarkdownBlock
import me.rerere.bettertext.markdown.MarkdownComponents
import me.rerere.bettertext.markdown.MarkdownInlineContent
import me.rerere.bettertext.markdown.MarkdownTheme
import me.rerere.bettertext.text.BetterSelectionContainer
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
 * Compose-native GFM Markdown with the library's LaTeX renderer. The library parses incrementally while streaming,
 * retains stable block nodes, leaves incomplete delimiters as literal text until they close, and provides selection.
 */
@Composable
fun MarkdownBody(text: String, modifier: Modifier = Modifier, streaming: Boolean = false, animateStreaming: Boolean = true) {
    val context = LocalContext.current
    val latestText by rememberUpdatedState(text)
    val latestStreaming by rememberUpdatedState(streaming && animateStreaming)
    var revealedText by remember { mutableStateOf(if (streaming && animateStreaming) "" else text) }
    LaunchedEffect(text, streaming, animateStreaming) {
        if (!streaming || !animateStreaming) revealedText = text
    }
    LaunchedEffect(streaming, animateStreaming) {
        if (streaming && animateStreaming) {
            while (latestStreaming) {
                val target = latestText
                if (!target.startsWith(revealedText)) revealedText = ""
                val nextLength = nextTypingRevealLength(revealedText.length, target.length)
                if (nextLength > revealedText.length) revealedText = target.substring(0, nextLength)
                delay(12)
            }
        } else {
            revealedText = latestText
        }
    }
    val source = remember(revealedText, streaming) { normalizeMarkdownForRenderer(revealedText, streaming) }
    val theme = remember {
        val body = Typography.bodyLarge.copy(color = Ink.I100, textDirection = TextDirection.Content)
        MarkdownTheme(
            headings = listOf(
                body.copy(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Ink.White),
                body.copy(fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = Ink.White),
                body.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Ink.White),
                body.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ink.I100),
                body.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink.I100),
                body.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Ink.I300),
            ),
            link = SpanStyle(color = Ink.White, textDecoration = TextDecoration.Underline),
            inlineCode = SpanStyle(color = Ink.White, background = whiteA(0.10f)),
            markerColor = Ink.I300,
            quoteColor = Ink.I500,
            dividerColor = whiteA(0.10f),
            blockSpacing = 8.dp,
        )
    }
    val components = remember(context) {
        MarkdownComponents(
            codeBlock = { block -> MonoCodeBlock(block.code, block.language, streaming = !block.closed) },
            inlineMath = { formula -> LatexInline.cached(formula, context) },
            mathBlock = { formula ->
                val style = LocalMarkdownTextStyle.current
                LatexBlock(formula, fontSize = style.fontSize, color = style.color)
            },
            table = { table -> BetterMarkdownTable(table) },
            image = { image, _ -> MarkdownImageCard(image.alt.orEmpty(), image.url, image.title.orEmpty()) },
        )
    }
    val body = Typography.bodyLarge.copy(
        color = Ink.I100,
        textDirection = TextDirection.Content,
        textMotion = if (streaming && animateStreaming) androidx.compose.ui.text.style.TextMotion.Animated else androidx.compose.ui.text.style.TextMotion.Static,
    )
    BetterSelectionContainer(modifier = modifier.fillMaxWidth()) {
        Markdown(
            content = source,
            modifier = Modifier.fillMaxWidth(),
            style = body,
            theme = theme,
            components = components,
            streaming = streaming,
        )
    }
}

/** Adaptive reveal cadence: smooth at normal output speed, catch up quickly after slow frames. */
internal fun nextTypingRevealLength(currentLength: Int, targetLength: Int): Int {
    val remaining = (targetLength - currentLength).coerceAtLeast(0)
    val step = when {
        remaining > 96 -> 8
        remaining > 32 -> 4
        remaining > 12 -> 2
        else -> 1
    }
    return (currentLength + step).coerceAtMost(targetLength)
}

/** Repair common heading syntax and escape currency tokens without touching fenced/inline code. */
internal fun normalizeMarkdownForRenderer(source: String, streaming: Boolean = false): String {
    val output = StringBuilder(source.length + 16)
    var fenceChar: Char? = null
    var fenceLength = 0
    for (line in source.replace("\r\n", "\n").replace('\r', '\n').split('\n')) {
        val fence = Regex("""^ {0,3}(\x60{3,}|~{3,})(.*)$""").matchEntire(line)
        if (fence != null) {
            val marker = fence.groupValues[1]
            val rest = fence.groupValues[2]
            if (fenceChar == null && (marker[0] == '~' || !rest.contains('\u0060'))) {
                fenceChar = marker[0]
                fenceLength = marker.length
            } else if (fenceChar == marker[0] && marker.length >= fenceLength && rest.isBlank()) {
                fenceChar = null
                fenceLength = 0
            }
            output.append(line).append('\n')
            continue
        }
        if (fenceChar != null) {
            output.append(line).append('\n')
            continue
        }
        val heading = Regex("""^( {0,3})(#{1,6})(?=\S)(.*)$""").matchEntire(line)
        val fixedHeading = if (heading != null) heading.groupValues[1] + heading.groupValues[2] + " " + heading.groupValues[3] else line
        output.append(escapeCurrencyOutsideCode(fixedHeading)).append('\n')
    }
    return output.toString().trimEnd('\n')
}

private fun escapeCurrencyOutsideCode(line: String): String {
    fun amountEnd(start: Int): Int? {
        if (start >= line.length || line[start] != '$' || start + 1 >= line.length || !line[start + 1].isDigit()) return null
        var end = start + 1
        while (end < line.length && (line[end].isDigit() || line[end] == ',' || line[end] == '.')) end++
        val boundary = end == line.length || line[end].isWhitespace() || line[end] in ",;:!?)]}"
        return end.takeIf { boundary }
    }

    fun nextUnescapedDollar(from: Int): Int? {
        var index = from
        while (index < line.length) {
            if (line[index] == '$' && (index == 0 || line[index - 1] != '\\')) return index
            index++
        }
        return null
    }

    val out = StringBuilder(line.length + 8)
    var i = 0
    var codeTicks = 0
    while (i < line.length) {
        if (line[i] == '\u0060') {
            var end = i + 1
            while (end < line.length && line[end] == '\u0060') end++
            val count = end - i
            if (codeTicks == 0) codeTicks = count else if (count == codeTicks) codeTicks = 0
            out.append(line, i, end)
            i = end
            continue
        }
        if (codeTicks == 0 && line[i] == '$' && (i == 0 || line[i - 1] != '\\')) {
            val end = amountEnd(i)
            if (end != null) {
                val nextDollar = nextUnescapedDollar(end)
                val nextIsAnotherAmount = nextDollar != null && amountEnd(nextDollar) != null
                // A paired closing delimiter means this may be genuine math, e.g. $5 + x$.
                // Two dollar tokens that each start an amount are currency, e.g. $5 and $10.
                val isCurrency = nextIsAnotherAmount || nextDollar == null
                if (isCurrency) {
                    out.append("\\$")
                    i++
                    continue
                }
            }
        }
        out.append(line[i])
        i++
    }
    return out.toString()
}
@Composable
private fun BetterMarkdownTable(table: MarkdownBlock.Table) {
    val rows = listOf(table.header) + table.rows
    Box(
        Modifier.fillMaxWidth().border(1.dp, whiteA(0.12f), RoundedCornerShape(Radii.row))
            .clip(RoundedCornerShape(Radii.row)).horizontalScroll(rememberScrollState()),
    ) {
        Column {
            rows.forEachIndexed { rowIndex, row ->
                Row(verticalAlignment = Alignment.Top) {
                    row.forEach { cell ->
                        Box(Modifier.widthIn(min = 116.dp, max = 176.dp).padding(horizontal = 10.dp, vertical = 9.dp)) {
                            MarkdownInlineContent(
                                cell.content,
                                style = TextStyle(fontWeight = if (rowIndex == 0) FontWeight.SemiBold else FontWeight.Normal)
                                    .merge(cell.style),
                            )
                        }
                    }
                }
                if (rowIndex != rows.lastIndex) Hairline()
            }
        }
    }
}

private val MarkdownImagePattern = Regex("""!\[([^\]]*)\]\(((?:https?://|file://)[^)\s]+)(?:\s+"([^"]*)")?\)""")

@Composable
private fun MarkdownImageCard(alt: String, url: String, fallbackUrl: String = "") {
    val uriHandler = LocalUriHandler.current
    var activeUrl by remember(url, fallbackUrl) { mutableStateOf(url) }
    var failed by remember(url, fallbackUrl) { mutableStateOf(false) }
    Column(Modifier.widthIn(max = 360.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        if (!failed) {
            AsyncImage(
                model = activeUrl,
                contentDescription = alt.ifBlank { "Search result image" },
                contentScale = ContentScale.Crop,
                onError = {
                    if (activeUrl == url && fallbackUrl.startsWith("https://") && fallbackUrl != url) activeUrl = fallbackUrl
                    else failed = true
                },
                modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)
                    .clip(RoundedCornerShape(Radii.row)).background(Ink.SurfaceHigh)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button) {
                        runCatching { uriHandler.openUri(activeUrl) }
                    },
            )
        } else {
            Box(
                Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(RoundedCornerShape(Radii.row))
                    .background(Ink.SurfaceHigh).padding(14.dp),
                contentAlignment = Alignment.CenterStart,
            ) { Text("Image unavailable", style = Typography.bodySmall, color = Ink.I500) }
        }
        if (alt.isNotBlank()) Text(alt, style = Typography.bodySmall, color = Ink.I500, maxLines = 2)
    }
}

/** Code card: language bar with a copy action, hairline seam, horizontally scrolling mono code. */
@Composable
fun MonoCodeBlock(code: String, language: String?, modifier: Modifier = Modifier, streaming: Boolean = false) {
    val shape = RoundedCornerShape(Radii.row)
    val context = LocalContext.current
    val trimmed = code.trimEnd('\n')
    val info = language?.trim()?.lowercase().orEmpty()
    val runLanguage = info.removeSuffix("-run").substringBefore(' ')
    val autoRun = info.endsWith(" run") || info.endsWith("-run")
    var showPreview by remember(code, language) { mutableStateOf(false) }
    LaunchedEffect(streaming, autoRun, code) { if (autoRun && !streaming) showPreview = true }
    val runnable = runLanguage in setOf("html", "htm", "svg", "xml", "javascript", "js", "css")
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Ink.I900)
            .border(1.dp, whiteA(0.08f), shape),
    ) {
        Row(
            Modifier.fillMaxWidth().background(whiteA(0.03f)).padding(start = 18.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (runnable && !streaming) {
                CodePreviewControl(showingPreview = showPreview) { showPreview = it }
                Spacer(Modifier.size(8.dp))
            }
            Text(
                language?.trim()?.takeIf { it.isNotBlank() } ?: "text",
                style = RowMeta.copy(fontFamily = MonoBody.fontFamily),
                color = Ink.I500,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            CopyChip(onCopy = { copyToClipboard(context, trimmed, "Code") })
        }
        Hairline()
        AnimatedContent(
            targetState = showPreview,
            transitionSpec = { fadeIn(tween(120)) togetherWith fadeOut(tween(90)) },
            label = "codePreviewInline",
        ) { preview ->
            if (preview) {
                CodeRunnerInline(code = trimmed, language = runLanguage, modifier = Modifier.fillMaxWidth())
            } else {
                Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    val highlighted = if (streaming) AnnotatedString(trimmed) else highlightCode(trimmed, language)
                    Text(highlighted, style = MonoBody.copy(color = CodePalette.Text), softWrap = false,
                        modifier = Modifier.padding(start = 18.dp, end = 14.dp, top = 14.dp, bottom = 14.dp))
                }
            }
        }
    }
}

/** Labeled segmented switch for source and live preview, anchored at the left of the code toolbar. */
@Composable
private fun CodePreviewControl(showingPreview: Boolean, onSelect: (Boolean) -> Unit) {
    val activeOffset by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (showingPreview) 75.dp else 2.dp,
        animationSpec = tween(190),
        label = "codePreviewToggle",
    )
    Box(
        Modifier.padding(end = 2.dp).size(width = 146.dp, height = 34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(whiteA(0.09f))
            .border(1.dp, whiteA(0.10f), RoundedCornerShape(10.dp)),
    ) {
        Box(
            Modifier.offset(x = activeOffset, y = 3.dp).size(width = 69.dp, height = 27.dp)
                .clip(RoundedCornerShape(8.dp)).background(whiteA(0.20f)),
        )
        Row(Modifier.fillMaxWidth().fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
            CodePreviewOption("Code", Lucide.Braces, !showingPreview, Modifier.weight(1f)) { onSelect(false) }
            CodePreviewOption("Preview", Lucide.Play, showingPreview, Modifier.weight(1f)) { onSelect(true) }
        }
    }
}

@Composable
private fun CodePreviewOption(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = tween(120),
        label = "codePreviewPress",
    )
    Row(
        modifier.fillMaxHeight()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(8.dp))
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = label, Modifier.size(13.dp),
            tint = if (selected) Ink.White else Ink.I500)
        Spacer(Modifier.size(4.dp))
        Text(label, style = Typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = if (selected) Ink.White else Ink.I500, maxLines = 1)
    }
}

/** Copy becomes a checkmark only; transparent background and an animated press. */
@Composable
private fun CopyChip(onCopy: () -> Unit) {
    var copied by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.80f else 1f,
        animationSpec = tween(150),
        label = "codeCopyPress",
    )
    LaunchedEffect(copied) { if (copied) { delay(1_100); copied = false } }
    Box(
        Modifier.size(width = 30.dp, height = 30.dp).graphicsLayer { scaleX = scale; scaleY = scale; rotationZ = if (pressed) 5f else 0f }
            .clip(RoundedCornerShape(8.dp)).background(whiteA(if (pressed) 0.16f else 0f))
            .clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                haptics.tap(); onCopy(); copied = true
            },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(copied, transitionSpec = { fadeIn(tween(130)) togetherWith fadeOut(tween(80)) }, label = "codeCopy") { done ->
            Icon(if (done) Lucide.Check else Lucide.Copy, contentDescription = if (done) "Copied" else S.copyCode,
                Modifier.size(15.dp), tint = if (done) Ink.White else Ink.I500)
        }
    }
}
