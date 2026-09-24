package com.example.ladybug.providers

import android.content.Context
import com.example.ladybug.model.CocktailData
import com.example.ladybug.model.CocktailIngredient
import com.example.ladybug.model.SearchResult
import org.json.JSONArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CocktailProvider(private val context: Context) : SearchProvider {

    // Cache the database in memory after the first load
    private var cocktailCache: List<CocktailData>? = null

    override suspend fun performSearch(query: String): SearchResult = withContext(Dispatchers.IO) {
        if (!query.lowercase().startsWith("bar ")) {
            return@withContext SearchResult.NoMatch
        }

        val searchTerm = query.substring(4).trim()

        try {
            val cocktails = getOrLoadCocktails()

            // 1. If they just typed "bar ", anchor the card with default suggestions
            if (searchTerm.isEmpty()) {
                val defaultSuggestions = cocktails.take(12).map { it.name }
                return@withContext SearchResult.CocktailSuggestions(defaultSuggestions)
            }

            // 2. Check for an exact match first
            val exactMatch = cocktails.find { it.name.equals(searchTerm, ignoreCase = true) }
            if (exactMatch != null) {
                return@withContext SearchResult.CocktailRecipe(exactMatch)
            }

            // 3. The Autocomplete Fix: Check if ANY word in the name STARTS with the search term
            val suggestions = cocktails
                .filter { cocktail ->
                    val words = cocktail.name.split("\\s+".toRegex())
                    words.any { word -> word.startsWith(searchTerm, ignoreCase = true) }
                }
                .map { it.name }
                .take(12) // Limit to top 12 so it doesn't flood the screen

            if (suggestions.isNotEmpty()) {
                return@withContext SearchResult.CocktailSuggestions(suggestions)
            }

            // THE FIX: If there are no exact matches and no suggestions, return Not Found!
            return@withContext SearchResult.CocktailNotFound(searchTerm)

        } catch (e: Exception) {
            e.printStackTrace()
        }

        return@withContext SearchResult.NoMatch
    }

    private fun getOrLoadCocktails(): List<CocktailData> {
        cocktailCache?.let { return it }

        val jsonString = context.assets.open("cocktails_data_with_glasses.json")
            .bufferedReader()
            .use { it.readText() }

        val jsonArray = JSONArray(jsonString)
        val loadedList = mutableListOf<CocktailData>()

        for (i in 0 until jsonArray.length()) {
            val jsonObject = jsonArray.getJSONObject(i)

            // Parse Ingredients
            val ingredientsArray = jsonObject.getJSONArray("ingredients")
            val ingredientsList = mutableListOf<CocktailIngredient>()
            for (j in 0 until ingredientsArray.length()) {
                val ingObj = ingredientsArray.getJSONObject(j)
                ingredientsList.add(CocktailIngredient(direction = ingObj.getString("direction")))
            }

            loadedList.add(
                CocktailData(
                    name = jsonObject.getString("name"),
                    method = jsonObject.getString("method"),
                    garnish = if (jsonObject.has("garnish")) jsonObject.getString("garnish") else null,
                    glass = jsonObject.getString("glass"),
                    ingredients = ingredientsList
                )
            )
        }

        cocktailCache = loadedList
        return loadedList
    }
}