package com.example.ladybug.model

import java.net.URLEncoder

enum class SearchEngine(
    val id: String,
    val displayName: String,
    val shortLabel: String
) {
    SEARXNG("searxng", "SearXNG", "🔎 SearXNG"),
    GOOGLE("google", "Google", "🌐 Google"),
    BRAVE("brave", "Brave", "🦁 Brave"),
    DUCKDUCKGO("duckduckgo", "DuckDuckGo", "🦆 DuckDuckGo"),
    STARTPAGE("startpage", "Startpage", "🛡️ Startpage");

    fun buildSearchUrl(rawQuery: String): String {
        val encoded = URLEncoder.encode(rawQuery.trim(), "UTF-8")
        return when (this) {
            SEARXNG -> "http://127.0.0.1:8888/search?q=$encoded"
            GOOGLE -> "https://www.google.com/search?q=$encoded"
            BRAVE -> "https://search.brave.com/search?q=$encoded"
            DUCKDUCKGO -> "https://duckduckgo.com/?q=$encoded"
            STARTPAGE -> "https://www.startpage.com/sp/search?query=$encoded"
        }
    }

    fun buildAutocompleteUrl(rawQuery: String): String {
        val encoded = URLEncoder.encode(rawQuery.trim(), "UTF-8")
        return when (this) {
            SEARXNG -> "http://127.0.0.1:8888/autocompleter?q=$encoded"
            GOOGLE -> "https://suggestqueries.google.com/complete/search?client=firefox&q=$encoded"
            BRAVE -> "https://search.brave.com/api/suggest?q=$encoded"
            DUCKDUCKGO -> "https://ac.duckduckgo.com/ac/?q=$encoded&type=list"
            STARTPAGE -> "https://www.startpage.com/osuggestions?q=$encoded"
        }
    }

    companion object {
        fun fromId(id: String?): SearchEngine =
            entries.find { it.id == id } ?: SEARXNG
    }
}

data class AutocompleteSuggestion(
    val text: String,
    val isMagicCommand: Boolean
)
