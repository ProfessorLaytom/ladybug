package com.example.ladybug.searxng

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.HttpURLConnection
import java.net.URL

object SearxngServerManager {

    const val PREFS_NAME = "AppGlobalPrefs"
    const val KEY_SELECTED_ENGINE = "selected_search_engine"
    const val DEFAULT_ENGINE_ID = "searxng"
    const val PORT = 8888

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val startMutex = Mutex()

    @Volatile
    var isReady: Boolean = false
        private set

    @Volatile
    private var startJob: Job? = null

    fun isSearxngSelected(context: Context): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_SELECTED_ENGINE, DEFAULT_ENGINE_ID) == DEFAULT_ENGINE_ID
    }

    fun ensureStarted(context: Context) {
        val appContext = context.applicationContext
        if (!isSearxngSelected(appContext)) return

        val serviceIntent = Intent(appContext, SearxngKeepAliveService::class.java)
        try {
            ContextCompat.startForegroundService(appContext, serviceIntent)
        } catch (_: Exception) {
            // If OS restricts background FGS start, still start Python server in-process
        }

        startPythonServerAsync(appContext)
    }

    @Synchronized
    fun startPythonServerAsync(context: Context) {
        val appContext = context.applicationContext
        if (!isSearxngSelected(appContext)) return

        val currentJob = startJob
        if (currentJob != null && currentJob.isActive) return

        startJob = scope.launch {
            startMutex.withLock {
                if (!isSearxngSelected(appContext)) return@withLock

                if (isReady && probeHealthz()) {
                    touchActivityInternal()
                    return@withLock
                }

                try {
                    if (!Python.isStarted()) {
                        Python.start(AndroidPlatform(appContext))
                    }
                    val module = Python.getInstance().getModule("searxng_server")
                    module.callAttr("start_server", appContext.filesDir.absolutePath, PORT)

                    val deadlineMs = System.currentTimeMillis() + 20_000L
                    while (System.currentTimeMillis() < deadlineMs && isSearxngSelected(appContext)) {
                        if (probeHealthz()) {
                            isReady = true
                            return@withLock
                        }
                        delay(40L)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("SearxngServerManager", "Failed to start SearXNG", e)
                    isReady = false
                }
            }
        }
    }

    suspend fun awaitReady(context: Context, timeoutMs: Long = 10_000L): Boolean {
        val appContext = context.applicationContext
        if (!isSearxngSelected(appContext)) return false

        if (isReady) {
            scope.launch { touchActivityInternal() }
            return true
        }

        ensureStarted(appContext)
        val deadlineMs = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadlineMs) {
            if (isReady) {
                scope.launch { touchActivityInternal() }
                return true
            }
            if (!isSearxngSelected(appContext)) return false
            delay(40L)
        }
        return isReady
    }

    fun stop(context: Context) {
        val appContext = context.applicationContext
        isReady = false
        startJob?.cancel()
        startJob = null

        try {
            appContext.stopService(Intent(appContext, SearxngKeepAliveService::class.java))
        } catch (_: Exception) {
        }

        scope.launch {
            startMutex.withLock {
                isReady = false
                if (Python.isStarted()) {
                    try {
                        Python.getInstance().getModule("searxng_server").callAttr("stop_server")
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    fun getSecondsSinceLastRequest(): Double {
        if (!Python.isStarted()) return Double.MAX_VALUE
        return try {
            Python.getInstance()
                .getModule("searxng_server")
                .callAttr("seconds_since_last_request")
                .toDouble()
        } catch (_: Exception) {
            Double.MAX_VALUE
        }
    }

    private fun touchActivityInternal() {
        if (!Python.isStarted()) return
        try {
            Python.getInstance().getModule("searxng_server").callAttr("touch_activity")
        } catch (_: Exception) {
        }
    }

    private fun probeHealthz(): Boolean {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL("http://127.0.0.1:$PORT/healthz").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 300
                readTimeout = 300
                setRequestProperty("X-Internal-Probe", "1")
                useCaches = false
            }
            conn.responseCode == 200
        } catch (_: Exception) {
            false
        } finally {
            conn?.disconnect()
        }
    }
}
