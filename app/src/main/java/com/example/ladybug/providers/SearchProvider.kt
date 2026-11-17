package com.example.ladybug.providers

import com.example.ladybug.model.SearchResult
import java.text.Normalizer

interface SearchProvider {
    // 'suspend' allows this to run on background threads so the UI doesn't freeze
    suspend fun performSearch(query: String): SearchResult

    // Optional background pre-warming hook called on app startup
    suspend fun preload() {}
}

private val combiningDiacriticsRegex = Regex("\\p{InCombiningDiacriticalMarks}+")

/**
 * Normalizes text for accent-insensitive and case-insensitive search
 * (e.g. "Paramètres" -> "parametres", "Crème" -> "creme", "Piña" -> "pina", "Bœuf" -> "boeuf").
 */
fun String.normalizeForSearch(): String {
    val lowered = this.lowercase()
        .replace("œ", "oe")
        .replace("æ", "ae")
    val decomposed = Normalizer.normalize(lowered, Normalizer.Form.NFD)
    return combiningDiacriticsRegex.replace(decomposed, "")
}