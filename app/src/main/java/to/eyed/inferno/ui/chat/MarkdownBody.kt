package to.eyed.inferno.ui.chat

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
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
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.Lucide
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
 * chunks so only the growing last chunk re-parses while tokens arrive; `key(index)` keeps every earlier
 * chunk's parse alive. `TextDirection.Content` keeps RTL answers readable inside the LTR chrome.
 */
@Composable
fun MarkdownBody(text: String, modifier: Modifier = Modifier, streaming: Boolean = false) {
    val formatted = remember(text) { MarkdownMath.format(text) }
    val split = remember(formatted) { MarkdownChunker.splitOpenFence(formatted) }
    val chunks = remember(split.first) { MarkdownChunker.chunks(split.first) }
    SelectionContainer {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            chunks.forEachIndexed { index, chunk -> key(index) { MarkdownChunkView(chunk) } }
            split.second?.let { fence ->
                key("live-code") { MonoCodeBlock(fence.code, fence.language, streaming = streaming) }
            }
        }
    }
}

@Composable
private fun MarkdownChunkView(chunk: String) {
    val body = Typography.bodyLarge.copy(color = Ink.I100, textDirection = TextDirection.Content)
    // immediate parses the first composition synchronously (no blank frame) and retainState keeps the
    // previous tree on screen during the async re-parse; without both, each publish flashes the
    // library's placeholder, which reads as violent flicker at streaming rate.
    val state = rememberMarkdownState(content = chunk, retainState = true, immediate = true)
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
            Text(highlightCode(trimmed, language), style = MonoBody, softWrap = false, modifier = Modifier.padding(14.dp))
        }
    }
}

/** "Copy" -> "Copied" for 1.6 s; the system clipboard overlay on API 33+ is the only other feedback. */
@Composable
private fun CopyChip(onCopy: () -> Unit) {
    var copied by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
    LaunchedEffect(copied) { if (copied) { delay(1_600); copied = false } }
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
            ) { haptics.tap(); onCopy(); copied = true }
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            if (copied) Lucide.Check else Lucide.Copy,
            contentDescription = S.copyCode,
            Modifier.size(12.dp),
            tint = if (copied) Ink.White else Ink.I500,
        )
        Text(if (copied) S.copied else S.copy, style = RowMeta, color = if (copied) Ink.White else Ink.I500)
    }
}


/** Offline-friendly LaTeX-to-Unicode formatting; fenced code is passed through byte-for-byte. */
internal object MarkdownMath {
    private val symbols = mapOf(
        "alpha" to "α", "beta" to "β", "gamma" to "γ", "delta" to "δ", "epsilon" to "ε", "varepsilon" to "ϵ",
        "zeta" to "ζ", "eta" to "η", "theta" to "θ", "vartheta" to "ϑ", "iota" to "ι", "kappa" to "κ",
        "lambda" to "λ", "mu" to "μ", "nu" to "ν", "xi" to "ξ", "omicron" to "ο", "pi" to "π",
        "varpi" to "ϖ", "rho" to "ρ", "varrho" to "ϱ", "sigma" to "σ", "varsigma" to "ς", "tau" to "τ",
        "upsilon" to "υ", "phi" to "φ", "varphi" to "ϕ", "chi" to "χ", "psi" to "ψ", "omega" to "ω",
        "Gamma" to "Γ", "Delta" to "Δ", "Theta" to "Θ", "Lambda" to "Λ", "Xi" to "Ξ", "Pi" to "Π",
        "Sigma" to "Σ", "Upsilon" to "Υ", "Phi" to "Φ", "Psi" to "Ψ", "Omega" to "Ω",
        "times" to "×", "cdot" to "·", "div" to "÷", "pm" to "±", "mp" to "∓", "neq" to "≠",
        "ne" to "≠", "le" to "≤", "leq" to "≤", "ge" to "≥", "geq" to "≥", "approx" to "≈",
        "equiv" to "≡", "sim" to "∼", "simeq" to "≃", "propto" to "∝", "infty" to "∞",
        "partial" to "∂", "nabla" to "∇", "sum" to "∑", "prod" to "∏", "int" to "∫",
        "iint" to "∬", "iiint" to "∭", "oint" to "∮", "sqrt" to "√", "angle" to "∠",
        "degree" to "°", "circ" to "∘", "perp" to "⊥", "parallel" to "∥", "wedge" to "∧",
        "vee" to "∨", "land" to "∧", "lor" to "∨", "neg" to "¬", "forall" to "∀",
        "exists" to "∃", "nexists" to "∄", "in" to "∈", "notin" to "∉", "ni" to "∋",
        "subset" to "⊂", "supset" to "⊃", "subseteq" to "⊆", "supseteq" to "⊇",
        "cup" to "∪", "cap" to "∩", "setminus" to "∖", "emptyset" to "∅", "varnothing" to "∅",
        "rightarrow" to "→", "to" to "→", "leftarrow" to "←", "leftrightarrow" to "↔",
        "Rightarrow" to "⇒", "Leftarrow" to "⇐", "Leftrightarrow" to "⇔", "mapsto" to "↦",
        "uparrow" to "↑", "downarrow" to "↓", "dots" to "…", "ldots" to "…", "cdots" to "⋯",
        "vdots" to "⋮", "ddots" to "⋱", "therefore" to "∴", "because" to "∵",
        "aleph" to "ℵ", "hbar" to "ℏ", "ell" to "ℓ", "Re" to "ℜ", "Im" to "ℑ",
        "quad" to " ", "qquad" to "  ", "," to " ", ";" to " ", "!" to ""
    )
    private val superscript = mapOf('0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴', '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹', '+' to '⁺', '-' to '⁻', '=' to '⁼', '(' to '⁽', ')' to '⁾', 'n' to 'ⁿ', 'i' to 'ⁱ')
    private val subscript = mapOf('0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄', '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉', '+' to '₊', '-' to '₋', '=' to '₌', '(' to '₍', ')' to '₎', 'a' to 'ₐ', 'e' to 'ₑ', 'i' to 'ᵢ', 'j' to 'ⱼ', 'k' to 'ₖ', 'n' to 'ₙ', 'x' to 'ₓ')

    fun format(source: String): String {
        val out = StringBuilder()
        var fence: Char? = null
        var fenceLength = 0
        source.lineSequence().forEach { line ->
            val marker = Regex("^(\\x60{3,}|~{3,})(.*)$").matchEntire(line.trim())
            if (marker != null && fence == null && (marker.groupValues[1][0] == 126.toChar() || !marker.groupValues[2].contains(96.toChar()))) {
                fence = marker.groupValues[1][0]
                fenceLength = marker.groupValues[1].length
                out.append(line).append('\n')
            } else if (marker != null && fence != null && marker.groupValues[1][0] == fence && marker.groupValues[1].length >= fenceLength && marker.groupValues[2].isBlank()) {
                fence = null
                fenceLength = 0
                out.append(line).append('\n')
            } else {
                out.append(if (fence == null) formatLine(line) else line).append('\n')
            }
        }
        return out.toString().trimEnd('\n')
    }

    private fun formatLine(line: String): String {
        var value = Regex("""\$\$(.+?)\$\$""").replace(line) { "> **" + formatExpression(it.groupValues[1]) + "**" }
        value = Regex("""\\\[(.+?)\\\]""").replace(value) { "> **" + formatExpression(it.groupValues[1]) + "**" }
        value = Regex("""(?<!\$)\$([^$\n]+)\$(?!\$)""").replace(value) { formatExpression(it.groupValues[1]) }
        value = Regex("""\\\((.+?)\\\)""").replace(value) { formatExpression(it.groupValues[1]) }
        return value
    }

    private fun formatExpression(raw: String): String {
        var value = raw.trim()
        repeat(4) {
            value = Regex("""\\frac\s*\{([^{}]+)}\s*\{([^{}]+)}""").replace(value) { "(" + it.groupValues[1] + ")/(" + it.groupValues[2] + ")" }
            value = Regex("""\\sqrt\s*\{([^{}]+)}""").replace(value) { "√(" + it.groupValues[1] + ")" }
        }
        value = Regex("""\\mathbb\s*\{([RNCQZ])}""").replace(value) {
            when (it.groupValues[1]) { "R" -> "ℝ"; "N" -> "ℕ"; "C" -> "ℂ"; "Q" -> "ℚ"; else -> "ℤ" }
        }
        value = Regex("""\\(?:text|mathrm|mathbf|mathit|operatorname)\s*\{([^{}]*)}""").replace(value) { it.groupValues[1] }
        value = Regex("""\\(?:bar|overline)\s*\{([^{}]*)}""").replace(value) { it.groupValues[1] + "̄" }
        value = Regex("""\\hat\s*\{([^{}]*)}""").replace(value) { it.groupValues[1] + "̂" }
        value = Regex("""\\vec\s*\{([^{}]*)}""").replace(value) { it.groupValues[1] + "⃗" }
        value = Regex("""\^\{([^{}]+)}""").replace(value) { mapIndex(it.groupValues[1], superscript, "^(" + it.groupValues[1] + ")") }
        value = Regex("""_\{([^{}]+)}""").replace(value) { mapIndex(it.groupValues[1], subscript, "_(" + it.groupValues[1] + ")") }
        value = Regex("""\^([A-Za-z0-9])""").replace(value) { mapIndex(it.groupValues[1], superscript, "^(" + it.groupValues[1] + ")") }
        value = Regex("""_([A-Za-z0-9])""").replace(value) { mapIndex(it.groupValues[1], subscript, "_(" + it.groupValues[1] + ")") }
        value = Regex("""\\([A-Za-z]+|,|;|!)""").replace(value) { symbols[it.groupValues[1]] ?: it.value.removePrefix("\\") }
        value = value.replace(Regex("""\\left|\\right|\\displaystyle|\\limits|\\,|\\;"""), "")
            .replace("""\%""", "%").replace("""\_""", "_")
            .replace("{", "").replace("}", "")
            .replace(Regex("""\s+"""), " ").trim()
        return value
    }

    private fun mapIndex(value: String, table: Map<Char, Char>, fallback: String): String =
        value.map { table[it] ?: it }.joinToString("").let { result -> if (result.any { it !in table.values }) fallback else result }
}

private fun highlightCode(code: String, language: String?): AnnotatedString {
    val keywords = setOf(
        "abstract", "as", "async", "await", "break", "case", "catch", "class", "const", "continue",
        "def", "do", "else", "elseif", "enum", "export", "extends", "false", "final", "finally",
        "for", "foreach", "from", "fun", "function", "if", "implements", "import", "in", "interface",
        "let", "local", "mut", "new", "nil", "null", "of", "override", "package", "private",
        "protected", "public", "raise", "return", "self", "static", "super", "switch", "this",
        "throw", "true", "try", "type", "typeof", "var", "val", "void", "while", "yield"
    )
    val types = setOf("Any", "Boolean", "Char", "Double", "Float", "Int", "Integer", "List", "Map", "Object", "String", "Table", "Vector", "number", "string", "boolean", "table", "nil")
    val tokens = Regex("""<!--.*?-->|//[^\n]*|#[^\n]*|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|\x60[^\x60]*\x60|</?[A-Za-z][^>]*>|&[A-Za-z0-9#]+;|\b[A-Za-z_][A-Za-z0-9_]*\b|\b\d+(?:\.\d+)?\b""")
    return buildAnnotatedString {
        var offset = 0
        tokens.findAll(code).forEach { match ->
            append(code.substring(offset, match.range.first))
            val token = match.value
            val color = when {
                token.startsWith("<!--") || token.startsWith("//") || token.startsWith("#") -> Color(0xFF6A9955)
                token.startsWith('"') || token.startsWith('\'') || token.startsWith(96.toChar()) -> Color(0xFFCE9178)
                token.startsWith("<") || token.startsWith("&") -> Color(0xFF569CD6)
                token.firstOrNull()?.isDigit() == true -> Color(0xFFB5CEA8)
                token in keywords -> Color(0xFFC586C0)
                token in types -> Color(0xFF4EC9B0)
                else -> Color(0xFFDCDCAA)
            }
            withStyle(SpanStyle(color = color)) { append(token) }
            offset = match.range.last + 1
        }
        append(code.substring(offset))
    }
}

/**
 * Splits markdown into independently renderable chunks on blank lines, keeping fenced code blocks/**
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
        return lines.take(start).joinToString("\n") to OpenFence(language, lines.drop(start + 1).joinToString("\n"))
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
