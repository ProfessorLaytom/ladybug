package com.example.ladybug.providers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.example.ladybug.model.AppItem
import com.example.ladybug.model.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class AppSearchProvider(private val context: Context) : SearchProvider {

    private val cacheMutex = Mutex()
    private val wordSplitRegex = Regex("[\\s._-]+")
    @Volatile
    private var cachedApps: List<AppItem>? = null

    init {
        // Automatically invalidate the app cache if an app is installed, updated, or uninstalled
        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addDataScheme("package")
            }
            context.applicationContext.registerReceiver(object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    cachedApps = null
                }
            }, filter)
        } catch (_: Exception) {
            // Safe fallback if receiver registration fails in test environments
        }
    }

    override suspend fun preload() {
        getOrLoadApps()
    }

    override suspend fun performSearch(query: String): SearchResult {
        val cleanQuery = query.trim()

        // 1. Fast guard clause BEFORE switching coroutine dispatchers
        if (cleanQuery.length < 2) {
            return SearchResult.NoMatch
        }

        val normQuery = cleanQuery.normalizeForSearch()
        if (normQuery.length < 2) {
            return SearchResult.NoMatch
        }

        return withContext(Dispatchers.Default) {
            try {
                val allApps = getOrLoadApps()

                // 2. Accent-insensitive filter + smart ranking:
                //    0 = Exact match, 1 = Starts with query, 2 = Any word starts with query, 3 = Contains query
                //    (allApps is pre-sorted alphabetically, and sortedBy is stable so alphabetical tie-breaking is preserved)
                val matchedApps = allApps.mapNotNull { app ->
                    val rank = when {
                        app.normalizedName == normQuery -> 0
                        app.normalizedName.startsWith(normQuery) -> 1
                        app.normalizedWords.any { it.startsWith(normQuery) } -> 2
                        app.normalizedName.contains(normQuery) -> 3
                        else -> return@mapNotNull null
                    }
                    rank to app
                }.sortedBy { it.first }
                    .map { it.second }

                if (matchedApps.isNotEmpty()) {
                    SearchResult.AppSearch(matchedApps)
                } else {
                    SearchResult.NoMatch
                }
            } catch (_: Exception) {
                SearchResult.NoMatch
            }
        }
    }

    private suspend fun getOrLoadApps(): List<AppItem> {
        cachedApps?.let { return it }

        return withContext(Dispatchers.IO) {
            cacheMutex.withLock {
                cachedApps?.let { return@withLock it }

                val packageManager = context.packageManager
                val intent = Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }

                val resolveInfos = packageManager.queryIntentActivities(intent, 0)
                val loadedApps = resolveInfos.mapNotNull { info ->
                    try {
                        val appName = info.loadLabel(packageManager).toString()
                        val packageName = info.activityInfo.packageName
                        val drawable = info.loadIcon(packageManager)
                        val bitmap = drawable.toBitmap(width = 128, height = 128).asImageBitmap()
                        val normName = appName.normalizeForSearch()
                        AppItem(
                            name = appName,
                            packageName = packageName,
                            icon = bitmap,
                            normalizedName = normName,
                            normalizedWords = normName.split(wordSplitRegex).filter { it.isNotEmpty() }
                        )
                    } catch (_: Exception) {
                        null
                    }
                }.sortedBy { it.normalizedName }

                cachedApps = loadedApps
                loadedApps
            }
        }
    }
}
