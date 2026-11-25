package com.phantom.ghost.actions

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import org.json.JSONObject

data class ActionResult(val ok: Boolean, val message: String)

/**
 * Executes only explicit allow-listed Android intents. No shell, arbitrary URI, hidden input,
 * password access, or background cross-app control is supported.
 */
class ActionExecutor(private val context: Context) {
    fun execute(json: String, userConfirmed: Boolean = false): ActionResult = runCatching {
        val root = JSONObject(json)
        require(root.keys().asSequence().toSet() == setOf("action", "params")) { "Unexpected action fields." }
        val params = root.getJSONObject("params")
        when (root.getString("action")) {
            "system_command" -> when (params.getString("command")) {
                "open_settings" -> launch(Intent(Settings.ACTION_SETTINGS))
                "open_app" -> {
                    val packageName = params.getString("app_name")
                    require(packageName.matches(Regex("[A-Za-z0-9_.]+"))) { "Invalid package name." }
                    require(userConfirmed) { "Opening another app requires user confirmation." }
                    val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                        ?: error("App is not installed or has no launch activity.")
                    launch(intent)
                }
                else -> error("Command is not allow-listed.")
            }
            else -> error("Action type is not allow-listed.")
        }
        ActionResult(true, "Action completed.")
    }.getOrElse { ActionResult(false, it.message ?: "Action rejected.") }

    private fun launch(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
