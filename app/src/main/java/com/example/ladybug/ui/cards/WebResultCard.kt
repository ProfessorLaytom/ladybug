package com.example.ladybug.ui.cards

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent // <-- The new import!
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ladybug.model.SearchResult
import androidx.core.net.toUri

@Composable
fun WebResultCard(result: SearchResult.WebLink) {
    // We still need the context to launch the tab
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable {
                // ---------------------------------------------------------
                // THE IN-APP BROWSER UPGRADE
                // ---------------------------------------------------------
                // 1. Build the intent. (You can actually customize toolbar colors here later if you want!)
                val customTabsIntent = CustomTabsIntent.Builder().build()

                // 2. Launch it over the current screen
                customTabsIntent.launchUrl(context, result.url.toUri())
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