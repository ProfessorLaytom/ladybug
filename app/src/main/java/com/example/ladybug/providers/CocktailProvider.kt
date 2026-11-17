package com.example.ladybug.providers

import android.content.Context
import com.example.ladybug.model.CocktailData
import com.example.ladybug.model.CocktailIngredient
import com.example.ladybug.model.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray

class CocktailProvider(private val context: Context) : SearchProvider {

    private val cacheMutex = Mutex()
    @Volatile
    private var cocktailCache: List<CocktailData>? = null
    private val whitespaceRegex = Regex("\\s+")

    override suspend fun preload() {
        getOrLoadCocktails()
    }

    override suspend fun performSearch(query: String): SearchResult {
        val trimmed = query.trim()
        val lower = trimmed.lowercase()

        // Fast guard clause before switching coroutine dispatchers
        if (lower != "bar" && !lower.startsWith("bar ")) {
            return SearchResult.NoMatch
        }

        val searchTerm = if (lower == "bar") "" else trimmed.substring(4).trim()
        val normSearchTerm = searchTerm.normalizeForSearch()

        return withContext(Dispatchers.Default) {
            try {
                val cocktails = getOrLoadCocktails()

                // 1. If they just typed "bar" or "bar ", anchor the card with default suggestions
                if (normSearchTerm.isEmpty()) {
                    val defaultSuggestions = cocktails.take(12).map { it.name }
                    return@withContext SearchResult.CocktailSuggestions(
                        suggestions = defaultSuggestions,
                        subtitle = "Popular cocktails:"
                    )
                }

                // 2. Check for an exact name match first (accent-insensitive)
                val exactMatch = cocktails.find { it.normalizedName == normSearchTerm }
                if (exactMatch != null) {
                    return@withContext SearchResult.CocktailRecipe(exactMatch)
                }

                // 3. Name word-prefix autocomplete matches (accent-insensitive, pre-split)
                val nameMatches = cocktails.filter { cocktail ->
                    cocktail.nameWords.any { word -> word.startsWith(normSearchTerm) }
                }

                // 4. Ingredient & Category matches (e.g., "bar gin", "bar prosecco", "bar creme", "bar classic")
                val ingredientOrCategoryMatches = cocktails.filter { cocktail ->
                    val matchesIngredient = cocktail.ingredients.any { ing ->
                        ing.ingredientWords.any { w -> w.startsWith(normSearchTerm) } ||
                            ing.normalizedName == normSearchTerm
                    }
                    val matchesCategory = cocktail.normalizedCategory?.contains(normSearchTerm) == true
                    matchesIngredient || matchesCategory
                }

                val combinedSuggestions = (nameMatches + ingredientOrCategoryMatches)
                    .distinctBy { it.name }
                    .map { it.name }
                    .take(12)

                if (combinedSuggestions.isNotEmpty()) {
                    val subtitle = if (nameMatches.isEmpty() && ingredientOrCategoryMatches.isNotEmpty()) {
                        "Cocktails with '$searchTerm'..."
                    } else {
                        "Did you mean..."
                    }
                    return@withContext SearchResult.CocktailSuggestions(
                        suggestions = combinedSuggestions,
                        subtitle = subtitle
                    )
                }

                // 5. No exact matches and no suggestions -> Not Found!
                SearchResult.CocktailNotFound(searchTerm)

            } catch (e: Exception) {
                e.printStackTrace()
                SearchResult.NoMatch
            }
        }
    }

    private suspend fun getOrLoadCocktails(): List<CocktailData> {
        cocktailCache?.let { return it }

        return withContext(Dispatchers.IO) {
            cacheMutex.withLock {
                cocktailCache?.let { return@withLock it }

                val jsonString = context.assets.open("cocktails_data_with_glasses.json")
                    .bufferedReader()
                    .use { it.readText() }

                val jsonArray = JSONArray(jsonString)
                val loadedList = ArrayList<CocktailData>(jsonArray.length())

                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)

                    // Parse Ingredients with pre-normalized & pre-split words
                    val ingredientsArray = jsonObject.getJSONArray("ingredients")
                    val ingredientsList = ArrayList<CocktailIngredient>(ingredientsArray.length())
                    for (j in 0 until ingredientsArray.length()) {
                        val ingObj = ingredientsArray.getJSONObject(j)
                        val rawIngName = ingObj.optString("ingredient", "")
                        val normIngName = rawIngName.normalizeForSearch()
                        ingredientsList.add(
                            CocktailIngredient(
                                direction = ingObj.getString("direction"),
                                ingredientName = rawIngName,
                                normalizedName = normIngName,
                                ingredientWords = if (normIngName.isNotEmpty()) {
                                    normIngName.split(whitespaceRegex)
                                } else {
                                    emptyList()
                                }
                            )
                        )
                    }

                    val name = jsonObject.getString("name")
                    val normName = name.normalizeForSearch()
                    val category = jsonObject.optString("category", "").takeIf { it.isNotBlank() }

                    loadedList.add(
                        CocktailData(
                            name = name,
                            category = category,
                            method = jsonObject.getString("method"),
                            garnish = if (jsonObject.has("garnish")) jsonObject.getString("garnish") else null,
                            glass = jsonObject.getString("glass"),
                            ingredients = ingredientsList,
                            nameWords = normName.split(whitespaceRegex),
                            normalizedName = normName,
                            normalizedCategory = category?.normalizeForSearch()
                        )
                    )
                }

                cocktailCache = loadedList
                loadedList
            }
        }
    }
}
