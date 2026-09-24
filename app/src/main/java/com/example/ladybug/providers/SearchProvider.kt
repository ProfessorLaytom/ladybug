package com.example.ladybug.providers

import com.example.ladybug.model.SearchResult

interface SearchProvider {
    // 'suspend' allows this to run on background threads so the UI doesn't freeze
    suspend fun performSearch(query: String): SearchResult
}