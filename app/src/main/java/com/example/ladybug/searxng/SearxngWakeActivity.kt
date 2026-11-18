package com.example.ladybug.searxng

import android.app.Activity
import android.os.Bundle

/**
 * Invisible transparent activity invoked via `ladybug://wake` from Firefox's `/sw.js`
 * when SearXNG has auto-stopped after 3 minutes of idle time.
 */
class SearxngWakeActivity : Activity() {

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        overridePendingTransition(0, 0)
        super.onCreate(savedInstanceState)

        if (SearxngServerManager.isSearxngSelected(this)) {
            SearxngServerManager.ensureStarted(this)
        }

        finish()
        overridePendingTransition(0, 0)
    }
}
