package to.eyed.inferno.ui.chat

/**
 * The markdown renderer is not a TeX engine. Keep TeX delimiters out of its block parser and
 * present formulas as readable inline / display code instead of letting partial delimiters
 * corrupt the markdown tree during streaming.
 */
object SafeMathMarkdown {
    private val fence = Regex("^\\s*([~]{3,}|\\x60{3,}).*$")

    fun sanitize(source: String): String {
        val out = StringBuilder(source.length + 32)
        var fenceChar: Char? = null
        var fenceSize = 0
        var displayMath = false
        for (line in source.split('\n')) {
            val marker = fence.matchEntire(line)
            if (!displayMath && marker != null) {
                val raw = marker.groupValues[1]
                if (fenceChar == null) {
                    fenceChar = raw[0]
                    fenceSize = raw.length
                } else if (raw[0] == fenceChar && raw.length >= fenceSize) {
                    fenceChar = null
                    fenceSize = 0
                }
                out.append(line).append('\n')
                continue
            }
            if (fenceChar != null) {
                out.append(line).append('\n')
                continue
            }
            val trimmed = line.trim()
            if (displayMath) {
                if (trimmed == "$$" || trimmed == "\\]") {
                    out.append("\u0060\u0060\u0060\n")
                    displayMath = false
                } else out.append(line).append('\n')
                continue
            }
            if (trimmed == "$$" || trimmed == "\\[") {
                out.append("\u0060\u0060\u0060math\n")
                displayMath = true
                continue
            }
            if (trimmed.startsWith("$$") && trimmed.length > 4 && trimmed.endsWith("$$")) {
                out.append("\u0060\u0060\u0060math\n").append(trimmed.substring(2, trimmed.length - 2)).append("\n\u0060\u0060\u0060\n")
                continue
            }
            if (trimmed.startsWith("\\[") && trimmed.endsWith("\\]") && trimmed.length > 4) {
                out.append("\u0060\u0060\u0060math\n").append(trimmed.substring(2, trimmed.length - 2)).append("\n\u0060\u0060\u0060\n")
                continue
            }
            out.append(inlineMath(line)).append('\n')
        }
        if (displayMath) out.append("\u0060\u0060\u0060\n")
        return out.toString().trimEnd('\n')
    }

    private fun inlineMath(line: String): String {
        val out = StringBuilder(line.length + 16)
        var i = 0
        while (i < line.length) {
            val start: Int
            val close: String
            when {
                line.startsWith("\\(", i) -> { start = i + 2; close = "\\)" }
                line[i] == '$' && !line.startsWith("$$", i) -> { start = i + 1; close = "$" }
                else -> { out.append(line[i++]); continue }
            }
            val end = line.indexOf(close, start)
            if (end < 0) {
                out.append(line.substring(i, start - if (close == "$") 1 else 2))
                out.append(codeSpan(line.substring(start)))
                return out.toString()
            }
            val content = line.substring(start, end)
            out.append(line.substring(i, start - if (close == "$") 1 else 2))
            out.append(codeSpan(content))
            i = end + close.length
        }
        return out.toString()
    }

    private fun codeSpan(value: String): String {
        val longest = Regex("\\x60+").findAll(value).maxOfOrNull { it.value.length } ?: 0
        val tick = "\u0060".repeat(longest + 1)
        val pad = value.startsWith('\u0060') || value.endsWith('\u0060')
        return if (pad) "$tick $value $tick" else "$tick$value$tick"
    }
}
