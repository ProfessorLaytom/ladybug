package com.example.ladybug.providers

import android.util.Log
import com.example.ladybug.model.SearchResult
import com.example.ladybug.model.WikiArticle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class WikipediaProvider : SearchProvider {

    override suspend fun performSearch(query: String): SearchResult = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()

        // 1. Minimum character check
        if (cleanQuery.length < 3) {
            return@withContext SearchResult.NoMatch
        }

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
                // 3. STANDARD BEHAVIOR: ENGLISH PRIORITY
                val enArticle = fetchArticle("en", cleanQuery)
                val frArticle = fetchArticle("fr", cleanQuery)

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
                SearchResult.Wikipedia(finalArticles)
            } else {
                SearchResult.NoMatch
            }

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // By passing 'e' to the log, the warning disappears!
            Log.e("WikiDebug", "Something went wrong", e)
            SearchResult.NoMatch
        }
    }

    private fun fetchArticle(lang: String, query: String): WikiArticle? {
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            // Using the modern REST API v1
            val url = URL("https://$lang.wikipedia.org/api/rest_v1/page/summary/$encodedQuery")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            // Ignore 404s (Not Found) gracefully
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return null
            }

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)

            // Ignore Disambiguation pages
            if (json.optString("type") == "disambiguation") {
                return null
            }

            // ---------------------------------------------------------
            // MISSING PIECE #1: We must extract the hidden Universal ID
            // ---------------------------------------------------------
            val wikiDataId = if (json.has("wikibase_item")) {
                json.getString("wikibase_item")
            } else null

            val title = json.getString("title")
            val rawSummary = json.optString("extract", "")

            // Extract nested image URL if it exists
            val imageUrl = if (json.has("thumbnail")) {
                json.getJSONObject("thumbnail").getString("source")
            } else null
            Log.d("WikiDebug", "Found Image URL: $imageUrl")

            // Extract nested desktop URL
            val articleUrl = if (json.has("content_urls")) {
                json.getJSONObject("content_urls").getJSONObject("desktop").getString("page")
            } else "https://$lang.wikipedia.org/wiki/$encodedQuery"

            // Data Formatting Rule: Clean and truncate the summary
            val cleanedSummary = rawSummary
                .replace(Regex("\\s+"), " ") // Replaces tabs, newlines, and multiple spaces with a single space
                .trim()

            val finalSummary = if (cleanedSummary.length > 150) {
                "${cleanedSummary.substring(0, 150)}..."
            } else {
                cleanedSummary
            }

            return WikiArticle(
                title = title,
                summary = finalSummary,
                imageUrl = imageUrl,
                articleUrl = articleUrl,
                // ---------------------------------------------------------
                // MISSING PIECE #2: Wire the ID into the returned object
                // ---------------------------------------------------------
                wikiDataId = wikiDataId
            )
        } catch (e: Exception) {
            // THIS PRINTS THE CRASH TO LOGCAT
            Log.e("WikiDebug", "Failed to fetch $lang article", e)
            return null
        }
    }
}