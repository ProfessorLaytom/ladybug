package com.example.ladybug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.enableEdgeToEdge
// THE CRITICAL IMPORT FOR THE SPLASH SCREEN
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.ladybug.ui.MainSearchScreen
import com.example.ladybug.ui.theme.LadybugTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // 1. Install the splash screen FIRST!
        installSplashScreen()

        // 2. Enable the modern glass system bars
        enableEdgeToEdge()

        // 3. Continue normal Android boot
        super.onCreate(savedInstanceState)

        // 4. Check if the widget specifically opened the app
        val shouldFocusKeyboard = intent.getBooleanExtra("FORCE_KEYBOARD_FOCUS", false)

        setContent {
            LadybugTheme {
                // 5. Pass the autoFocus flag down to the main screen
                MainSearchScreen(viewModel = viewModel, autoFocus = shouldFocusKeyboard)
            }
        }
    }
}