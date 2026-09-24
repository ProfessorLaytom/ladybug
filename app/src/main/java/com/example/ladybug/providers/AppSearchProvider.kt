package com.example.ladybug.providers

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.example.ladybug.model.AppItem
import com.example.ladybug.model.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppSearchProvider(private val context: Context) : SearchProvider {

    override suspend fun performSearch(query: String): SearchResult = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()

        // 1. Guard clause: Only search if the user has typed 2 or more letters
        if (cleanQuery.length < 2) {
            return@withContext SearchResult.NoMatch
        }

        return@withContext try {
            val packageManager = context.packageManager

            // We use an Intent query to find ONLY apps that can be launched (like Settings, Games, etc.)
            // This filters out hundreds of invisible system background services.
            val intent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }

            val resolveInfos = packageManager.queryIntentActivities(intent, 0)
            val matchedApps = mutableListOf<AppItem>()

            for (info in resolveInfos) {
                val appName = info.loadLabel(packageManager).toString()

                // 2. The Check: Does the app name contain the typed letters anywhere? (Case insensitive)
                if (appName.contains(cleanQuery, ignoreCase = true)) {
                    val packageName = info.activityInfo.packageName
                    val drawable = info.loadIcon(packageManager)

                    // Convert the Android Drawable into a Compose-friendly ImageBitmap (128x128 resolution)
                    val bitmap = drawable.toBitmap(width = 128, height = 128).asImageBitmap()

                    matchedApps.add(
                        AppItem(
                            name = appName,
                            packageName = packageName,
                            icon = bitmap
                        )
                    )
                }
            }

            // 3. Return the populated list, or NoMatch if nothing was found
            if (matchedApps.isNotEmpty()) {
                // Sort alphabetically so it looks clean
                SearchResult.AppSearch(matchedApps.sortedBy { it.name })
            } else {
                SearchResult.NoMatch
            }

        } catch (e: Exception) {
            // Catch any PackageManager crashes and fail gracefully
            SearchResult.NoMatch
        }
    }
}