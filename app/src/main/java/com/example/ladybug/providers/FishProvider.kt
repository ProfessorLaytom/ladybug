package com.example.ladybug.providers

import android.content.Context
import androidx.core.content.edit
import com.example.ladybug.model.SearchResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.FileNotFoundException

class FishProvider(private val context: Context) : SearchProvider {

    private var fishCache: JSONArray? = null
    private val prefs = context.getSharedPreferences("FishModulePrefs", Context.MODE_PRIVATE)

    override suspend fun performSearch(query: String): SearchResult = withContext(Dispatchers.IO) {
        val trimmedQuery = query.trim().lowercase()
        // Safety guard: If empty, do nothing
        if (trimmedQuery.isEmpty()) return@withContext SearchResult.NoMatch

        try {
            val array = loadJsonArray() ?: return@withContext SearchResult.NoMatch
            if (array.length() == 0) return@withContext SearchResult.NoMatch

            // TRIGGER 1: The Daily Fish (Auto-typed by ViewModel at 5 AM)
            if (trimmedQuery == "daily fish") {
                val index = getNextFishIndex(array.length())
                return@withContext getFishFromCache(array, index, feedback = "Ton petit poisson du matin ❤️")
            }

            // TRIGGER 2: Explicit user command (Instant - no waiting)
            if (trimmedQuery == "fish pls") {
                val index = getNextFishIndex(array.length())
                return@withContext getFishFromCache(array, index)
            }

            // --- THE MAGIC TRICK ---
            // Wait for 0.5 seconds. If the user types another letter during this time,
            // the MainViewModel will cancel this coroutine, and the code below will NEVER run!
            delay(500)

            // TRIGGER 3: The Random Encounter (Only runs if they stopped typing)
            if (shouldRollRandomChance()) {
                val index = getNextFishIndex(array.length())
                return@withContext getFishFromCache(array, index, feedback = "OMG UN POISSON SURPRISE")
            }

            return@withContext SearchResult.NoMatch

        } catch (e: CancellationException) {
            // CRITICAL: If the ViewModel cancels us because the user typed a letter,
            // we must throw this exception back to the system so it cancels properly.
            throw e
        } catch (_: Exception) {
            return@withContext SearchResult.NoMatch
        }
    }

    private fun loadJsonArray(): JSONArray? {
        if (fishCache == null) {
            try {
                val jsonString = context.assets.open("waterlife_data.json").bufferedReader().use { it.readText() }
                fishCache = JSONArray(jsonString)
            } catch (_: FileNotFoundException) {
                return null
            }
        }
        return fishCache
    }

    private fun getFishFromCache(array: JSONArray, index: Int, feedback: String? = null): SearchResult {
        val fishObj = array.getJSONObject(index)
        return SearchResult.Fish(
            commonName = fishObj.optString("common_name", "Unknown"),
            scientificName = fishObj.optString("scientific_name", "Unknown"),
            imageUrl = fishObj.optString("image_url", ""),
            description = fishObj.optString("description", "No description available."),
            wikipediaUrl = fishObj.optString("wikipedia_url", ""),
            systemFeedback = feedback
        )
    }

    private fun getNextFishIndex(arrayLength: Int): Int {
        val currentIndex = prefs.getInt("current_fish_index", 0)
        val nextIndex = (currentIndex + 1) % arrayLength
        prefs.edit { putInt("current_fish_index", nextIndex) }
        return currentIndex
    }

    private fun shouldRollRandomChance(): Boolean {
        val currentTime = System.currentTimeMillis()
        val lastRollTime = prefs.getLong("last_random_roll_time", 0L)

        // Global Cooldown: Even if they stop typing, only let the dice roll
        // a maximum of once every 3 seconds to prevent spam.
        if (currentTime - lastRollTime > 3000) {
            prefs.edit { putLong("last_random_roll_time", currentTime) }

            // 1% chance
            return (1..100).random() <= 1
        }
        return false
    }
}