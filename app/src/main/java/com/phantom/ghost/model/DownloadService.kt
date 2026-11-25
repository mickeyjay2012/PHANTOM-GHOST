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
import kotlinx.coroutines.launch

class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var manager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Model downloads", NotificationManager.IMPORTANCE_LOW))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val name = intent?.getStringExtra(EXTRA_NAME) ?: return START_NOT_STICKY
        val url = intent.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
        val sha = intent.getStringExtra(EXTRA_SHA256) ?: return START_NOT_STICKY
        startForeground(NOTIFICATION_ID, notification("Preparing $name", 0))
        scope.launch {
            runCatching {
                ModelManager(this@DownloadService).download(ModelSpec(name, name, url, sha)) { done, total ->
                    val progress = if (total > 0) ((done * 100) / total).toInt() else 0
                    manager.notify(NOTIFICATION_ID, notification("Downloading $name", progress))
                }
            }.onSuccess {
                manager.notify(NOTIFICATION_ID, notification("$name verified and ready", 100))
            }.onFailure {
                manager.notify(NOTIFICATION_ID, notification("Download failed: " + (it.message ?: "unknown error"), 0))
            }
            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf(startId)
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
        const val EXTRA_URL = "model_url"
        const val EXTRA_SHA256 = "model_sha256"
        private const val CHANNEL = "phantom_model_downloads"
        private const val NOTIFICATION_ID = 412
    }
}
