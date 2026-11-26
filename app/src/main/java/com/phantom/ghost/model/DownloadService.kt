package com.phantom.ghost.model

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Foreground notification wrapper for the app's pinned default model download. */
class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var manager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Model downloads", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val name = intent?.getStringExtra(EXTRA_NAME) ?: ModelManager.MODEL_NAME
        startForeground(NOTIFICATION_ID, notification("Preparing $name", 0))
        scope.launch {
            val modelManager = ModelManager(this@DownloadService)
            val progressJob = launch {
                modelManager.downloadProgress.collect { fraction ->
                    val percent = (fraction * 100).toInt().coerceIn(0, 100)
                    manager.notify(NOTIFICATION_ID, notification("Downloading $name", percent))
                }
            }
            try {
                modelManager.downloadModel()
                manager.notify(NOTIFICATION_ID, notification("$name verified and ready", 100))
            } catch (error: Throwable) {
                manager.notify(
                    NOTIFICATION_ID,
                    notification("Download failed: " + (error.message ?: "unknown error"), 0)
                )
            } finally {
                progressJob.cancel()
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    private fun notification(text: String, progress: Int) =
        NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("PHANTOM GHOST models")
            .setContentText(text)
            .setOngoing(progress in 1..99)
            .setProgress(100, progress, progress == 0)
            .build()

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    companion object {
        const val EXTRA_NAME = "model_name"
        private const val CHANNEL = "phantom_model_downloads"
        private const val NOTIFICATION_ID = 412
    }
}
