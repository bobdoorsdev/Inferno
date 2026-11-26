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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
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
 * Compose-native GFM Markdown with KaTeX math. The library parses incrementally while streaming,
 * retains stable block nodes, suppresses incomplete delimiters, and provides its own selection model.
 */
@Composable
fun MarkdownBody(text: String, modifier: Modifier = Modifier, streaming: Boolean = false, animateStreaming: Boolean = true) {
    val context = LocalContext.current
    val source = remember(text, streaming) { normalizeMarkdownForRenderer(text, streaming) }
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
        textMotion = androidx.compose.ui.text.style.TextMotion.Animated,
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
        if (codeTicks == 0 && line[i] == '$' && (i == 0 || line[i - 1] != '\\') &&
            i + 1 < line.length && line[i + 1].isDigit()
        ) {
            var end = i + 1
            while (end < line.length && (line[end].isDigit() || line[end] == ',' || line[end] == '.')) end++
            val boundary = end == line.length || line[end].isWhitespace() || line[end] in ",;:!?)]}"
            if (boundary) {
                out.append("\\$")
                i++
                continue
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
            Text(
                language?.trim()?.takeIf { it.isNotBlank() } ?: "text",
                style = RowMeta.copy(fontFamily = MonoBody.fontFamily),
                color = Ink.I500,
                modifier = Modifier.weight(1f),
            )
            if (runnable && !streaming) CodeRunChip(showingPreview = showPreview) { showPreview = it }
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

/** Compact two-icon code/preview switch with a moving selected surface. */
@Composable
private fun CodeRunChip(showingPreview: Boolean, onSelect: (Boolean) -> Unit) {
    val activeOffset by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (showingPreview) 25.dp else 0.dp,
        animationSpec = tween(180),
        label = "codePreviewToggle",
    )
    Box(
        Modifier.padding(end = 3.dp).size(width = 50.dp, height = 27.dp)
            .clip(RoundedCornerShape(8.dp)).background(whiteA(0.06f)),
    ) {
        Box(
            Modifier.offset(x = activeOffset + 2.dp, y = 2.dp).size(21.dp, 23.dp)
                .clip(RoundedCornerShape(6.dp)).background(whiteA(0.16f)),
        )
        Row(Modifier.fillMaxWidth().fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
            CodeToggleIcon(Lucide.Code, "Show code", !showingPreview, Modifier.weight(1f).fillMaxHeight()) { onSelect(false) }
            CodeToggleIcon(Lucide.Play, "Preview code", showingPreview, Modifier.weight(1f).fillMaxHeight()) { onSelect(true) }
        }
    }
}

@Composable
private fun CodeToggleIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.90f else 1f,
        animationSpec = tween(100),
        label = "codeTogglePress",
    )
    Box(
        modifier.graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, Modifier.size(13.dp), tint = if (selected) Ink.White else Ink.I500)
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
        targetValue = if (pressed) 0.90f else 1f,
        animationSpec = tween(100),
        label = "codeCopyPress",
    )
    LaunchedEffect(copied) { if (copied) { delay(1_100); copied = false } }
    Box(
        Modifier.size(width = 25.dp, height = 25.dp).graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(7.dp))
            .clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                haptics.tap(); onCopy(); copied = true
            },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(copied, transitionSpec = { fadeIn(tween(130)) togetherWith fadeOut(tween(80)) }, label = "codeCopy") { done ->
            Icon(if (done) Lucide.Check else Lucide.Copy, contentDescription = if (done) "Copied" else S.copyCode,
                Modifier.size(13.dp), tint = if (done) Ink.White else Ink.I500)
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
