package to.eyed.inferno.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownBodyTest {
    @Test fun repairsHeadingsWithoutRequiredWhitespace() {
        assertEquals("#### Title", normalizeMarkdownForRenderer("####Title"))
        assertEquals("###### Heading", normalizeMarkdownForRenderer("######Heading"))
    }

    @Test fun currencyAmountsAreNotConvertedIntoMath() {
        val expected = "Prices: " + '\\' + '$' + "5 and " + '\\' + '$' + "10"
        assertEquals(expected, normalizeMarkdownForRenderer("Prices: \$5 and \$10"))
    }

    @Test fun numberLeadingMathIsNotMistakenForCurrency() {
        assertEquals("\$5 + x\$", normalizeMarkdownForRenderer("\$5 + x\$"))
        assertEquals("\$\$5 + x\$\$", normalizeMarkdownForRenderer("\$\$5 + x\$\$"))
    }

    @Test fun mathInsideInlineAndFencedCodeIsPreservedLiterally() {
        val fence = "\u0060\u0060\u0060kotlin"
        val source = listOf(
            "Inline \u0060\$x\$\u0060",
            fence,
            "val price = \"\$5\"",
            "val formula = \"\$x\$\"",
            "\u0060\u0060\u0060",
        ).joinToString("\n")
        assertEquals(source, normalizeMarkdownForRenderer(source))
    }

    @Test fun unfinishedMarkdownAndMathDelimitersAreLeftUntouched() {
        assertEquals("**unfinished emphasis", normalizeMarkdownForRenderer("**unfinished emphasis"))
        assertEquals("Formula: \$x", normalizeMarkdownForRenderer("Formula: \$x"))
        assertTrue(normalizeMarkdownForRenderer("Formula: \\(x + y\\)").contains("\\(x + y\\)"))
    }

    @Test fun typingRevealAdvancesGraduallyAndCatchesUpWithoutSkippingCharacters() {
        assertEquals(1, nextTypingRevealLength(0, 1))
        assertEquals(2, nextTypingRevealLength(0, 2))
        assertEquals(2, nextTypingRevealLength(0, 13))
        assertEquals(4, nextTypingRevealLength(0, 33))
        assertEquals(8, nextTypingRevealLength(0, 97))
        assertEquals(104, nextTypingRevealLength(100, 140))
        assertEquals(140, nextTypingRevealLength(140, 140))
    }
}
