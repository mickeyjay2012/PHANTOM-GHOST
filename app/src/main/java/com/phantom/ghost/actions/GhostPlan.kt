package com.phantom.ghost.actions

import org.json.JSONObject

sealed interface AgentAction {
    data class OpenApp(val category: AppCategory) : AgentAction
    data class NavigateTo(val query: String) : AgentAction
    data class SetAlarm(val hour: Int, val minute: Int) : AgentAction
}

data class GhostPlan(val title: String, val actions: List<AgentAction>)

/** Strict parser: unknown fields, commands, malformed values and oversized plans are rejected. */
object GhostPlanParser {
    fun parse(modelOutput: String): GhostPlan? = runCatching {
        val start = modelOutput.indexOf('{')
        val end = modelOutput.lastIndexOf('}')
        require(start >= 0 && end > start) { "No plan JSON found." }
        val root = JSONObject(modelOutput.substring(start, end + 1))
        require(root.length() == 1 && root.has("ghost_plan")) { "Unexpected plan envelope." }
        val plan = root.getJSONObject("ghost_plan")
        require(plan.has("title") && plan.has("actions")) { "Plan title and actions are required." }
        val raw = plan.getJSONArray("actions")
        require(raw.length() in 1..5) { "A Ghost Plan must contain between 1 and 5 actions." }
        val actions = (0 until raw.length()).map { index ->
            val item = raw.getJSONObject(index)
            when (item.getString("type")) {
                "open_app" -> {
                    require(item.length() == 2)
                    AgentAction.OpenApp(AppCategory.valueOf(item.getString("category").uppercase()))
                }
                "navigate_to" -> {
                    require(item.length() == 2)
                    val query = item.getString("query").trim()
                    require(query.isNotEmpty() && query.length <= 200)
                    AgentAction.NavigateTo(query)
                }
                "set_alarm" -> {
                    require(item.length() == 3)
                    val hour = item.getInt("hour")
                    val minute = item.getInt("minute")
                    require(hour in 0..23 && minute in 0..59)
                    AgentAction.SetAlarm(hour, minute)
                }
                else -> error("Unsupported action type.")
            }
        }
        GhostPlan(plan.getString("title").take(100), actions)
    }.getOrNull()
}
