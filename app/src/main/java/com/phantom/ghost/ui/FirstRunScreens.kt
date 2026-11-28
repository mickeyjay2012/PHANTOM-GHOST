package com.phantom.ghost.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun GhostNameSetupScreen(onContinue: (String) -> Unit) {
    var name by remember { mutableStateOf("Phantom") }
    Column(
        Modifier.fillMaxSize().background(PhantomColors.PhantomBlack)
            .verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        PhantomOrb(132.dp)
        Spacer(Modifier.height(24.dp))
        PhantomBrandHeader()
        Spacer(Modifier.height(16.dp))
        ScreenTitle("IDENTITY / PERSONALIZATION", "Name your Ghost", "Choose the name you want your assistant to use.")
        Spacer(Modifier.height(20.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = name, onValueChange = { name = it.take(32) },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Assistant name") },
                placeholder = { Text("e.g. Phantom") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
            )
        }
        Spacer(Modifier.height(18.dp))
        PhantomButton("CONTINUE", Modifier.fillMaxWidth(), enabled = name.isNotBlank()) {
            onContinue(name.trim())
        }
    }
}

@Composable
fun PhantomLogoRevealScreen(onContinue: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "logo_reveal")
    val pulse by transition.animateFloat(
        0.78f, 1.08f,
        infiniteRepeatable(tween(1450, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "logo_pulse"
    )
    Column(
        Modifier.fillMaxSize().background(PhantomColors.PhantomBlack).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(Modifier.size(230.dp).alpha(pulse), contentAlignment = Alignment.Center) {
            PhantomOrb(196.dp, AgentState.IDLE, loading = true)
        }
        Spacer(Modifier.height(28.dp))
        Text("SYSTEM IDENTITY INITIALIZED", style = PhantomType.label, color = PhantomColors.GhostCyan)
        Text("PHANTOM GHOST", color = PhantomColors.GhostWhite, fontSize = 27.sp)
        Text("Intelligence Beyond the Ordinary", color = PhantomColors.Muted, fontSize = 12.sp)
        Spacer(Modifier.height(32.dp))
        PhantomButton("CONTINUE", Modifier.fillMaxWidth(), onClick = onContinue)
    }
}

@Composable
fun VoiceSetupChoiceScreen(
    onEnableVoiceInput: () -> Unit,
    onSkip: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().background(PhantomColors.PhantomBlack)
            .verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        WaveformIcon(Modifier.size(150.dp))
        Spacer(Modifier.height(20.dp))
        ScreenTitle("VOICE / INPUT", "Set up voice", "Allow microphone access so you can speak to your assistant.")
        Spacer(Modifier.height(16.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Text("Voice input", color = PhantomColors.GhostWhite)
            Text("This enables speech-to-text when you choose to speak. It does not yet create a verified speaker identity or a voiceprint. That requires a separate on-device speaker-recognition model.", color = PhantomColors.Muted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(16.dp))
        PhantomButton("ENABLE MICROPHONE", Modifier.fillMaxWidth(), onClick = onEnableVoiceInput)
        TextButtonLike("SKIP FOR NOW", onSkip)
    }
}

@Composable
fun AlwaysAvailableSetupScreen(onChoice: (Boolean) -> Unit) {
    Column(
        Modifier.fillMaxSize().background(PhantomColors.PhantomBlack)
            .verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        PhantomOrb(116.dp, AgentState.LISTENING, loading = true)
        Spacer(Modifier.height(20.dp))
        ScreenTitle("HANDS-FREE MODE", "Should Ghost stay available?", "An advanced assistant experience — with clear Android limits.")
        Spacer(Modifier.height(14.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Text("What this means", color = PhantomColors.GhostWhite)
            Text(
                "Hands-free mode would let you start voice interactions without opening the chat each time. Android requires visible microphone permission and an ongoing foreground-service notification. Battery saver, device restarts and system restrictions can stop background listening. It cannot secretly listen or guarantee it runs forever.",
                color = PhantomColors.Muted, fontSize = 12.sp
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "This first setup records your preference only. Continuous wake-word listening is not enabled until its on-device wake-word model and compliant foreground service are implemented and tested.",
                color = PhantomColors.GhostCyan, fontSize = 12.sp
            )
        }
        Spacer(Modifier.height(16.dp))
        PhantomButton("YES, SET UP HANDS-FREE LATER", Modifier.fillMaxWidth()) { onChoice(true) }
        TextButtonLike("NO, OPEN ONLY WHEN I CHOOSE", { onChoice(false) })
    }
}

@Composable
fun PersonalAddressSetupScreen(onContinue: (String, String) -> Unit) {
    var preferredName by remember { mutableStateOf("") }
    var selfReference by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().background(PhantomColors.PhantomBlack)
            .verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        PhantomOrb(104.dp)
        Spacer(Modifier.height(18.dp))
        ScreenTitle("PERSONALIZATION", "How should Ghost address you?", "These preferences stay on this device and can be changed later.")
        Spacer(Modifier.height(16.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            OutlinedTextField(
                preferredName, { preferredName = it.take(40) },
                Modifier.fillMaxWidth(), label = { Text("What should Ghost call you?") }, singleLine = true
            )
            OutlinedTextField(
                selfReference, { selfReference = it.take(40) },
                Modifier.fillMaxWidth(), label = { Text("How do you refer to yourself? (optional)") }, singleLine = true
            )
        }
        Spacer(Modifier.height(18.dp))
        PhantomButton("FINISH SETUP", Modifier.fillMaxWidth(), enabled = preferredName.isNotBlank()) {
            onContinue(preferredName.trim(), selfReference.trim())
        }
    }
}

@Composable
private fun TextButtonLike(label: String, onClick: () -> Unit) {
    androidx.compose.material3.TextButton(onClick = onClick) {
        Text(label, color = PhantomColors.Muted)
    }
}
