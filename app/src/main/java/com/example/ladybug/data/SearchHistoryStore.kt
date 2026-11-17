package com.example.ladybug.data

import android.content.Context
import android.util.Log
import com.example.ladybug.model.SearchHistoryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile

/**
 * Persistent, append-only search history stored on device as JSON Lines
 * (one JSON object per line) in `filesDir/search_history.jsonl`.
 *
 * - Survives app updates (internal storage is only wiped on uninstall).
 * - Excluded from Google Cloud Backup in AndroidManifest.xml, backup_rules.xml, and data_extraction_rules.xml for privacy.
 * - Every event is kept (no deduplication, no size cap) so it can later be searched by date/keywords.
 */
object SearchHistoryStore {
    private const val TAG = "SearchHistoryStore"
    private const val FILE_NAME = "search_history.jsonl"

    // Newest entry first
    private val _entries = MutableStateFlow<List<SearchHistoryEntry>>(emptyList())
    val entries: StateFlow<List<SearchHistoryEntry>> = _entries.asStateFlow()

    private val mutex = Mutex()

    @Volatile
    private var isLoaded = false

    private fun historyFile(context: Context): File = File(context.applicationContext.filesDir, FILE_NAME)

    /** Loads the history file once into memory (no-op if already loaded). */
    suspend fun ensureLoaded(context: Context) {
        if (isLoaded) return
        withContext(Dispatchers.IO) {
            mutex.withLock {
                if (isLoaded) return@withLock
                val file = historyFile(context)
                val parsed = try {
                    if (file.exists()) {
                        repairTrailingNewline(file)
                        file.useLines(Charsets.UTF_8) { lines ->
                            lines.mapNotNull { SearchHistoryEntry.fromJsonLine(it) }.toList()
                        }
                    } else {
                        emptyList()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to read search history", e)
                    emptyList()
                }
                // File is chronological (oldest first); expose newest first
                _entries.value = parsed.asReversed().toList()
                isLoaded = true
            }
        }
    }

    /** Appends a new entry to the history file and publishes it at the top of [entries]. */
    suspend fun record(context: Context, entry: SearchHistoryEntry) {
        ensureLoaded(context)
        withContext(Dispatchers.IO) {
            mutex.withLock {
                try {
                    historyFile(context).appendText(entry.toJson().toString() + "\n", Charsets.UTF_8)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to write search history entry", e)
                }
                _entries.value = listOf(entry) + _entries.value
            }
        }
    }

    /**
     * If a previous write was interrupted (e.g. process killed mid-write), the file may not end
     * with a newline. Appending one prevents the next entry from being glued onto the broken line.
     */
    private fun repairTrailingNewline(file: File) {
        if (file.length() == 0L) return
        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(raf.length() - 1)
            if (raf.read() != '\n'.code) {
                raf.seek(raf.length())
                raf.write('\n'.code)
            }
        }
    }
}
