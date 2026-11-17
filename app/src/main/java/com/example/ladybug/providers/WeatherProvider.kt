package com.example.ladybug.providers

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.example.ladybug.model.DailyForecast
import com.example.ladybug.model.HourlyForecast
import com.example.ladybug.model.SearchResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap

class WeatherProvider(private val context: Context) : SearchProvider {

    // Matches "weather", "meteo", or "météo" optionally followed by a city name
    private val weatherRegex = Regex("^(?i)(?:weather|meteo|météo)(?:\\s+(.*))?$")

    private data class GeoLocation(
        val lat: Double,
        val lon: Double,
        val cityName: String
    )

    private data class CachedForecast(
        val timestamp: Long,
        val dailyForecasts: List<DailyForecast>
    )

    companion object {
        private const val PREFS_NAME = "weather_cache"
        private const val KEY_LAST_LAT = "last_lat"
        private const val KEY_LAST_LON = "last_lon"
        private const val KEY_LAST_CITY = "last_city"
        private const val FORECAST_TTL_MILLIS = 15 * 60 * 1000L // 15 minutes

        private val geocodeCache = ConcurrentHashMap<String, GeoLocation>()
        private val forecastCache = ConcurrentHashMap<String, CachedForecast>()
    }

    override suspend fun performSearch(query: String): SearchResult {
        val cleanQuery = query.trim()
        // Fast guard clause BEFORE switching to Dispatchers.IO
        val match = weatherRegex.find(cleanQuery) ?: return SearchResult.NoMatch
        val cityInput = match.groupValues[1].trim()
        val geoCacheKey = cityInput.normalizeForSearch()

        return withContext(Dispatchers.IO) {
            try {
                val cachedGeo = geocodeCache[geoCacheKey]
                val now = System.currentTimeMillis()
                if (cachedGeo != null) {
                    val coordKey = formatCoordKey(cachedGeo.lat, cachedGeo.lon)
                    val cachedForecast = forecastCache[coordKey]
                    if (cachedForecast != null && (now - cachedForecast.timestamp <= FORECAST_TTL_MILLIS)) {
                        return@withContext SearchResult.Weather(cachedGeo.cityName, cachedForecast.dailyForecasts)
                    }
                }

                delay(300)

                // 1. DETERMINE LOCATION (with in-memory geocode cache & SharedPreferences IP fallback)
                val location = cachedGeo ?: resolveLocation(cityInput)?.also { resolved ->
                    geocodeCache[geoCacheKey] = resolved
                } ?: return@withContext SearchResult.NoMatch

                val coordKey = formatCoordKey(location.lat, location.lon)
                val freshCachedForecast = forecastCache[coordKey]
                if (freshCachedForecast != null && (System.currentTimeMillis() - freshCachedForecast.timestamp <= FORECAST_TTL_MILLIS)) {
                    return@withContext SearchResult.Weather(location.cityName, freshCachedForecast.dailyForecasts)
                }

                // 2. FETCH FORECAST (using lightweight /compact endpoint)
                val weatherUrl = "https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=${location.lat}&lon=${location.lon}"
                val weatherData = fetchJson(weatherUrl) ?: return@withContext SearchResult.NoMatch

                val timeseries = weatherData.getJSONObject("properties").getJSONArray("timeseries")
                if (timeseries.length() == 0) return@withContext SearchResult.NoMatch

                val dailyMap = LinkedHashMap<String, MutableList<HourlyForecast>>()
                val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val dayLabelFormat = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
                val hourFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                val dateCompareFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

                val currentMillis = System.currentTimeMillis()
                val todayDateStr = dateCompareFormat.format(currentMillis)

                // Parse Every Hour
                for (i in 0 until timeseries.length()) {
                    val hourObj = timeseries.getJSONObject(i)
                    val date = isoFormat.parse(hourObj.getString("time")) ?: continue

                    // STRICT FILTER: Drop past hours
                    if (date.time < currentMillis - 3600000) continue

                    val itemDateStr = dateCompareFormat.format(date)
                    val dayLabel = if (itemDateStr == todayDateStr) "Today" else dayLabelFormat.format(date)

                    val hData = hourObj.getJSONObject("data")
                    val hDetails = hData.getJSONObject("instant").getJSONObject("details")

                    val hTemp = hDetails.getDouble("air_temperature")
                    val hWind = hDetails.optDouble("wind_speed", 0.0)
                    val hHum = hDetails.optDouble("relative_humidity", 0.0)

                    // Safe cascading search for the precipitation block
                    val next1 = hData.optJSONObject("next_1_hours")
                    val next6 = hData.optJSONObject("next_6_hours")
                    val next12 = hData.optJSONObject("next_12_hours")

                    val periodObj = next1 ?: next6 ?: next12
                    val hSymbol = periodObj?.optJSONObject("summary")?.optString("symbol_code", "unknown") ?: "unknown"

                    val precipAmount = next1?.optJSONObject("details")?.optDouble("precipitation_amount", 0.0)
                        ?: next6?.optJSONObject("details")?.optDouble("precipitation_amount", 0.0)
                        ?: next12?.optJSONObject("details")?.optDouble("precipitation_amount", 0.0)
                        ?: 0.0

                    val dayHours = dailyMap.getOrPut(dayLabel) { mutableListOf() }
                    dayHours.add(
                        HourlyForecast(hourFormat.format(date), hTemp, precipAmount, hWind, hHum, hSymbol)
                    )
                }

                // Aggregate Hours into Daily summaries
                val dailyForecasts = dailyMap.map { (dateLabel, hours) ->
                    val minT = hours.minOf { it.temp }
                    val maxT = hours.maxOf { it.temp }
                    val maxWind = hours.maxOf { it.windSpeed }
                    val totalPrecip = hours.sumOf { it.precipitationAmount }
                    val avgHum = hours.map { it.humidity }.average().let { if (it.isNaN()) 0.0 else it }
                    val midDaySymbol = hours.find { it.time.startsWith("12") }?.symbolCode ?: hours.first().symbolCode

                    DailyForecast(
                        dateLabel, minT, maxT, midDaySymbol, maxWind, avgHum, totalPrecip, hours
                    )
                }.take(7)

                if (dailyForecasts.isNotEmpty()) {
                    forecastCache[coordKey] = CachedForecast(System.currentTimeMillis(), dailyForecasts)
                }

                SearchResult.Weather(location.cityName, dailyForecasts)

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("WeatherDebug", "Weather fetch failed", e)
                SearchResult.NoMatch
            }
        }
    }

    private fun resolveLocation(cityInput: String): GeoLocation? {
        if (cityInput.isEmpty()) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val ipData = fetchJson("https://ipapi.co/json/")
            if (ipData != null && ipData.has("latitude") && ipData.has("longitude")) {
                val lat = ipData.getDouble("latitude")
                val lon = ipData.getDouble("longitude")
                val city = ipData.optString("city", "Local").ifBlank { "Local" }
                prefs.edit {
                    putString(KEY_LAST_LAT, lat.toString())
                    putString(KEY_LAST_LON, lon.toString())
                    putString(KEY_LAST_CITY, city)
                }
                return GeoLocation(lat, lon, city)
            }

            // Fallback to last known location if ipapi.co is rate-limited or blocked by VPN
            val savedLat = prefs.getString(KEY_LAST_LAT, null)?.toDoubleOrNull()
            val savedLon = prefs.getString(KEY_LAST_LON, null)?.toDoubleOrNull()
            val savedCity = prefs.getString(KEY_LAST_CITY, null)
            if (savedLat != null && savedLon != null && !savedCity.isNullOrBlank()) {
                return GeoLocation(savedLat, savedLon, savedCity)
            }
            return null
        } else {
            val encodedCity = URLEncoder.encode(cityInput, "UTF-8")
            val geoArray = fetchJsonArray("https://nominatim.openstreetmap.org/search?q=$encodedCity&format=json&limit=1")
            if (geoArray == null || geoArray.length() == 0) return null

            val geoData = geoArray.getJSONObject(0)
            val lat = geoData.getString("lat").toDouble()
            val lon = geoData.getString("lon").toDouble()
            val finalCityName = geoData.getString("display_name").split(",").first()
            return GeoLocation(lat, lon, finalCityName)
        }
    }

    private fun formatCoordKey(lat: Double, lon: Double): String =
        String.format(Locale.US, "%.2f,%.2f", lat, lon)

    // Helper function to safely fetch and parse JSON Objects
    private fun fetchJson(urlString: String): JSONObject? {
        try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            // yr.no requires a custom User-Agent to avoid 403 Forbidden errors
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android 14; Mobile; rv:130.0) Gecko/130.0 Firefox/130.0")
            connection.connectTimeout = 4000
            connection.readTimeout = 4000

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                return JSONObject(response)
            }
        } catch (_: Exception) {}
        return null
    }

    // Helper function to safely fetch and parse JSON Arrays (used by Nominatim geocoder)
    private fun fetchJsonArray(urlString: String): JSONArray? {
        try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android 14; Mobile; rv:130.0) Gecko/130.0 Firefox/130.0")
            connection.connectTimeout = 4000
            connection.readTimeout = 4000

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                return JSONArray(response)
            }
        } catch (_: Exception) {}
        return null
    }
}
