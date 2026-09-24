package com.example.ladybug.ui.cards

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ladybug.model.SearchResult

@Composable
fun AppSearchCard(result: SearchResult.AppSearch) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            // Use Theme Colors for dark/light mode compatibility
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp) // Slight padding for the top/bottom of the list
        ) {
            // Loop through every matched app and draw a row for it
            result.apps.forEach { app ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        // Makes the row clickable to launch the app!
                        .clickable {
                            val launchIntent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                            launchIntent?.let {
                                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(it)
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. The App Icon
                    Image(
                        bitmap = app.icon,
                        contentDescription = "${app.name} icon",
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp)) // Gives the icon soft rounded corners
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    // 2. The App Name
                    Text(
                        text = app.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}