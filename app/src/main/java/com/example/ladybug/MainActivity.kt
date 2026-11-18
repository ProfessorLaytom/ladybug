package com.example.ladybug

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.ladybug.searxng.SearxngServerManager
import com.example.ladybug.ui.MainSearchScreen
import com.example.ladybug.ui.theme.LadybugTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var shouldFocusKeyboard by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        // 1. Install the splash screen FIRST!
        installSplashScreen()

        // 2. Enable the modern glass system bars
        enableEdgeToEdge()

        // 3. Continue normal Android boot
        super.onCreate(savedInstanceState)

        // 4. Check if the widget specifically opened the app
        shouldFocusKeyboard = intent.getBooleanExtra("FORCE_KEYBOARD_FOCUS", false)

        // 5. Check if launched directly from Android Studio's Run (Play) button in debug mode
        val isStudioDebugLaunch = savedInstanceState == null && isLaunchedFromStudioRun(intent)
        viewModel.onAppOpened(forceForDebugRun = isStudioDebugLaunch)
        if (SearxngServerManager.isSearxngSelected(this)) {
            SearxngServerManager.ensureStarted(this)
        }

        setContent {
            LadybugTheme {
                // 6. Pass the autoFocus flag down to the main screen
                MainSearchScreen(viewModel = viewModel, autoFocus = shouldFocusKeyboard)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.onAppOpened(forceForDebugRun = false)
        if (SearxngServerManager.isSearxngSelected(this)) {
            SearxngServerManager.ensureStarted(this)
        }
    }

    override fun onStop() {
        super.onStop()
        val powerManager = getSystemService(POWER_SERVICE) as? PowerManager
        val isScreenInteractive = powerManager?.isInteractive ?: true
        if (!isChangingConfigurations && isScreenInteractive) {
            viewModel.onAppBackgrounded()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("FORCE_KEYBOARD_FOCUS", false)) {
            shouldFocusKeyboard = true
        }
        val isStudioDebugLaunch = isLaunchedFromStudioRun(intent)
        viewModel.onAppOpened(forceForDebugRun = isStudioDebugLaunch)
    }

    /**
     * Detects when the debug app is launched directly via Android Studio's Play ('Run') button
     * (`adb shell am start -n com.example.ladybug/.MainActivity -a MAIN -c LAUNCHER`).
     * Normal home screen launchers set `sourceBounds` and/or `FLAG_ACTIVITY_RESET_TASK_IF_NEEDED`,
     * the widget sets `FORCE_KEYBOARD_FOCUS`, and Recents sets `FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY`.
     */
    private fun isLaunchedFromStudioRun(launchIntent: Intent?): Boolean {
        val isDebuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (!isDebuggable || launchIntent == null) return false

        val isMainLauncher = launchIntent.action == Intent.ACTION_MAIN &&
            launchIntent.hasCategory(Intent.CATEGORY_LAUNCHER)
        val hasLauncherFlags =
            (launchIntent.flags and Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED) != 0 ||
                (launchIntent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0
        val hasIconBounds = launchIntent.sourceBounds != null
        val isFromWidget = launchIntent.getBooleanExtra("FORCE_KEYBOARD_FOCUS", false)

        return isMainLauncher && !hasLauncherFlags && !hasIconBounds && !isFromWidget
    }
}
