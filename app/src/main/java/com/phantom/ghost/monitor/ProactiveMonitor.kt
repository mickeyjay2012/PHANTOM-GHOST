package com.phantom.ghost.monitor

import android.content.Context
import android.os.BatteryManager

data class ProactiveStatus(val lowPower: Boolean, val message: String?)

/** Foreground, on-demand status check; scheduling/background monitoring requires separate consent. */
class ProactiveMonitor(context: Context) {
    private val appContext = context.applicationContext

    fun checkBattery(): ProactiveStatus {
        val manager = appContext.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return if (level in 0..9) {
            ProactiveStatus(true, "Battery is low. Ghost Mode is recommended to reduce background work.")
        } else {
            ProactiveStatus(false, null)
        }
    }
}
