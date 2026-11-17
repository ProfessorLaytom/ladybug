package com.example.ladybug

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ladybug.data.SearchHistoryStore
import com.example.ladybug.model.SearchHistoryEntry
import com.example.ladybug.model.SearchResult
import com.example.ladybug.model.WikiArticle
import com.example.ladybug.providers.AppSearchProvider
import com.example.ladybug.providers.CocktailProvider
import com.example.ladybug.providers.CurrencyProvider
import com.example.ladybug.providers.FishProvider
import com.example.ladybug.providers.SearchProvider
import com.example.ladybug.providers.UnitConversionProvider
import com.example.ladybug.providers.WeatherProvider
import com.example.ladybug.providers.WebSearchProvider
import com.example.ladybug.providers.WikipediaProvider
import com.example.ladybug.providers.WorldClockProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Calendar

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val fishProvider = FishProvider(application)

    private val providers: List<SearchProvider> = listOf(
        WebSearchProvider(),
        UnitConversionProvider(),
        AppSearchProvider(application),
        WikipediaProvider(),
        CurrencyProvider(application),
        WeatherProvider(application),
        WorldClockProvider(application),
        fishProvider,
        CocktailProvider(application),
    )

    init {
        // Pre-warm local caches (installed launcher apps & JSON assets) in the background on startup
        viewModelScope.launch(Dispatchers.Default) {
            providers.forEach { provider ->
                launch { provider.preload() }
            }
            // Load the persisted search history (JSON Lines file) so the 📜 dialog opens instantly
            launch { SearchHistoryStore.ensureLoaded(application) }
        }
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults: StateFlow<List<SearchResult>> = _searchResults.asStateFlow()

    // Stores the previous "bar ..." search query when the user taps a cocktail from the suggestions list
    private val _previousBarQuery = MutableStateFlow<String?>(null)
    val previousBarQuery: StateFlow<String?> = _previousBarQuery.asStateFlow()

    // Holds the morning Daily Fish in memory for the first session after 5:00 AM
    @Volatile
    private var sessionDailyFish: SearchResult.Fish? = null

    @Volatile
    private var isFetchingDailyFish: Boolean = false

    @Volatile
    private var dailyFishShownAt: Long = 0L

    @Volatile
    private var wasBackgroundedAfterDailyFish: Boolean = false

    private var searchJob: Job? = null

    // Persisted internet search history (web searches, direct URLs, opened Wikipedia articles), newest first
    val searchHistory: StateFlow<List<SearchHistoryEntry>> = SearchHistoryStore.entries

    /**
     * Records a web search (Google fallback) or direct URL opened via keyboard Enter
     * or by tapping the WebResultCard.
     */
    fun recordWebSearch(link: SearchResult.WebLink) {
        val query = link.query.ifBlank { _searchQuery.value.trim() }
        if (query.isBlank()) return
        recordHistoryEntry(
            SearchHistoryEntry(
                timestamp = System.currentTimeMillis(),
                type = if (link.isDirectUrl) SearchHistoryEntry.Type.URL else SearchHistoryEntry.Type.WEB,
                query = query,
                url = link.url
            )
        )
    }

    /** Records a Wikipedia article opened from the WikipediaCard. */
    fun recordWikipediaArticle(result: SearchResult.Wikipedia, article: WikiArticle) {
        val query = result.query.ifBlank { _searchQuery.value.trim() }
        recordHistoryEntry(
            SearchHistoryEntry(
                timestamp = System.currentTimeMillis(),
                type = SearchHistoryEntry.Type.WIKIPEDIA,
                query = query,
                url = article.articleUrl,
                title = article.title
            )
        )
    }

    private fun recordHistoryEntry(entry: SearchHistoryEntry) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            SearchHistoryStore.record(context, entry)
        }
    }

    suspend fun getDiscoveredFish(): List<SearchResult.Fish> {
        return fishProvider.getDiscoveredFish()
    }

    fun onAppOpened(forceForDebugRun: Boolean = false) {
        if (isFetchingDailyFish) return

        val context = getApplication<Application>()
        val prefs = context.getSharedPreferences("AppGlobalPrefs", Context.MODE_PRIVATE)
        val lastDailyTime = prefs.getLong("last_daily_fish_time", 0L)
        val lastApkUpdateTime = prefs.getLong("last_daily_fish_apk_update_time", 0L)
        val currentTime = System.currentTimeMillis()

        val currentApkUpdateTime = try {
            context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
        } catch (_: Exception) {
            0L
        }

        val calendar = Calendar.getInstance()
        calendar.timeInMillis = currentTime

        if (calendar.get(Calendar.HOUR_OF_DAY) < 5) {
            calendar.add(Calendar.DAY_OF_YEAR, -1)
        }
        calendar.set(Calendar.HOUR_OF_DAY, 5)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        val currentFishDayStart = calendar.timeInMillis

        // Trigger on:
        // 1. First app open after 5:00 AM (or if saved time was in the future due to clock changes)
        // 2. First app open after an APK install / reinstall (e.g., Android Studio Play button build)
        // 3. Explicit Android Studio debug 'Run' launch
        val isNewFishDay = lastDailyTime < currentFishDayStart || lastDailyTime > currentTime
        val isFreshInstallOrUpdate = currentApkUpdateTime > 0L && currentApkUpdateTime != lastApkUpdateTime
        val shouldTrigger = isNewFishDay || isFreshInstallOrUpdate || forceForDebugRun

        if (shouldTrigger) {
            isFetchingDailyFish = true
            wasBackgroundedAfterDailyFish = false
            dailyFishShownAt = currentTime

            prefs.edit {
                putLong("last_daily_fish_time", currentTime)
                if (currentApkUpdateTime > 0L) {
                    putLong("last_daily_fish_apk_update_time", currentApkUpdateTime)
                }
            }

            // Ensure the search bar is empty for the first open of the day
            searchJob?.cancel()
            _previousBarQuery.value = null
            _searchQuery.value = ""

            viewModelScope.launch {
                try {
                    val dailyFish = fishProvider.fetchDailyFish()
                    if (dailyFish != null) {
                        sessionDailyFish = dailyFish
                        dailyFishShownAt = System.currentTimeMillis()
                        if (_searchQuery.value.isBlank()) {
                            _searchResults.value = listOf(dailyFish)
                        } else if (_searchResults.value.none { it is SearchResult.Fish && it.isDailyFish }) {
                            _searchResults.value = (_searchResults.value + dailyFish)
                                .sortedBy { getPriorityRanking(it) }
                        }
                    }
                } finally {
                    isFetchingDailyFish = false
                }
            }
        } else if (wasBackgroundedAfterDailyFish) {
            // Subsequent app open on the same day after closing/leaving the app: clear the morning fish
            wasBackgroundedAfterDailyFish = false
            if (sessionDailyFish != null) {
                sessionDailyFish = null
                if (_searchQuery.value.isBlank()) {
                    _searchResults.value = emptyList()
                } else {
                    _searchResults.value = _searchResults.value.filterNot {
                        it is SearchResult.Fish && it.isDailyFish
                    }
                }
            }
        }
    }

    fun onAppBackgrounded() {
        if (sessionDailyFish != null && System.currentTimeMillis() - dailyFishShownAt > 1500L) {
            wasBackgroundedAfterDailyFish = true
        }
    }

    /**
     * Dismisses the morning Fish of the Day for the current session.
     * Clears sessionDailyFish and removes it from search results immediately.
     */
    fun dismissDailyFish() {
        sessionDailyFish = null
        wasBackgroundedAfterDailyFish = false
        if (_searchQuery.value.isBlank()) {
            _searchResults.value = emptyList()
        } else {
            _searchResults.value = _searchResults.value.filterNot {
                it is SearchResult.Fish && it.isDailyFish
            }
        }
    }

    fun onCocktailSuggestionSelected(selectedDrink: String) {
        _previousBarQuery.value = _searchQuery.value
        onQueryChanged("bar $selectedDrink", preservePreviousBarQuery = true)
    }

    fun returnToPreviousBarQuery() {
        val prev = _previousBarQuery.value ?: return
        _previousBarQuery.value = null
        onQueryChanged(prev, preservePreviousBarQuery = false)
    }

    fun onQueryChanged(newQuery: String, preservePreviousBarQuery: Boolean = false) {
        if (!preservePreviousBarQuery) {
            _previousBarQuery.value = null
        }
        _searchQuery.value = newQuery

        // Cancel any previous search that was still calculating
        searchJob?.cancel()

        // If the user clears the text bar, show only the session Daily Fish (if active) or empty state.
        if (newQuery.isBlank()) {
            val daily = sessionDailyFish
            _searchResults.value = if (daily != null) listOf(daily) else emptyList()
            return
        }

        searchJob = viewModelScope.launch {
            // A fresh list for THIS keystroke; include sessionDailyFish at lowest priority if present
            val resultsForThisSearch = mutableListOf<SearchResult>()
            sessionDailyFish?.let { resultsForThisSearch.add(it) }

            // A lock to prevent multiple providers from editing the list at the exact same microsecond
            val mutex = Mutex()

            // Launch all providers concurrently
            val jobs = providers.map { provider ->
                launch {
                    val result = provider.performSearch(newQuery)

                    if (result !is SearchResult.NoMatch) {
                        mutex.withLock {
                            resultsForThisSearch.add(result)
                            val sortedResults = resultsForThisSearch.sortedBy { getPriorityRanking(it) }
                            _searchResults.value = sortedResults
                        }
                    }
                }
            }

            // Wait until every provider has finished searching
            jobs.joinAll()

            // If no provider matched, keep only the session Daily Fish (if active) or empty list
            if (resultsForThisSearch.isEmpty()) {
                _searchResults.value = emptyList()
            }
        }
    }

    private fun getPriorityRanking(result: SearchResult): Int {
        return when (result) {
            // 1. Dedicated command & pattern cards (always first)
            is SearchResult.Fish -> if (result.isDailyFish) 98 else 0
            is SearchResult.FishHistory -> 0
            is SearchResult.CocktailSuggestions -> 1
            is SearchResult.CocktailRecipe -> 1
            is SearchResult.CocktailNotFound -> 1
            is SearchResult.Weather -> 2
            is SearchResult.WorldClock -> 2
            is SearchResult.ConversionOptions -> 3
            is SearchResult.Currency -> 3
            is SearchResult.UnitConversion -> 4
            // 2. General matched cards (Installed Apps -> Wikipedia)
            is SearchResult.AppSearch -> 5
            is SearchResult.Wikipedia -> 6
            // 3. Direct URLs at top (0); fallback Google search ("internet magique") after all matches (10)
            is SearchResult.WebLink -> if (result.isDirectUrl) 0 else 10
            else -> 99
        }
    }
}
