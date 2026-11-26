package com.phantom.ghost.monitor

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.provider.CalendarContract
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

data class ProactiveStatus(val lowPower: Boolean, val message: String?)

class ProactiveMonitor(context: Context) {
    private val appContext = context.applicationContext

    fun checkBattery(): ProactiveStatus {
        val battery = appContext.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return if (level in 0..9) ProactiveStatus(true, "Ghost Mode is active below 10% battery.")
        else ProactiveStatus(false, null)
    }

    /** WorkManager respects Doze; periodic work has a 15-minute minimum interval. */
    fun schedule() {
        val work = PeriodicWorkRequestBuilder<ProactiveWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
            ProactiveWorker.UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, work
        )
    }

    fun cancel() = WorkManager.getInstance(appContext).cancelUniqueWork(ProactiveWorker.UNIQUE_NAME)
}

class ProactiveWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = runCatching {
        val battery = applicationContext.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        applicationContext.getSharedPreferences("phantom_power", Context.MODE_PRIVATE)
            .edit().putBoolean("ghost_mode", level in 0..9).putInt("last_battery_level", level).apply()
        if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED) notifyUpcomingEvent()
        Result.success()
    }.getOrElse { Result.retry() }

    private fun notifyUpcomingEvent() {
        val now = System.currentTimeMillis()
        val end = now + TimeUnit.HOURS.toMillis(24)
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(now.toString()).appendPath(end.toString()).build()
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN
        )
        val selection = CalendarContract.Instances.BEGIN + " >= ?"
        val order = CalendarContract.Instances.BEGIN + " ASC"
        val event = applicationContext.contentResolver.query(
            uri, projection, selection, arrayOf(now.toString()), order
        )?.use { cursor ->
            if (cursor.moveToFirst()) Triple(cursor.getLong(0), cursor.getString(1).orEmpty(), cursor.getLong(2))
            else null
        } ?: return
        if (event.third - now > TimeUnit.HOURS.toMillis(1)) return
        val prefs = applicationContext.getSharedPreferences("phantom_calendar", Context.MODE_PRIVATE)
        if (prefs.getLong("last_event_notified", -1L) == event.first) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED) return

        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "PHANTOM GHOST reminders", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Upcoming calendar event")
            .setContentText("You have an event coming up within the next hour.")
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
        prefs.edit().putLong("last_event_notified", event.first).apply()
    }

    companion object {
        const val UNIQUE_NAME = "phantom-proactive-monitor"
        private const val CHANNEL_ID = "phantom_reminders"
        private const val NOTIFICATION_ID = 8124
    }
}
