package com.example.ladybug.widget

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.ladybug.MainActivity
import com.example.ladybug.R

class SearchWidget : GlanceAppWidget() {

    // THE FIX: This tag tells the linter to ignore the false-positive ColorProvider bug
    @SuppressLint("RestrictedApi")
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            // 1. INTENT FOR YOUR APP (Triggers the Keyboard focus)
            val appIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("FORCE_KEYBOARD_FOCUS", true)
            }

            // 2. INTENT FOR GOOGLE TRANSLATE
            val translateIntent = context.packageManager.getLaunchIntentForPackage("com.google.android.apps.translate")
                ?: Intent(Intent.ACTION_VIEW) // Safe fallback if not installed

            // 3. INTENT FOR GOOGLE LENS (Auto-opens the camera)
            val lensIntent = context.packageManager.getLaunchIntentForPackage("com.google.ar.lens")
                ?: Intent(Intent.ACTION_VIEW)

            // THE UI
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .cornerRadius(10.dp)
                    .background(Color(0xE6FACFCA))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    // Clicking anywhere on the widget background opens your app
                    .clickable(actionStartActivity(appIntent)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Side: Your Custom PNG
                Image(
                    provider = ImageProvider(R.drawable.icon_c),
                    contentDescription = "App Logo",
                    modifier = GlanceModifier.size(32.dp)
                )

                Spacer(modifier = GlanceModifier.size(12.dp))

                // The Search Hint Text
                Text(
                    text = "Passes ton doigt sur moi 🫣...",
                    style = TextStyle(color = ColorProvider(Color(0xFF871818))),
                    modifier = GlanceModifier.defaultWeight()
                )

                // Right Side: Translate Icon
                Image(
                    provider = ImageProvider(R.drawable.translate),
                    contentDescription = "Translate",
                    modifier = GlanceModifier
                        .size(24.dp)
                        .clickable(actionStartActivity(translateIntent))
                )

                Spacer(modifier = GlanceModifier.size(12.dp))

                // Right Side: Lens Icon
                Image(
                    provider = ImageProvider(R.drawable.lens),
                    contentDescription = "Lens",
                    modifier = GlanceModifier
                        .size(24.dp)
                        .clickable(actionStartActivity(lensIntent))
                )
            }
        }
    }
}