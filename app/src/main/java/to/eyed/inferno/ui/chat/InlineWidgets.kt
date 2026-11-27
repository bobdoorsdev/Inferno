package to.eyed.inferno.ui.chat

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import to.eyed.inferno.data.LocalContextTools
import to.eyed.inferno.data.WeatherSnapshot
import to.eyed.inferno.ui.theme.Ink
import to.eyed.inferno.ui.theme.Radii
import to.eyed.inferno.ui.theme.Typography
import to.eyed.inferno.ui.theme.whiteA
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class InlineBodyPart(val text: String = "", val widget: String? = null, val arguments: String = "")

internal object InlineWidgetMarkup {
    private val token = Regex("""\[\[inferno_widget:(weather|time|calculator|currency|unit)(?::([^\]\r\n]*))?]]""")

    fun split(source: String): List<InlineBodyPart> {
        if (!source.contains("[[inferno_widget:")) return listOf(InlineBodyPart(text = source))
        val out = mutableListOf<InlineBodyPart>()
        var cursor = 0
        token.findAll(source).forEach { match ->
            if (match.range.first > cursor) out += InlineBodyPart(text = source.substring(cursor, match.range.first))
            out += InlineBodyPart(widget = match.groupValues[1], arguments = match.groupValues.getOrElse(2) { "" })
            cursor = match.range.last + 1
        }
        if (cursor < source.length) out += InlineBodyPart(text = source.substring(cursor))
        return out.ifEmpty { listOf(InlineBodyPart(text = source)) }
    }
}

@Composable
internal fun InlineWidget(name: String, arguments: String) {
    when (name) {
        "weather" -> WeatherWidget()
        "time" -> TimeWidget()
        "calculator" -> CalculatorWidget(arguments)
        "currency" -> CurrencyWidget(arguments)
        "unit" -> UnitWidget(arguments)
    }
}

@Composable
private fun WidgetCard(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier.fillMaxWidth()
            .background(Ink.I900, RoundedCornerShape(Radii.row))
            .border(1.dp, whiteA(0.10f), RoundedCornerShape(Radii.row))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = Typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = Ink.I500)
        content()
    }
}

@Composable
private fun TimeWidget() {
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = ZonedDateTime.now()
            delay(30_000)
        }
    }
    WidgetCard("LOCAL TIME") {
        Text(now.format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())), style = Typography.headlineLarge, color = Ink.White)
        Text(now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.getDefault())), style = Typography.bodyMedium, color = Ink.I300)
        Text(now.zone.id, style = Typography.labelSmall, color = Ink.I500)
    }
}

@Composable
private fun WeatherWidget() {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var weather by remember { mutableStateOf<WeatherSnapshot?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) refresh++ else error = "Approximate location permission was not granted."
    }
    LaunchedEffect(refresh) {
        if (LocalContextTools.hasCoarseLocationPermission(context)) {
            loading = true
            error = null
            val result = runCatching { LocalContextTools.weather(context) }
            weather = result.getOrNull()
            error = result.exceptionOrNull()?.message
            loading = false
        }
    }
    WidgetCard("WEATHER · UP TO 16 DAYS") {
        val current = weather
        if (loading) {
            Text("Getting approximate location and forecast…", style = Typography.bodyMedium, color = Ink.I300)
        } else if (current == null) {
            Text(error ?: "Allow approximate location to see the local forecast.", style = Typography.bodyMedium, color = Ink.I300)
            Text(
                if (LocalContextTools.hasCoarseLocationPermission(context)) "Try again" else "Allow approximate location",
                modifier = Modifier.clickable {
                    if (LocalContextTools.hasCoarseLocationPermission(context)) refresh++ else permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                }.padding(vertical = 4.dp),
                style = Typography.labelLarge,
                color = Ink.White,
            )
        } else {
            Text(current.city, style = Typography.titleMedium, color = Ink.White)
            current.days.firstOrNull()?.let { today ->
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("${today.maximum.toInt()}°", style = Typography.headlineLarge, color = Ink.White)
                    Text("H ${today.maximum.toInt()}°  ·  L ${today.minimum.toInt()}°", style = Typography.bodyMedium, color = Ink.I300, modifier = Modifier.padding(bottom = 5.dp))
                }
                Text(LocalContextTools.weatherDescription(today.code), style = Typography.bodyMedium, color = Ink.I300)
            }
            current.days.take(5).forEach { day ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(runCatching { LocalDate.parse(day.date).format(DateTimeFormatter.ofPattern("EEE d", Locale.getDefault())) }.getOrDefault(day.date), Modifier.weight(1f), style = Typography.bodySmall, color = Ink.I300)
                    Text(LocalContextTools.weatherDescription(day.code), Modifier.weight(1.5f), style = Typography.bodySmall, color = Ink.I500, maxLines = 1)
                    Text("${day.minimum.toInt()}° / ${day.maximum.toInt()}°", style = Typography.labelMedium, color = Ink.I100)
                }
            }
            Text("Forecasts beyond 16 days are not available from this provider.", style = Typography.labelSmall, color = Ink.I500)
        }
    }
}

@Composable
private fun CalculatorWidget(arguments: String) {
    val expression = arguments.trim().take(80)
    val result = remember(expression) { runCatching { CalculatorParser(expression).parse() }.getOrNull() }
    WidgetCard("CALCULATOR") {
        Text(expression.ifBlank { "No expression supplied" }, style = Typography.bodyLarge, color = Ink.I300)
        Text(result?.let(::formatNumber) ?: "Unable to evaluate", style = Typography.titleLarge, color = Ink.White)
    }
}

@Composable
private fun CurrencyWidget(arguments: String) {
    val parts = arguments.split(':')
    val amount = parts.getOrNull(0)?.toDoubleOrNull() ?: 1.0
    val base = parts.getOrNull(1)?.uppercase(Locale.ROOT) ?: "USD"
    val quote = parts.getOrNull(2)?.uppercase(Locale.ROOT) ?: "BRL"
    var result by remember(arguments) { mutableStateOf<Double?>(null) }
    var error by remember(arguments) { mutableStateOf<String?>(null) }
    var loading by remember(arguments) { mutableStateOf(true) }
    LaunchedEffect(arguments) {
        loading = true
        val response = runCatching { LocalContextTools.currency(amount, base, quote) }
        result = response.getOrNull()
        error = response.exceptionOrNull()?.message
        loading = false
    }
    WidgetCard("CURRENCY CONVERSION") {
        Text("${formatNumber(amount)} $base → $quote", style = Typography.bodyLarge, color = Ink.I300)
        Text(
            when {
                loading -> "Fetching latest reference rate…"
                result != null -> "${formatNumber(result!!)} $quote"
                else -> error ?: "Rate unavailable"
            },
            style = Typography.titleLarge,
            color = Ink.White,
        )
        Text("Reference exchange rate; not a live trading quote.", style = Typography.labelSmall, color = Ink.I500)
    }
}

@Composable
private fun UnitWidget(arguments: String) {
    val parts = arguments.split(':')
    val value = parts.getOrNull(0)?.toDoubleOrNull() ?: 1.0
    val from = parts.getOrNull(1)?.lowercase(Locale.ROOT) ?: "km"
    val to = parts.getOrNull(2)?.lowercase(Locale.ROOT) ?: "mi"
    val converted = remember(value, from, to) { convertUnit(value, from, to) }
    WidgetCard("UNIT CONVERSION") {
        Text("${formatNumber(value)} $from", style = Typography.bodyLarge, color = Ink.I300)
        Text(converted?.let { "${formatNumber(it)} $to" } ?: "Unsupported unit pair", style = Typography.titleLarge, color = Ink.White)
    }
}

private fun convertUnit(value: Double, from: String, to: String): Double? {
    val result = when ("$from:$to") {
        "km:mi" -> value * 0.621371
        "mi:km" -> value / 0.621371
        "m:ft" -> value * 3.28084
        "ft:m" -> value / 3.28084
        "cm:in" -> value / 2.54
        "in:cm" -> value * 2.54
        "kg:lb" -> value * 2.20462262
        "lb:kg" -> value / 2.20462262
        "l:gal" -> value * 0.264172
        "gal:l" -> value / 0.264172
        "c:f" -> value * 9.0 / 5.0 + 32.0
        "f:c" -> (value - 32.0) * 5.0 / 9.0
        else -> return null
    }
    return result.takeIf { it.isFinite() }
}

private fun formatNumber(value: Double): String =
    String.format(Locale.getDefault(), "%,.2f", value).trimEnd('0').trimEnd(',', '.')

private class CalculatorParser(private val source: String) {
    private var index = 0

    fun parse(): Double {
        require(source.isNotBlank() && source.length <= 80) { "Expression is empty or too long" }
        val value = expression()
        skipSpaces()
        require(index == source.length && value.isFinite()) { "Invalid expression" }
        return value
    }

    private fun expression(): Double {
        var value = term()
        while (true) {
            skipSpaces()
            value = when (peek()) {
                '+' -> { index++; value + term() }
                '-' -> { index++; value - term() }
                else -> return value
            }
        }
    }

    private fun term(): Double {
        var value = factor()
        while (true) {
            skipSpaces()
            value = when (peek()) {
                '*' -> { index++; value * factor() }
                '/' -> { index++; val divisor = factor(); require(divisor != 0.0); value / divisor }
                else -> return value
            }
        }
    }

    private fun factor(): Double {
        skipSpaces()
        if (peek() == '+') { index++; return factor() }
        if (peek() == '-') { index++; return -factor() }
        if (peek() == '(') {
            index++
            val value = expression()
            skipSpaces()
            require(peek() == ')') { "Missing closing parenthesis" }
            index++
            return value
        }
        val start = index
        var dots = 0
        while (index < source.length && (source[index].isDigit() || source[index] == '.')) {
            if (source[index] == '.') dots++
            index++
        }
        require(index > start && dots <= 1) { "Expected a number" }
        return source.substring(start, index).toDouble()
    }

    private fun peek(): Char? = source.getOrNull(index)
    private fun skipSpaces() { while (index < source.length && source[index].isWhitespace()) index++ }
}
