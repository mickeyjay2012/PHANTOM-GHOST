package com.phantom.ghost.actions

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.BatteryManager
import android.provider.AlarmClock
import org.json.JSONObject

sealed interface ApprovedAction {
    data class OpenApp(val packageName: String) : ApprovedAction
    data class NavigateTo(val query: String, val targetPackage: String = MAPS_PACKAGE) : ApprovedAction
    data class SetAlarm(val hour: Int, val minute: Int) : ApprovedAction

    companion object {
        const val MAPS_PACKAGE = "com.google.android.apps.maps"
        const val CHROME_PACKAGE = "com.android.chrome"
        const val GOOGLE_PACKAGE = "com.google.android.googlequicksearchbox"
        val OPENABLE_PACKAGES = setOf(MAPS_PACKAGE, CHROME_PACKAGE, GOOGLE_PACKAGE)
        val SEARCH_PACKAGES = setOf(MAPS_PACKAGE, CHROME_PACKAGE, GOOGLE_PACKAGE)
    }
}

data class ActionResult(val ok: Boolean, val message: String)
enum class AssistantMode { ACTIVE, PASSIVE_LOW_BATTERY }

/** Only launches a small, explicit set of user-visible Android intents. */
class ActionExecutor(private val context: Context) {
    fun currentMode(): AssistantMode {
        val battery = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val percent = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return if (percent in 0..19) AssistantMode.PASSIVE_LOW_BATTERY else AssistantMode.ACTIVE
    }

    fun automationAllowed(): Boolean = currentMode() == AssistantMode.ACTIVE

    /** Backward-compatible JSON envelope for the assistant action router. */
    fun execute(json: String, userConfirmed: Boolean = false): ActionResult = runCatching {
        val root = JSONObject(json)
        require(root.keys().asSequence().toSet() == setOf("action", "params")) {
            "Unexpected action fields."
        }
        require(root.getString("action") == "system_command") { "Action type is not allow-listed." }
        val params = root.getJSONObject("params")
        val action = when (params.getString("command")) {
            "open_app" -> ApprovedAction.OpenApp(params.getString("app_name"))
            "navigate_to" -> ApprovedAction.NavigateTo(
                params.getString("query"),
                params.optString("package_name", ApprovedAction.MAPS_PACKAGE)
            )
            "set_alarm" -> ApprovedAction.SetAlarm(
                params.getInt("hour"), params.getInt("minute")
            )
            else -> error("Command is not allow-listed.")
        }
        execute(action, userConfirmed)
    }.getOrElse { ActionResult(false, it.message ?: "Action was rejected.") }

    fun execute(action: ApprovedAction, userConfirmed: Boolean): ActionResult = runCatching {
        require(userConfirmed) { "This action needs your confirmation." }
        require(automationAllowed()) { "Low Power Mode: actions are paused below 20% battery." }
        when (action) {
            is ApprovedAction.OpenApp -> {
                require(action.packageName in ApprovedAction.OPENABLE_PACKAGES) { "App is not on the allow-list." }
                val intent = context.packageManager.getLaunchIntentForPackage(action.packageName)
                    ?: error("Approved app is not installed or has no launch activity.")
                launch(intent)
                ActionResult(true, "Opened the approved app.")
            }
            is ApprovedAction.NavigateTo -> {
                val query = action.query.trim()
                require(query.isNotEmpty() && query.length <= 200) { "Enter a destination or search phrase (maximum 200 characters)." }
                require(action.targetPackage in ApprovedAction.SEARCH_PACKAGES) { "Search target is not on the allow-list." }
                val injected = PhantomAccessibilityService.requestSearchAfterUserConfirmation(
                    action.targetPackage, query
                )
                if (injected) {
                    val intent = context.packageManager.getLaunchIntentForPackage(action.targetPackage)
                        ?: error("Search app is not installed.")
                    launch(intent)
                    ActionResult(true, "Opened the approved app and queued a one-time search-field entry.")
                } else {
                    val fallback = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("geo:0,0?q=" + Uri.encode(query))
                    )
                    launch(fallback)
                    ActionResult(true, "Opened the Android maps search. Review the result in Maps.")
                }
            }
            is ApprovedAction.SetAlarm -> {
                require(action.hour in 0..23 && action.minute in 0..59) { "Alarm time is invalid." }
                val intent = Intent(AlarmClock.ACTION_SET_ALARM)
                    .putExtra(AlarmClock.EXTRA_HOUR, action.hour)
                    .putExtra(AlarmClock.EXTRA_MINUTES, action.minute)
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                launch(intent)
                ActionResult(true, "Opened Android's alarm confirmation screen.")
            }
        }
    }.getOrElse { ActionResult(false, it.message ?: "Action was rejected.") }

    private fun launch(intent: Intent) {
        require(intent.resolveActivity(context.packageManager) != null) {
            "No compatible Android app is available."
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
