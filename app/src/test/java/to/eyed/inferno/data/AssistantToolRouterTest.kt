package to.eyed.inferno.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantToolRouterTest {
    @Test fun modelCanChooseSearchAndLocalTools() {
        val plan = AssistantToolRouter.parse(
            """{"search_queries":["latest Android release","Android 17 release"],"calculation":"2 + 2","current_time":true}"""
        )
        assertEquals(listOf("latest Android release", "Android 17 release"), plan.searchQueries)
        assertEquals("2 + 2", plan.calculation)
        assertTrue(plan.currentTime)
    }

    @Test fun noToolIsTheDefaultForStableGeneralKnowledge() {
        assertFalse(AssistantToolRouter.shouldRouteLocally("Explain how neural networks learn."))
        assertFalse(AssistantToolRouter.shouldRouteLocally("Write a short poem about rain."))
        assertTrue(AssistantToolRouter.shouldRouteLocally("What time is it right now?"))
        assertTrue(AssistantToolRouter.shouldRouteLocally("Get the real time."))
        assertTrue(AssistantToolRouter.shouldRouteLocally("How much is 18 * 24?"))
    }

    @Test fun webPlannerRunsOnlyForLikelyLookups() {
        assertTrue(AssistantToolRouter.shouldConsiderWeb("What is the latest Android release?"))
        assertTrue(AssistantToolRouter.shouldConsiderWeb("Compare the best local LLM apps this month"))
        assertTrue(AssistantToolRouter.shouldConsiderWeb("Show me pictures of a red panda"))
        assertFalse(AssistantToolRouter.shouldConsiderWeb("Explain how a transformer works"))
        assertFalse(AssistantToolRouter.shouldConsiderWeb("Write a Lua function to select a room"))
    }

    @Test fun plannerAcceptsImageSearchAndGeneratedTitle() {
        val plan = AssistantToolRouter.parse(
            """{"search_queries":["current release"],"image_queries":["red panda"],"title":"Current Release"}"""
        )
        assertEquals(listOf("red panda"), plan.imageQueries)
        assertEquals("Current Release", plan.title)
    }

    @Test fun obviousCurrentRequestsHaveFallbackQueries() {
        assertEquals("What is the latest Android release?", AssistantToolRouter.fallbackSearchQuery("What is the latest Android release?"))
        assertNull(AssistantToolRouter.fallbackSearchQuery("Explain a neural network"))
        assertEquals("Show me pictures of a red panda", AssistantToolRouter.fallbackImageQuery("Show me pictures of a red panda"))
    }

    @Test fun calculatorUsesOperatorPrecedenceAndParentheses() {
        assertEquals("14", AssistantToolRouter.calculate("2 + 3 * 4")?.result)
        assertEquals("20", AssistantToolRouter.calculate("(2 + 3) * 4")?.result)
        assertEquals("8", AssistantToolRouter.calculate("2 ^ 3")?.result)
        assertEquals("-3", AssistantToolRouter.calculate("-(8 - 5)")?.result)
    }

    @Test fun calculatorRejectsInvalidAndUnsafeExpressions() {
        assertNull(AssistantToolRouter.calculate("1 / 0"))
        assertNull(AssistantToolRouter.calculate("2 + System.exit(0)"))
        assertNull(AssistantToolRouter.calculate("1 + 2 trailing text"))
    }

    @Test fun toolNotesKeepSourceLinksSeparateFromTheVisibleAnswer() {
        val notes = AssistantToolRouter.notes(
            listOf(WebSearchResult("Example", "https://example.com", "A useful result")),
            AssistantToolRouter.calculate("2 + 2"),
            "Wednesday, 25 November 2026",
            listOf("latest release"),
        )
        assertTrue(notes!!.startsWith(AssistantToolRouter.NOTES_MARKER))
        assertTrue(notes.contains("**Web search**"))
        assertTrue(notes.contains("latest release"))
        assertTrue(notes.contains("[Example](https://example.com)"))
        assertTrue(notes.contains("2 + 2 = 4"))
        assertTrue(notes.contains("Wednesday, 25 November 2026"))
    }
}
