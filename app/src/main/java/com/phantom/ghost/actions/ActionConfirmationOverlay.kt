package com.phantom.ghost.actions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.phantom.ghost.ui.GlassCard
import com.phantom.ghost.ui.PhantomButton
import com.phantom.ghost.ui.PhantomColors
import com.phantom.ghost.ui.PhantomType

/** Action chooser followed by a distinct review/confirm step. */
@Composable
fun ActionConfirmationOverlay(
    executor: ActionExecutor,
    onEnableProactiveMonitor: () -> Unit,
    onDisableProactiveMonitor: () -> Unit,
    onDismiss: () -> Unit
) {
    var destination by remember { mutableStateOf("") }
    var hourText by remember { mutableStateOf("07") }
    var minuteText by remember { mutableStateOf("00") }
    var pending by remember { mutableStateOf<ApprovedAction?>(null) }
    var result by remember { mutableStateOf<ActionResult?>(null) }

    val description = when (val action = pending) {
        is ApprovedAction.OpenApp -> when (action.packageName) {
            ApprovedAction.MAPS_PACKAGE -> "Open Google Maps."
            ApprovedAction.CHROME_PACKAGE -> "Open Chrome."
            else -> "Open an approved app."
        }
        is ApprovedAction.NavigateTo -> "Open Maps and search for: \"${action.query}\". PHANTOM will not start navigation automatically."
        is ApprovedAction.SetAlarm -> "Open Android's alarm screen for %02d:%02d. Review and save it in the Android alarm app.".format(action.hour, action.minute)
        null -> ""
    }

    Dialog(onDismissRequest = onDismiss) {
        GlassCard(Modifier.fillMaxWidth().heightIn(max = 620.dp).verticalScroll(rememberScrollState())) {
            Text("PHANTOM / HANDS", style = PhantomType.label, color = PhantomColors.GhostCyan)
            Spacer(Modifier.height(8.dp))
            if (result != null) {
                Text(if (result!!.ok) "ACTION HANDED TO ANDROID" else "ACTION NOT COMPLETED",
                    color = PhantomColors.GhostWhite)
                Text(result!!.message, color = PhantomColors.Muted)
                Spacer(Modifier.height(12.dp))
                PhantomButton("CLOSE", Modifier.fillMaxWidth(), onClick = onDismiss)
            } else if (pending != null) {
                Text("CONFIRM THIS ACTION", style = PhantomType.label, color = PhantomColors.PhantomPurple)
                Text(description, color = PhantomColors.GhostWhite)
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PhantomButton("CONFIRM", Modifier.weight(1f), onClick = {
                        result = executor.execute(pending!!, userConfirmed = true)
                        pending = null
                    })
                    OutlinedButton(onClick = { pending = null }, modifier = Modifier.weight(1f)) {
                        Text("ABORT", color = PhantomColors.Muted)
                    }
                }
            } else {
                Text("Choose an allow-listed action. PHANTOM will show a review screen before executing it.",
                    color = PhantomColors.Muted)
                Spacer(Modifier.height(10.dp))
                PhantomButton("OPEN GOOGLE MAPS", Modifier.fillMaxWidth(),
                    onClick = { pending = ApprovedAction.OpenApp(ApprovedAction.MAPS_PACKAGE) })
                PhantomButton("OPEN CHROME", Modifier.fillMaxWidth(),
                    onClick = { pending = ApprovedAction.OpenApp(ApprovedAction.CHROME_PACKAGE) })
                Spacer(Modifier.height(6.dp))
                Text("SEARCH IN MAPS", style = PhantomType.label, color = PhantomColors.GhostCyan)
                OutlinedTextField(
                    value = destination, onValueChange = { destination = it.take(200) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    placeholder = { Text("Enter a place or search phrase") }
                )
                PhantomButton("REVIEW SEARCH", Modifier.fillMaxWidth(), enabled = destination.isNotBlank(),
                    onClick = { pending = ApprovedAction.NavigateTo(destination.trim()) })
                Spacer(Modifier.height(6.dp))
                Text("SET AN ALARM", style = PhantomType.label, color = PhantomColors.GhostCyan)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = hourText, onValueChange = { hourText = it.filter(Char::isDigit).take(2) },
                        modifier = Modifier.weight(1f), label = { Text("Hour 0–23") }, singleLine = true)
                    OutlinedTextField(value = minuteText, onValueChange = { minuteText = it.filter(Char::isDigit).take(2) },
                        modifier = Modifier.weight(1f), label = { Text("Minute 0–59") }, singleLine = true)
                }
                val hour = hourText.toIntOrNull()
                val minute = minuteText.toIntOrNull()
                PhantomButton("REVIEW ALARM", Modifier.fillMaxWidth(),
                    enabled = hour != null && hour in 0..23 && minute != null && minute in 0..59,
                    onClick = { pending = ApprovedAction.SetAlarm(hour!!, minute!!) })
                Spacer(Modifier.height(12.dp))
                Text("PROACTIVE MONITOR", style = PhantomType.label, color = PhantomColors.PhantomPurple)
                Text("With permission, PHANTOM checks battery state and the next 24 hours of calendar events. It does not edit your calendar.",
                    color = PhantomColors.Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onEnableProactiveMonitor) { Text("ENABLE", color = PhantomColors.GhostCyan) }
                    TextButton(onClick = onDisableProactiveMonitor) { Text("DISABLE", color = PhantomColors.Muted) }
                }
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = onDismiss) { Text("CLOSE", color = PhantomColors.Muted) }
            }
        }
    }
}
