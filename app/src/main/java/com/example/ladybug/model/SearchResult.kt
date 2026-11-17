package com.example.ladybug.model

sealed class SearchResult {
    // The fallback: General web search (or direct URL when isDirectUrl == true)
    data class WebLink(
        val title: String,
        val url: String,
        val isDirectUrl: Boolean = false,
        // Raw trimmed text typed in the search bar (saved to the search history when opened)
        val query: String = ""
    ) : SearchResult()

    // Shown when typing "conv" or "conversion"
    object ConversionOptions : SearchResult()

    data class UnitConversion(
        val initialValue: Double?,
        val initialFromUnit: String,
        val initialToUnit: String,
        val category: Category
    ) : SearchResult() {
        // Defines which family of units we are working with
        enum class Category { WEIGHT, LENGTH, VELOCITY, VOLUME, TEMPERATURE, TIME }
    }

    data class AppSearch(
        val apps: List<AppItem>
    ) : SearchResult()

    data class Wikipedia(
        val articles: List<WikiArticle>,
        // Raw trimmed text typed in the search bar (saved to the search history when an article is opened)
        val query: String = ""
    ) : SearchResult()

    // Interactive API features
    data class Currency(
        val initialValue: Double?,
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
        // Used to show messages like "Ton petit poisson du matin ❤️"
        val systemFeedback: String? = null,
        // Marks the morning Daily Fish so its priority lowers when the user types a search
        val isDailyFish: Boolean = false
    ) : SearchResult()

    // Past discovered fish & favorites collection (Fishdex)
    data class FishHistory(
        val discoveredFish: List<Fish>
    ) : SearchResult()

    // --- Cocktail Search Results ---
    data class CocktailRecipe(
        val cocktail: CocktailData
    ) : SearchResult()

    data class CocktailSuggestions(
        val suggestions: List<String>,
        val subtitle: String = "Did you mean..."
    ) : SearchResult()

    data class CocktailNotFound(
        val query: String
    ) : SearchResult()

    // --- World Clock Results ---
    data class WorldClock(
        val singleLocation: WorldLocation? = null,
        val matchedByCountry: Boolean = false
    ) : SearchResult()

    // When a provider doesn't understand the query
    object NoMatch : SearchResult()
}

// --- SHARED DATA CLASSES ---

data class AppItem(
    val name: String,
    val packageName: String,
    val icon: androidx.compose.ui.graphics.ImageBitmap,
    val normalizedName: String = "",
    val normalizedWords: List<String> = emptyList()
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

// --- Cocktail Data Models ---

// Represents a single ingredient from the JSON
data class CocktailIngredient(
    val direction: String,
    val ingredientName: String = "",
    val normalizedName: String = "",
    val ingredientWords: List<String> = emptyList()
)

// The detailed recipe payload
data class CocktailData(
    val name: String,
    val category: String? = null,
    val method: String,
    val garnish: String?,
    val glass: String,
    val ingredients: List<CocktailIngredient>,
    val nameWords: List<String> = emptyList(),
    val normalizedName: String = "",
    val normalizedCategory: String? = null
)

// --- World Clock Data Models ---

data class WorldLocation(
    val id: String,
    val cityName: String,
    val countryNameEn: String,
    val countryNameFr: String,
    val countryCode: String,
    val timeZoneId: String,
    val isCapital: Boolean = false,
    val converterLabel: String = "$countryCode - $cityName ($countryNameEn)"
)
