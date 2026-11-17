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

    // Matches suffix format (e.g., "100 usd", "10,50€", "25₪")
    private val suffixCurrencyRegex = Regex("^([0-9]+(?:[.,][0-9]+)?)\\s*([a-zA-Z$€£¥₪]+)$")
    // Matches prefix symbol format (e.g., "$100", "€50,25", "£20", "₪100")
    private val prefixCurrencyRegex = Regex("^([$€£¥₪])\\s*([0-9]+(?:[.,][0-9]+)?)$")

    companion object {
        private const val PREFS_NAME = "currency_cache"
        private const val KEY_RATES = "cached_rates_json"
        private const val KEY_TIMESTAMP = "last_fetch_timestamp"
        private const val ONE_DAY_MILLIS = 24 * 60 * 60 * 1000L

        @Volatile
        private var memoryRatesCache: Map<String, Double>? = null
        @Volatile
        private var memoryFetchTimestamp: Long = 0L

        private val fallbackRates = mapOf(
            "EUR" to 1.0,
            "USD" to 1.08,
            "GBP" to 0.85,
            "AUD" to 1.65,
            "JPY" to 162.0,
            "CNY" to 7.80,
            "CAD" to 1.48,
            "CHF" to 0.95,
            "ILS" to 4.00,
            "INR" to 90.0,
            "IDR" to 17200.0
        )

        /**
         * Fast non-blocking lookup of in-memory or SharedPreferences cached rates
         * (with fallback rates if never fetched yet) for instant UI switching.
         */
        fun getCachedRatesImmediate(context: Context): Map<String, Double> {
            memoryRatesCache?.takeIf { it.isNotEmpty() }?.let { return it }

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val cachedJson = prefs.getString(KEY_RATES, null)
            if (cachedJson != null) {
                try {
                    val jsonObject = JSONObject(cachedJson)
                    val map = mutableMapOf<String, Double>()
                    jsonObject.keys().forEach { key ->
                        map[key] = jsonObject.getDouble(key)
                    }
                    if (map.isNotEmpty()) {
                        memoryRatesCache = map
                        memoryFetchTimestamp = prefs.getLong(KEY_TIMESTAMP, 0L)
                        return map
                    }
                } catch (_: Exception) {}
            }
            return fallbackRates
        }

        /**
         * Full rate lookup (in-memory -> 24h network refresh -> SharedPreferences -> fallback).
         * Call from Dispatchers.IO.
         */
        fun getOrFetchRates(context: Context): Map<String, Double> {
            val currentTime = System.currentTimeMillis()

            // 1. Fast path: return in-memory parsed map if still fresh
            val inMemory = memoryRatesCache
            if (inMemory != null && inMemory.isNotEmpty() && (currentTime - memoryFetchTimestamp <= ONE_DAY_MILLIS)) {
                return inMemory
            }

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val lastFetch = prefs.getLong(KEY_TIMESTAMP, 0L)

            // 2. Network refresh if cache is older than 24h
            if (currentTime - lastFetch > ONE_DAY_MILLIS) {
                val freshRates = fetchRatesFromNetwork()
                if (freshRates.isNotEmpty()) {
                    prefs.edit {
                        putString(KEY_RATES, JSONObject(freshRates as Map<*, *>).toString())
                        putLong(KEY_TIMESTAMP, currentTime)
                    }
                    memoryRatesCache = freshRates
                    memoryFetchTimestamp = currentTime
                    return freshRates
                }
            }

            // 3. Fallback to SharedPreferences JSON and populate in-memory cache
            val cachedJson = prefs.getString(KEY_RATES, null)
            if (cachedJson != null) {
                try {
                    val jsonObject = JSONObject(cachedJson)
                    val map = mutableMapOf<String, Double>()
                    jsonObject.keys().forEach { key ->
                        map[key] = jsonObject.getDouble(key)
                    }
                    if (map.isNotEmpty()) {
                        memoryRatesCache = map
                        memoryFetchTimestamp = lastFetch
                        return map
                    }
                } catch (_: Exception) {}
            }

            return fallbackRates
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
            } catch (_: Exception) { }
            return emptyMap()
        }
    }

    override suspend fun performSearch(query: String): SearchResult {
        val cleanQuery = query.trim().lowercase()
        if (cleanQuery.length < 2) return SearchResult.NoMatch

        // Fast guard clauses BEFORE switching to Dispatchers.IO
        val (rawNumber, rawCurrency) = suffixCurrencyRegex.find(cleanQuery)?.let {
            it.groupValues[1] to it.groupValues[2]
        } ?: prefixCurrencyRegex.find(cleanQuery)?.let {
            it.groupValues[2] to it.groupValues[1]
        } ?: return SearchResult.NoMatch

        val value = rawNumber.replace(',', '.').toDoubleOrNull()
            ?: return SearchResult.NoMatch

        val fromCurrency = identifyCurrencyCode(rawCurrency) ?: return SearchResult.NoMatch

        return withContext(Dispatchers.IO) {
            try {
                val rates = getOrFetchRates(context)

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
            } catch (_: Exception) {
                SearchResult.NoMatch
            }
        }
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
