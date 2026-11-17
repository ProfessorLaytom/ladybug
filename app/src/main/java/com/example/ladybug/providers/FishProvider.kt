package com.example.ladybug.providers

import android.content.Context
import androidx.core.content.edit
import com.example.ladybug.model.SearchResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.FileNotFoundException

class FishProvider(private val context: Context) : SearchProvider {

    private val cacheMutex = Mutex()
    @Volatile
    private var fishCache: List<SearchResult.Fish>? = null
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        ensureFavoritesLoaded(context)
    }

    override suspend fun preload() {
        loadFishList()
    }

    companion object {
        private const val PREFS_NAME = "FishModulePrefs"
        private const val KEY_CURRENT_INDEX = "current_fish_index"
        private const val KEY_DISCOVERED_COUNT = "discovered_fish_count"
        private const val KEY_LAST_ROLL_TIME = "last_random_roll_time"
        private const val KEY_FAVORITES = "favorite_fish_scientific_names"

        private val _favoritesFlow = MutableStateFlow<Set<String>>(emptySet())
        val favoritesFlow: StateFlow<Set<String>> = _favoritesFlow.asStateFlow()
        @Volatile
        private var favoritesInitialized = false

        fun ensureFavoritesLoaded(context: Context) {
            if (!favoritesInitialized) {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val saved = prefs.getStringSet(KEY_FAVORITES, emptySet())?.toSet() ?: emptySet()
                _favoritesFlow.value = saved
                favoritesInitialized = true
            }
        }

        fun toggleFavorite(context: Context, scientificName: String): Boolean {
            ensureFavoritesLoaded(context)
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val current = _favoritesFlow.value.toMutableSet()
            val isNowFavorite = if (current.contains(scientificName)) {
                current.remove(scientificName)
                false
            } else {
                current.add(scientificName)
                true
            }
            prefs.edit { putStringSet(KEY_FAVORITES, current) }
            _favoritesFlow.value = current.toSet()
            return isNowFavorite
        }
    }

    /**
     * Called directly by MainViewModel on the first launch after 5:00 AM
     * so the Daily Fish appears without typing "daily fish" in the search bar.
     */
    suspend fun fetchDailyFish(): SearchResult.Fish? = withContext(Dispatchers.IO) {
        val list = loadFishList() ?: return@withContext null
        if (list.isEmpty()) return@withContext null
        val index = getNextFishIndex(list.size)
        list[index].copy(
            systemFeedback = "Ton petit poisson du matin ❤️",
            isDailyFish = true
        )
    }

    /**
     * Returns all past discovered fish (most recent first), excluding any future undiscovered fish.
     */
    suspend fun getDiscoveredFish(): List<SearchResult.Fish> = withContext(Dispatchers.IO) {
        val list = loadFishList() ?: return@withContext emptyList()
        val total = list.size
        if (total == 0) return@withContext emptyList()

        val discoveredCount = getDiscoveredCount(total)
        if (discoveredCount <= 0) return@withContext emptyList()

        // Order from most recently discovered (discoveredCount - 1) down to 0
        (discoveredCount - 1 downTo 0).map { idx ->
            list[idx]
        }
    }

    override suspend fun performSearch(query: String): SearchResult {
        val trimmedQuery = query.trim().lowercase()
        if (trimmedQuery.isEmpty()) return SearchResult.NoMatch

        return withContext(Dispatchers.IO) {
            try {
                // TRIGGER 1: The Daily Fish command (if typed manually)
                if (trimmedQuery == "daily fish") {
                    val list = loadFishList() ?: return@withContext SearchResult.NoMatch
                    if (list.isEmpty()) return@withContext SearchResult.NoMatch
                    val index = getNextFishIndex(list.size)
                    return@withContext list[index].copy(
                        systemFeedback = "Ton petit poisson du matin ❤️"
                    )
                }

                // TRIGGER 2: Explicit user command (Instant - no waiting)
                if (trimmedQuery == "fish pls") {
                    val list = loadFishList() ?: return@withContext SearchResult.NoMatch
                    if (list.isEmpty()) return@withContext SearchResult.NoMatch
                    val index = getNextFishIndex(list.size)
                    return@withContext list[index]
                }

                // TRIGGER 3: Past Discovered Fish / Fishdex command
                if (trimmedQuery == "my fish" ||
                    trimmedQuery == "fishdex" ||
                    trimmedQuery == "past fish" ||
                    trimmedQuery == "mes poissons" ||
                    trimmedQuery == "poissons"
                ) {
                    return@withContext SearchResult.FishHistory(getDiscoveredFish())
                }

                // --- THE MAGIC TRICK ---
                // Wait for 0.5 seconds. If the user types another letter during this time,
                // the MainViewModel will cancel this coroutine, and the code below will NEVER run!
                delay(500)

                // TRIGGER 4: The Random Encounter (Only runs if they stopped typing)
                // Check the 1% roll BEFORE loading the 633 KB JSON array!
                if (shouldRollRandomChance()) {
                    val list = loadFishList() ?: return@withContext SearchResult.NoMatch
                    if (list.isEmpty()) return@withContext SearchResult.NoMatch
                    val index = getNextFishIndex(list.size)
                    return@withContext list[index].copy(systemFeedback = "OMG UN POISSON SURPRISE")
                }

                SearchResult.NoMatch

            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                SearchResult.NoMatch
            }
        }
    }

    private suspend fun loadFishList(): List<SearchResult.Fish>? {
        fishCache?.let { return it }

        return cacheMutex.withLock {
            fishCache?.let { return@withLock it }
            try {
                val jsonString = context.assets.open("waterlife_data.json")
                    .bufferedReader()
                    .use { it.readText() }
                val array = JSONArray(jsonString)
                val parsedList = ArrayList<SearchResult.Fish>(array.length())
                for (i in 0 until array.length()) {
                    val fishObj = array.getJSONObject(i)
                    parsedList.add(
                        SearchResult.Fish(
                            commonName = fishObj.optString("common_name", "Unknown"),
                            scientificName = fishObj.optString("scientific_name", "Unknown"),
                            imageUrl = fishObj.optString("image_url", ""),
                            description = fishObj.optString("description", "No description available."),
                            wikipediaUrl = fishObj.optString("wikipedia_url", "")
                        )
                    )
                }
                fishCache = parsedList
                parsedList
            } catch (_: FileNotFoundException) {
                null
            }
        }
    }

    private fun getDiscoveredCount(arrayLength: Int): Int {
        val currentIndex = prefs.getInt(KEY_CURRENT_INDEX, 0)
        // Migrate seamlessly from existing current_fish_index if KEY_DISCOVERED_COUNT wasn't stored yet
        val storedDiscovered = prefs.getInt(KEY_DISCOVERED_COUNT, currentIndex)
        return storedDiscovered.coerceIn(0, arrayLength)
    }

    private fun getNextFishIndex(arrayLength: Int): Int {
        val currentIndex = prefs.getInt(KEY_CURRENT_INDEX, 0)
        val currentDiscovered = getDiscoveredCount(arrayLength)
        val nextIndex = (currentIndex + 1) % arrayLength
        val nextDiscovered = (currentDiscovered + 1).coerceAtMost(arrayLength)

        prefs.edit {
            putInt(KEY_CURRENT_INDEX, nextIndex)
            putInt(KEY_DISCOVERED_COUNT, nextDiscovered)
        }
        return currentIndex
    }

    private fun shouldRollRandomChance(): Boolean {
        val currentTime = System.currentTimeMillis()
        val lastRollTime = prefs.getLong(KEY_LAST_ROLL_TIME, 0L)

        // Global Cooldown: Even if they stop typing, only let the dice roll
        // a maximum of once every 3 seconds to prevent spam.
        if (currentTime - lastRollTime > 3000) {
            prefs.edit { putLong(KEY_LAST_ROLL_TIME, currentTime) }

            // 1% chance
            return (1..100).random() <= 1
        }
        return false
    }
}
