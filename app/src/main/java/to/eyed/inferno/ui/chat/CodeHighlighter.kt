package to.eyed.inferno.ui.chat

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight

/**
 * Lexical code highlighting for snippets commonly emitted in chat. Comments and strings are
 * consumed before identifiers are classified, so keywords inside them keep their literal colour.
 */
object CodePalette {
    val Text = Color(0xFFE4E8EE)
    val Keyword = Color(0xFFC4A7FF)
    val String = Color(0xFFA6DDAF)
    val Number = Color(0xFFE9BE83)
    val Comment = Color(0xFF87939F)
    val Function = Color(0xFF82CDEB)
    val Tag = Color(0xFFF19AA5)
    val Attribute = Color(0xFFC8B8F2)
    val Punctuation = Color(0xFFABB5C0)
    val Entity = Color(0xFFE8C58E)
}

fun highlightCode(code: String, language: String?): AnnotatedString {
    val lang = language.orEmpty().trim().lowercase().substringBefore(' ').removeSuffix("-run")
    val html = lang in setOf("html", "htm", "xml", "xhtml", "svg")
    val lua = lang in setOf("lua", "luau")
    val python = lang in setOf("python", "py", "python3")
    val json = lang == "json" || lang == "jsonc"
    val keywords = when {
        lua -> setOf("and", "break", "continue", "do", "else", "elseif", "end", "export", "false", "for", "function", "if", "in", "local", "nil", "not", "or", "repeat", "return", "then", "true", "type", "typeof", "until", "while")
        python -> setOf("and", "as", "assert", "async", "await", "break", "class", "continue", "def", "del", "elif", "else", "except", "False", "finally", "for", "from", "global", "if", "import", "in", "is", "lambda", "nonlocal", "not", "or", "pass", "raise", "return", "True", "try", "while", "with", "yield", "None")
        json -> emptySet()
        else -> setOf("as", "async", "await", "break", "case", "catch", "class", "const", "continue", "debugger", "default", "delete", "do", "else", "export", "extends", "false", "finally", "for", "from", "function", "if", "import", "in", "instanceof", "let", "new", "null", "of", "return", "static", "super", "switch", "this", "throw", "true", "try", "typeof", "undefined", "var", "void", "while", "yield")
    }
    val builtins = when {
        lua -> setOf("assert", "error", "getmetatable", "ipairs", "pairs", "pcall", "print", "rawequal", "rawget", "rawset", "require", "select", "setmetatable", "tonumber", "tostring", "type", "unpack", "warn", "xpcall")
        python -> setOf("abs", "all", "any", "bool", "dict", "enumerate", "filter", "float", "int", "len", "list", "map", "max", "min", "print", "range", "set", "str", "sum", "tuple", "zip")
        else -> setOf("alert", "console", "document", "fetch", "JSON", "Math", "parseInt", "Promise", "require", "setTimeout", "String")
    }
    val out = AnnotatedString.Builder()
    var i = 0
    fun add(end: Int, color: Color = CodePalette.Text, bold: Boolean = false) {
        if (end <= i) return
        val start = out.length
        out.append(code, i, end)
        out.addStyle(SpanStyle(color = color, fontWeight = if (bold) FontWeight.SemiBold else null), start, out.length)
        i = end
    }
    fun quotedEnd(start: Int, quote: Char, triple: Boolean = false): Int {
        var p = start + if (triple) 3 else 1
        while (p < code.length) {
            if (code[p] == '\\') { p = (p + 2).coerceAtMost(code.length); continue }
            if (triple && p + 2 < code.length && code[p] == quote && code[p + 1] == quote && code[p + 2] == quote) return p + 3
            if (!triple && code[p] == quote) return p + 1
            p++
        }
        return code.length
    }
    if (html) {
        while (i < code.length) {
            if (code.startsWith("<!--", i)) {
                val close = code.indexOf("-->", i + 4)
                add(if (close < 0) code.length else close + 3, CodePalette.Comment)
                continue
            }
            if (code[i] != '<') {
                val next = code.indexOf('<', i).let { if (it < 0) code.length else it }
                val entity = code.indexOf('&', i)
                if (entity >= 0 && entity < next) {
                    add(entity)
                    val semi = code.indexOf(';', entity + 1)
                    add(if (semi >= 0 && semi - entity <= 12) semi + 1 else entity + 1, CodePalette.Entity)
                } else add(next)
                continue
            }
            add(i + 1, CodePalette.Punctuation)
            if (i < code.length && code[i] in "/!?") add(i + 1, CodePalette.Punctuation)
            val tagStart = i
            var tagEnd = i
            while (tagEnd < code.length && (code[tagEnd].isLetterOrDigit() || code[tagEnd] in ":._-")) tagEnd++
            if (tagEnd > tagStart) add(tagEnd, CodePalette.Tag, true)
            while (i < code.length) {
                when {
                    code.startsWith("/>", i) -> { add(i + 2, CodePalette.Punctuation); break }
                    code[i] == '>' -> { add(i + 1, CodePalette.Punctuation); break }
                    code[i].isWhitespace() -> add(i + 1)
                    code[i] == '\'' || code[i] == '"' -> add(quotedEnd(i, code[i]), CodePalette.String)
                    code[i] == '=' -> add(i + 1, CodePalette.Punctuation)
                    else -> {
                        val start = i
                        var end = i
                        while (end < code.length && !code[end].isWhitespace() && code[end] !in "=>/'\"") end++
                        if (end == start) add(end + 1, CodePalette.Punctuation) else add(end, CodePalette.Attribute)
                    }
                }
            }
        }
        return out.toAnnotatedString()
    }

    while (i < code.length) {
        val c = code[i]
        when {
            lua && code.startsWith("--[[", i) -> {
                val end = code.indexOf("]]", i + 4)
                add(if (end < 0) code.length else end + 2, CodePalette.Comment)
            }
            lua && code.startsWith("--", i) -> {
                val end = code.indexOf('\n', i).let { if (it < 0) code.length else it }
                add(end, CodePalette.Comment)
            }
            code.startsWith("//", i) && !python && !lua -> {
                val end = code.indexOf('\n', i).let { if (it < 0) code.length else it }
                add(end, CodePalette.Comment)
            }
            code.startsWith("/*", i) && !python && !lua -> {
                val end = code.indexOf("*/", i + 2)
                add(if (end < 0) code.length else end + 2, CodePalette.Comment)
            }
            python && c == '#' -> {
                val end = code.indexOf('\n', i).let { if (it < 0) code.length else it }
                add(end, CodePalette.Comment)
            }
            c == '"' || c == '\'' || (c.code == 96 && !python) -> {
                val triple = python && i + 2 < code.length && code[i + 1] == c && code[i + 2] == c
                add(quotedEnd(i, c, triple), CodePalette.String)
            }
            c.isDigit() -> {
                var end = i + 1
                while (end < code.length && (code[end].isLetterOrDigit() || code[end] in "._")) end++
                add(end, CodePalette.Number)
            }
            c.isLetter() || c == '_' || (c == '$' && !json) -> {
                var end = i + 1
                while (end < code.length && (code[end].isLetterOrDigit() || code[end] == '_' || (code[end] == '$' && !json))) end++
                val word = code.substring(i, end)
                var look = end
                while (look < code.length && code[look].isWhitespace()) look++
                val color = when {
                    word in keywords -> CodePalette.Keyword
                    word in builtins || look < code.length && code[look] == '(' -> CodePalette.Function
                    else -> CodePalette.Text
                }
                add(end, color, word in keywords)
            }
            c in "{}[](),.:;+-*/%=<>!&|^~?" -> add(i + 1, CodePalette.Punctuation)
            else -> add(i + 1)
        }
    }
    return out.toAnnotatedString()
}
