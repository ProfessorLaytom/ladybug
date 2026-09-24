package com.example.ladybug.providers

import android.content.Context
import androidx.core.content.edit
import com.example.ladybug.model.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class CurrencyProvider(private val context: Context) : SearchProvider {

    // FIX 1: Removed the redundant \\ before the $ symbol
    private val currencyRegex = Regex("^([0-9]+(?:\\.[0-9]+)?)\\s*([a-zA-Z$€£¥]+)$")

    // FIX 4: Moved constants into a companion object to respect Kotlin naming conventions
    companion object {
        private const val PREFS_NAME = "currency_cache"
        private const val KEY_RATES = "cached_rates_json"
        private const val KEY_TIMESTAMP = "last_fetch_timestamp"
        private const val ONE_DAY_MILLIS = 24 * 60 * 60 * 1000L
    }

    override suspend fun performSearch(query: String): SearchResult = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim().lowercase()

        try {
            val match = currencyRegex.find(cleanQuery) ?: return@withContext SearchResult.NoMatch

            val value = match.groupValues[1].toDoubleOrNull() ?: return@withContext SearchResult.NoMatch
            val rawCurrency = match.groupValues[2]

            val fromCurrency = identifyCurrencyCode(rawCurrency) ?: return@withContext SearchResult.NoMatch

            val rates = getRates()

            if (rates.isEmpty() || !rates.containsKey(fromCurrency)) {
                return@withContext SearchResult.NoMatch
            }

            val toCurrency = if (fromCurrency == "EUR") "AUD" else "EUR"

            SearchResult.Currency(
                initialValue = value,
                initialFromCurrency = fromCurrency,
                initialToCurrency = toCurrency,
                rates = rates
            )
            // FIX 2: Renamed 'e' to '_'
        } catch (_: Exception) {
            SearchResult.NoMatch
        }
    }

    private fun getRates(): Map<String, Double> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastFetch = prefs.getLong(KEY_TIMESTAMP, 0L)
        val currentTime = System.currentTimeMillis()

        if (currentTime - lastFetch > ONE_DAY_MILLIS) {
            val freshRates = fetchRatesFromNetwork()
            if (freshRates.isNotEmpty()) {

                // FIX 3: Used the modern KTX .edit { } block
                prefs.edit {
                    putString(KEY_RATES, JSONObject(freshRates as Map<*, *>).toString())
                    putLong(KEY_TIMESTAMP, currentTime)
                }

                return freshRates
            }
        }

        val cachedJson = prefs.getString(KEY_RATES, null)
        if (cachedJson != null) {
            val jsonObject = JSONObject(cachedJson)
            val map = mutableMapOf<String, Double>()
            jsonObject.keys().forEach { key ->
                map[key] = jsonObject.getDouble(key)
            }
            return map
        }

        return emptyMap()
    }

    private fun fetchRatesFromNetwork(): Map<String, Double> {
        try {
            val url = URL("https://open.er-api.com/v6/latest/EUR")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 3000
            connection.readTimeout = 3000

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val ratesJson = JSONObject(response).getJSONObject("rates")

                val map = mutableMapOf<String, Double>()
                ratesJson.keys().forEach { key ->
                    map[key] = ratesJson.getDouble(key)
                }
                return map
            }
            // FIX 2: Renamed 'e' to '_'
        } catch (_: Exception) { }
        return emptyMap()
    }

    private fun identifyCurrencyCode(input: String): String? {
        return when (input) {
            "eur", "euro", "euros", "€" -> "EUR"
            "usd", "dollar", "dollars", "$" -> "USD"
            "gbp", "pound", "pounds", "£" -> "GBP"
            "aud", "australian" -> "AUD"
            "idr", "indonesian", "rupiah" -> "IDR"
            "jpy", "yen", "¥" -> "JPY"
            "cad", "canadian" -> "CAD"
            "chf", "swiss", "franc" -> "CHF"
            "inr", "rupee", "rupees", "indian" -> "INR"
            "cny", "yuan", "chinese" -> "CNY"
            "ils", "shekel", "shekels", "₪" -> "ILS"
            else -> {
                if (input.length == 3) input.uppercase() else null
            }
        }
    }
}