package to.eyed.inferno.ui.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeMathMarkdownTest {
    @Test fun convertsInlineAndDisplayMathOutsideCodeFences() {
        val source = "Value \\(x^2 + 1\\)\n\n$$\ny = mx + b\n$$"
        val safe = SafeMathMarkdown.sanitize(source)
        assertTrue(safe.contains("\u0060x^2 + 1\u0060"))
        assertTrue(safe.contains("\u0060\u0060\u0060math"))
        assertFalse(safe.contains("\\("))
    }

    @Test fun leavesFencedCodeUntouched() {
        val source = "\u0060\u0060\u0060tex\n\\\\(x+1\\\\)\n\u0060\u0060\u0060"
        assertTrue(SafeMathMarkdown.sanitize(source).contains("\\\\(x+1\\\\)"))
    }

    @Test fun safelyDisplaysAnUnclosedInlineFormulaDuringStreaming() {
        val safe = SafeMathMarkdown.sanitize("Estimate: \\\\(\\\\frac{1}{2}")
        assertTrue(safe.contains("\u0060\\\\frac{1}{2}\u0060"))
    }
}
