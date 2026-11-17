package com.example.ladybug.model

import org.json.JSONObject

/**
 * A single persisted search history event.
 *
 * Only explicit "internet" actions are recorded (never the other cards):
 * - [Type.WEB]: Google search opened via keyboard Enter or the "internet magique" card
 * - [Type.URL]: Direct URL (http/https/www) opened via keyboard Enter or the "Open ... 🌐" card
 * - [Type.WIKIPEDIA]: A Wikipedia article opened from the Wikipedia card
 *
 * Serialized as one JSON object per line (JSON Lines) in `filesDir/search_history.jsonl`:
 * `{"ts":1793047869000,"type":"web","query":"recette crêpes","url":"https://www.google.com/search?q=..."}`
 */
data class SearchHistoryEntry(
    /** Epoch milliseconds when the search was opened. */
    val timestamp: Long,
    val type: Type,
    /** The exact text typed in the search bar (trimmed). */
    val query: String,
    /** The URL that was opened (Google search, direct URL, or Wikipedia article). */
    val url: String,
    /** Article title for [Type.WIKIPEDIA] entries, null otherwise. */
    val title: String? = null
) {
    enum class Type(val jsonValue: String) {
        WEB("web"),
        URL("url"),
        WIKIPEDIA("wikipedia");

        companion object {
            fun fromJson(value: String): Type? = Type.entries.firstOrNull { it.jsonValue == value }
        }
    }

    /** Text placed back in the search bar when the entry is tapped in the history dialog. */
    val searchBarText: String
        get() = if (type == Type.WIKIPEDIA && !title.isNullOrBlank()) title else query

    fun toJson(): JSONObject = JSONObject().apply {
        put(KEY_TIMESTAMP, timestamp)
        put(KEY_TYPE, type.jsonValue)
        put(KEY_QUERY, query)
        put(KEY_URL, url)
        if (title != null) put(KEY_TITLE, title)
    }

    companion object {
        private const val KEY_TIMESTAMP = "ts"
        private const val KEY_TYPE = "type"
        private const val KEY_QUERY = "query"
        private const val KEY_URL = "url"
        private const val KEY_TITLE = "title"

        /** Parses a single JSON line. Returns null for blank, corrupted, or unknown-type lines. */
        fun fromJsonLine(line: String): SearchHistoryEntry? {
            if (line.isBlank()) return null
            return try {
                val json = JSONObject(line)
                val type = Type.fromJson(json.optString(KEY_TYPE)) ?: return null
                SearchHistoryEntry(
                    timestamp = json.getLong(KEY_TIMESTAMP),
                    type = type,
                    query = json.optString(KEY_QUERY),
                    url = json.optString(KEY_URL),
                    title = if (json.has(KEY_TITLE) && !json.isNull(KEY_TITLE)) json.getString(KEY_TITLE) else null
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}
