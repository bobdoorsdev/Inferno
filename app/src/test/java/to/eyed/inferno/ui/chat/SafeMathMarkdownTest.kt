package to.eyed.inferno.ui.chat

import org.junit.Assert.assertEquals
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
        val source = "\u0060\u0060\u0060tex\n\\(x+1\\)\n\u0060\u0060\u0060"
        assertTrue(SafeMathMarkdown.sanitize(source).contains("\\(x+1\\)"))
    }

    @Test fun safelyDisplaysAnUnclosedInlineFormulaDuringStreaming() {
        val safe = SafeMathMarkdown.sanitize("Estimate: \\(\\frac{1}{2}")
        assertTrue(safe.contains("\u0060\\frac{1}{2}\u0060"))
    }

    
    @Test fun formatsCommonLatexIdentifiersAsReadableUnicode() {
        val formatted = MarkdownMath.format("Value: $\\alpha + \\beta = \\frac{1}{2}$")
        assertTrue(formatted.contains("α + β"))
        assertTrue(formatted.contains("(1)⁄(2)"))
        assertFalse(formatted.contains("$"))
    }

    @Test fun formatsStyledNumberSetsAndCalculusSymbols() {
        val formatted = MarkdownMath.format("""$\mathbb{R} \to \mathcal{A}, \int_0^1 x^2 \, dx$""")
        assertTrue(formatted.contains("ℝ"))
        assertTrue(formatted.contains("→"))
        assertTrue(formatted.contains("𝒜"))
        assertTrue(formatted.contains("∫"))
        assertTrue(formatted.contains("x²"))
    }

    @Test fun leavesMathLookingTextInsideCodeFencesUntouched() {
        val source = "\u0060\u0060\u0060tex\n$\\alpha + \\beta$\n\u0060\u0060\u0060"
        assertEquals(source, MarkdownMath.format(source))
    }

    @Test fun boundsVeryLargeMathInputWithoutThrowing() {
        val source = "x".repeat(300_001)
        assertEquals(source, MarkdownMath.format(source))
    }

    @Test fun nestedQuadraticFormulaRendersAsReadableUnicode() {
        val source = """The solutions are \(x = \frac{-b \pm \sqrt{b^2 - 4ac}}{2a}\)."""
        val formatted = MarkdownMath.format(source)
        assertTrue(formatted.contains("x = (-b ± √(b² - 4ac))⁄(2a)"))
        assertFalse(formatted.contains("\\frac"))
        assertFalse(formatted.contains("\\sqrt"))
    }

    @Test fun formatsTheFullQuadraticWorkedExampleWithoutBlockQuoteArtifacts() {
        val source = "Given \$\$x^2 + 3x - 5 = 0\$\$, use \$\$x = \\frac{-b \\pm \\sqrt{b^2 - 4ac}}{2a}\$\$."
        val formatted = MarkdownMath.format(source)
        assertTrue(formatted.contains("x² + 3x - 5 = 0"))
        assertTrue(formatted.contains("x = (-b ± √(b² - 4ac))⁄(2a)"))
        assertFalse(formatted.contains("> **"))
        assertFalse(formatted.contains("\\frac"))
    }

    @Test fun formatsBareMathIdentifiersAndRemovesStrayDollarMarkers() {
        val formatted = MarkdownMath.format("The equation is $x^2 and x_1, with braces x^{3} and x_{2}.")
        assertTrue(formatted.contains("x²"))
        assertTrue(formatted.contains("x₁"))
        assertTrue(formatted.contains("x³"))
        assertTrue(formatted.contains("x₂"))
        assertFalse(formatted.contains("$"))
    }

    @Test fun rendersGreekIdentifiersAndSubscriptsInTheSameExpression() {
        val formatted = MarkdownMath.format("""\(\Delta = b^2 - 4ac,\quad x_1, x_2\)""")
        assertTrue(formatted.contains("Δ = b² - 4ac"))
        assertTrue(formatted.contains("x₁, x₂"))
    }
}
