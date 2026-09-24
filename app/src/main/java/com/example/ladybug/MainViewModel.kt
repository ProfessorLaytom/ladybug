package com.example.ladybug

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ladybug.model.SearchResult
import com.example.ladybug.providers.AppSearchProvider
import com.example.ladybug.providers.CurrencyProvider
import com.example.ladybug.providers.FishProvider
import com.example.ladybug.providers.SearchProvider
import com.example.ladybug.providers.UnitConversionProvider
import com.example.ladybug.providers.WeatherProvider
import com.example.ladybug.providers.WebSearchProvider
import com.example.ladybug.providers.WikipediaProvider
import com.example.ladybug.providers.CocktailProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.joinAll // NEW IMPORT
import kotlinx.coroutines.sync.Mutex // NEW IMPORT
import kotlinx.coroutines.sync.withLock // NEW IMPORT
import java.util.Calendar
import androidx.core.content.edit

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val providers: List<SearchProvider> = listOf(
        WebSearchProvider(),
        UnitConversionProvider(),
        AppSearchProvider(application),
        WikipediaProvider(),
        CurrencyProvider(application),
        WeatherProvider(),
        FishProvider(application),
        CocktailProvider(application),
    )

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults: StateFlow<List<SearchResult>> = _searchResults.asStateFlow()

    private var searchJob: Job? = null

    init {
        checkAndTriggerDailyFish(application)
    }

    private fun checkAndTriggerDailyFish(context: Context) {
        val prefs = context.getSharedPreferences("AppGlobalPrefs", Context.MODE_PRIVATE)
        val lastDailyTime = prefs.getLong("last_daily_fish_time", 0L)
        val currentTime = System.currentTimeMillis()

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

        if (currentFishDayStart in (lastDailyTime + 1)..currentTime) {
            prefs.edit { putLong("last_daily_fish_time", currentTime) }
            onQueryChanged("daily fish")
        }
    }

    fun onQueryChanged(newQuery: String) {
        _searchQuery.value = newQuery

        // Cancel any previous search that was still calculating
        searchJob?.cancel()

        // If the user clears the text bar, clear the screen immediately.
        if (newQuery.isBlank()) {
            _searchResults.value = emptyList()
            return
        }

        searchJob = viewModelScope.launch {
            // A fresh, empty list specifically for THIS keystroke
            val resultsForThisSearch = mutableListOf<SearchResult>()

            // A lock to prevent multiple providers from editing the list at the exact same microsecond
            val mutex = Mutex()

            // A flag to act as our "Bridge" to prevent flickering
            var isFirstResult = true

            // 1. Launch all providers concurrently
            val jobs = providers.map { provider ->
                launch {
                    val result = provider.performSearch(newQuery)

                    if (result !is SearchResult.NoMatch) {
                        // Safely lock the list, add the new result, and push to UI
                        mutex.withLock {
                            resultsForThisSearch.add(result)
                            val sortedResults = resultsForThisSearch.sortedBy { getPriorityRanking(it) }

                            if (isFirstResult) {
                                // The absolute second the first provider finishes, swap out the OLD search state
                                _searchResults.value = sortedResults
                                isFirstResult = false
                            } else {
                                // For the slower providers (like Wikipedia), append them as they finish!
                                _searchResults.value = sortedResults
                            }
                        }
                    }
                }
            }

            // 2. Wait here until every single provider has finished searching
            jobs.joinAll()

            // 3. If every provider finished and absolutely NO ONE found anything, drop to empty state
            if (resultsForThisSearch.isEmpty()) {
                _searchResults.value = emptyList()
            }
        }
    }

    private fun getPriorityRanking(result: SearchResult): Int {
        return when (result) {
            is SearchResult.Fish -> 0
            is SearchResult.CocktailSuggestions -> 0
            is SearchResult.CocktailRecipe -> 0
            is SearchResult.CocktailNotFound -> 0
            is SearchResult.WebLink -> 1
            is SearchResult.Weather -> 2
            is SearchResult.Currency -> 3
            is SearchResult.UnitConversion -> 4
            is SearchResult.Wikipedia -> 5
            is SearchResult.AppSearch -> 6
            else -> 99
        }
    }
}