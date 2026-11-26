package to.eyed.inferno.ui.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.Code
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Play
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
import kotlinx.coroutines.delay
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
 * chunks so only completed chunks enter the Markdown parser while tokens arrive; the live tail stays lightweight.
 * Embedded image links are rendered with Coil at their original position. `TextDirection.Content` keeps RTL readable.
 */
@Composable
fun MarkdownBody(
    text: String,
    modifier: Modifier = Modifier,
    streaming: Boolean = false,
    animateStreaming: Boolean = true,
    citationUrls: List<String> = emptyList(),
) {
    val linkedText = remember(text, citationUrls) { linkInlineCitations(text, citationUrls) }
    if (streaming) {
        StreamingMarkdownBody(linkedText, modifier, animateStreaming)
    } else {
        val formatted = remember(linkedText) { formatMarkdownChunk(linkedText) }
        val split = remember(formatted) { MarkdownChunker.splitOpenFence(formatted) }
        val chunks = remember(split.first) { MarkdownChunker.chunks(split.first) }
        SelectionContainer {
            Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                chunks.forEachIndexed { index, chunk -> key(index) { MarkdownChunkView(chunk) } }
                split.second?.let { fence -> key("live-code") { MonoCodeBlock(fence.code, fence.language) } }
            }
        }
    }
}

private fun formatMarkdownChunk(source: String): String =
    runCatching { SafeMathMarkdown.sanitize(MarkdownMath.format(source)) }.getOrDefault(source)

private fun linkInlineCitations(source: String, urls: List<String>): String {
    if (urls.isEmpty()) return source
    val citation = Regex("""(?<!\!)\[(\d{1,2}(?:\s*[,;]\s*\d{1,2})*)](?!\s*\()""")
    return citation.replace(source) { match ->
        Regex("""\d{1,2}""").findAll(match.groupValues[1]).mapNotNull { item ->
            val index = item.value.toIntOrNull()?.minus(1) ?: return@mapNotNull null
            urls.getOrNull(index)?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
                ?.let { "[" + item.value + "](" + it + ")" }
        }.joinToString(", ").ifBlank { match.value }
    }
}

@Composable
private fun StreamingMarkdownBody(text: String, modifier: Modifier, animateStreaming: Boolean) {
    // Split the raw stream before formatting. Every completed paragraph is formatted and parsed once;
    // only the final growing paragraph is revisited as tokens arrive.
    val split = remember(text) { MarkdownChunker.splitOpenFence(text) }
    val chunks = remember(split.first) { MarkdownChunker.chunks(split.first) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        chunks.forEachIndexed { index, rawChunk ->
            if (index == chunks.lastIndex) {
                // The active paragraph stays stable and unanimated while tokens arrive.
                key("stream-tail") {
                    val chunk = remember(rawChunk) { formatMarkdownChunk(rawChunk) }
                    StreamingMarkdownTail(chunk, animateStreaming)
                }
            } else {
                // A paragraph only enters this branch after its terminating blank line arrives.
                // Its content key gives it one entrance animation, never one animation per token.
                key(rawChunk) {
                    val chunk = remember(rawChunk) { formatMarkdownChunk(rawChunk) }
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(tween(115)) + expandVertically(tween(115)),
                    ) { MarkdownChunkView(chunk) }
                }
            }
        }
        split.second?.let { fence ->
            key("live-code") { MonoCodeBlock(fence.code, fence.language, streaming = true) }
        }
    }
}

@Composable
private fun MarkdownChunkView(chunk: String, animated: Boolean = false) {
    val matches = remember(chunk) { MarkdownImagePattern.findAll(chunk).toList() }
    if (matches.isEmpty()) {
        MarkdownTextChunk(chunk, animated)
        return
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        var cursor = 0
        matches.forEach { match ->
            val start = match.range.first
            if (start > cursor) {
                val preceding = chunk.substring(cursor, start)
                if (preceding.isNotBlank()) MarkdownTextChunk(preceding, animated)
            }
            if (match.groupValues[1] == "__favicon__") {
                AsyncImage(
                    model = match.groupValues[2],
                    contentDescription = "Website icon",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(18.dp).clip(RoundedCornerShape(4.dp)),
                )
            } else MarkdownImageCard(match.groupValues[1], match.groupValues[2], match.groupValues.getOrNull(3).orEmpty())
            cursor = match.range.last + 1
        }
        if (cursor < chunk.length) {
            val trailing = chunk.substring(cursor)
            if (trailing.isNotBlank()) MarkdownTextChunk(trailing, animated)
        }
    }
}

private val MarkdownImagePattern = Regex("""!\[([^\]]*)\]\(((?:https?://|file://)[^)\s]+)(?:\s+"([^"]*)")?\)""")

@Composable
private fun MarkdownImageCard(alt: String, url: String, fallbackUrl: String = "") {
    val uriHandler = LocalUriHandler.current
    var activeUrl by remember(url, fallbackUrl) { mutableStateOf(url) }
    var failed by remember(url, fallbackUrl) { mutableStateOf(false) }
    Column(
        Modifier.widthIn(max = 360.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
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
                    .clip(RoundedCornerShape(Radii.row))
                    .background(Ink.SurfaceHigh)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                    ) { runCatching { uriHandler.openUri(activeUrl) } },
            )
        } else {
            Box(
                Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(RoundedCornerShape(Radii.row))
                    .background(Ink.SurfaceHigh).padding(14.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text("Image unavailable", style = Typography.bodySmall, color = Ink.I500)
            }
        }
        if (alt.isNotBlank()) Text(alt, style = Typography.bodySmall, color = Ink.I500, maxLines = 2)
    }
}

@Composable
private fun StreamingMarkdownTail(chunk: String, animateStreaming: Boolean) {
    val lines = remember(chunk) { chunk.split('\n') }
    val opener = remember(lines) { Regex("^(\u0060{3,}|~{3,})([A-Za-z0-9_+.-]*)$").matchEntire(lines.firstOrNull()?.trim().orEmpty()) }
    val closeIndex = remember(lines, opener) {
        if (opener == null) -1 else lines.withIndex().firstOrNull { (index, line) ->
            index > 0 && line.trim().let { it.length >= opener.groupValues[1].length && it.all { c -> c == opener.groupValues[1][0] } }
        }?.index ?: -1
    }
    if (opener != null && closeIndex > 0) {
        MonoCodeBlock(lines.drop(1).take(closeIndex - 1).joinToString("\n"), opener.groupValues[2], streaming = true)
        val trailing = lines.drop(closeIndex + 1).joinToString("\n")
        if (trailing.isNotBlank()) MarkdownTextChunk(trailing, animateStreaming)
    } else {
        val rendered = chunk
        // Fade once when the live message first appears. Restarting this animation for every
        // token batch caused opacity to stutter and kept the tail from ever settling at 1f.
        val tailFade = remember { Animatable(if (animateStreaming) 0.92f else 1f) }
        LaunchedEffect(animateStreaming) {
            if (animateStreaming && tailFade.value < 1f) {
                tailFade.animateTo(1f, tween(180, easing = LinearOutSlowInEasing))
            } else if (!animateStreaming) {
                tailFade.snapTo(1f)
            }
        }
        Box(Modifier.fillMaxWidth().graphicsLayer { alpha = tailFade.value }) {
            StreamingMarkdownText(formatMarkdownChunk(rendered), animateStreaming)
        }
    }
}

@Composable
private fun StreamingMarkdownText(chunk: String, animateStreaming: Boolean) {
    val uriHandler = LocalUriHandler.current
    val readable = remember(chunk) {
        chunk.replace("**", "").replace("__", "").replace("~~", "").replace(96.toChar().toString(), "")
    }
    var settled by remember { mutableStateOf(readable) }
    var incoming by remember { mutableStateOf("") }
    val fade = remember { Animatable(1f) }
    LaunchedEffect(readable, animateStreaming) {
        if (!animateStreaming || !readable.startsWith(settled) || readable.length <= settled.length) {
            settled = readable
            incoming = ""
            fade.snapTo(1f)
        } else {
            incoming = readable.removePrefix(settled)
            fade.snapTo(0f)
            fade.animateTo(1f, tween(170, easing = LinearOutSlowInEasing))
            settled = readable
            incoming = ""
        }
    }
    val fullText = settled + incoming
    val annotated = remember(fullText, incoming, fade.value) {
        buildAnnotatedString {
            val links = Regex("""\[([^\]]+)\]\((https?://[^)\s]+)\)""")
            var cursor = 0
            links.findAll(fullText).forEach { match ->
                append(fullText.substring(cursor, match.range.first))
                val start = length
                append(match.groupValues[1])
                val end = length
                addStringAnnotation("URL", match.groupValues[2], start, end)
                addStyle(SpanStyle(color = Ink.White, textDecoration = TextDecoration.Underline), start, end)
                cursor = match.range.last + 1
            }
            append(fullText.substring(cursor))
            if (incoming.isNotEmpty()) {
                addStyle(SpanStyle(color = Ink.I100.copy(alpha = fade.value)), (settled.length).coerceAtMost(length), length)
            }
        }
    }
    ClickableText(
        text = annotated,
        style = Typography.bodyLarge.copy(color = Ink.I100, textDirection = TextDirection.Content),
        onClick = { offset ->
            annotated.getStringAnnotations("URL", offset, offset).firstOrNull()?.let { annotation ->
                runCatching { uriHandler.openUri(annotation.item) }
            }
        },
    )
}

@Composable
private fun MarkdownTextChunk(chunk: String, animated: Boolean = false) {
    val body = Typography.bodyLarge.copy(color = Ink.I100, textDirection = TextDirection.Content)
    // immediate parses the first composition synchronously (no blank frame) and retainState keeps the
    // previous tree on screen during the async re-parse; without both, each publish flashes the
    // library's placeholder, which reads as violent flicker at streaming rate.
    val state = rememberMarkdownState(content = chunk, retainState = true, immediate = !animated)
    Markdown(
        markdownState = state,
        colors = markdownColor(
            text = Ink.I100,
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
        // Cells at the library's 160 dp would push a 3-column table past a phone's width into a horizontal scroll
        // with no affordance (the last column looked clipped); 110 dp keeps three columns on screen and the wrapping
        // rows below make long descriptions readable instead of ellipsised.
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
        modifier = Modifier.fillMaxWidth(),
    )
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
            Text(
                language?.trim()?.takeIf { it.isNotBlank() } ?: "text",
                style = RowMeta.copy(fontFamily = MonoBody.fontFamily),
                color = Ink.I500,
                modifier = Modifier.weight(1f),
            )
            if (runnable && !streaming) CodeRunChip(showingPreview = showPreview) { showPreview = !showPreview }
            CopyChip(onCopy = { copyToClipboard(context, trimmed, "Code") })
        }
        Hairline()
        AnimatedContent(
            targetState = showPreview,
            transitionSpec = { (fadeIn(tween(160)) + scaleIn(initialScale = 0.985f)) togetherWith (fadeOut(tween(110)) + scaleOut(targetScale = 0.99f)) },
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

/** Run affordance beside Copy. Only self-contained browser formats are enabled. */
@Composable
private fun CodeRunChip(showingPreview: Boolean, onRun: () -> Unit) {
    Row(
        Modifier.padding(end = 3.dp).heightIn(min = 27.dp).clip(RoundedCornerShape(8.dp)).background(whiteA(0.06f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onRun)
            .padding(horizontal = 7.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(if (showingPreview) Lucide.Code else Lucide.Play, contentDescription = if (showingPreview) "Show code" else "Preview code", Modifier.size(13.dp), tint = Ink.I100)
        Text(if (showingPreview) "Code" else "Preview", style = Typography.labelSmall, color = Ink.I100)
    }
}

/** Copy becomes a checkmark only; no expanding "Copied" label. */
@Composable
private fun CopyChip(onCopy: () -> Unit) {
    var copied by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
    LaunchedEffect(copied) { if (copied) { delay(1_100); copied = false } }
    Box(
        Modifier.size(width = 27.dp, height = 27.dp).clip(RoundedCornerShape(8.dp)).background(whiteA(0.06f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button) {
                haptics.tap(); onCopy(); copied = true
            },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(copied, transitionSpec = { fadeIn(tween(130)) togetherWith fadeOut(tween(80)) }, label = "codeCopy") { done ->
            Icon(if (done) Lucide.Check else Lucide.Copy, contentDescription = if (done) "Copied" else S.copyCode,
                Modifier.size(14.dp), tint = if (done) Ink.White else Ink.I500)
        }
    }
}


/** Offline-friendly LaTeX-to-Unicode formatting; fenced code is passed through byte-for-byte. */
internal object MarkdownMath {
    private const val MAX_SOURCE_CHARS = 300_000
    private const val MAX_LINE_CHARS = 12_000
    private const val MAX_EXPRESSION_CHARS = 2_048

    private val fenceMarker = Regex("^(\\x60{3,}|~{3,})(.*)$")
    private val inlineDisplayDollar = Regex("""\$\$(.+?)\$\$""")
    private val inlineDisplayBracket = Regex("""\\\[(.+?)\\\]""")
    private val inlineDollar = Regex("""(?<!\$)\$([^$\n]+)\$(?!\$)""")
    private val inlineParen = Regex("""\\\((.+?)\\\)""")
    private val simpleFraction = Regex("""\\(?:dfrac|tfrac|cfrac|frac)\s*\{([^{}]{1,512})}\s*\{([^{}]{1,512})}""")
    private val indexedRoot = Regex("""\\sqrt\s*\[([^]]{1,32})]\s*\{([^{}]{1,512})}""")
    private val squareRoot = Regex("""\\sqrt\s*\{([^{}]{1,512})}""")
    private val binomial = Regex("""\\(?:binom|dbinom|tbinom)\s*\{([^{}]{1,128})}\s*\{([^{}]{1,128})}""")
    private val wrappedCommand = Regex("""\\(mathbb|mathcal|mathfrak|mathscr|mathbf|boldsymbol|mathit|mathrm|mathsf|mathtt|text|textnormal|texttt|operatorname)\s*\{([^{}]*)}""")
    private val accentCommand = Regex("""\\(bar|overline|underline|hat|widehat|tilde|widetilde|vec|dot|ddot|acute|grave|breve|check)\s*\{([^{}]*)}""")
    private val overUnder = Regex("""\\(?:overset|stackrel)\s*\{([^{}]+)}\s*\{([^{}]+)}""")
    private val environment = Regex("""\\(?:begin|end)\s*\{[^{}]+}""")
    private val ignoredCommands = Regex("""\\(?:left|right|displaystyle|textstyle|scriptstyle|scriptscriptstyle|limits|nolimits|allowbreak|,|;|:|!|quad|qquad|enspace|thinspace)""")
    private val escapedSymbols = Regex("""\\([A-Za-z]+|,|;|:|!)""")
    private val inlineSuperscript = Regex("""\^\{([^{}]+)}|\^([A-Za-z0-9])""")
    private val inlineSubscript = Regex("""_\{([^{}]+)}|_([A-Za-z0-9])""")
    private val whitespace = Regex("""\s+""")

    private val symbols = mapOf(
        "alpha" to "α", "beta" to "β", "gamma" to "γ", "delta" to "δ", "epsilon" to "ε", "varepsilon" to "ϵ",
        "zeta" to "ζ", "eta" to "η", "theta" to "θ", "vartheta" to "ϑ", "iota" to "ι", "kappa" to "κ",
        "varkappa" to "ϰ", "lambda" to "λ", "mu" to "μ", "nu" to "ν", "xi" to "ξ", "omicron" to "ο", "pi" to "π",
        "varpi" to "ϖ", "rho" to "ρ", "varrho" to "ϱ", "sigma" to "σ", "varsigma" to "ς", "tau" to "τ",
        "upsilon" to "υ", "phi" to "φ", "varphi" to "ϕ", "chi" to "χ", "psi" to "ψ", "omega" to "ω",
        "Gamma" to "Γ", "Delta" to "Δ", "Theta" to "Θ", "Lambda" to "Λ", "Xi" to "Ξ", "Pi" to "Π",
        "Sigma" to "Σ", "Upsilon" to "Υ", "Phi" to "Φ", "Psi" to "Ψ", "Omega" to "Ω",
        "times" to "×", "cdot" to "·", "ast" to "∗", "star" to "⋆", "bullet" to "•", "div" to "÷",
        "pm" to "±", "mp" to "∓", "oplus" to "⊕", "ominus" to "⊖", "otimes" to "⊗", "oslash" to "⊘",
        "odot" to "⊙", "dagger" to "†", "ddagger" to "‡", "amalg" to "⨿", "diamond" to "◇",
        "neq" to "≠", "ne" to "≠", "le" to "≤", "leq" to "≤", "ge" to "≥", "geq" to "≥",
        "ll" to "≪", "gg" to "≫", "lesssim" to "≲", "gtrsim" to "≳", "lessapprox" to "⪅", "gtrapprox" to "⪆",
        "equiv" to "≡", "approx" to "≈", "asymp" to "≍", "sim" to "∼", "simeq" to "≃", "cong" to "≅",
        "propto" to "∝", "prec" to "≺", "succ" to "≻", "preceq" to "≼", "succeq" to "≽",
        "infty" to "∞", "partial" to "∂", "nabla" to "∇", "sum" to "∑", "prod" to "∏", "coprod" to "∐",
        "int" to "∫", "iint" to "∬", "iiint" to "∭", "oint" to "∮", "oiint" to "∯", "oiiint" to "∰",
        "sqrt" to "√", "angle" to "∠", "measuredangle" to "∡", "degree" to "°", "circ" to "∘",
        "perp" to "⊥", "parallel" to "∥", "nparallel" to "∦", "wedge" to "∧", "vee" to "∨",
        "land" to "∧", "lor" to "∨", "neg" to "¬", "forall" to "∀", "exists" to "∃", "nexists" to "∄",
        "in" to "∈", "notin" to "∉", "ni" to "∋", "subset" to "⊂", "supset" to "⊃",
        "subseteq" to "⊆", "supseteq" to "⊇", "nsubseteq" to "⊈", "nsupseteq" to "⊉",
        "cup" to "∪", "cap" to "∩", "uplus" to "⊎", "sqcup" to "⊔", "sqcap" to "⊓",
        "setminus" to "∖", "smallsetminus" to "∖", "emptyset" to "∅", "varnothing" to "∅",
        "rightarrow" to "→", "to" to "→", "longrightarrow" to "⟶", "leftarrow" to "←", "longleftarrow" to "⟵",
        "leftrightarrow" to "↔", "longleftrightarrow" to "⟷", "Rightarrow" to "⇒", "Longrightarrow" to "⟹",
        "Leftarrow" to "⇐", "Longleftarrow" to "⟸", "Leftrightarrow" to "⇔", "Longleftrightarrow" to "⟺",
        "mapsto" to "↦", "longmapsto" to "⟼", "uparrow" to "↑", "downarrow" to "↓", "updownarrow" to "↕",
        "nearrow" to "↗", "searrow" to "↘", "swarrow" to "↙", "nwarrow" to "↖",
        "hookrightarrow" to "↪", "hookleftarrow" to "↩", "leftharpoonup" to "↼", "leftharpoondown" to "↽",
        "rightharpoonup" to "⇀", "rightharpoondown" to "⇁", "rightleftharpoons" to "⇌",
        "twoheadrightarrow" to "↠", "twoheadleftarrow" to "↞", "leadsto" to "⇝", "rightsquigarrow" to "⇝",
        "dots" to "…", "ldots" to "…", "cdots" to "⋯", "vdots" to "⋮", "ddots" to "⋱",
        "therefore" to "∴", "because" to "∵", "aleph" to "ℵ", "beth" to "ℶ", "gimel" to "ℷ", "daleth" to "ℸ",
        "hbar" to "ℏ", "ell" to "ℓ", "Re" to "ℜ", "Im" to "ℑ", "wp" to "℘",
        "top" to "⊤", "bot" to "⊥", "models" to "⊨", "vdash" to "⊢", "dashv" to "⊣",
        "langle" to "⟨", "rangle" to "⟩", "lfloor" to "⌊", "rfloor" to "⌋", "lceil" to "⌈", "rceil" to "⌉",
        "lbrace" to "{", "rbrace" to "}", "lvert" to "|", "rvert" to "|", "lVert" to "‖", "rVert" to "‖",
        "colon" to ":", "backslash" to "∖", "prime" to "′", "hslash" to "ℏ",
        "quad" to " ", "qquad" to "  ", "enspace" to " ", "thinspace" to " ", "," to " ", ";" to " ", ":" to " ", "!" to ""
    )
    private val superscript = mapOf(
        '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴', '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
        '+' to '⁺', '-' to '⁻', '=' to '⁼', '(' to '⁽', ')' to '⁾', 'n' to 'ⁿ', 'i' to 'ⁱ', 'T' to 'ᵀ', 'k' to 'ᵏ',
        'a' to 'ᵃ', 'b' to 'ᵇ', 'c' to 'ᶜ', 'd' to 'ᵈ', 'e' to 'ᵉ', 'f' to 'ᶠ', 'g' to 'ᵍ', 'h' to 'ʰ',
        'j' to 'ʲ', 'l' to 'ˡ', 'm' to 'ᵐ', 'o' to 'ᵒ', 'p' to 'ᵖ', 'r' to 'ʳ', 's' to 'ˢ', 't' to 'ᵗ',
        'u' to 'ᵘ', 'v' to 'ᵛ', 'w' to 'ʷ', 'x' to 'ˣ', 'y' to 'ʸ', 'z' to 'ᶻ'
    )
    private val subscript = mapOf(
        '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄', '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉',
        '+' to '₊', '-' to '₋', '=' to '₌', '(' to '₍', ')' to '₎', 'a' to 'ₐ', 'e' to 'ₑ', 'i' to 'ᵢ',
        'j' to 'ⱼ', 'k' to 'ₖ', 'n' to 'ₙ', 'x' to 'ₓ', 'h' to 'ₕ', 'r' to 'ᵣ', 's' to 'ₛ', 't' to 'ₜ', 'u' to 'ᵤ', 'v' to 'ᵥ',
        'm' to 'ₘ', 'o' to 'ₒ', 'p' to 'ₚ'
    )

    private val doubleStruckUpper = "𝔸𝔹ℂ𝔻𝔼𝔽𝔾ℍ𝕀𝕁𝕂𝕃𝕄ℕ𝕆ℙℚℝ𝕊𝕋𝕌𝕍𝕎𝕏𝕐ℤ"
    private val doubleStruckLower = "𝕒𝕓𝕔𝕕𝕖𝕗𝕘𝕙𝕚𝕛𝕜𝕝𝕞𝕟𝕠𝕡𝕢𝕣𝕤𝕥𝕦𝕧𝕨𝕩𝕪𝕫"
    private val scriptUpper = "𝒜ℬ𝒞𝒟ℰℱ𝒢ℋℐ𝒥𝒦ℒℳ𝒩𝒪𝒫𝒬ℛ𝒮𝒯𝒰𝒱𝒲𝒳𝒴𝒵"
    private val scriptLower = "𝒶𝒷𝒸𝒹ℯ𝒻ℊ𝒽𝒾𝒿𝓀𝓁𝓂𝓃ℴ𝓅𝓆𝓇𝓈𝓉𝓊𝓋𝓌𝓍𝓎𝓏"
    private val frakturUpper = "𝔄𝔅ℭ𝔇𝔈𝔉𝔊ℌℑ𝔍𝔎𝔏𝔐𝔑𝔒𝔓𝔔ℜ𝔖𝔗𝔘𝔙𝔚𝔛𝔜ℨ"
    private val frakturLower = "𝔞𝔟𝔠𝔡𝔢𝔣𝔤𝔥𝔦𝔧𝔨𝔩𝔪𝔫𝔬𝔭𝔮𝔯𝔰𝔱𝔲𝔳𝔴𝔵𝔶𝔷"
    private val boldUpper = "𝐀𝐁𝐂𝐃𝐄𝐅𝐆𝐇𝐈𝐉𝐊𝐋𝐌𝐍𝐎𝐏𝐐𝐑𝐒𝐓𝐔𝐕𝐖𝐗𝐘𝐙"
    private val boldLower = "𝐚𝐛𝐜𝐝𝐞𝐟𝐠𝐡𝐢𝐣𝐤𝐥𝐦𝐧𝐨𝐩𝐪𝐫𝐬𝐭𝐮𝐯𝐰𝐱𝐲𝐳"
    private val italicUpper = "𝐴𝐵𝐶𝐷𝐸𝐹𝐺𝐻𝐼𝐽𝐾𝐿𝑀𝑁𝑂𝑃𝑄𝑅𝑆𝑇𝑈𝑉𝑊𝑋𝑌𝑍"
    private val italicLower = "𝑎𝑏𝑐𝑑𝑒𝑓𝑔ℎ𝑖𝑗𝑘𝑙𝑚𝑛𝑜𝑝𝑞𝑟𝑠𝑡𝑢𝑣𝑤𝑥𝑦𝑧"
    private val sansUpper = "𝖠𝖡𝖢𝖣𝖤𝖥𝖦𝖧𝖨𝖩𝖪𝖫𝖬𝖭𝖮𝖯𝖰𝖱𝖲𝖳𝖴𝖵𝖶𝖷𝖸𝖹"
    private val sansLower = "𝖺𝖻𝖼𝖽𝖾𝖿𝗀𝗁𝗂𝗃𝗄𝗅𝗆𝗇𝗈𝗉𝗊𝗋𝗌𝗍𝗎𝗏𝗐𝗑𝗒𝗓"
    private val monoUpper = "𝙰𝙱𝙲𝙳𝙴𝙵𝙶𝙷𝙸𝙹𝙺𝙻𝙼𝙽𝙾𝙿𝚀𝚁𝚂𝚃𝚄𝚅𝚆𝚇𝚈𝚉"
    private val monoLower = "𝚊𝚋𝚌𝚍𝚎𝚏𝚐𝚑𝚒𝚓𝚔𝚕𝚖𝚗𝚘𝚙𝚚𝚛𝚜𝚝𝚞𝚟𝚠𝚡𝚢𝚣"

    fun format(source: String): String {
        if (source.length > MAX_SOURCE_CHARS) return source
        val out = StringBuilder(source.length + 32)
        val display = StringBuilder()
        var fence: Char? = null
        var fenceLength = 0
        var displayEnd: String? = null
        source.lineSequence().forEach { line ->
            if (displayEnd != null) {
                if (line.trim() == displayEnd) {
                    out.append("\n\n**").append(formatExpression(display.toString())).append("**\n\n")
                    display.setLength(0)
                    displayEnd = null
                } else display.append(line).append(' ')
                return@forEach
            }
            val marker = fenceMarker.matchEntire(line.trim())
            if (marker != null && (fence == null || marker.groupValues[1][0] == fence)) {
                val raw = marker.groupValues[1]
                if (fence == null && (raw[0] == '~' || !marker.groupValues[2].contains('`'))) {
                    fence = raw[0]
                    fenceLength = raw.length
                } else if (fence != null && raw.length >= fenceLength && marker.groupValues[2].isBlank()) {
                    fence = null
                    fenceLength = 0
                }
                out.append(line).append('\n')
                return@forEach
            }
            if (fence != null) {
                out.append(line).append('\n')
                return@forEach
            }
            val trimmed = line.trim()
            when {
                trimmed == "$$" -> { displayEnd = "$$"; return@forEach }
                trimmed == "\\[" -> { displayEnd = "\\]"; return@forEach }
                trimmed.startsWith("$$") && trimmed.length > 4 && trimmed.endsWith("$$") ->
                    out.append("\n\n**").append(formatExpression(trimmed.substring(2, trimmed.length - 2))).append("**\n\n")
                trimmed.startsWith("\\[") && trimmed.endsWith("\\]") && trimmed.length > 4 ->
                    out.append("\n\n**").append(formatExpression(trimmed.substring(2, trimmed.length - 2))).append("**\n\n")
                else -> out.append(formatLine(line)).append('\n')
            }
        }
        if (displayEnd != null) out.append("\n\n**").append(formatExpression(display.toString())).append("**\n\n")
        return out.toString().trimEnd('\n')
    }

    private fun formatLine(line: String): String {
        if (line.length > MAX_LINE_CHARS) return line
        var value = inlineDisplayDollar.replace(line) { "\n\n**" + formatExpression(it.groupValues[1]) + "**\n\n" }
        value = inlineDisplayBracket.replace(value) { "\n\n**" + formatExpression(it.groupValues[1]) + "**\n\n" }
        value = inlineDollar.replace(value) { formatExpression(it.groupValues[1]) }
        value = inlineParen.replace(value) { formatExpression(it.groupValues[1]) }
        // Some local models omit delimiters and emit plain x^2 / x_1 notation in prose.
        value = Regex("""(?<![\w\\])([A-Za-zΑ-Ωα-ω])\^\{([^{}]{1,8})}""").replace(value) { m ->
            m.groupValues[1] + mapIndex(m.groupValues[2], superscript, "^(" + m.groupValues[2] + ")")
        }
        value = Regex("""(?<![\w\\])([A-Za-zΑ-Ωα-ω])\^([0-9]+|[A-Za-z])""").replace(value) { m ->
            m.groupValues[1] + mapIndex(m.groupValues[2], superscript, "^" + m.groupValues[2])
        }
        value = Regex("""(?<![\w\\])([A-Za-zΑ-Ωα-ω])_\{([^{}]{1,8})}""").replace(value) { m ->
            m.groupValues[1] + mapIndex(m.groupValues[2], subscript, "_(" + m.groupValues[2] + ")")
        }
        value = Regex("""(?<![\w\\])([A-Za-zΑ-Ωα-ω])_([0-9]+|[A-Za-z])""").replace(value) { m ->
            m.groupValues[1] + mapIndex(m.groupValues[2], subscript, "_" + m.groupValues[2])
        }
        // Strip unmatched math delimiters, while retaining currency such as $25.
        value = value.replace(Regex("""(?<!\\)\$(?=[A-Za-z\\(])"""), "")
            .replace(Regex("""(?<=[A-Za-z0-9)}])\$(?=\s|[.,;:!?)]|$)"""), "")
        return value
    }

    private fun formatExpression(raw: String): String {
        if (raw.length > MAX_EXPRESSION_CHARS) return raw
        return LatexReadableParser(raw).parse()
    }

    /** Small bounded LaTeX reader: braces are balanced, so nested fractions and roots stay readable. */
    private class LatexReadableParser(private val source: String) {
        private var index = 0

        fun parse(): String = readUntil(null).replace(Regex("""\s+"""), " ").trim()

        private fun readUntil(close: Char?): String {
            val out = StringBuilder()
            while (index < source.length) {
                val ch = source[index]
                if (close != null && ch == close) {
                    index++
                    break
                }
                when (ch) {
                    '{' -> { index++; out.append(readUntil('}')) }
                    '}' -> { index++; out.append('}') }
                    '\\' -> out.append(readCommand())
                    '^', '_' -> {
                        index++
                        val argument = readArgument()
                        val table = if (ch == '^') MarkdownMath.superscript else MarkdownMath.subscript
                        val fallback = if (ch == '^') "^($argument)" else "_($argument)"
                        out.append(MarkdownMath.mapIndex(argument, table, fallback))
                    }
                    else -> {
                        out.append(ch)
                        index++
                    }
                }
            }
            return out.toString()
        }

        private fun readArgument(): String {
            while (index < source.length && source[index].isWhitespace()) index++
            if (index >= source.length) return ""
            if (source[index] == '{') {
                index++
                return readUntil('}')
            }
            if (source[index] == '\\') return readCommand()
            val start = index
            index++
            return source.substring(start, index)
        }

        private fun readCommand(): String {
            index++
            if (index >= source.length) return ""
            if (!source[index].isLetter()) {
                val symbol = source[index++]
                return when (symbol) {
                    ',', ';', ':' -> " "
                    '!' -> ""
                    '%' -> "%"
                    '_' -> "_"
                    '&' -> "&"
                    '{' -> "{"
                    '}' -> "}"
                    '\\' -> " "
                    else -> symbol.toString()
                }
            }
            val start = index
            while (index < source.length && source[index].isLetter()) index++
            val command = source.substring(start, index)
            return when (command) {
                "frac", "dfrac", "tfrac", "cfrac" -> {
                    val numerator = readArgument()
                    val denominator = readArgument()
                    "($numerator)⁄($denominator)"
                }
                "sqrt" -> {
                    val rootIndex = readOptionalIndex()
                    val value = readArgument()
                    val prefix = rootIndex?.let { MarkdownMath.mapIndex(it, MarkdownMath.superscript, it) }.orEmpty()
                    "$prefix" + if (value.matches(Regex("""[\p{L}\p{N}.]+"""))) "√$value" else "√($value)"
                }
                "mathbb", "mathcal", "mathscr", "mathfrak", "mathbf", "boldsymbol", "mathit", "mathsf", "mathtt" -> {
                    val value = readArgument()
                    when (command) {
                        "mathbb" -> MarkdownMath.styleLetters(value, MarkdownMath.doubleStruckUpper, MarkdownMath.doubleStruckLower)
                        "mathcal", "mathscr" -> MarkdownMath.styleLetters(value, MarkdownMath.scriptUpper, MarkdownMath.scriptLower)
                        "mathfrak" -> MarkdownMath.styleLetters(value, MarkdownMath.frakturUpper, MarkdownMath.frakturLower)
                        "mathbf", "boldsymbol" -> MarkdownMath.styleLetters(value, MarkdownMath.boldUpper, MarkdownMath.boldLower)
                        "mathit" -> MarkdownMath.styleLetters(value, MarkdownMath.italicUpper, MarkdownMath.italicLower)
                        "mathsf" -> MarkdownMath.styleLetters(value, MarkdownMath.sansUpper, MarkdownMath.sansLower)
                        else -> MarkdownMath.styleLetters(value, MarkdownMath.monoUpper, MarkdownMath.monoLower)
                    }
                }
                "mathrm", "text", "textnormal", "texttt", "operatorname", "textrm", "textsf" -> readArgument()
                "bar", "overline", "underline", "hat", "widehat", "tilde", "widetilde", "vec", "dot", "ddot", "acute", "grave", "breve", "check" -> {
                    val value = readArgument()
                    val mark = when (command) {
                        "bar", "overline" -> "̄"
                        "underline" -> "̲"
                        "hat", "widehat" -> "̂"
                        "tilde", "widetilde" -> "̃"
                        "vec" -> "⃗"
                        "dot" -> "̇"
                        "ddot" -> "̈"
                        "acute" -> "́"
                        "grave" -> "̀"
                        "breve" -> "̆"
                        else -> "̌"
                    }
                    value + mark
                }
                "boxed" -> "⟦" + readArgument() + "⟧"
                "cancel", "underbrace", "overbrace" -> readArgument()
                "overset", "stackrel" -> {
                    val top = readArgument()
                    val bottom = readArgument()
                    "$bottom⁽$top⁾"
                }
                "begin", "end" -> { readArgument(); "" }
                "left", "right", "displaystyle", "textstyle", "scriptstyle", "scriptscriptstyle",
                "limits", "nolimits", "allowbreak" -> ""
                "quad", "qquad", "enspace", "thinspace" -> " "
                "sin", "cos", "tan", "cot", "sec", "csc", "arcsin", "arccos", "arctan",
                "sinh", "cosh", "tanh", "log", "ln", "exp", "lim", "max", "min", "sup",
                "inf", "det", "dim", "ker", "Pr", "arg", "deg", "gcd" -> command
                else -> MarkdownMath.symbols[command] ?: command
            }
        }

        private fun readOptionalIndex(): String? {
            while (index < source.length && source[index].isWhitespace()) index++
            if (index >= source.length || source[index] != '[') return null
            index++
            val start = index
            while (index < source.length && source[index] != ']') index++
            val result = source.substring(start, index)
            if (index < source.length) index++
            return result
        }
    }

    private fun styleLetters(value: String, upper: String, lower: String): String {
        val uppers = upper.codePoints().toArray()
        val lowers = lower.codePoints().toArray()
        return buildString {
            value.codePoints().toArray().forEach { point ->
                when (point) {
                    in 'A'.code..'Z'.code -> append(String(Character.toChars(uppers[point - 'A'.code])))
                    in 'a'.code..'z'.code -> append(String(Character.toChars(lowers[point - 'a'.code])))
                    else -> append(String(Character.toChars(point)))
                }
            }
        }
    }

    private fun mapIndex(value: String, table: Map<Char, Char>, fallback: String): String =
        value.map { table[it] ?: it }.joinToString("").let { result -> if (result.any { it !in table.values }) fallback else result }
}

/**
 * Splits markdown into independently renderable chunks on blank lines, keeping fenced code blocks
 * (even unterminated, mid-stream) and list runs intact so numbering and fences never break across
 * chunks. Pure Kotlin: covered by MarkdownChunkerTest.
 */
object MarkdownChunker {
    private val fenceStart = Regex("^(`{3,}|~{3,})(.*)$")

    data class OpenFence(val language: String?, val code: String)

    fun splitOpenFence(source: String): Pair<String, OpenFence?> {
        val lines = source.split("\n")
        var markerChar: Char? = null
        var markerLength = 0
        var start = -1
        var language: String? = null
        for ((index, line) in lines.withIndex()) {
            val match = fenceStart.matchEntire(line.trim()) ?: continue
            val marker = match.groupValues[1]
            val rest = match.groupValues[2]
            if (markerChar == null) {
                if (marker[0] == 126.toChar() || !rest.contains(96.toChar())) {
                    markerChar = marker[0]
                    markerLength = marker.length
                    start = index
                    language = rest.trim().takeIf { it.isNotBlank() }
                }
            } else if (marker[0] == markerChar && marker.length >= markerLength && rest.isBlank()) {
                markerChar = null
                markerLength = 0
                start = -1
                language = null
            }
        }
        if (markerChar == null || start < 0) return source to null
        return lines.take(start).joinToString("\n").trimEnd('\n') to OpenFence(language, lines.drop(start + 1).joinToString("\n"))
    }

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
