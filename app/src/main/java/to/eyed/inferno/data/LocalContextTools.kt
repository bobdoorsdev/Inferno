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
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.coroutines.resume

data class WeatherDay(
    val date: String,
    val code: Int,
    val minimum: Double,
    val maximum: Double,
    val precipitationChance: Int?,
)

data class WeatherSnapshot(
    val city: String,
    val timezone: String,
    val days: List<WeatherDay>,
)

data class ApproximatePlace(
    val city: String,
    internal val latitude: Double,
    internal val longitude: Double,
)

/**
 * Only invoked by an explicit model tool marker or a rendered widget. It requests coarse permission only;
 * no address, street, or precise coordinates are sent to the model.
 */
object LocalContextTools {
    fun hasCoarseLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun currentTime(): String = ZonedDateTime.now().format(
        DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy, HH:mm z", Locale.getDefault())
    )

    suspend fun runTool(context: Context, name: String): String = when (name) {
        "time" -> "Current local date and time: ${currentTime()}."
        "location" -> {
            val place = approximatePlace(context)
            if (place == null) "Approximate location is unavailable. The user can enable the optional approximate-location permission from the weather widget."
            else "Approximate current city: ${place.city}. This is city-level location only; no street address is available or shared."
        }
        "weather" -> {
            val data = weather(context)
            buildString {
                append("Weather forecast for ${data.city} (${data.timezone}).")
                append(" Forecast dates: ${data.days.firstOrNull()?.date ?: "unavailable"} to ${data.days.lastOrNull()?.date ?: "unavailable"}.")
                append(" The provider supplies up to 16 forecast days, not 20; do not invent a longer range.")
                data.days.forEach { day ->
                    append("\n${day.date}: ${weatherDescription(day.code)}, low ${day.minimum.toInt()}°C, high ${day.maximum.toInt()}°C")
                    day.precipitationChance?.let { append(", precipitation chance ${it}%") }
                }
            }
        }
        else -> "Unknown local tool."
    }

    suspend fun weather(context: Context): WeatherSnapshot {
        val place = approximatePlace(context)
            ?: throw IllegalStateException("Approximate location permission is needed. Ask the user to tap Allow approximate location on the weather widget.")
        val url = "https://api.open-meteo.com/v1/forecast?latitude=${place.latitude}&longitude=${place.longitude}" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max&forecast_days=16&timezone=auto"
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
                date = dates.getString(index),
                code = codes.getInt(index),
                minimum = lows.getDouble(index),
                maximum = highs.getDouble(index),
                precipitationChance = rain?.takeIf { index < it.length() && !it.isNull(index) }?.optInt(index),
            )
        }
        return WeatherSnapshot(place.city, json.optString("timezone", "local time"), days)
    }

    suspend fun currency(amount: Double, base: String, quote: String): Double {
        val from = base.uppercase(Locale.ROOT).takeIf { it.matches(Regex("[A-Z]{3}")) }
            ?: throw IllegalArgumentException("Use a three-letter currency code.")
        val to = quote.uppercase(Locale.ROOT).takeIf { it.matches(Regex("[A-Z]{3}")) }
            ?: throw IllegalArgumentException("Use a three-letter currency code.")
        val json = withContext(Dispatchers.IO) {
            getJson("https://api.frankfurter.dev/v2/rate/$from/$to")
        }
        return amount * json.getDouble("rate")
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
            if (status !in 200..299) throw IllegalStateException("Weather service returned HTTP $status.")
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
