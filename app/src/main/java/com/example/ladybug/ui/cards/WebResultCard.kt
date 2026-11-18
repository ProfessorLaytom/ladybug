package com.example.ladybug.ui.cards

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent // <-- The new import!
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ladybug.model.SearchResult
import com.example.ladybug.searxng.SearxngServerManager
import kotlinx.coroutines.launch
import androidx.core.net.toUri

@Composable
fun WebResultCard(result: SearchResult.WebLink, onOpened: () -> Unit = {}) {
    // We still need the context to launch the tab
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable {
                // 1. Save to the 📜 search history, then launch over the current screen
                onOpened()
                if (result.url.startsWith("http://127.0.0.1:8888")) {
                    scope.launch {
                        SearxngServerManager.awaitReady(context)
                        val customTabsIntent = CustomTabsIntent.Builder().build()
                        customTabsIntent.launchUrl(context, result.url.toUri())
                    }
                } else {
                    val customTabsIntent = CustomTabsIntent.Builder().build()
                    customTabsIntent.launchUrl(context, result.url.toUri())
                }
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = result.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "touch me 😗",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}