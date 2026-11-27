package to.eyed.inferno.ui.chat

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import to.eyed.inferno.data.AssistantToolRouter
import to.eyed.inferno.data.WebSearchClient
import to.eyed.inferno.ui.theme.Ink
import to.eyed.inferno.ui.theme.Radii
import to.eyed.inferno.ui.theme.Typography
import to.eyed.inferno.ui.theme.whiteA
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

private data class AssistantWidgetSpec(
    val type: String,
    val title: String? = null,
    val location: String? = null,
    val timezone: String? = null,
    val target: String? = null,
    val durationSeconds: Long = 0L,
    val expression: String? = null,
    val request: String? = null,
    val amount: Double? = null,
    val from: String? = null,
    val to: String? = null,
    val label: String? = null,
    val value: Double? = null,
    val max: Double? = null,
    val labels: List<String> = emptyList(),
    val values: List<Double> = emptyList(),
)

private fun parseAssistantWidget(source: String): AssistantWidgetSpec? = runCatching {
    val root = Json.parseToJsonElement(source) as? JsonObject ?: return null
    fun string(key: String) = (root[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
    fun number(key: String) = (root[key] as? JsonPrimitive)?.doubleOrNull
    fun strings(key: String): List<String> =
        (root[key] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf { value -> value.isNotBlank() } }.take(12)
    fun numbers(key: String): List<Double> =
        (root[key] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.doubleOrNull?.takeIf(Double::isFinite) }.take(12)
    val type = string("type")?.lowercase(Locale.ROOT)?.replace('-', '_') ?: return null
    AssistantWidgetSpec(
        type = type,
        title = string("title")?.take(70),
        location = string("location")?.take(100),
        timezone = string("timezone")?.take(80),
        target = string("target")?.take(80),
        durationSeconds = (number("duration_seconds") ?: 0.0).toLong().coerceIn(1, 604_800),
        expression = string("expression")?.take(128),
        request = string("request")?.take(180),
        amount = number("amount")?.takeIf { it.isFinite() && kotlin.math.abs(it) <= 1.0e12 },
        from = string("from")?.uppercase(Locale.ROOT)?.take(3),
        to = string("to")?.uppercase(Locale.ROOT)?.take(3),
        label = string("label")?.take(80),
        value = number("value")?.takeIf(Double::isFinite),
        max = number("max")?.takeIf { it.isFinite() && it > 0.0 },
        labels = strings("labels"),
        values = numbers("values"),
    )
}.getOrNull()

@Composable
internal fun AssistantWidgetPlaceholder(modifier: Modifier = Modifier) {
    WidgetFrame("Preparing widget", modifier) {
        Text("Rendering the live or interactive card…", style = Typography.bodySmall, color = Ink.I500)
    }
}

/** Renders a strict JSON widget block rather than displaying the JSON as a code sample. */
@Composable
internal fun AssistantWidgetBlock(code: String, modifier: Modifier = Modifier) {
    val spec = remember(code) { parseAssistantWidget(code) }
    if (spec == null) {
        WidgetFrame("Widget unavailable", modifier) {
            Text("The widget data was incomplete or invalid.", style = Typography.bodySmall, color = Ink.I500)
        }
        return
    }
    val title = spec.title ?: spec.type.replace('_', ' ').replaceFirstChar { it.uppercase(Locale.ROOT) }
    when (spec.type) {
        "weather" -> CurrentWeatherWidget(spec, title, modifier)
        "clock", "world_clock" -> WorldClockWidget(spec, title, modifier)
        "countdown" -> CountdownWidget(spec, title, modifier)
        "timer" -> TimerWidget(spec, title, modifier)
        "stopwatch" -> StopwatchWidget(title, modifier)
        "calculator" -> CalculatorWidget(spec, title, modifier)
        "unit_converter" -> UnitConverterWidget(spec, title, modifier)
        "currency", "currency_converter" -> CurrencyWidget(spec, title, modifier)
        "date", "date_calculator" -> DateWidget(spec, title, modifier)
        "progress" -> ProgressWidget(spec, title, modifier)
        "bar_chart", "chart" -> BarChartWidget(spec, title, modifier)
        else -> WidgetFrame("Unsupported widget", modifier = modifier) {
            Text("Unknown type: " + spec.type, style = Typography.bodySmall, color = Ink.I500)
        }
    }
}

@Composable
private fun WidgetFrame(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(Radii.row)
    Column(
        modifier.fillMaxWidth().clip(shape).background(Ink.SurfaceHigh)
            .border(1.dp, whiteA(0.09f), shape).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
        content = {
            Text(title, style = Typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = Ink.White)
            content()
        },
    )
}

@Composable
private fun WidgetAction(label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier.clip(RoundedCornerShape(9.dp)).background(Ink.I800)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp),
    ) {
        Text(label, style = Typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = Ink.I100)
    }
}

@Composable
private fun WeatherMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = Typography.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = Ink.I100)
        Text(label, style = Typography.labelSmall, color = Ink.I500)
    }
}

private data class WeatherCardData(
    val city: String,
    val condition: String,
    val temperature: String,
    val feelsLike: String,
    val humidity: String,
    val precipitation: String,
    val wind: String,
)

private fun parseWeatherCardData(raw: String): WeatherCardData? {
    val match = Regex(
        """^(.+?):\s*([^,]+),\s*([^,]+) °C,\s*feels like ([^,]+) °C,\s*humidity ([^,]+)%,\s*precipitation ([^,]+) mm,\s*wind ([^,]+) km/h""",
        RegexOption.IGNORE_CASE,
    ).find(raw) ?: return null
    return WeatherCardData(
        city = match.groupValues[1].substringBefore(',').trim(),
        condition = match.groupValues[2].trim(),
        temperature = match.groupValues[3].trim(),
        feelsLike = match.groupValues[4].trim(),
        humidity = match.groupValues[5].trim() + "%",
        precipitation = match.groupValues[6].trim() + " mm",
        wind = match.groupValues[7].trim() + " km/h",
    )
}

@Composable
private fun CurrentWeatherWidget(spec: AssistantWidgetSpec, title: String, modifier: Modifier) {
    val location = spec.location.orEmpty()
    val client = remember { WebSearchClient() }
    var refreshKey by remember(location) { mutableIntStateOf(0) }
    var raw by remember(location) { mutableStateOf<String?>(null) }
    var updatedAt by remember(location) { mutableLongStateOf(0L) }
    var loading by remember(location) { mutableStateOf(true) }
    LaunchedEffect(location, refreshKey) {
        if (location.isNotBlank()) {
            while (true) {
                loading = raw == null
                raw = runCatching { client.currentWeather(location) }.getOrNull()
                updatedAt = System.currentTimeMillis()
                loading = false
                delay(120_000L)
            }
        }
    }
    val data = remember(raw) { raw?.let(::parseWeatherCardData) }
    WidgetFrame(if (location.isBlank()) title else data?.city ?: location.substringBefore(','), modifier) {
        if (location.isBlank()) {
            Text("Add a city to track current conditions.", style = Typography.bodySmall, color = Ink.I500)
        } else if (data != null) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(data.temperature + "°", style = Typography.titleLarge.copy(fontSize = 36.sp, fontWeight = FontWeight.Medium), color = Ink.White)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(data.condition, style = Typography.bodySmall.copy(fontWeight = FontWeight.Medium), color = Ink.I100)
                    Text("Feels like " + data.feelsLike + "°", style = Typography.labelSmall, color = Ink.I500)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                WeatherMetric("Humidity", data.humidity, Modifier.weight(1f))
                WeatherMetric("Rain", data.precipitation, Modifier.weight(1f))
                WeatherMetric("Wind", data.wind, Modifier.weight(1f))
            }
        } else {
            Text(if (loading) "Loading current conditions…" else raw ?: "Weather data is unavailable.", style = Typography.bodySmall, color = Ink.I500)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (updatedAt == 0L) "Refreshes automatically every 2 minutes" else
                    "Updated " + DateTimeFormatter.ofPattern("HH:mm").format(Instant.ofEpochMilli(updatedAt).atZone(ZoneId.systemDefault())) + " · auto-refresh 2 min",
                modifier = Modifier.weight(1f), style = Typography.labelSmall, color = Ink.I500,
            )
            WidgetAction("Refresh") { refreshKey++ }
        }
    }
}

@Composable
private fun WorldClockWidget(spec: AssistantWidgetSpec, title: String, modifier: Modifier) {
    val zone = remember(spec.timezone) { runCatching { ZoneId.of(spec.timezone ?: ZoneId.systemDefault().id) }.getOrDefault(ZoneId.systemDefault()) }
    val now by produceState(initialValue = ZonedDateTime.now(zone), key1 = zone) {
        while (true) {
            value = ZonedDateTime.now(zone)
            delay(1_000L)
        }
    }
    WidgetFrame(title, modifier) {
        Text(now.format(DateTimeFormatter.ofPattern("HH:mm:ss", Locale.US)), style = Typography.titleLarge.copy(fontSize = 34.sp, fontWeight = FontWeight.Medium), color = Ink.White)
        Text(now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.US)) + " · " + zone.id, style = Typography.labelSmall, color = Ink.I500)
    }
}

private fun parseWidgetTarget(value: String): Instant? =
    runCatching { Instant.parse(value) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()
        ?: runCatching { LocalDate.parse(value).atStartOfDay(ZoneId.systemDefault()).toInstant() }.getOrNull()

@Composable
private fun CountdownWidget(spec: AssistantWidgetSpec, title: String, modifier: Modifier) {
    val target = remember(spec.target) { spec.target?.let(::parseWidgetTarget) }
    val now by produceState(initialValue = Instant.now(), key1 = target) {
        while (true) {
            value = Instant.now()
            delay(1_000L)
        }
    }
    val seconds = target?.let { Duration.between(now, it).seconds.coerceAtLeast(0L) } ?: 0L
    WidgetFrame(title, modifier) {
        if (target == null) {
            Text("Provide a valid ISO date or date/time.", style = Typography.bodySmall, color = Ink.I500)
        } else {
            val days = seconds / 86_400
            val hours = (seconds % 86_400) / 3_600
            val minutes = (seconds % 3_600) / 60
            val secs = seconds % 60
            Text(String.format(Locale.US, "%d d  %02d:%02d:%02d", days, hours, minutes, secs), style = Typography.titleLarge.copy(fontSize = 27.sp, fontWeight = FontWeight.Medium), color = Ink.White)
            Text("Target: " + spec.target, style = Typography.labelSmall, color = Ink.I500)
        }
    }
}

@Composable
private fun TimerWidget(spec: AssistantWidgetSpec, title: String, modifier: Modifier) {
    val duration = spec.durationSeconds.coerceAtLeast(1L)
    var remaining by remember(duration) { mutableLongStateOf(duration) }
    var running by remember { mutableStateOf(false) }
    var endAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(running, endAt) {
        while (running) {
            val left = ((endAt - SystemClock.elapsedRealtime()) / 1_000L).coerceAtLeast(0L)
            remaining = left
            if (left == 0L) {
                running = false
                break
            }
            delay(200L)
        }
    }
    WidgetFrame(title, modifier) {
        Text(String.format(Locale.US, "%02d:%02d:%02d", remaining / 3_600, (remaining % 3_600) / 60, remaining % 60), style = Typography.titleLarge.copy(fontSize = 34.sp), color = Ink.White)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WidgetAction(if (running) "Pause" else "Start") {
                if (running) {
                    remaining = ((endAt - SystemClock.elapsedRealtime()) / 1_000L).coerceAtLeast(0L)
                    running = false
                } else if (remaining > 0L) {
                    endAt = SystemClock.elapsedRealtime() + remaining * 1_000L
                    running = true
                }
            }
            WidgetAction("Reset") { running = false; remaining = duration }
        }
    }
}

@Composable
private fun StopwatchWidget(title: String, modifier: Modifier) {
    var elapsed by remember { mutableLongStateOf(0L) }
    var startedAt by remember { mutableLongStateOf(0L) }
    var running by remember { mutableStateOf(false) }
    LaunchedEffect(running, startedAt) {
        while (running) {
            elapsed = SystemClock.elapsedRealtime() - startedAt
            delay(100L)
        }
    }
    val display = elapsed / 1_000L
    WidgetFrame(title, modifier) {
        Text(String.format(Locale.US, "%02d:%02d.%02d", display / 60, display % 60, (elapsed % 1_000) / 10), style = Typography.titleLarge.copy(fontSize = 34.sp), color = Ink.White)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WidgetAction(if (running) "Pause" else "Start") {
                if (running) {
                    elapsed = SystemClock.elapsedRealtime() - startedAt
                    running = false
                } else {
                    startedAt = SystemClock.elapsedRealtime() - elapsed
                    running = true
                }
            }
            WidgetAction("Reset") { running = false; elapsed = 0L; startedAt = 0L }
        }
    }
}

@Composable
private fun CalculatorWidget(spec: AssistantWidgetSpec, title: String, modifier: Modifier) {
    val expression = spec.expression.orEmpty()
    val result = remember(expression) { AssistantToolRouter.calculate(expression)?.result ?: "Invalid expression" }
    WidgetFrame(title, modifier) {
        Text(expression.ifBlank { "No expression provided" }, style = Typography.bodySmall, color = Ink.I500)
        Text(result, style = Typography.titleLarge.copy(fontSize = 28.sp, fontWeight = FontWeight.SemiBold), color = Ink.White)
    }
}

@Composable
private fun UnitConverterWidget(spec: AssistantWidgetSpec, title: String, modifier: Modifier) {
    val request = spec.request.orEmpty()
    val result = remember(request) { AssistantToolRouter.convertUnits(request) ?: "Could not convert these units" }
    WidgetFrame(title, modifier) {
        Text(request.ifBlank { "No conversion provided" }, style = Typography.bodySmall, color = Ink.I500)
        Text(result, style = Typography.titleLarge.copy(fontSize = 26.sp, fontWeight = FontWeight.SemiBold), color = Ink.White)
    }
}

private val currencyClient by lazy {
    OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS).connectTimeout(5, TimeUnit.SECONDS).build()
}

private suspend fun fetchCurrency(amount: Double, from: String, to: String): String? = withContext(Dispatchers.IO) {
    if (!from.matches(Regex("[A-Z]{3}")) || !to.matches(Regex("[A-Z]{3}"))) return@withContext null
    if (from == to) return@withContext String.format(Locale.US, "%.2f %s", amount, to)
    val url = "https://api.frankfurter.app/latest?amount=" + amount + "&from=" + from + "&to=" + to
    val responseText = currencyClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
        if (!response.isSuccessful) return@withContext null
        response.body?.string().orEmpty()
    }
    val rate = runCatching {
        Json.parseToJsonElement(responseText).jsonObject["rates"]?.jsonObject?.get(to)?.jsonPrimitive?.doubleOrNull
    }.getOrNull() ?: return@withContext null
    String.format(Locale.US, "%.2f %s", rate, to)
}

@Composable
private fun CurrencyWidget(spec: AssistantWidgetSpec, title: String, modifier: Modifier) {
    val amount = spec.amount ?: 1.0
    val from = spec.from ?: "USD"
    val to = spec.to ?: "BRL"
    var result by remember(amount, from, to) { mutableStateOf<String?>(null) }
    var updatedAt by remember(amount, from, to) { mutableLongStateOf(0L) }
    LaunchedEffect(amount, from, to) {
        while (true) {
            result = runCatching { fetchCurrency(amount, from, to) }.getOrNull()
            updatedAt = System.currentTimeMillis()
            delay(3_600_000L)
        }
    }
    WidgetFrame(title, modifier) {
        Text(String.format(Locale.US, "%.2f %s", amount, from), style = Typography.bodySmall, color = Ink.I500)
        Text(result ?: "Fetching current exchange rate…", style = Typography.titleLarge.copy(fontSize = 28.sp, fontWeight = FontWeight.SemiBold), color = Ink.White)
        Text(if (updatedAt == 0L) "Rates refresh periodically" else "Updated " + DateTimeFormatter.ofPattern("HH:mm").format(Instant.ofEpochMilli(updatedAt).atZone(ZoneId.systemDefault())), style = Typography.labelSmall, color = Ink.I500)
    }
}

@Composable
private fun DateWidget(spec: AssistantWidgetSpec, title: String, modifier: Modifier) {
    val request = spec.request.orEmpty()
    val result = remember(request) { AssistantToolRouter.calculateDate(request) ?: "Could not calculate this date" }
    WidgetFrame(title, modifier) {
        Text(request.ifBlank { "No date calculation provided" }, style = Typography.bodySmall, color = Ink.I500)
        Text(result, style = Typography.titleLarge.copy(fontSize = 24.sp, fontWeight = FontWeight.SemiBold), color = Ink.White)
    }
}

@Composable
private fun ProgressWidget(spec: AssistantWidgetSpec, title: String, modifier: Modifier) {
    val value = spec.value ?: 0.0
    val max = spec.max ?: 100.0
    val ratio = (value / max).toFloat().coerceIn(0f, 1f)
    WidgetFrame(title, modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(spec.label ?: "Progress", modifier = Modifier.weight(1f), style = Typography.bodySmall, color = Ink.I100)
            Text(String.format(Locale.US, "%.0f%%", ratio * 100f), style = Typography.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = Ink.White)
        }
        Box(Modifier.fillMaxWidth().height(9.dp).clip(RoundedCornerShape(8.dp)).background(Ink.I800)) {
            Box(Modifier.fillMaxWidth(ratio).fillMaxHeight().background(Ink.White))
        }
        Text(String.format(Locale.US, "%.2f / %.2f", value, max), style = Typography.labelSmall, color = Ink.I500)
    }
}

@Composable
private fun BarChartWidget(spec: AssistantWidgetSpec, title: String, modifier: Modifier) {
    val items = spec.labels.zip(spec.values).take(8)
    val maxValue = items.maxOfOrNull { kotlin.math.abs(it.second) }?.coerceAtLeast(1.0) ?: 1.0
    WidgetFrame(title, modifier) {
        if (items.isEmpty()) {
            Text("Add labels and numeric values to display a chart.", style = Typography.bodySmall, color = Ink.I500)
        } else {
            items.forEach { (label, value) ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(label, modifier = Modifier.width(76.dp), style = Typography.labelSmall, color = Ink.I300, maxLines = 1)
                    Box(Modifier.weight(1f).height(9.dp).clip(RoundedCornerShape(8.dp)).background(Ink.I800)) {
                        Box(Modifier.fillMaxWidth((kotlin.math.abs(value) / maxValue).toFloat()).fillMaxHeight().background(Ink.White))
                    }
                    Text(String.format(Locale.US, "%.1f", value), style = Typography.labelSmall, color = Ink.I500)
                }
            }
        }
    }
}
