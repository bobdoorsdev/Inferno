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
}
