package to.eyed.inferno.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.coroutines.resume

data class LocalToolCommand(
    val name: String,
    val arguments: Map<String, String>,
)

/**
 * Tool calls use one reserved self-closing command at the very beginning of the answer.
 * An incomplete "<inferno:" prefix stays hidden until it is either completed or becomes ordinary
 * text. Normal HTML/XML such as "<html>" is rendered normally and never triggers a tool.
 */
object LocalToolCommandParser {
    private const val PREFIX = "<inferno:"
    private val command = Regex(
        """^\s*<inferno:(time|location|weather|calculator|convert|date|timezone|percentage)(?:\s+([^>]*?))?\s*/>"""
    )
    private val attribute = Regex("""([A-Za-z][A-Za-z0-9_-]*)\s*=\s*"([^"]*)"""")

    fun parse(source: String): LocalToolCommand? {
        val match = command.find(source) ?: return null
        val args = linkedMapOf<String, String>()
        attribute.findAll(match.groupValues.getOrElse(2) { "" }).forEach { item ->
            args[item.groupValues[1]] = item.groupValues[2]
                .replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")
                .replace("&lt;", "<").replace("&gt;", ">")
        }
        return LocalToolCommand(match.groupValues[1], args)
    }

    fun isExplicitlyRequested(userText: String, command: LocalToolCommand): Boolean {
        val prompt = userText.lowercase(Locale.ROOT)
        fun matches(pattern: String) = Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(prompt)
        // A request for HTML/scripts/programming must never invoke local context tools,
        // but merely mentioning an app must not suppress a normal time/weather question.
        val codeGeneration = matches("""\b(html|javascript|python|luau|programming|xml|json)\b""") ||
            matches("""\b(write|create|make|generate|build|draft|asked|request|show|give)\b.{0,80}\b(code|script|website|webpage|program|app)\b""")
        if (codeGeneration) return false
        return when (command.name) {
            "time" -> matches("""\b(what time|what is the time|time is it|current time|local time|what's the time|que horas|qual e a hora|horas sao|horario agora|current date|today's date|what date|data de hoje|que dia e hoje)\b""")
            "location" -> matches("""\b(where am i|my location|current location|where do i live|what city am i|where is my phone|minha localizacao|onde estou|onde eu moro|qual cidade estou)\b""")
            "weather" -> matches("""\b(weather|forecast|previsao do tempo|clima|temperatura hoje)\b""")
            "calculator" -> matches("""\b(calculate|calculator|compute|solve this|calcule|calcular|quanto e|quanto da|resolva|resolver|solve)\b""") ||
                Regex("""\d+(?:\.\d+)?\s*[+*/%-]\s*\d+""").containsMatchIn(prompt)
            "convert" -> matches("""\b(convert|conversion|converta|converter|quanto vale|exchange rate|convertir|converter para)\b""") ||
                matches("""\b\d+(?:\.\d+)?\s*(?:km|mi|m|ft|cm|in|kg|lb|g|oz|l|gal)\s+(?:to|in)\s+[a-z]{2,8}\b""")
            "date" -> matches("""\b(days between|how many days|what day of the week|day of week|date calculation|quantos dias|que dia da semana|calcular datas)\b""")
            "timezone" -> matches("""\b(time in|timezone|time zone|fuso horario|hora em)\b""")
            "percentage" -> matches("""\b(percent|percentage|por cento|porcentagem|percentage of)\b""")
            else -> false
        }
    }

    fun visibleText(source: String): String {
        val first = source.indexOfFirst { !it.isWhitespace() }
        if (first < 0) return ""
        val tail = source.substring(first)
        if ((tail.length < PREFIX.length && PREFIX.startsWith(tail)) ||
            (tail.startsWith(PREFIX) && !tail.contains("/>"))
        ) return ""
        // Hold all text behind an intercepted command until the tool result is available.
        // This prevents an unverified guessed time/weather sentence from appearing before the tool runs.
        if (command.find(source) != null) return ""
        return stripLeadingThought(source)
            .replace(Regex("""\[\[inferno_widget:[^\]]*]]"""), "")
    }

    private fun stripLeadingThought(source: String): String {
        val leading = source.trimStart()
        if (leading.startsWith("<think>")) {
            val close = leading.indexOf("</think>")
            return if (close < 0) "" else leading.substring(close + "</think>".length).trimStart()
        }
        return source
    }
}

data class WeatherDay(val date: String, val code: Int, val minimum: Double, val maximum: Double, val precipitationChance: Int?)
data class WeatherSnapshot(val city: String, val timezone: String, val days: List<WeatherDay>)
data class ApproximatePlace(val city: String, internal val latitude: Double, internal val longitude: Double)

/** On-demand local context plus five utility tools. Only COARSE location permission is requested. */
object LocalContextTools {
    fun hasCoarseLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun currentTime(): String = ZonedDateTime.now().format(
        DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy, HH:mm z", Locale.getDefault())
    )

    suspend fun runTool(context: Context, command: LocalToolCommand): String = when (command.name) {
        "time" -> "Local date and time: ${currentTime()}."
        "location" -> {
            requireCoarsePermission(context)
            val place = approximatePlace(context)
                ?: throw IllegalStateException("Approximate location is unavailable. Check that Location is enabled.")
            "Approximate city-level location: ${place.city}. No street address or precise coordinates are shared."
        }
        "weather" -> {
            requireCoarsePermission(context)
            val data = weather(context)
            buildString {
                append("10-day weather forecast for ${data.city} (${data.timezone}).")
                append(" ${data.days.size} forecast days are available.")
                data.days.forEach { day ->
                    append("\n${day.date}: ${weatherDescription(day.code)}, low ${day.minimum.toInt()}°C, high ${day.maximum.toInt()}°C")
                    day.precipitationChance?.let { append(", precipitation chance ${it}%") }
                }
            }
        }
        "calculator" -> {
            val expression = command.arguments["expr"].orEmpty()
            "Calculation: $expression = ${formatNumber(Calculator(expression).parse())}."
        }
        "convert" -> {
            val value = command.arguments["value"]?.toDoubleOrNull()
                ?: throw IllegalArgumentException("A numeric value is required.")
            val from = command.arguments["from"]?.trim().orEmpty()
            val to = command.arguments["to"]?.trim().orEmpty()
            require(from.isNotBlank() && to.isNotBlank()) { "Source and target units are required." }
            convert(value, from, to)
        }
        "date" -> dateCalculation(command.arguments)
        "timezone" -> timezone(command.arguments["zone"].orEmpty())
        "percentage" -> percentage(command.arguments)
        else -> throw IllegalArgumentException("Unknown local tool.")
    }

    private fun requireCoarsePermission(context: Context) {
        check(hasCoarseLocationPermission(context)) {
            "Approximate location permission is not enabled. Enable it in Android app permissions before asking for local weather or location."
        }
    }

    suspend fun weather(context: Context): WeatherSnapshot {
        requireCoarsePermission(context)
        val place = approximatePlace(context)
            ?: throw IllegalStateException("Could not resolve an approximate city. Check that Location is enabled.")
        val url = "https://api.open-meteo.com/v1/forecast?latitude=${place.latitude}&longitude=${place.longitude}" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max&forecast_days=10&timezone=auto"
        val json = withContext(Dispatchers.IO) { getJson(url) }
        val daily = json.getJSONObject("daily")
        val dates = daily.getJSONArray("time")
        val codes = daily.getJSONArray("weather_code")
        val highs = daily.getJSONArray("temperature_2m_max")
        val lows = daily.getJSONArray("temperature_2m_min")
        val rain = daily.optJSONArray("precipitation_probability_max")
        val days = (0 until dates.length()).mapNotNull { index ->
            if (highs.isNull(index) || lows.isNull(index) || codes.isNull(index)) return@mapNotNull null
            WeatherDay(
                dates.getString(index), codes.getInt(index), lows.getDouble(index), highs.getDouble(index),
                rain?.takeIf { index < it.length() && !it.isNull(index) }?.optInt(index),
            )
        }
        return WeatherSnapshot(place.city, json.optString("timezone", "local time"), days)
    }

    suspend fun approximatePlace(context: Context): ApproximatePlace? {
        if (!hasCoarseLocationPermission(context)) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val provider = runCatching {
            when {
                manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                else -> return null
            }
        }.getOrNull() ?: return null
        val fresh = withTimeoutOrNull(8_000L) {
            suspendCancellableCoroutine<Location?> { continuation ->
                val cancellation = CancellationSignal()
                continuation.invokeOnCancellation { cancellation.cancel() }
                try {
                    manager.getCurrentLocation(provider, cancellation, context.mainExecutor) { location ->
                        if (continuation.isActive) continuation.resume(location)
                    }
                } catch (_: SecurityException) {
                    if (continuation.isActive) continuation.resume(null)
                } catch (_: IllegalArgumentException) {
                    if (continuation.isActive) continuation.resume(null)
                }
            }
        }
        val location = fresh ?: runCatching { manager.getLastKnownLocation(provider) }.getOrNull() ?: return null
        val now = System.currentTimeMillis()
        if (location.time > 0 && now - location.time > 6 * 60 * 60 * 1000L) return null
        val city = withContext(Dispatchers.IO) {
            runCatching {
                if (!Geocoder.isPresent()) null
                else Geocoder(context, Locale.getDefault()).getFromLocation(location.latitude, location.longitude, 1)
                    ?.firstOrNull()?.let { address ->
                        listOfNotNull(address.locality, address.subAdminArea, address.adminArea, address.countryName)
                            .distinct().joinToString(", ")
                    }?.takeIf { it.isNotBlank() }
            }.getOrNull()
        } ?: return null
        return ApproximatePlace(city, location.latitude, location.longitude)
    }

    private suspend fun convert(value: Double, from: String, to: String): String {
        val source = from.lowercase(Locale.ROOT)
        val target = to.lowercase(Locale.ROOT)
        if (source.matches(Regex("[a-z]{3}")) && target.matches(Regex("[a-z]{3}"))) {
            val rate = withContext(Dispatchers.IO) {
                getJson("https://api.frankfurter.dev/v2/rate/${source.uppercase(Locale.ROOT)}/${target.uppercase(Locale.ROOT)}")
            }.getDouble("rate")
            return "Currency conversion: ${formatNumber(value)} ${source.uppercase(Locale.ROOT)} = ${formatNumber(value * rate)} ${target.uppercase(Locale.ROOT)}. Reference exchange rate."
        }
        val converted = when ("$source:$target") {
            "km:mi" -> value * 0.621371
            "mi:km" -> value / 0.621371
            "m:ft" -> value * 3.28084
            "ft:m" -> value / 3.28084
            "cm:in" -> value / 2.54
            "in:cm" -> value * 2.54
            "kg:lb" -> value * 2.20462262
            "lb:kg" -> value / 2.20462262
            "g:oz" -> value / 28.349523125
            "oz:g" -> value * 28.349523125
            "l:gal" -> value * 0.264172
            "gal:l" -> value / 0.264172
            "ml:fl_oz" -> value / 29.5735296
            "c:f", "°c:°f" -> value * 9.0 / 5.0 + 32.0
            "f:c", "°f:°c" -> (value - 32.0) * 5.0 / 9.0
            else -> throw IllegalArgumentException("Unsupported conversion: $from to $to.")
        }
        return "Unit conversion: ${formatNumber(value)} $from = ${formatNumber(converted)} $to."
    }

    private fun dateCalculation(args: Map<String, String>): String {
        val target = args["date"]?.takeIf { it.isNotBlank() }?.let(LocalDate::parse) ?: LocalDate.now()
        val today = LocalDate.now()
        val distance = ChronoUnit.DAYS.between(today, target)
        val distanceText = when {
            distance == 0L -> "today"
            distance > 0 -> "in $distance days"
            else -> "${-distance} days ago"
        }
        val other = args["other"]?.takeIf { it.isNotBlank() }?.let(LocalDate::parse)
        return if (other != null) {
            "There are ${kotlin.math.abs(ChronoUnit.DAYS.between(target, other))} days between $target and $other."
        } else {
            "$target is a ${target.dayOfWeek.name.lowercase().replaceFirstChar { it.titlecase() }} ($distanceText)."
        }
    }

    private fun timezone(zoneName: String): String {
        require(zoneName.isNotBlank()) { "Provide an IANA time-zone name, such as America/Sao_Paulo." }
        val zone = ZoneId.of(zoneName)
        val now = ZonedDateTime.now(zone)
        return "Current time in ${zone.id}: ${now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy, HH:mm z", Locale.getDefault()))}."
    }

    private fun percentage(args: Map<String, String>): String {
        val value = args["value"]?.toDoubleOrNull() ?: throw IllegalArgumentException("A numeric value is required.")
        val percent = args["percent"]?.toDoubleOrNull() ?: throw IllegalArgumentException("A percentage is required.")
        val operation = args["operation"].orEmpty().ifBlank { "of" }
        val result = when (operation) {
            "of" -> value * percent / 100.0
            "increase" -> value * (1.0 + percent / 100.0)
            "decrease" -> value * (1.0 - percent / 100.0)
            else -> throw IllegalArgumentException("Use percentage operation of, increase, or decrease.")
        }
        return when (operation) {
            "of" -> "$percent% of $value = ${formatNumber(result)}."
            "increase" -> "$value increased by $percent% = ${formatNumber(result)}."
            else -> "$value decreased by $percent% = ${formatNumber(result)}."
        }
    }

    private class Calculator(private val source: String) {
        private var index = 0
        fun parse(): Double {
            require(source.isNotBlank() && source.length <= 120) { "Expression is empty or too long." }
            val value = expression()
            spaces()
            require(index == source.length && value.isFinite()) { "Invalid expression." }
            return value
        }
        private fun expression(): Double {
            var value = term()
            while (true) {
                spaces()
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
                spaces()
                value = when (peek()) {
                    '*' -> { index++; value * factor() }
                    '/' -> { index++; val divisor = factor(); require(divisor != 0.0) { "Division by zero." }; value / divisor }
                    '%' -> { index++; val divisor = factor(); require(divisor != 0.0) { "Division by zero." }; value % divisor }
                    else -> return value
                }
            }
        }
        private fun factor(): Double {
            spaces()
            if (peek() == '+') { index++; return factor() }
            if (peek() == '-') { index++; return -factor() }
            if (peek() == '(') {
                index++
                val value = expression()
                spaces()
                require(peek() == ')') { "Missing closing parenthesis." }
                index++
                return value
            }
            val start = index
            var dots = 0
            while (index < source.length && (source[index].isDigit() || source[index] == '.')) {
                if (source[index] == '.') dots++
                index++
            }
            require(index > start && dots <= 1) { "Expected a number." }
            return source.substring(start, index).toDouble()
        }
        private fun peek(): Char? = source.getOrNull(index)
        private fun spaces() { while (index < source.length && source[index].isWhitespace()) index++ }
    }

    private fun formatNumber(value: Double): String =
        String.format(Locale.US, "%,.4f", value).trimEnd('0').trimEnd('.')

    private fun getJson(address: String): JSONObject {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5_000
            readTimeout = 7_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Inferno-Android")
        }
        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) throw IllegalStateException("Data service returned HTTP $status.")
            return JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    fun weatherDescription(code: Int): String = when (code) {
        0 -> "Clear"
        1 -> "Mostly clear"
        2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Fog"
        51, 53, 55 -> "Drizzle"
        56, 57 -> "Freezing drizzle"
        61, 63, 65 -> "Rain"
        66, 67 -> "Freezing rain"
        71, 73, 75, 77 -> "Snow"
        80, 81, 82 -> "Rain showers"
        85, 86 -> "Snow showers"
        95 -> "Thunderstorm"
        96, 99 -> "Thunderstorm with hail"
        else -> "Conditions unavailable"
    }
}