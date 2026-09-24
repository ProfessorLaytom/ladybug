package com.example.ladybug.providers


import com.example.ladybug.model.SearchResult

class WebSearchProvider : SearchProvider {
    override suspend fun performSearch(query: String): SearchResult {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) return SearchResult.NoMatch

        // THE STRICT RULE: Only trigger a direct link if it starts with the magic prefixes
        val isExplicitUrl = trimmedQuery.startsWith("http://", ignoreCase = true) ||
                trimmedQuery.startsWith("https://", ignoreCase = true) ||
                trimmedQuery.startsWith("www.", ignoreCase = true)

        if (isExplicitUrl) {
            // Fix the URL for the browser: add https:// if it only starts with www.
            val url = if (trimmedQuery.startsWith("www.", ignoreCase = true)) {
                "https://$trimmedQuery"
            } else {
                trimmedQuery
            }

            return SearchResult.WebLink(
                title = "Open $trimmedQuery 🌐",
                url = url
            )
        }

        // 2. DEFAULT SEARCH
        val formattedQuery = trimmedQuery.replace(" ", "+")

        return SearchResult.WebLink(
            title = "internet magique 🙌 '$trimmedQuery'",
            url = "https://www.google.com/search?q=$formattedQuery"
        )
    }
}