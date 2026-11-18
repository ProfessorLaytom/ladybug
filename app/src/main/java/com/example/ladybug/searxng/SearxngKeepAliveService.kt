package com.example.ladybug.searxng

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.ladybug.MainActivity
import com.example.ladybug.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SearxngKeepAliveService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var watchdogJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat(buildNotification())

        if (intent?.action == ACTION_STOP || !SearxngServerManager.isSearxngSelected(applicationContext)) {
            stopAndShutdown()
            return START_NOT_STICKY
        }

        SearxngServerManager.startPythonServerAsync(applicationContext)
        startWatchdogIfNeeded()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        watchdogJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startWatchdogIfNeeded() {
        if (watchdogJob?.isActive == true) return
        watchdogJob = serviceScope.launch {
            while (isActive) {
                delay(WATCHDOG_POLL_MS)
                if (!SearxngServerManager.isSearxngSelected(applicationContext)) {
                    stopAndShutdown()
                    break
                }
                if (SearxngServerManager.isReady &&
                    SearxngServerManager.getSecondsSinceLastRequest() >= IDLE_TIMEOUT_SECONDS
                ) {
                    stopAndShutdown()
                    break
                }
            }
        }
    }

    private fun stopAndShutdown() {
        watchdogJob?.cancel()
        SearxngServerManager.stop(applicationContext)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SearXNG Local Engine",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Keeps embedded SearXNG responsive while searching"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, SearxngKeepAliveService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_magic_wand)
            .setContentTitle("SearXNG local actif")
            .setContentText("Veille automatique après 3 min d'inactivité")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openAppIntent)
            .addAction(0, "Stop", stopIntent)
            .build()
    }

    companion object {
        const val ACTION_STOP = "com.example.ladybug.searxng.ACTION_STOP"
        private const val CHANNEL_ID = "searxng_keepalive"
        private const val NOTIFICATION_ID = 8888
        private const val IDLE_TIMEOUT_SECONDS = 180.0
        private const val WATCHDOG_POLL_MS = 15_000L
    }
}
