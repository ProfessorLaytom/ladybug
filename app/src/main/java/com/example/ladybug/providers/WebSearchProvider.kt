package com.example.ladybug.providers

import android.content.Context
import android.util.LruCache
import com.example.ladybug.model.SearchEngine
import com.example.ladybug.model.SearchResult
import com.example.ladybug.searxng.SearxngServerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class WebSearchProvider(
    private val engineProvider: () -> SearchEngine = { SearchEngine.SEARXNG }
) : SearchProvider {

    private val autocompleteCache = LruCache<String, List<String>>(60)

    override suspend fun performSearch(query: String): SearchResult {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) return SearchResult.NoMatch
        return buildWebLink(trimmedQuery)
    }

    fun buildWebLink(rawQuery: String, engine: SearchEngine = engineProvider()): SearchResult.WebLink {
        val trimmedQuery = rawQuery.trim()
        val isExplicitUrl = trimmedQuery.startsWith("http://", ignoreCase = true) ||
            trimmedQuery.startsWith("https://", ignoreCase = true) ||
            trimmedQuery.startsWith("www.", ignoreCase = true)

        if (isExplicitUrl) {
            val url = if (trimmedQuery.startsWith("www.", ignoreCase = true)) {
                "https://$trimmedQuery"
            } else {
                trimmedQuery
            }
            return SearchResult.WebLink(
                title = "Open $trimmedQuery 🌐",
                url = url,
                isDirectUrl = true,
                query = trimmedQuery
            )
        }

        return SearchResult.WebLink(
            title = "internet magique 🙌 '$trimmedQuery'",
            url = engine.buildSearchUrl(trimmedQuery),
            isDirectUrl = false,
            query = trimmedQuery
        )
    }

    fun getCachedAutocomplete(query: String, engine: SearchEngine): List<String>? {
        val key = cacheKey(query, engine)
        return synchronized(autocompleteCache) { autocompleteCache.get(key) }
    }

    suspend fun fetchAutocomplete(
        context: Context,
        query: String,
        engine: SearchEngine
    ): List<String> {
        val trimmed = query.trim()
        if (trimmed.length < 3) return emptyList()

        getCachedAutocomplete(trimmed, engine)?.let { return it }

        if (engine == SearchEngine.SEARXNG) {
            val ready = SearxngServerManager.awaitReady(context, timeoutMs = 1500L)
            if (!ready) return emptyList()
        }

        val fetched = withContext(Dispatchers.IO) {
            var conn: HttpURLConnection? = null
            try {
                val url = URL(engine.buildAutocompleteUrl(trimmed))
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 4000
                    readTimeout = 4000
                    setRequestProperty("User-Agent", FIREFOX_USER_AGENT)
                    setRequestProperty("Accept", "application/json, text/plain, */*")
                }
                if (conn.responseCode !in 200..299) return@withContext emptyList()

                val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }.trim()
                parseAutocompletePayload(body, trimmed)
            } catch (_: Exception) {
                emptyList()
            } finally {
                conn?.disconnect()
            }
        }

        if (fetched.isNotEmpty()) {
            val key = cacheKey(trimmed, engine)
            synchronized(autocompleteCache) {
                autocompleteCache.put(key, fetched)
            }
        }
        return fetched
    }

    private fun parseAutocompletePayload(body: String, originalQuery: String): List<String> {
        if (body.isEmpty()) return emptyList()

        val rawSuggestions = mutableListOf<String>()
        if (body.startsWith("[")) {
            val root = JSONArray(body)
            // Standard OpenSearch format: ["query", ["s1", "s2", ...]]
            if (root.length() >= 2 && root.optJSONArray(1) != null) {
                val inner = root.getJSONArray(1)
                extractArraySuggestions(inner, rawSuggestions)
            } else {
                // Flat array of strings or objects (e.g. DuckDuckGo/Brave fallback)
                extractArraySuggestions(root, rawSuggestions)
            }
        } else if (body.startsWith("{")) {
            val obj = JSONObject(body)
            val candidateArray = obj.optJSONArray("results")
                ?: obj.optJSONArray("suggestions")
                ?: obj.optJSONArray("items")
            if (candidateArray != null) {
                extractArraySuggestions(candidateArray, rawSuggestions)
            }
        }

        val normQuery = originalQuery.normalizeForSearch()
        return rawSuggestions
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.normalizeForSearch() != normQuery }
            .distinctBy { it.normalizeForSearch() }
            .take(6)
    }

    private fun extractArraySuggestions(array: JSONArray, out: MutableList<String>) {
        for (i in 0 until array.length()) {
            val item = array.opt(i) ?: continue
            when (item) {
                is String -> out.add(item)
                is JSONObject -> {
                    val text = item.optString("phrase")
                        .ifBlank { item.optString("q") }
                        .ifBlank { item.optString("text") }
                        .ifBlank { item.optString("title") }
                    if (text.isNotBlank()) out.add(text)
                }
            }
        }
    }

    private fun cacheKey(query: String, engine: SearchEngine): String =
        "${engine.id}:${query.trim().lowercase()}"

    companion object {
        private const val FIREFOX_USER_AGENT =
            "Mozilla/5.0 (Android 14; Mobile; rv:130.0) Gecko/130.0 Firefox/130.0"
    }
}
