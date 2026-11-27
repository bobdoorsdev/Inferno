package to.eyed.inferno.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantToolRouterTest {
    @Test fun modelCanChooseSearchAndLocalTools() {
        val plan = AssistantToolRouter.parse(
            """{"decision":"search","search_queries":["latest Android release","Android 17 release"],"calculation":"2 + 2","current_time":true}"""
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

    @Test fun plannerAcceptsModelSelectedSearchOrAnswerDecisions() {
        val stable = AssistantToolRouter.parseStrict("""{"decision":"answer"}""")
        assertEquals("answer", stable?.decision)
        assertTrue(stable?.searchQueries.isNullOrEmpty())

        val current = AssistantToolRouter.parseStrict(
            """{"decision":"search","search_queries":["2026 FIFA World Cup winner official result"]}"""
        )
        assertEquals("search", current?.decision)
        assertEquals(listOf("2026 FIFA World Cup winner official result"), current?.searchQueries)
    }

    @Test fun strictPlannerRejectsProseScriptsAndInvalidDecisions() {
        assertNull(AssistantToolRouter.parseStrict("I cannot access the internet."))
        assertNull(AssistantToolRouter.parseStrict("local results = {}"))
        assertNull(AssistantToolRouter.parseStrict("""{"decision":"maybe"}"""))
        val malformedOptional = AssistantToolRouter.parseStrict("""{"decision":"search","search_queries":{}}""")
        assertEquals("search", malformedOptional?.decision)
        assertTrue(malformedOptional?.searchQueries.isNullOrEmpty())
    }

    @Test fun plannerAcceptsImageSearchAndGeneratedTitle() {
        val plan = AssistantToolRouter.parse(
            """{"decision":"answer","search_queries":["current release"],"image_queries":["red panda"],"title":"Current Release"}"""
        )
        assertEquals(listOf("red panda"), plan.imageQueries)
        assertEquals("Current Release", plan.title)
    }

    @Test fun imageSearchCanBeRequestedByTheModelWithoutKeywordFallbacks() {
        val plan = AssistantToolRouter.parseStrict(
            """{"decision":"search","search_queries":["best gaming PCs 2026"],"image_queries":["desktop gaming PCs"]}"""
        )
        assertEquals("search", plan?.decision)
        assertEquals(listOf("best gaming PCs 2026"), plan?.searchQueries)
        assertEquals(listOf("desktop gaming PCs"), plan?.imageQueries)
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
        assertFalse(answer.contains("[Image source]"))
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

    @Test fun everyFirstMessageGetsAFallbackTitleIncludingGreetings() {
        assertEquals("Hi", ChatRepository.titleFrom("hi"))
        assertEquals("Hello", ChatRepository.titleFrom("Hello!"))
        assertEquals("Hi, can you help with Roblox", ChatRepository.titleFrom("Hi, can you help with Roblox?"))
        assertTrue(ChatRepository.isTitleWorthy("Qwen model comparison"))
        assertFalse(ChatRepository.isTitleWorthy("Hello"))
    }

    @Test fun modelInventedUtilitiesAreRejectedUnlessTheUserAskedForThem() {
        val invented = AssistantToolPlan(
            unitConversion = "25 C to F",
            calculation = "25 * 2",
            currentTime = true,
        )
        val rejected = AssistantToolRouter.validateForRequest(invented, "Explain why the sky is blue.")
        assertNull(rejected.unitConversion)
        assertNull(rejected.calculation)
        assertFalse(rejected.currentTime)

        val explicit = AssistantToolRouter.validateForRequest(invented, "Convert 25 C to F.")
        assertEquals("25 C to F", explicit.unitConversion)
        assertNull(explicit.calculation)
        assertFalse(explicit.currentTime)
    }

    @Test fun responseLanguageFollowsTheLatestUserMessageAndExplicitCodeLanguage() {
        assertTrue(AssistantToolRouter.responseLanguageInstruction("Please fix this code and explain what changed.").contains("exclusively in English"))
        val htmlPrompt = AssistantToolRouter.responseLanguageInstruction("Create an HTML page in English")
        assertTrue(htmlPrompt.contains("English"))
        assertTrue(htmlPrompt.contains("every user-facing string"))
        assertTrue(AssistantToolRouter.responseLanguageInstruction("Por favor, explique como isso funciona para mim.").contains("Brazilian Portuguese"))
        assertTrue(AssistantToolRouter.responseLanguageInstruction("ho").contains("exclusively in English"))
        assertTrue(AssistantToolRouter.responseLanguageInstruction("ho").contains("Do not infer or mention the user's city"))
    }

    @Test fun explicitImageSearchIsForcedForDirectPictureRequests() {
        assertEquals("2026 FIFA World Cup", AssistantToolRouter.explicitImageSearchQuery("Please search images of 2026 FIFA World Cup"))
        assertEquals("the 2026 World Cup final", AssistantToolRouter.explicitImageSearchQuery("Find photos of the 2026 World Cup final"))
        assertNull(AssistantToolRouter.explicitImageSearchQuery("Generate an image of the 2026 World Cup"))
        assertNull(AssistantToolRouter.explicitImageSearchQuery("Explain how image search works"))
    }


    @Test fun productRecommendationsAutomaticallyRequestProductPhotos() {
        assertTrue(AssistantToolRouter.automaticProductImageSearchQuery("Find the best prebuilt gaming PC for 2026")!!.contains("product photos"))
        assertTrue(AssistantToolRouter.automaticProductImageSearchQuery("Compare the best laptops under $900")!!.contains("product photos"))
        assertNull(AssistantToolRouter.automaticProductImageSearchQuery("Explain how GPUs work"))
    }

    @Test fun nativeWidgetContractExposesAtLeastTenRenderableWidgetTypes() {
        val instructions = AssistantToolRouter.widgetInstructions()
        val types = listOf("weather", "clock", "countdown", "timer", "stopwatch", "calculator", "unit_converter", "currency", "date", "progress", "bar_chart")
        assertTrue(types.all { instructions.contains("\"type\":\"$it\"") })
    }

    @Test fun imageFallbackCanPlaceSeveralImagesInline() {
        val images = (1..6).map { index ->
            WebImageResult("Gaming PC $index", "https://images.example/pc$index.jpg", "https://source.example/pc$index")
        }
        val answer = AssistantToolRouter.enrichAnswer("Compare these six prebuilt gaming PCs.", emptyList(), images)
        assertEquals(6, Regex("!\\[").findAll(answer).count())
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
        assertTrue(notes.contains("[Source page](https://store.example/product)"))
        assertFalse(notes.contains("gaming PCs"))
    }

    @Test fun weatherLocationComesOnlyFromTheCityTheUserActuallyTyped() {
        assertEquals("Piumhi", AssistantToolRouter.explicitWeatherLocation("What's the current weather in Piumhi?"))
        assertEquals("London", AssistantToolRouter.explicitWeatherLocation("What's the weather for London?"))
        assertEquals("Rio de Janeiro", AssistantToolRouter.explicitWeatherLocation("Is it raining near Rio de Janeiro right now?"))
        assertNull(AssistantToolRouter.explicitWeatherLocation("What's the weather today?"))
        assertNull(AssistantToolRouter.explicitWeatherLocation("What's the weather like near me?"))

        val guessed = AssistantToolPlan(weatherLocation = "Piumhi")
        assertNull(AssistantToolRouter.validateForRequest(guessed, "What's the weather today?").weatherLocation)
        assertEquals("Piumhi", AssistantToolRouter.validateForRequest(guessed, "What's the weather in Piumhi?").weatherLocation)
    }

    @Test fun currentWeatherAlwaysGetsALiveWidgetInsteadOfDependingOnModelFormatting() {
        val answer = AssistantToolRouter.ensureWeatherWidget("Current conditions are available.", "Piumhi")
        assertTrue(answer.contains(96.toChar().toString().repeat(3) + "inferno-widget"))
        assertTrue(answer.contains("type"))
        assertTrue(answer.contains("Piumhi"))
        assertEquals(answer, AssistantToolRouter.ensureWeatherWidget(answer, "Piumhi"))
        assertEquals("No location required.", AssistantToolRouter.ensureWeatherWidget("No location required.", null))
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

    @Test fun simulatedSearchOutputIsRejectedAndFallsBackToRealSources() {
        assertTrue(AssistantToolRouter.looksLikeSimulatedSearchOutput("local results = {} -- http.get('duckduckgo.com')"))
        assertTrue(AssistantToolRouter.looksLikeSimulatedSearchOutput("This is a simulation of a web search."))
        assertFalse(AssistantToolRouter.looksLikeSimulatedSearchOutput("The search returned three independent sources [1] [2] [3]."))
        val fallback = AssistantToolRouter.rawSearchFallback(
            listOf(WebSearchResult("Real source", "https://example.com/real", "A verified excerpt.")),
            listOf("best gaming PCs"),
            "Search completed",
        )
        assertTrue(fallback.contains("**[Real source](https://example.com/real)**"))
        assertFalse(fallback.contains("[1]"))
        assertTrue(fallback.contains("A verified excerpt."))
    }

    @Test fun toolNotesKeepSourceLinksSeparateFromTheVisibleAnswerAndHidePlannerQueries() {
        val notes = AssistantToolRouter.notes(
            listOf(WebSearchResult("Example", "https://example.com", "A useful result")),
            AssistantToolRouter.calculate("2 + 2"),
            "Wednesday, 25 November 2026",
            listOf("latest release"),
        )
        assertTrue(notes!!.startsWith(AssistantToolRouter.NOTES_MARKER))
        assertFalse(notes.contains("**Web search**"))
        assertFalse(notes.contains("latest release"))
        assertFalse(Regex("""(?m)^\d+\.""").containsMatchIn(notes))
        assertTrue(notes.contains("**[Example](https://example.com)** · example.com"))
        assertTrue(notes.contains("2 + 2 = 4"))
        assertFalse(notes.contains("Wednesday, 25 November 2026"))
    }
}
