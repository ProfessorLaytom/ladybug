package com.example.ladybug.providers

import android.util.Log
import android.util.LruCache
import com.example.ladybug.model.SearchResult
import com.example.ladybug.model.WikiArticle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class WikipediaProvider : SearchProvider {

    private val whitespaceRegex = Regex("\\s+")

    companion object {
        // Caches recently fetched Wikipedia articles in memory so backspacing/retyping is instant
        private val articleCache = LruCache<String, WikiArticle>(40)
    }

    override suspend fun performSearch(query: String): SearchResult {
        val cleanQuery = query.trim()

        // 1. Fast minimum character check & skip explicit internal commands BEFORE switching dispatchers
        if (cleanQuery.length < 3 || isExplicitCommandQuery(cleanQuery)) {
            return SearchResult.NoMatch
        }

        return withContext(Dispatchers.IO) {
            try {
                delay(300) // Debounce

                val articles = mutableListOf<WikiArticle>()

                // 2. CHECK THE EXPLICIT FRENCH TRIGGER
                // Does the query end with " fr" (case insensitive)?
                val isFrenchOnly = cleanQuery.lowercase().endsWith(" fr")

                if (isFrenchOnly) {
                    // Strip the " fr" off the end before sending it to Wikipedia
                    val actualQuery = cleanQuery.dropLast(3).trim()

                    // Safety check: Make sure stripping " fr" didn't leave us with too few letters
                    if (actualQuery.length >= 3) {
                        val frArticle = fetchArticle("fr", actualQuery)
                        if (frArticle != null) {
                            articles.add(frArticle)
                        }
                    }
                } else {
                    // 3. STANDARD BEHAVIOR: PARALLEL FETCH (ENGLISH PRIORITY, FRENCH SECOND)
                    val (enArticle, frArticle) = coroutineScope {
                        val enDeferred = async { fetchArticle("en", cleanQuery) }
                        val frDeferred = async { fetchArticle("fr", cleanQuery) }
                        enDeferred.await() to frDeferred.await()
                    }

                    // Add English first
                    if (enArticle != null) {
                        articles.add(enArticle)
                    }

                    // 4. SMART DEDUPLICATION (FRENCH SECOND)
                    if (frArticle != null) {
                        // Only flag as duplicate if the English article exists AND the IDs match
                        val isDuplicateConcept = enArticle != null && enArticle.wikiDataId == frArticle.wikiDataId

                        if (!isDuplicateConcept) {
                            articles.add(frArticle)
                        }
                    }
                }

                // 5. THE STRICT LIMIT
                // Guarantee that we never, ever return more than 2 results.
                val finalArticles = articles.take(2)

                if (finalArticles.isNotEmpty()) {
                    SearchResult.Wikipedia(finalArticles, query = cleanQuery)
                } else {
                    SearchResult.NoMatch
                }

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("WikiDebug", "Something went wrong", e)
                SearchResult.NoMatch
            }
        }
    }

    private fun isExplicitCommandQuery(cleanQuery: String): Boolean {
        val lower = cleanQuery.lowercase()
        return lower == "bar" ||
            lower.startsWith("bar ") ||
            lower == "conv" ||
            lower == "conversion" ||
            lower == "weather" ||
            lower.startsWith("weather ") ||
            lower == "meteo" ||
            lower.startsWith("meteo ") ||
            lower == "météo" ||
            lower.startsWith("météo ") ||
            lower == "heure" ||
            lower.startsWith("heure ") ||
            lower == "heures" ||
            lower.startsWith("heures ") ||
            lower == "time" ||
            lower.startsWith("time ") ||
            lower == "clock" ||
            lower.startsWith("clock ") ||
            lower == "horloge" ||
            lower.startsWith("horloge ") ||
            lower == "fish pls" ||
            lower == "daily fish" ||
            lower == "my fish" ||
            lower == "fishdex" ||
            lower == "past fish" ||
            lower == "mes poissons" ||
            lower == "poissons" ||
            lower.startsWith("http://") ||
            lower.startsWith("https://") ||
            lower.startsWith("www.")
    }

    private fun fetchArticle(lang: String, query: String): WikiArticle? {
        val cacheKey = "$lang:${query.lowercase()}"
        synchronized(articleCache) {
            articleCache.get(cacheKey)?.let { return it }
        }

        // 1. Try direct REST v1 summary lookup first
        val directResult = fetchSummaryByTitle(lang, query)
        if (directResult is SummaryResult.Found) {
            synchronized(articleCache) {
                articleCache.put(cacheKey, directResult.article)
            }
            return directResult.article
        }

        // 2. Fallback to Wikipedia OpenSearch when direct lookup returns 404 (typo/partial title)
        //    or hits a disambiguation page (e.g. "avocat", "mercury")
        val rejectedDisambiguationTitle = (directResult as? SummaryResult.Disambiguation)?.title
        val candidateTitles = searchCandidateTitles(lang, query)

        for (candidate in candidateTitles) {
            if (rejectedDisambiguationTitle != null && candidate.equals(rejectedDisambiguationTitle, ignoreCase = true)) {
                continue
            }
            val candidateResult = fetchSummaryByTitle(lang, candidate)
            if (candidateResult is SummaryResult.Found) {
                synchronized(articleCache) {
                    articleCache.put(cacheKey, candidateResult.article)
                }
                return candidateResult.article
            }
        }

        return null
    }

    private sealed class SummaryResult {
        data class Found(val article: WikiArticle) : SummaryResult()
        data class Disambiguation(val title: String) : SummaryResult()
        object NotFound : SummaryResult()
    }

    private fun fetchSummaryByTitle(lang: String, titleQuery: String): SummaryResult {
        try {
            // Wikipedia REST API v1 expects underscores for spaces in page titles
            val normalizedTitle = titleQuery.trim().replace(whitespaceRegex, "_")
            val encodedQuery = URLEncoder.encode(normalizedTitle, "UTF-8")

            val url = URL("https://$lang.wikipedia.org/api/rest_v1/page/summary/$encodedQuery")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android 14; Mobile; rv:130.0) Gecko/130.0 Firefox/130.0")
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return SummaryResult.NotFound
            }

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)
            val title = json.optString("title", titleQuery)

            if (json.optString("type") == "disambiguation") {
                return SummaryResult.Disambiguation(title)
            }

            val wikiDataId = if (json.has("wikibase_item")) {
                json.getString("wikibase_item")
            } else null

            val rawSummary = json.optString("extract", "")

            // Extract nested image URL if it exists
            val imageUrl = if (json.has("thumbnail")) {
                json.getJSONObject("thumbnail").getString("source")
            } else null

            // Extract nested desktop URL
            val articleUrl = if (json.has("content_urls")) {
                json.getJSONObject("content_urls").getJSONObject("desktop").getString("page")
            } else "https://$lang.wikipedia.org/wiki/$encodedQuery"

            // Data Formatting Rule: Clean and truncate the summary
            val cleanedSummary = rawSummary
                .replace(whitespaceRegex, " ")
                .trim()

            val finalSummary = if (cleanedSummary.length > 150) {
                "${cleanedSummary.substring(0, 150)}..."
            } else {
                cleanedSummary
            }

            return SummaryResult.Found(
                WikiArticle(
                    title = title,
                    summary = finalSummary,
                    imageUrl = imageUrl,
                    articleUrl = articleUrl,
                    wikiDataId = wikiDataId
                )
            )
        } catch (e: Exception) {
            Log.e("WikiDebug", "Failed to fetch $lang article", e)
            return SummaryResult.NotFound
        }
    }

    /**
     * Queries Wikipedia's OpenSearch API for up to 3 candidate article titles when
     * an exact title lookup misses (typo) or lands on a disambiguation page.
     */
    private fun searchCandidateTitles(lang: String, query: String): List<String> {
        try {
            val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")
            val url = URL(
                "https://$lang.wikipedia.org/w/api.php?action=opensearch&search=$encodedQuery&limit=3&namespace=0&format=json"
            )
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android 14; Mobile; rv:130.0) Gecko/130.0 Firefox/130.0")
            connection.connectTimeout = 4000
            connection.readTimeout = 4000

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return emptyList()
            }

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val rootArray = JSONArray(response)
            if (rootArray.length() < 2) return emptyList()

            val titlesArray = rootArray.getJSONArray(1)
            val candidates = ArrayList<String>(titlesArray.length())
            for (i in 0 until titlesArray.length()) {
                val title = titlesArray.optString(i, "").trim()
                if (title.isNotEmpty()) {
                    candidates.add(title)
                }
            }
            return candidates
        } catch (_: Exception) {
            return emptyList()
        }
    }
}
