package to.eyed.inferno.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeMathMarkdownTest {
    @Test fun normalizesInlineAndDisplayMathDelimiters() {
        val source = "Value \\\\(x^2 + 1\\\\)\\n\\\\[\\ny = mx + b\\n\\\\]"
        val normalized = SafeMathMarkdown.normalize(source)
        assertTrue(normalized.contains("Value $" + "x^2 + 1$")) 
        assertTrue(normalized.contains("$$\\ny = mx + b\\n$$"))
        assertTrue(SafeMathMarkdown.containsMath(source))
    }

    @Test fun leavesFencedCodeUntouched() {
        val source = "```tex\\n\\\\(x+1\\\\)\\n```"
        assertEquals(source, SafeMathMarkdown.normalize(source))
        assertFalse(SafeMathMarkdown.containsMath(source))
    }

    @Test fun wrapsBareLatexCommandsAsInlineMath() {
        assertEquals("Angle: $" + "\\\\delta$", SafeMathMarkdown.normalize("Angle: \\\\delta"))
        assertTrue(SafeMathMarkdown.containsMath("\\\\delta"))
    }

    @Test fun leavesAnIncompleteStreamingDelimiterUntouched() {
        val source = "Estimate: \\\\(\\\\frac{1}{2}"
        assertEquals(source, SafeMathMarkdown.normalize(source))
    }
}
