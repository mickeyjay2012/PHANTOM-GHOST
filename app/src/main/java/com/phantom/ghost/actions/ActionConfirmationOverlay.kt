package com.phantom.ghost.actions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.phantom.ghost.ui.*

@Composable
fun ActionConfirmationOverlay(
    executor: ActionExecutor,
    plan: GhostPlan?,
    onEnableProactiveMonitor: () -> Unit,
    onDisableProactiveMonitor: () -> Unit,
    onDismiss: () -> Unit
) {
    var report by remember { mutableStateOf<ActionExecutionReport?>(null) }
    Dialog(onDismissRequest = onDismiss) {
        GlassCard(Modifier.fillMaxWidth().heightIn(max = 620.dp).verticalScroll(rememberScrollState())) {
            Text("PHANTOM / HANDS", style = PhantomType.label, color = PhantomColors.GhostCyan)
            Spacer(Modifier.height(8.dp))
            if (plan != null) {
                Text("GHOST PLAN", style = PhantomType.label, color = PhantomColors.PhantomPurple)
                Text(plan.title, color = PhantomColors.GhostWhite)
                Spacer(Modifier.height(10.dp))
                plan.actions.forEachIndexed { index, action ->
                    val line = when (action) {
                        is AgentAction.OpenApp -> "Open the installed ${action.category.label} app"
                        is AgentAction.NavigateTo -> "Prepare Maps search for “${action.query}” (no route starts automatically)"
                        is AgentAction.SetAlarm -> "Open Android alarm review for %02d:%02d".format(action.hour, action.minute)
                    }
                    Text("${index + 1}. $line", color = PhantomColors.GhostWhite)
                    Spacer(Modifier.height(6.dp))
                }
                if (report == null) {
                    Text("Confirm All approves these steps in order. Android may switch between apps; dispatch does not prove task completion.",
                        color = PhantomColors.Muted)
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PhantomButton("CONFIRM ALL", Modifier.weight(1f), onClick = {
                            report = executor.executePlan(plan, userConfirmed = true)
                        })
                        OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                            Text("ABORT", color = PhantomColors.Muted)
                        }
                    }
                } else {
                    Text(if (report!!.completed) "PLAN DISPATCHED" else "PLAN STOPPED",
                        style = PhantomType.label, color = PhantomColors.GhostCyan)
                    report!!.observations.forEach { item ->
                        Text("${item.step}. ${if (item.ok) "DISPATCHED" else "FAILED"} · ${item.action}",
                            color = if (item.ok) PhantomColors.GhostWhite else PhantomColors.PhantomPurple)
                        Text(item.observation, color = PhantomColors.Muted)
                    }
                    Spacer(Modifier.height(12.dp))
                    PhantomButton("CLOSE", Modifier.fillMaxWidth(), onClick = onDismiss)
                }
            } else {
                Text("No valid Ghost Plan is pending. Ask in Unrestricted mode for a goal-based plan.",
                    color = PhantomColors.Muted)
                Spacer(Modifier.height(12.dp))
                Text("PROACTIVE MONITOR", style = PhantomType.label, color = PhantomColors.PhantomPurple)
                Text("With permission, PHANTOM checks battery state and upcoming calendar events. It does not edit your calendar.",
                    color = PhantomColors.Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onEnableProactiveMonitor) { Text("ENABLE", color = PhantomColors.GhostCyan) }
                    TextButton(onClick = onDisableProactiveMonitor) { Text("DISABLE", color = PhantomColors.Muted) }
                }
                TextButton(onClick = onDismiss) { Text("CLOSE", color = PhantomColors.Muted) }
            }
        }
    }
}
