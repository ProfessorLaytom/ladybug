package com.example.ladybug.model
// Ensure this matches your actual package name!

sealed class SearchResult {
    // The fallback: General web search
    data class WebLink(val title: String, val url: String) : SearchResult()

    data class UnitConversion(
        val initialValue: Double,
        val initialFromUnit: String,
        val initialToUnit: String,
        val category: Category
    ) : SearchResult() {
        // Defines which family of units we are working with
        enum class Category { WEIGHT, LENGTH, VELOCITY, VOLUME }
    }

    data class AppSearch(
        val apps: List<AppItem>
    ) : SearchResult()

    data class Wikipedia(
        val articles: List<WikiArticle>
    ) : SearchResult()

    // Interactive API features
    data class Currency(
        val initialValue: Double,
        val initialFromCurrency: String,
        val initialToCurrency: String,
        val rates: Map<String, Double> // Holds the cached rates (e.g., "USD" to 1.08)
    ) : SearchResult()

    data class Weather(
        val cityName: String,
        val dailyForecasts: List<DailyForecast>
    ) : SearchResult()

    data class Fish(
        val commonName: String,
        val scientificName: String,
        val imageUrl: String,
        val description: String,
        val wikipediaUrl: String,
        // Used to show messages like "Fish mode activated!"
        val systemFeedback: String? = null
    ) : SearchResult()

    // --- NEW: Cocktail Search Results ---
    data class CocktailRecipe(
        val cocktail: CocktailData
    ) : SearchResult()

    data class CocktailSuggestions(
        val suggestions: List<String>
    ) : SearchResult()

    data class CocktailNotFound(
        val query: String
    ) : SearchResult()

    // When a provider doesn't understand the query
    object NoMatch : SearchResult()
}

// --- SHARED DATA CLASSES ---

data class AppItem(
    val name: String,
    val packageName: String,
    val icon: androidx.compose.ui.graphics.ImageBitmap
)

data class WikiArticle(
    val title: String,
    val summary: String,
    val imageUrl: String?,
    val articleUrl: String,
    val wikiDataId: String?
)

data class DailyForecast(
    val dateLabel: String,
    val minTemp: Double,
    val maxTemp: Double,
    val symbolCode: String,
    val windSpeed: Double,
    val humidity: Double,
    val precipitationAmount: Double,
    val hourlyForecasts: List<HourlyForecast>
)

data class HourlyForecast(
    val time: String,
    val temp: Double,
    val precipitationAmount: Double,
    val windSpeed: Double,
    val humidity: Double,
    val symbolCode: String
)

// --- NEW: Cocktail Data Models ---

// Represents a single ingredient from the JSON
data class CocktailIngredient(
    val direction: String
)

// The detailed recipe payload
data class CocktailData(
    val name: String,
    val method: String,
    val garnish: String?,
    val glass: String,
    val ingredients: List<CocktailIngredient>
)