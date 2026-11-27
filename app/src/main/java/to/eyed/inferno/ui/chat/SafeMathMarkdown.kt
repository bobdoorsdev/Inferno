package to.eyed.inferno.ui.chat

/**
 * Normalizes LaTeX delimiters for the native math renderer. Fenced and inline code are opaque:
 * a command inside code is never interpreted as math.
 */
object SafeMathMarkdown {
    private val fenceStart = Regex("""^\s*(\x60{3,}|~{3,}).*$""")
    private val inlineParen = Regex("""\\\((.+?)\\\)""")
    private val inlineBracket = Regex("""\\\[(.+?)\\\]""")
    private val bareCommand = Regex(
        """\\(alpha|beta|gamma|delta|epsilon|varepsilon|theta|vartheta|lambda|mu|pi|rho|sigma|tau|phi|varphi|omega|Gamma|Delta|Theta|Lambda|Pi|Sigma|Phi|Omega|frac|dfrac|tfrac|sqrt|sum|prod|int|oint|cdot|times|div|pm|mp|leq|geq|neq|approx|infty|partial|nabla|degree)(?:\{[^{}\n]*\}){0,2}"""
    )

    fun containsMath(source: String): Boolean {
        var fenceChar: Char? = null
        var fenceSize = 0
        for (line in source.split('\n')) {
            val marker = fenceStart.matchEntire(line)
            if (marker != null) {
                val token = marker.groupValues[1]
                if (fenceChar == null) {
                    fenceChar = token[0]
                    fenceSize = token.length
                } else if (token[0] == fenceChar && token.length >= fenceSize) {
                    fenceChar = null
                    fenceSize = 0
                }
                continue
            }
            if (fenceChar != null) continue
            val prose = stripInlineCode(line)
            if (prose.trim() == "$$" || prose.trim() == "\\[" || prose.trim() == "\\]") return true
            if (Regex("""(?<!\\)\$[^$\n]+\$""").containsMatchIn(prose)) return true
            if (inlineParen.containsMatchIn(prose) || inlineBracket.containsMatchIn(prose)) return true
            if (bareCommand.containsMatchIn(prose)) return true
        }
        return false
    }

    fun normalize(source: String): String {
        val output = StringBuilder(source.length + 16)
        val lines = source.split('\n')
        var fenceChar: Char? = null
        var fenceSize = 0
        var inDisplayMath = false
        for ((index, line) in lines.withIndex()) {
            val marker = fenceStart.matchEntire(line)
            if (marker != null) {
                val token = marker.groupValues[1]
                if (fenceChar == null) {
                    fenceChar = token[0]
                    fenceSize = token.length
                } else if (token[0] == fenceChar && token.length >= fenceSize) {
                    fenceChar = null
                    fenceSize = 0
                }
                output.append(line)
            } else if (fenceChar != null) {
                output.append(line)
            } else {
                when (line.trim()) {
                    "\\[" -> { output.append("$$"); inDisplayMath = true }
                    "\\]" -> { output.append("$$"); inDisplayMath = false }
                    "$$" -> { output.append(line); inDisplayMath = !inDisplayMath }
                    else -> {
                        var normalized = inlineParen.replace(line) { "\$" + it.groupValues[1] + "\$" }
                        normalized = inlineBracket.replace(normalized) { "\$" + it.groupValues[1] + "\$" }
                                                val incompleteParenMath = line.contains("\\(") && !line.contains("\\)")
                        if (!inDisplayMath && !incompleteParenMath) normalized = wrapBareCommands(normalized)
                        output.append(normalized)
                    }
                }
            }
            if (index != lines.lastIndex) output.append('\n')
        }
        return output.toString()
    }

    /** Wrap un-delimited LaTeX commands (for example \delta) as inline math. */
    private fun wrapBareCommands(line: String): String {
        val out = StringBuilder(line.length + 8)
        var i = 0
        var inMath = false
        var mathDelimiter = 0
        var codeTicks = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '\u0060') {
                var end = i
                while (end < line.length && line[end] == '\u0060') end++
                val count = end - i
                if (codeTicks == 0) codeTicks = count else if (codeTicks == count) codeTicks = 0
                out.append(line, i, end)
                i = end
                continue
            }
            if (codeTicks == 0 && c == '$' && (i == 0 || line[i - 1] != '\\')) {
                var end = i
                while (end < line.length && line[end] == '$') end++
                val count = end - i
                if (mathDelimiter == 0) { mathDelimiter = count; inMath = true }
                else if (mathDelimiter == count) { mathDelimiter = 0; inMath = false }
                out.append(line, i, end)
                i = end
                continue
            }
            if (codeTicks == 0 && !inMath && c == '\\' && (i == 0 || line[i - 1] != '\\')) {
                val match = bareCommand.find(line, i)
                if (match != null && match.range.first == i) {
                    out.append('$').append(match.value).append('$')
                    i = match.range.last + 1
                    continue
                }
            }
            out.append(c)
            i++
        }
        return out.toString()
    }

    private fun stripInlineCode(line: String): String =
        Regex("""(\x60+).*?\1""").replace(line, "")
}
