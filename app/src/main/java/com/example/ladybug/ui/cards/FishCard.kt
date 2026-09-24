package com.example.ladybug.ui.cards

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.ladybug.model.SearchResult
import androidx.core.net.toUri

@Composable
fun FishCard(result: SearchResult.Fish) {
    val context = LocalContext.current

    // STATE: Keeps track of whether the full-screen image is open or closed
    var showFullImage by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column {
            if (result.systemFeedback != null) {
                Text(
                    text = result.systemFeedback,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            // 1. The Image (Now Clickable!)
            AsyncImage(
                model = result.imageUrl,
                contentDescription = result.commonName,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(if (result.systemFeedback == null) RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp) else RoundedCornerShape(0.dp))
                    .clickable { showFullImage = true }, // TRIGGER THE DIALOG
                contentScale = ContentScale.Crop
            )

            // 2. The Content
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = result.commonName,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = result.scientificName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = result.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 3. The Custom Button
                if (result.wikipediaUrl.isNotEmpty()) {
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, result.wikipediaUrl.toUri())
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        // CUSTOM SHAPE (e.g., highly rounded pill shape)
                        shape = RoundedCornerShape(12.dp),
                        // CUSTOM COLORS
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.background, // Background color
                            contentColor = MaterialTheme.colorScheme.surfaceVariant  // Text color
                        ),
                        // CUSTOM OUTLINE
                        border = BorderStroke(
                            width = 0.dp, // Thickness
                            color = MaterialTheme.colorScheme.primary // Outline color
                        )
                    ) {
                        Text("Read More on Wikipedia")
                    }
                }
            }
        }
    }

    // 4. The Full-Screen Image Overlay
    if (showFullImage) {
        Dialog(
            onDismissRequest = { showFullImage = false },
            // usePlatformDefaultWidth = false lets the dialog take up the entire screen edge-to-edge
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.9f)) // Dark semi-transparent background
                    .clickable { showFullImage = false }, // Tap anywhere to close
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = result.imageUrl,
                    contentDescription = "Full size ${result.commonName}",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp), // Gives a little breathing room on the edges
                    contentScale = ContentScale.Fit // Ensures the whole image fits on screen without cropping
                )
            }
        }
    }
}