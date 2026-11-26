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

    @Test fun stableExplanationsDoNotTriggerSearchButCurrentLookupsDo() {
        assertFalse(AssistantToolRouter.shouldConsiderWeb("What is the quadratic formula?"))
        assertFalse(AssistantToolRouter.shouldConsiderWeb("Explain how gravity works"))
        assertTrue(AssistantToolRouter.shouldConsiderWeb("Search for the official Qwen model documentation"))
        assertTrue(AssistantToolRouter.shouldConsiderWeb("Is this GPU still supported?"))
        assertEquals("Qwen model documentation", AssistantToolRouter.fallbackSearchQuery("Search for Qwen model documentation"))
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

    @Test fun localUnitConverterHandlesTemperatureLengthAndStorage() {
        assertEquals("77 °F", AssistantToolRouter.convertUnits("25 C to F"))
        assertEquals("1000 m", AssistantToolRouter.convertUnits("1 km to m"))
        assertEquals("1 m", AssistantToolRouter.convertUnits("100 cm to m"))
        assertEquals("1 GiB", AssistantToolRouter.convertUnits("1024 MiB to GiB"))
        assertTrue(AssistantToolRouter.shouldRouteLocally("100 cm to m"))
    }

    @Test fun localDateCalculatorHandlesDifferencesAndOffsets() {
        assertTrue(AssistantToolRouter.calculateDate("days between 2026-01-01 and 2026-01-03")!!.startsWith("2 days"))
        assertTrue(AssistantToolRouter.calculateDate("2026-01-01 + 7 days")!!.startsWith("2026-01-08"))
        assertNull(AssistantToolRouter.calculateDate("tomorrow"))
        assertTrue(AssistantToolRouter.shouldRouteLocally("2026-01-01 + 7 days"))
    }

    @Test fun localJsonToolValidatesFormatsAndMinifies() {
        assertTrue(AssistantToolRouter.processJson("validate", """{"ok":true}""").startsWith("Valid JSON"))
        assertTrue(AssistantToolRouter.processJson("format", """{"a":1}""").contains("\"a\": 1"))
        assertEquals("""{"a":1}""", AssistantToolRouter.processJson("minify", """{ "a" : 1 }"""))
        assertTrue(AssistantToolRouter.processJson("format", "{bad").startsWith("Invalid JSON"))
    }

    @Test fun localRegexTesterReturnsBoundedMatches() {
        assertTrue(AssistantToolRouter.testRegex("""[0-9]+""", "a12 b3").contains("2 match(es)"))
        assertTrue(AssistantToolRouter.testRegex("(", "test").startsWith("Regex error:"))
        assertTrue(AssistantToolRouter.testRegex("""(a+)+""", "aaaa").startsWith("Regex error:"))
    }

    @Test fun localColorConverterReturnsHexRgbAndHsl() {
        val orange = AssistantToolRouter.convertColor("#FF6600")!!
        assertTrue(orange.contains("#FF6600"))
        assertTrue(orange.contains("RGB(255, 102, 0)"))
        assertTrue(orange.contains("HSL(24, 100%, 50%)"))
        assertTrue(orange.contains("HSV(24°, 100%, 100%)"))
        assertTrue(AssistantToolRouter.convertColor("hsl(120, 100%, 50%)")!!.contains("#00FF00"))
        assertTrue(AssistantToolRouter.convertColor("hsv(240, 100%, 100%)")!!.contains("#0000FF"))
        assertTrue(AssistantToolRouter.shouldRouteLocally("HSV(240, 100%, 100%) to RGB"))
        assertNull(AssistantToolRouter.convertColor("not a color"))
    }

    @Test fun imageSearchFallbackPlacesImagesBesideTheMatchingAnswerParagraph() {
        val answer = "For a gaming PC, compare the GPU, CPU and cooling carefully.\n\nA good keyboard is a separate purchase."
        val result = AssistantToolRouter.enrichAnswer(
            answer,
            emptyList(),
            listOf(WebImageResult("Gaming PC", "https://images.example/gaming-pc.jpg", "https://source.example/pc")),
        )
        assertTrue(result.indexOf("gaming pc", ignoreCase = true) < result.indexOf("![Gaming PC]"))
        assertTrue(result.indexOf("![Gaming PC]") < result.indexOf("A good keyboard"))
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
    @Test fun plannerChoosesSourceCountAndExecutableUtilities() {
        val plan = AssistantToolRouter.parse(
            """{"search_queries":["best gaming PC Brazil under budget"],"max_sources":9,"image_queries":["gaming PC product photo"],"base64_action":"encode","base64_text":"Inferno","hash_algorithm":"SHA-256","hash_text":"abc","csv_action":"stats","csv_text":"name,value\nGPU,1","read_url":"https://example.com"}"""
        )
        assertEquals(9, plan.maxSources)
        assertEquals(listOf("best gaming PC Brazil under budget"), plan.searchQueries)
        assertEquals(listOf("gaming PC product photo"), plan.imageQueries)
        assertEquals("encode", plan.base64Action)
        assertEquals("SHA-256", plan.hashAlgorithm)
        assertEquals("stats", plan.csvAction)
        assertEquals("https://example.com", plan.readUrl)
    }

    @Test fun newLocalToolsExecuteCorrectly() {
        assertEquals("SW5mZXJubw==", AssistantToolRouter.processBase64("encode", "Inferno"))
        assertEquals("Inferno", AssistantToolRouter.processBase64("decode", "SW5mZXJubw=="))
        assertTrue(AssistantToolRouter.processBase64("decode", "!!!").startsWith("Base64 error:"))
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            AssistantToolRouter.calculateHash("SHA-256", "abc"),
        )
        assertTrue(AssistantToolRouter.processCsv("validate", "name,value\nGPU,1").contains("2 rows, 2 columns"))
        assertTrue(AssistantToolRouter.processCsv("stats", "name,value\nGPU,1").contains("4 non-empty cells"))
        assertEquals("name,value\nGPU,\"fast, quiet\"", AssistantToolRouter.processCsv("format", "name,value\nGPU,\"fast, quiet\""))
        assertTrue(AssistantToolRouter.processCsv("validate", "name,desc\nGPU,\"broken").startsWith("CSV error:"))
    }

    @Test fun approximateCityIsOnlyRequestedForExplicitLocalContext() {
        assertTrue(AssistantToolRouter.needsApproximateCity("Find coffee shops near me"))
        assertTrue(AssistantToolRouter.needsApproximateCity("What is the weather here?"))
        assertFalse(AssistantToolRouter.needsApproximateCity("What is the weather in São Paulo?"))
        assertFalse(AssistantToolRouter.needsApproximateCity("Explain how weather forecasts work"))
    }

    @Test fun citationEnrichmentLeavesCodeFenceReferencesUntouched() {
        val source = WebSearchResult("Example source", "https://example.com/page", "Evidence")
        val fence = 96.toChar().toString().repeat(3)
        val answer = "Code:\n" + fence + "lua\nlocal values = { [1] = true }\n" + fence + "\nClaim [1]"
        val enriched = AssistantToolRouter.enrichAnswer(answer, listOf(source), emptyList())
        assertTrue(enriched.contains("local values = { [1] = true }"))
        assertTrue(enriched.contains("Claim [1](https://example.com/page)"))
    }

    @Test fun sourcePanelHidesQueryListAndNumberingButRendersWebsiteFavicon() {
        val notes = AssistantToolRouter.searchNotes(
            listOf(WebSearchResult("Official product page", "https://example.com/product", "Product specification")),
            listOf("internal planner query that must stay hidden"),
        )!!
        assertTrue(notes.contains("![__favicon__](https://www.google.com/s2/favicons?domain=example.com&sz=64)"))
        assertTrue(notes.contains("**[Official product page](https://example.com/product)**"))
        assertFalse(notes.contains("internal planner query"))
        assertFalse(Regex("""(?m)^\s*\d+[.)]\s""").containsMatchIn(notes))
    }

}
