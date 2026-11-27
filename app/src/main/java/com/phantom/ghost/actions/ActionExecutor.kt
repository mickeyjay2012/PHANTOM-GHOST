package com.phantom.ghost.actions

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.BatteryManager
import android.provider.AlarmClock

data class ActionResult(val ok: Boolean, val message: String)
data class ActionObservation(val step: Int, val action: String, val ok: Boolean, val observation: String)
data class ActionExecutionReport(val observations: List<ActionObservation>) {
    val completed: Boolean get() = observations.isNotEmpty() && observations.all { it.ok }
}

enum class AssistantMode { ACTIVE, PASSIVE_LOW_BATTERY }

/**
 * Executes only typed, allow-listed Android intents. A plan requires one explicit
 * confirmation, is bounded to five steps, and stops on the first synchronous failure.
 * Observations describe intent dispatch, never claim the user completed the external task.
 */
class ActionExecutor(private val context: Context) {
    private val allowList = DynamicAllowList(context)

    fun currentMode(): AssistantMode {
        val battery = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val percent = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return if (percent in 0..19) AssistantMode.PASSIVE_LOW_BATTERY else AssistantMode.ACTIVE
    }

    fun automationAllowed(): Boolean = currentMode() == AssistantMode.ACTIVE

    fun executePlan(plan: GhostPlan, userConfirmed: Boolean): ActionExecutionReport {
        if (!userConfirmed) return ActionExecutionReport(
            listOf(ActionObservation(0, "Ghost Plan", false, "Confirmation is required."))
        )
        if (!automationAllowed()) return ActionExecutionReport(
            listOf(ActionObservation(0, "Ghost Plan", false, "Actions are paused below 20% battery."))
        )
        if (plan.actions.isEmpty() || plan.actions.size > 5) return ActionExecutionReport(
            listOf(ActionObservation(0, "Ghost Plan", false, "Plan must contain 1–5 actions."))
        )

        val observations = mutableListOf<ActionObservation>()
        plan.actions.forEachIndexed { index, action ->
            val label = when (action) {
                is AgentAction.OpenApp -> "Open ${action.category.label}"
                is AgentAction.NavigateTo -> "Search Maps: ${action.query}"
                is AgentAction.SetAlarm -> "Set alarm: %02d:%02d".format(action.hour, action.minute)
            }
            val result = executeOne(action)
            observations += ActionObservation(index + 1, label, result.ok, result.message)
            if (!result.ok) return ActionExecutionReport(observations)
        }
        return ActionExecutionReport(observations)
    }

    private fun executeOne(action: AgentAction): ActionResult = runCatching {
        when (action) {
            is AgentAction.OpenApp -> {
                val app = allowList.resolve(action.category)
                    ?: error("No compatible ${action.category.label} app was discovered.")
                launch(context.packageManager.getLaunchIntentForPackage(app.packageName)
                    ?: error("The selected app is no longer launchable."))
                ActionResult(true, "Launch intent dispatched to ${app.label}; completion is not verified.")
            }
            is AgentAction.NavigateTo -> {
                val maps = allowList.resolve(AppCategory.MAPS)
                    ?: error("No Maps app was discovered.")
                val intent = Intent(Intent.ACTION_VIEW,
                    Uri.parse("geo:0,0?q=" + Uri.encode(action.query)))
                    .setPackage(maps.packageName)
                launch(intent)
                ActionResult(true, "Search intent dispatched to ${maps.label}; review the destination before starting navigation.")
            }
            is AgentAction.SetAlarm -> {
                require(action.hour in 0..23 && action.minute in 0..59) { "Alarm time is invalid." }
                val intent = Intent(AlarmClock.ACTION_SET_ALARM)
                    .putExtra(AlarmClock.EXTRA_HOUR, action.hour)
                    .putExtra(AlarmClock.EXTRA_MINUTES, action.minute)
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                launch(intent)
                ActionResult(true, "Android alarm review screen opened; the alarm is not saved until the user confirms there.")
            }
        }
    }.getOrElse { ActionResult(false, it.message ?: "Action was rejected.") }

    private fun launch(intent: Intent) {
        require(intent.resolveActivity(context.packageManager) != null) {
            "No compatible Android activity is available."
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
