package to.eyed.inferno.ui.chat

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeHighlighterTest {
    private fun colorAt(source: String, annotated: androidx.compose.ui.text.AnnotatedString, token: String): Color {
        val index = source.indexOf(token)
        require(index >= 0)
        return annotated.spanStyles.first { index >= it.start && index < it.end }.item.color
    }

    @Test fun luauCommentWinsOverKeywordAndFunctionNames() {
        val source = "-- local print() should stay a comment\nlocal value = 1"
        val result = highlightCode(source, "luau")
        assertEquals(CodePalette.Comment, colorAt(source, result, "local"))
        val valueIndex = source.lastIndexOf("local")
        assertEquals(CodePalette.Keyword, result.spanStyles.first { valueIndex >= it.start && valueIndex < it.end }.item.color)
    }

    @Test fun htmlUsesSeparateTagAttributeAndStringColors() {
        val source = "<div class=\"card\">Hello</div>"
        val result = highlightCode(source, "html")
        assertEquals(CodePalette.Tag, colorAt(source, result, "div"))
        assertEquals(CodePalette.Attribute, colorAt(source, result, "class"))
        assertEquals(CodePalette.String, colorAt(source, result, "\"card\""))
    }

    @Test fun unknownLanguageRemainsReadable() {
        val source = "plain words"
        val result = highlightCode(source, "unknown")
        assertTrue(result.spanStyles.all { it.item.color == CodePalette.Text })
    }
}
