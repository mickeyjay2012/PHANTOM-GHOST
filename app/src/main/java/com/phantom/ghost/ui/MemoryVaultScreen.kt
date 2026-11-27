package com.phantom.ghost.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phantom.ghost.memory.LocalMemoryManager
import com.phantom.ghost.memory.MemoryChunk
import com.phantom.ghost.memory.ProfileManager
import kotlinx.coroutines.launch

@Composable
fun MemoryVaultScreen(
    memoryManager: LocalMemoryManager,
    profileManager: ProfileManager,
    onAutoLearnChanged: (Boolean) -> Unit,
    onRunNow: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var memories by remember { mutableStateOf<List<MemoryChunk>>(emptyList()) }
    var confirmClear by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var autoLearn by remember { mutableStateOf(profileManager.autoLearnEnabled) }
    val snackbar = remember { SnackbarHostState() }

    suspend fun refresh() {
        loading = true
        memories = runCatching { memoryManager.vaultTopTen() }.getOrDefault(emptyList())
        loading = false
    }
    LaunchedEffect(memoryManager) { refresh() }

    Scaffold(
        containerColor = PhantomColors.PhantomBlack,
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("LOCAL INTELLIGENCE", style = PhantomType.label, color = PhantomColors.GhostCyan)
                Text("Memory Vault", color = PhantomColors.GhostWhite, fontSize = 24.sp)
                Text("Only this device-local profile can access these facts.", color = PhantomColors.Muted, fontSize = 12.sp)
            }
            Spacer(Modifier.height(12.dp))
            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("AUTO-LEARN", style = PhantomType.label, color = PhantomColors.GhostCyan)
                        Text("Opt-in nightly extraction · runs approximately every 24 hours", color = PhantomColors.Muted, fontSize = 11.sp)
                    }
                    Switch(checked = autoLearn, onCheckedChange = { enabled ->
                        autoLearn = enabled
                        profileManager.autoLearnEnabled = enabled
                        onAutoLearnChanged(enabled)
                    })
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("TOP 10 · MOST RETRIEVED", style = PhantomType.label, color = PhantomColors.PhantomPurple)
                Text("${memories.size}/10", color = PhantomColors.Muted, fontSize = 11.sp)
            }
            if (loading) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PhantomColors.GhostCyan)
                }
            } else if (memories.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No permanent memories yet. Pin a message or enable Auto-Learn.", color = PhantomColors.Muted)
                }
            } else {
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    items(memories, key = { it.id }) { memory ->
                        var drag by remember(memory.id) { mutableFloatStateOf(0f) }
                        GlassCard(
                            Modifier.fillMaxWidth()
                                .pointerInput(memory.id) {
                                    detectHorizontalDragGestures(
                                        onHorizontalDrag = { _, amount -> drag += amount },
                                        onDragEnd = {
                                            if (drag < -72f) {
                                                scope.launch {
                                                    memoryManager.delete(memory.id)
                                                    refresh()
                                                    snackbar.showSnackbar("Memory deleted")
                                                }
                                            }
                                            drag = 0f
                                        },
                                        onDragCancel = { drag = 0f }
                                    )
                                }
                        ) {
                            Text(memory.content, color = PhantomColors.GhostWhite, fontSize = 14.sp, lineHeight = 20.sp)
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("RETRIEVED ${memory.retrievalCount}×", style = PhantomType.label, color = PhantomColors.Muted)
                                StatusBadge("COSINE ${memory.similarity.coerceIn(0f, 1f).let { (it * 100).toInt() }}%", PhantomColors.GhostCyan)
                            }
                            Text("Swipe left to delete", color = PhantomColors.Muted, fontSize = 10.sp)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRunNow, enabled = autoLearn, modifier = Modifier.weight(1f), shape = RoundedCornerShape(22.dp)) {
                    Text("RUN LEARN NOW", color = PhantomColors.GhostCyan, fontSize = 10.sp)
                }
                OutlinedButton(onClick = { confirmClear = true }, enabled = memories.isNotEmpty(), modifier = Modifier.weight(1f), shape = RoundedCornerShape(22.dp)) {
                    Text("CLEAR VAULT", color = MaterialTheme.colorScheme.error, fontSize = 10.sp)
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear Memory Vault?") },
            text = { Text("This permanently deletes all saved memories for this local profile. Conversation records are not affected.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    scope.launch {
                        memoryManager.clearVault()
                        refresh()
                        snackbar.showSnackbar("Memory Vault cleared")
                    }
                }) { Text("CLEAR") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("CANCEL") } }
        )
    }
}
