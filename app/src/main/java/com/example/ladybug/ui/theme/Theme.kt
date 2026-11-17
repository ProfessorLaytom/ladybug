package com.example.ladybug.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

// Flip this to `true` once you finish tweaking the Dark colors in Color.kt!
private const val ENABLE_DARK_THEME = false

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    background = BackgroundDark,
    outline = OutlineSearchBarDark,
    surfaceVariant = SurfaceVariantDark,
    surface = SurfaceDark,
    primaryContainer = PrimaryContainerDark,
    onBackground = OnBackgroundDark,
    onSurface = OnSurfaceDark,
    onSurfaceVariant = OnSurfaceVariantDark
)

private val LightColorScheme = lightColorScheme(
    secondary = PurpleGrey40,
    tertiary = Pink40,
    background = BackgroundLight,
    outline = OutlineSearchBarLight,
    primary = SurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    surface = SurfaceLight,
    primaryContainer = PrimaryContainerLight,
    onBackground = OnBackgroundLight,
    onSurface = OnSurfaceLight,
    onSurfaceVariant = OnSurfaceVariantLight
)

@Composable
fun LadybugTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme && ENABLE_DARK_THEME) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme && ENABLE_DARK_THEME -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = LadybugShapes,
        content = content
    )
}
