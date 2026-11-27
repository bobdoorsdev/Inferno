package to.eyed.inferno.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalToolCommandParserTest {
    @Test fun parsesOnlyReservedCommandAtStart() {
        val parsed = LocalToolCommandParser.parse("""<inferno:calculator expr="(12+5)*3"/>""")
        assertNotNull(parsed)
        assertEquals("calculator", parsed?.name)
        assertEquals("(12+5)*3", parsed?.arguments?.get("expr"))
        assertNull(LocalToolCommandParser.parse("""Use <inferno:time/> as an example."""))
    }

    @Test fun ordinaryHtmlIsNeverACommand() {
        val html = """<html><body><h1>Hello</h1></body></html>"""
        assertNull(LocalToolCommandParser.parse(html))
        assertEquals(html, LocalToolCommandParser.visibleText(html))
    }

    @Test fun partialCommandIsHiddenUntilItCanBeParsed() {
        assertEquals("", LocalToolCommandParser.visibleText("<inferno:wea"))
        assertEquals("", LocalToolCommandParser.visibleText("<inferno:weather/>The forecast is sunny."))
    }

    @Test fun removesLegacyWidgetSyntaxWithoutRenderingAWidget() {
        assertEquals("Temperature today is 22°C.", LocalToolCommandParser.visibleText("Temperature today is 22°C. [[inferno_widget:weather]]"))
    }

    @Test fun greetingCannotTriggerTimeOrWeather() {
        val time = LocalToolCommand("time", emptyMap())
        val weather = LocalToolCommand("weather", emptyMap())
        assertFalse(LocalToolCommandParser.isExplicitlyRequested("Hi", time))
        assertFalse(LocalToolCommandParser.isExplicitlyRequested("Hi", weather))
    }

    @Test fun explicitTimeAndWeatherRequestsAreAllowed() {
        assertTrue(LocalToolCommandParser.isExplicitlyRequested("What time is it?", LocalToolCommand("time", emptyMap())))
        assertTrue(LocalToolCommandParser.isExplicitlyRequested("What is the weather today?", LocalToolCommand("weather", emptyMap())))
    }

    @Test fun htmlCodeRequestCannotInvokeLocalTools() {
        val command = LocalToolCommand("weather", emptyMap())
        assertFalse(LocalToolCommandParser.isExplicitlyRequested("I asked for a simple HTML code example", command))
    }

    @Test fun locationRequiresAnExplicitLocationQuestion() {
        val command = LocalToolCommand("location", emptyMap())
        assertTrue(LocalToolCommandParser.isExplicitlyRequested("Where am I right now?", command))
        assertFalse(LocalToolCommandParser.isExplicitlyRequested("Hi, how are you?", command))
    }
}
