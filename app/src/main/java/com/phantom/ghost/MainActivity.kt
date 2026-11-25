package com.phantom.ghost

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phantom.ghost.ai.ChatMessage
import com.phantom.ghost.ai.DemoAssistantEngine

private val Obsidian = Color(0xFF0A0A0A)
private val Charcoal = Color(0xFF121212)
private val Cyan = Color(0xFF00F0FF)
private val Purple = Color(0xFF9D00FF)
private val SoftWhite = Color(0xFFE0E0E0)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PhantomGhostApp() }
    }
}

@Composable
private fun PhantomGhostApp() {
    val engine = remember { DemoAssistantEngine() }
    var input by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf(listOf(ChatMessage("assistant", "PHANTOM GHOST interface is ready. The AI model is not connected yet."))) }

    MaterialTheme(colorScheme = darkColorScheme(background = Obsidian, surface = Charcoal, primary = Cyan, secondary = Purple, onBackground = SoftWhite, onSurface = SoftWhite)) {
        Surface(Modifier.fillMaxSize(), color = Obsidian) {
            Column(Modifier.fillMaxSize().padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).background(Cyan, CircleShape))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("༒︎ PHANTOM GHOST ༒︎", color = SoftWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("INTELLIGENCE BEYOND THE ORDINARY", color = Cyan, fontSize = 9.sp, letterSpacing = 1.5.sp)
                    }
                }
                Spacer(Modifier.height(24.dp))
                Surface(color = Charcoal, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("◉", color = Cyan, fontSize = 52.sp)
                        Text("SYSTEM READY", color = Cyan, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        Text("Local interface preview · model runtime pending", color = SoftWhite.copy(alpha = .6f), fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.height(18.dp))
                Text("CONVERSATION", color = Purple, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Column(Modifier.weight(1f).fillMaxWidth().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    messages.takeLast(8).forEach { message ->
                        Surface(color = if (message.role == "user") Color(0xFF18252A) else Charcoal, shape = RoundedCornerShape(14.dp)) {
                            Text(message.content, modifier = Modifier.fillMaxWidth().padding(14.dp), color = SoftWhite, fontSize = 14.sp)
                        }
                    }
                }
                OutlinedTextField(value = input, onValueChange = { input = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Message PHANTOM GHOST…") }, shape = RoundedCornerShape(16.dp), singleLine = true)
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    val prompt = input.trim()
                    if (prompt.isNotEmpty()) {
                        messages = messages + ChatMessage("user", prompt) + ChatMessage("assistant", engine.reply(prompt))
                        input = ""
                    }
                }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Obsidian)) {
                    Text("SEND MESSAGE", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
