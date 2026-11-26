package com.phantom.ghost

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phantom.ghost.ai.ChatMessage
import com.phantom.ghost.ai.DemoAssistantEngine
import com.phantom.ghost.ui.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PhantomGhostScreen() }
    }
}

@Composable
private fun PhantomGhostScreen() {
    val engine = remember { DemoAssistantEngine() }
    var input by remember { mutableStateOf("") }
    var messages by remember {
        mutableStateOf(listOf(ChatMessage("assistant", "Neural core is not connected yet. Load a verified local model when native inference is available.")))
    }

    PhantomDesignSystem {
        Surface(Modifier.fillMaxSize(), color = PhantomColors.PhantomBlack) {
            Column(Modifier.fillMaxSize().padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(PhantomColors.GhostCyan, CircleShape))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("༒︎ PHANTOM GHOST ༒︎", color = PhantomColors.SoftWhite, fontSize = 20.sp)
                        Text("INTELLIGENCE BEYOND THE ORDINARY", color = PhantomColors.GhostCyan, fontSize = 9.sp, letterSpacing = 1.5.sp)
                    }
                }
                Spacer(Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PhantomOrb(loading = false)
                }
                Spacer(Modifier.height(8.dp))
                Text("NEURAL CORE · WAITING FOR MODEL", color = PhantomColors.GhostCyan, fontSize = 10.sp)
                Spacer(Modifier.height(8.dp))
                VoiceLockBadge(active = false)
                Spacer(Modifier.height(16.dp))
                Text("THOUGHT STREAM", color = PhantomColors.PhantomPurple, fontSize = 11.sp)
                ThoughtStream(
                    lines = messages.takeLast(8).map { "${it.role.uppercase()}: ${it.content}" },
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp)
                )
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Message PHANTOM GHOST…") },
                    shape = PhantomShapes.Input,
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        val prompt = input.trim()
                        if (prompt.isNotEmpty()) {
                            messages = messages + ChatMessage("user", prompt) + ChatMessage("assistant", engine.reply(prompt))
                            input = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("SEND MESSAGE") }
            }
        }
    }
}
