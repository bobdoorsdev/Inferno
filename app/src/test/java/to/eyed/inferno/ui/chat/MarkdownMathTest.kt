package to.eyed.inferno.ui.chat

import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownMathTest {
    @Test fun inlineDollarMathIsFormatted() {
        val source = "Area: " + '$' + "x^2" + '$'
        assertTrue(MarkdownMath.format(source).contains("x²"))
    }

    @Test fun latexFractionsAndDisplayDelimitersAreFormatted() {
        val source = "Result: " + '$' + """\frac{1}{2}""" + '$'
        val formatted = MarkdownMath.format(source)
        assertTrue(formatted.contains("1"))
        assertTrue(formatted.contains("2"))
        assertTrue(!formatted.contains('$'))
    }

    @Test fun fencedCodeKeepsDollarStringsUntouched() {
        val fence = "\u0060\u0060\u0060"
        val source = fence + "kotlin\nval price = " + '"' + '$' + "25" + '"' + "\n" + fence +
            "\nMath: " + '$' + "x^2" + '$'
        val formatted = MarkdownMath.format(source)
        assertTrue(formatted.contains("val price = " + '"' + '$' + "25" + '"'))
        assertTrue(formatted.contains("x²"))
    }
}
