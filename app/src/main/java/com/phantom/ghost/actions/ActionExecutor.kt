package com.phantom.ghost.actions

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.Settings
import android.net.Uri
import org.json.JSONObject

data class ActionResult(val ok: Boolean, val message: String)
enum class AssistantMode { ACTIVE, PASSIVE_LOW_BATTERY }

/** Visible, allow-listed Android intents only. No arbitrary shell or hidden cross-app actions. */
class ActionExecutor(private val context: Context) {
    fun currentMode(): AssistantMode {
        val battery = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val percent = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return if (percent in 0..19) AssistantMode.PASSIVE_LOW_BATTERY else AssistantMode.ACTIVE
    }

    fun automationAllowed(): Boolean = currentMode() == AssistantMode.ACTIVE

    fun execute(json: String, userConfirmed: Boolean = false): ActionResult = runCatching {
        val root = JSONObject(json)
        require(root.keys().asSequence().toSet() == setOf("action", "params")) { "Unexpected action fields." }
        val params = root.getJSONObject("params")
        when (root.getString("action")) {
            "system_command" -> when (params.getString("command")) {
                "open_settings" -> launch(Intent(Settings.ACTION_SETTINGS))
                "open_app" -> {
                    require(automationAllowed()) { "Low Power Mode: passive mode is active below 20% battery." }
                    val packageName = params.getString("app_name")
                    require(packageName.matches(Regex("[A-Za-z0-9_.]+"))) { "Invalid package name." }
                    require(userConfirmed) { "Opening another app requires user confirmation." }
                    val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                        ?: error("App is not installed or has no launch activity.")
                    launch(intent)
                }
                "navigate_to" -> {
                    require(userConfirmed) { "Navigation requires user confirmation." }
                    val query = params.getString("query").trim()
                    require(query.isNotEmpty() && query.length <= 200) { "Invalid navigation query." }
                    launch(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(query))))
                }
                "set_alarm" -> {
                    require(userConfirmed) { "Setting an alarm requires user confirmation." }
                    val hour = params.getInt("hour")
                    val minute = params.getInt("minute")
                    require(hour in 0..23 && minute in 0..59) { "Invalid alarm time." }
                    val intent = Intent(AlarmClock.ACTION_SET_ALARM)
                        .putExtra(AlarmClock.EXTRA_HOUR, hour)
                        .putExtra(AlarmClock.EXTRA_MINUTES, minute)
                        .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                    launch(intent)
                }
                else -> error("Command is not allow-listed.")
            }
            else -> error("Action type is not allow-listed.")
        }
        ActionResult(true, "Action handed to Android.")
    }.getOrElse { ActionResult(false, it.message ?: "Action rejected.") }

    private fun launch(intent: Intent) {
        require(intent.resolveActivity(context.packageManager) != null) { "No compatible Android app is available." }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
