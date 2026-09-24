package com.example.ladybug.ui.cards

import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.ladybug.model.SearchResult
import androidx.core.net.toUri
import coil.request.ImageRequest

@Composable
fun WikipediaCard(result: SearchResult.Wikipedia) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            // Loop through the articles (Will be 1 or 2 items usually: FR then EN)
            result.articles.forEachIndexed { index, article ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            // Launch the Custom Tab embedded browser on click
                            val customTabsIntent = CustomTabsIntent.Builder().build()
                            customTabsIntent.launchUrl(context, article.articleUrl.toUri())
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Side: The Image
                    if (article.imageUrl != null) {
                        AsyncImage(
                            // ---------------------------------------------------------
                            // THE FIX: We build a custom request to introduce our app
                            // ---------------------------------------------------------
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(article.imageUrl)
                                .addHeader("User-Agent", "LadybugApp/1.0 (Android)") // Tells Wikipedia we aren't a bot!
                                .crossfade(true) // Adds a nice smooth fade-in animation
                                .build(),

                            contentDescription = "Thumbnail for ${article.title}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface),

                            // You can keep or delete these debug listeners now!
                            onSuccess = {
                                android.util.Log.d("CoilDebug", "Successfully loaded image!")
                            },
                            onError = { error ->
                                android.util.Log.e("CoilDebug", "Coil failed to load: ${error.result.throwable}")
                            }
                        )
                    } else {
                        // Placeholder if the article has no image
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("W", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Right Side: Title and Summary
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = article.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = article.summary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            // Even though we truncated in the provider, this guarantees UI safety
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }


            }
        }
    }
}