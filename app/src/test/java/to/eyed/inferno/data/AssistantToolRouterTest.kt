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

    @Test fun productRecommendationsAlsoRequestImages() {
        assertEquals("Best gaming PCs online", AssistantToolRouter.fallbackImageQuery("Best gaming PCs online"))
        assertEquals("Compare the best monitors", AssistantToolRouter.fallbackImageQuery("Compare the best monitors"))
        assertNull(AssistantToolRouter.fallbackImageQuery("Explain how GPUs work"))
        assertEquals("Free royalty-free images of the World Cup", AssistantToolRouter.fallbackImageQuery("Free royalty-free images of the World Cup"))
    }

    @Test fun finalAnswerMakesCitationsClickableAndActuallyShowsFoundImages() {
        val answer = AssistantToolRouter.enrichAnswer(
            "These sources support the summary [1] and [2].",
            listOf(
                WebSearchResult("One", "https://one.example/page", ""),
                WebSearchResult("Two", "https://two.example/page", ""),
            ),
            listOf(WebImageResult("World Cup", "https://images.example/cup.jpg", "https://source.example/cup")),
        )
        assertTrue(answer.contains("[1](https://one.example/page)"))
        assertTrue(answer.contains("[2](https://two.example/page)"))
        assertTrue(answer.contains("![World Cup](https://images.example/cup.jpg)"))
        assertTrue(answer.contains("[Image source](https://source.example/cup)"))
    }

    @Test fun removesCitationNumbersWithoutMatchingSources() {
        val answer = AssistantToolRouter.enrichAnswer("Unsupported markers [1] and [9].", emptyList(), emptyList())
        assertFalse(answer.contains("[1]"))
        assertFalse(answer.contains("[9]"))
    }

    @Test fun resolvesCitationsAttachedToWordsAndGroupedReferences() {
        val answer = AssistantToolRouter.enrichAnswer(
            "Claim[1] follows from these pages [1, 2], but this one is missing[9].",
            listOf(
                WebSearchResult("One", "https://one.example", ""),
                WebSearchResult("Two", "https://two.example", ""),
            ),
            emptyList(),
        )
        assertTrue(answer.contains("Claim[1](https://one.example)"))
        assertTrue(answer.contains("[1](https://one.example), [2](https://two.example)"))
        assertFalse(answer.contains("[9]"))
    }

    @Test fun greetingOnlyMessagesDoNotBecomeChatTitles() {
        assertEquals("", ChatRepository.titleFrom("hi"))
        assertEquals("", ChatRepository.titleFrom("Hello!"))
        assertEquals("help with Roblox", ChatRepository.titleFrom("Hi, can you help with Roblox?"))
        assertTrue(ChatRepository.isTitleWorthy("Qwen model comparison"))
        assertFalse(ChatRepository.isTitleWorthy("Hello"))
    }

    @Test fun preservesImagesAlreadyEmbeddedAsMarkdown() {
        val answer = AssistantToolRouter.enrichAnswer(
            "![Already embedded](https://images.example/cup.jpg)",
            emptyList(),
            listOf(WebImageResult("World Cup", "https://images.example/cup.jpg", "https://source.example/cup")),
        )
        assertEquals(1, answer.windowed(2).count { it == "![" })
    }

    @Test fun searchImageMarkdownKeepsAnOriginalImageFallback() {
        val notes = AssistantToolRouter.searchNotes(
            emptyList(), listOf("gaming PCs"),
            listOf(WebImageResult("Gaming PC", "https://thumb.example/image.jpg", "https://store.example/product", "https://cdn.example/full.jpg")),
        )
        assertTrue(notes!!.contains("https://thumb.example/image.jpg"))
        assertTrue(notes.contains("\"https://cdn.example/full.jpg\""))
    }

    @Test fun weatherLocationIsParsedForThePrivateWeatherTool() {
        val plan = AssistantToolRouter.parse(
            """{"weather_location":"São Paulo, Brazil","current_time":true}"""
        )
        assertEquals("São Paulo, Brazil", plan.weatherLocation)
        assertTrue(plan.currentTime)
        assertEquals("Piumhi", AssistantToolRouter.fallbackWeatherLocation("What is the weather in Piumhi?"))
        assertNull(AssistantToolRouter.fallbackWeatherLocation("Explain how weather systems work"))
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
        assertTrue(notes.contains("**[Example](https://example.com)** · example.com"))
        assertTrue(notes.contains("2 + 2 = 4"))
        assertFalse(notes.contains("Wednesday, 25 November 2026"))
    }
}
