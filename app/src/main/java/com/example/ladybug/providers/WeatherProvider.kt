package com.example.ladybug.providers

import android.util.Log
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

class WeatherProvider : SearchProvider {

    // Matches "weather", "meteo", or "météo" optionally followed by a city name
    private val weatherRegex = Regex("^(?i)(?:weather|meteo|météo)\\s*(.*)$")

    override suspend fun performSearch(query: String): SearchResult = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        val match = weatherRegex.find(cleanQuery) ?: return@withContext SearchResult.NoMatch

        try {
            delay(300)

            val cityInput = match.groupValues[1].trim()
            var lat: Double
            var lon: Double
            var finalCityName: String

            // 1. DETERMINE LOCATION
            if (cityInput.isEmpty()) {
                // THE FIX: Swapped to a free HTTPS service to bypass Android's security block
                val ipData = fetchJson("https://ipapi.co/json/") ?: return@withContext SearchResult.NoMatch

                // Note: The JSON keys for this specific API are slightly different
                lat = ipData.getDouble("latitude")
                lon = ipData.getDouble("longitude")
                finalCityName = ipData.getString("city")
            } else {
                val encodedCity = URLEncoder.encode(cityInput, "UTF-8")
                val geoArray = fetchJsonArray("https://nominatim.openstreetmap.org/search?q=$encodedCity&format=json&limit=1")
                if (geoArray == null || geoArray.length() == 0) return@withContext SearchResult.NoMatch

                val geoData = geoArray.getJSONObject(0)
                lat = geoData.getString("lat").toDouble()
                lon = geoData.getString("lon").toDouble()
                finalCityName = geoData.getString("display_name").split(",").first()
            }

            val weatherUrl = "https://api.met.no/weatherapi/locationforecast/2.0/complete?lat=$lat&lon=$lon"
            val weatherData = fetchJson(weatherUrl) ?: return@withContext SearchResult.NoMatch

            val timeseries = weatherData.getJSONObject("properties").getJSONArray("timeseries")
            if (timeseries.length() == 0) return@withContext SearchResult.NoMatch

            val dailyMap = mutableMapOf<String, MutableList<HourlyForecast>>()
            val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            val dayLabelFormat = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
            val hourFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

            val now = System.currentTimeMillis()
            val todayDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(now)

            // Parse Every Hour
            for (i in 0 until timeseries.length()) {
                val hourObj = timeseries.getJSONObject(i)
                val date = isoFormat.parse(hourObj.getString("time")) ?: continue

                // STRICT FILTER: Drop past hours
                if (date.time < now - 3600000) continue

                val itemDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date)
                val dayLabel = if (itemDateStr == todayDateStr) "Today" else dayLabelFormat.format(date)

                val hData = hourObj.getJSONObject("data")
                val hDetails = hData.getJSONObject("instant").getJSONObject("details")

                val hTemp = hDetails.getDouble("air_temperature")
                val hWind = hDetails.optDouble("wind_speed", 0.0)
                val hHum = hDetails.optDouble("relative_humidity", 0.0)

                // -----------------------------------------------------------------
                // THE FIX: Safe cascading search for the precipitation block
                // -----------------------------------------------------------------
                val next1 = hData.optJSONObject("next_1_hours")
                val next6 = hData.optJSONObject("next_6_hours")
                val next12 = hData.optJSONObject("next_12_hours")

                val periodObj = next1 ?: next6 ?: next12
                val hSymbol = periodObj?.optJSONObject("summary")?.optString("symbol_code", "unknown") ?: "unknown"

                val precipAmount = next1?.optJSONObject("details")?.optDouble("precipitation_amount", 0.0)
                    ?: next6?.optJSONObject("details")?.optDouble("precipitation_amount", 0.0)
                    ?: next12?.optJSONObject("details")?.optDouble("precipitation_amount", 0.0)
                    ?: 0.0

                if (!dailyMap.containsKey(dayLabel)) dailyMap[dayLabel] = mutableListOf()

                dailyMap[dayLabel]!!.add(
                    HourlyForecast(hourFormat.format(date), hTemp, precipAmount, hWind, hHum, hSymbol)
                )
            }

            // Aggregate Hours into Daily summaries
            val dailyForecasts = dailyMap.map { (dateLabel, hours) ->
                val minT = hours.minOf { it.temp }
                val maxT = hours.maxOf { it.temp }
                val maxWind = hours.maxOf { it.windSpeed }

                // For a Daily summary, we SUM the hourly amounts
                val totalPrecip = hours.sumOf { it.precipitationAmount }

                val avgHum = hours.map { it.humidity }.average().let { if (it.isNaN()) 0.0 else it }
                val midDaySymbol = hours.find { it.time.startsWith("12") }?.symbolCode ?: hours.first().symbolCode

                DailyForecast(
                    dateLabel, minT, maxT, midDaySymbol, maxWind, avgHum, totalPrecip, hours
                )
            }.take(7)

            SearchResult.Weather(finalCityName, dailyForecasts)

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("WeatherDebug", "Weather fetch failed", e)
            SearchResult.NoMatch
        }
    }

    // Helper function to safely fetch and parse JSON Objects
    private fun fetchJson(urlString: String): JSONObject? {
        try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            // yr.no requires a custom User-Agent to avoid 403 Forbidden errors
            connection.setRequestProperty("User-Agent", "LadybugApp/1.0 (Android)")
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
            connection.setRequestProperty("User-Agent", "LadybugApp/1.0 (Android)")
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