package com.phantom.ghost.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import com.phantom.ghost.ai.PersonaMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

enum class PhantomScreen { ONBOARDING, VOICE, ASSISTANT, MEMORY, MODELS }
enum class AgentState { IDLE, LISTENING, ACTING }
enum class ThoughtKind { THOUGHT, ACTION, OBSERVATION }

@Composable
fun PhantomBrandHeader(compact: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        PhantomMark(Modifier.size(if (compact) 34.dp else 46.dp))
        Spacer(Modifier.width(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("PHANTOM", color = PhantomColors.GhostWhite, fontSize = if (compact) 16.sp else 21.sp,
                fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
            Spacer(Modifier.width(5.dp))
            Text("GHOST", color = PhantomColors.GhostCyan, fontSize = if (compact) 16.sp else 21.sp,
                fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        }
    }
}

@Composable
fun PhantomMark(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val p = Path().apply {
            moveTo(w * .5f, h * .04f); lineTo(w * .84f, h * .28f); lineTo(w * .94f, h * .55f)
            lineTo(w * .72f, h * .72f); lineTo(w * .64f, h * .96f); lineTo(w * .5f, h * .82f)
            lineTo(w * .36f, h * .96f); lineTo(w * .28f, h * .72f); lineTo(w * .06f, h * .55f)
            lineTo(w * .16f, h * .28f); close()
        }
        drawPath(p, Brush.linearGradient(listOf(PhantomColors.GhostCyan, PhantomColors.PhantomPurple)),
            style = Stroke(width = size.minDimension * .055f, cap = StrokeCap.Round))
        drawLine(PhantomColors.GhostCyan, Offset(w*.30f,h*.43f), Offset(w*.46f,h*.52f), size.minDimension*.05f)
        drawLine(PhantomColors.GhostCyan, Offset(w*.70f,h*.43f), Offset(w*.54f,h*.52f), size.minDimension*.05f)
    }
}

@Composable
fun PhantomOrb(size: Dp = 76.dp, state: AgentState = AgentState.IDLE, loading: Boolean = false) {
    val transition = rememberInfiniteTransition(label = "orb")
    val pulse by transition.animateFloat(0.82f, 1.08f,
        infiniteRepeatable(tween(if (state == AgentState.LISTENING) 420 else 1450, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    val color = when (state) {
        AgentState.LISTENING -> PhantomColors.GhostCyan
        AgentState.ACTING -> PhantomColors.PhantomPurple
        AgentState.IDLE -> Color(0xFF242B35)
    }
    Box(Modifier.size(size).alpha(if (loading || state == AgentState.LISTENING) pulse else 1f).glowEffect(color, 18.dp)
        .clip(CircleShape).background(Brush.radialGradient(listOf(color.copy(alpha=.95f), color.copy(alpha=.12f), PhantomColors.PhantomBlack))),
        contentAlignment = Alignment.Center) {
        PhantomMark(Modifier.fillMaxSize(.68f))
    }
}

@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.glassCard().padding(16.dp), content = content)
}

@Composable
private fun ScreenTitle(kicker: String, title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(kicker, style = PhantomType.label, color = PhantomColors.GhostCyan)
        Text(title, color = PhantomColors.GhostWhite, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        if (subtitle != null) Text(subtitle, color = PhantomColors.Muted, fontSize = 13.sp)
    }
}

@Composable
fun OnboardingScreen(onContinue: () -> Unit) {
    var name by remember { mutableStateOf("") }
    BoxWithConstraints(Modifier.fillMaxSize().background(PhantomColors.PhantomBlack), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val gap = 28.dp.toPx()
            for (x in 0..(size.width/gap).toInt()) for (y in 0..(size.height/gap).toInt())
                drawCircle(PhantomColors.GhostCyan.copy(alpha=.10f), 1.1.dp.toPx(), Offset(x*gap,y*gap))
        }
        val availableWidth = maxWidth
        Column(Modifier.fillMaxWidth(.88f).widthIn(max=460.dp).padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(22.dp)) {
            PhantomOrb(size = if (availableWidth < 380.dp) 112.dp else 148.dp)
            PhantomBrandHeader()
            Text("INTELLIGENCE BEYOND THE ORDINARY", style=PhantomType.label, color=PhantomColors.GhostCyan, textAlign=TextAlign.Center)
            GlassCard(Modifier.fillMaxWidth()) {
                Text("PERSONALIZE YOUR ASSISTANT", style=PhantomType.label, color=PhantomColors.PhantomPurple)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(value=name, onValueChange={name=it}, modifier=Modifier.fillMaxWidth(),
                    placeholder={Text("Name your Ghost…", color=PhantomColors.Muted)}, singleLine=true,
                    shape=PhantomShapes.Input, colors=OutlinedTextFieldDefaults.colors(
                        focusedBorderColor=PhantomColors.GhostCyan, unfocusedBorderColor=PhantomColors.GhostCyan.copy(alpha=.35f)))
            }
            PhantomButton("CONTINUE", Modifier.fillMaxWidth(), enabled=name.isNotBlank(), onClick=onContinue)
        }
    }
}

@Composable
fun VoiceprintEnrollmentScreen(onStart: () -> Unit) {
    var sample by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),
        horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.spacedBy(22.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.End) {
            StatusBadge("VOICE LOCK: ENROLLING", PhantomColors.PhantomPurple)
        }
        ScreenTitle("IDENTITY / AUDIO", "Voiceprint enrollment", "Create a local voice profile with three separate samples.")
        Box(Modifier.size(184.dp), contentAlignment=Alignment.Center) {
            CircularProgressIndicator(progress={sample/3f}, modifier=Modifier.fillMaxSize(),
                color=PhantomColors.GhostCyan, trackColor=PhantomColors.GhostCyan.copy(alpha=.12f), strokeWidth=4.dp)
            WaveformIcon(Modifier.size(116.dp))
        }
        Text("SAMPLE ${(sample+1).coerceAtMost(3)} OF 3", style=PhantomType.label, color=PhantomColors.GhostCyan)
        GlassCard(Modifier.fillMaxWidth()) {
            Text("Enrollment is optional", color=PhantomColors.GhostWhite, fontWeight=FontWeight.SemiBold)
            Text("Voice matching is a convenience feature, not proof of identity. Your voice profile should remain local and deletable.",
                color=PhantomColors.Muted, fontSize=12.sp)
        }
        PhantomButton(if(sample<3) "RECORD SAMPLE" else "COMPLETE ENROLLMENT", Modifier.fillMaxWidth(), onClick={
            if(sample<3) sample++ else onStart()
        })
        Text("Each sample is a separate 10-second recording in the completed audio flow.", color=PhantomColors.Muted, fontSize=11.sp, textAlign=TextAlign.Center)
    }
}

@Composable
fun WaveformIcon(modifier: Modifier = Modifier) {
    val transition= rememberInfiniteTransition(label="wave")
    val phase by transition.animateFloat(0f,1f,infiniteRepeatable(tween(1000),RepeatMode.Restart),label="phase")
    Canvas(modifier) {
        val bars=19
        for(i in 0 until bars) {
            val x=size.width*(i+.5f)/bars
            val amp=(.18f + .72f*kotlin.math.abs(kotlin.math.sin(i*.7f+phase*6.28f))).toFloat()
            drawLine(PhantomColors.GhostCyan.copy(alpha=.45f+.5f*amp), Offset(x,size.height*(.5f-amp*.42f)),
                Offset(x,size.height*(.5f+amp*.42f)), size.width*.025f, StrokeCap.Round)
        }
    }
}

@Composable
fun MainAssistantScreen(
    onConfirmAction: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenModels: () -> Unit,
    canSend: Boolean = true,
    modelStatusLabel: String = "MODEL STATUS UNKNOWN",
    voiceState: com.phantom.ghost.voice.VoiceUiState = com.phantom.ghost.voice.VoiceUiState(),
    onStartListening: (PersonaMode) -> Unit = {},
    onStopListening: () -> Unit = {},
    onSend: (String, PersonaMode) -> Flow<String>
) {
    var message by remember { mutableStateOf("") }
    var activePersonaMode by remember { mutableStateOf(PersonaMode.STANDARD) }
    val entries = remember { mutableStateListOf(
        "Neural interface initialized. Load a verified local GGUF model to begin inference.",
        "Persona changes response style; they do not disable safety or permission checks."
    ) }
    var state by remember { mutableStateOf(AgentState.IDLE) }
    val scope = rememberCoroutineScope()
    var generating by remember { mutableStateOf(false) }
    LaunchedEffect(voiceState.response) {
        if (voiceState.response.isNotBlank()) {
            val line = "PHANTOM (VOICE): " + voiceState.response
            if (entries.lastOrNull()?.startsWith("PHANTOM (VOICE): ") == true) entries[entries.lastIndex] = line else entries.add(line)
        }
    }
    var lastThoughtEvent by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(voiceState.thoughtEvents) {
        voiceState.thoughtEvents.lastOrNull()?.let { event ->
            if (event != lastThoughtEvent) {
                entries.add("VOICE / TTS · $event")
                lastThoughtEvent = event
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxHeight < 700.dp || maxWidth < 360.dp
        Scaffold(containerColor=PhantomColors.PhantomBlack, bottomBar={}) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal=if(compact) 14.dp else 20.dp)) {
                Row(Modifier.fillMaxWidth().padding(top=8.dp,bottom=12.dp), verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.SpaceBetween) {
                    PhantomBrandHeader(compact=true)
                    StatusBadge("LOCAL CORE",PhantomColors.GhostCyan)
                }
                val orbState = when {
                    voiceState.listening -> AgentState.LISTENING
                    generating || voiceState.transcribing || voiceState.preparing -> AgentState.ACTING
                    else -> state
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                    Column {
                        Text("THOUGHT STREAM",style=PhantomType.label,color=PhantomColors.PhantomPurple)
                        Text(when {
                            voiceState.preparing -> "Preparing Whisper model…"
                            voiceState.listening -> "LISTENING · Speak now"
                            voiceState.transcribing -> "Transcribing audio…"
                            generating -> "Generating response…"
                            canSend -> "Ready when you are."
                            else -> modelStatusLabel
                        }, color=if (voiceState.listening) PhantomColors.GhostCyan else PhantomColors.Muted,fontSize=12.sp)
                    }
                    PhantomOrb(48.dp,state=orbState,loading=generating || voiceState.transcribing || voiceState.preparing)
                }
                PersonaToggle(activePersonaMode, enabled = !generating && !voiceState.listening && !voiceState.preparing) { activePersonaMode = it }
                if (voiceState.transcript.isNotBlank()) LiveTranscriptText(voiceState.transcript)
                if (voiceState.error != null) Text(voiceState.error,Modifier.fillMaxWidth().padding(top=6.dp),color=Color(0xFFFF8585),fontSize=12.sp)
                LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(top=10.dp,bottom=8.dp),
                    verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    items(entries) { item -> ThoughtStreamItem(ThoughtKind.OBSERVATION,item) }
                }
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp), modifier=Modifier.fillMaxWidth().padding(bottom=10.dp)) {
                    OutlinedButton(onClick=onOpenMemory,modifier=Modifier.weight(1f)) { Text("MEMORY",fontSize=10.sp) }
                    OutlinedButton(onClick=onOpenModels,modifier=Modifier.weight(1f)) { Text("SYSTEM",fontSize=10.sp) }
                    OutlinedButton(onClick=onConfirmAction,modifier=Modifier.weight(1f)) { Text("ACTIONS",fontSize=10.sp) }
                }
                Surface(Modifier.fillMaxWidth().padding(bottom=12.dp),shape=RoundedCornerShape(28.dp),
                    color=PhantomColors.PhantomCharcoal,border=androidx.compose.foundation.BorderStroke(1.dp,PhantomColors.GhostCyan.copy(alpha=.3f))) {
                    Row(Modifier.padding(horizontal=12.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically) {
                        if (voiceState.listening) {
                            TextButton(onClick=onStopListening) { Text("STOP",color=Color(0xFFFF8585)) }
                        } else {
                            TextButton(
                                enabled=canSend && !generating && !voiceState.preparing && !voiceState.transcribing,
                                onClick={onStartListening(activePersonaMode)}
                            ) { Text("🎙",fontSize=18.sp,color=PhantomColors.GhostCyan) }
                        }
                        OutlinedTextField(value=message,onValueChange={message=it},modifier=Modifier.weight(1f),
                            placeholder={Text("Type a message…",fontSize=13.sp)},singleLine=true,
                            colors=OutlinedTextFieldDefaults.colors(unfocusedBorderColor=Color.Transparent,focusedBorderColor=Color.Transparent))
                        TextButton(
                            enabled = message.isNotBlank() && !generating && canSend && !voiceState.listening && !voiceState.transcribing,
                            onClick = {
                                val input = message.trim()
                                message = ""
                                generating = true
                                state = AgentState.ACTING
                                entries.add("YOU: " + input)
                                entries.add("")
                                scope.launch {
                                    val response = StringBuilder()
                                    try {
                                        onSend(input, activePersonaMode).collect { fragment ->
                                            response.append(fragment)
                                            entries[entries.lastIndex] = "PHANTOM: " + response.toString()
                                        }
                                        if (response.isEmpty()) entries[entries.lastIndex] = "No response was returned."
                                    } catch (error: Throwable) {
                                        entries[entries.lastIndex] = "CORE STATUS: " + (error.message ?: error.javaClass.simpleName)
                                    } finally {
                                        generating = false
                                        state = AgentState.IDLE
                                    }
                                }
                            }
                        ) { Text("SEND",color=PhantomColors.GhostCyan) }
                    }
                }
            }
        }
    }
}

@Composable
fun LiveTranscriptText(transcript: String) {
    GlassCard(Modifier.fillMaxWidth().padding(top=8.dp)) {
        Text("LIVE TRANSCRIPT",style=PhantomType.label,color=PhantomColors.GhostCyan)
        Text(transcript,color=PhantomColors.GhostWhite,fontSize=13.sp,lineHeight=19.sp)
    }
}

@Composable
fun PersonaToggle(mode: PersonaMode, enabled: Boolean = true, onModeChange: (PersonaMode) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)
            .glassCard().padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val standardSelected = mode == PersonaMode.STANDARD
        OutlinedButton(
            onClick = { onModeChange(PersonaMode.STANDARD) },
            enabled = enabled,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp, if (standardSelected) PhantomColors.GhostWhite else PhantomColors.Muted.copy(alpha = .35f)
            ),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = if (standardSelected) PhantomColors.GhostWhite.copy(alpha = .10f) else Color.Transparent
            )
        ) { Text("STANDARD", style = PhantomType.label, color = PhantomColors.GhostWhite) }
        OutlinedButton(
            onClick = { onModeChange(PersonaMode.UNRESTRICTED) },
            enabled = enabled,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp, if (!standardSelected) PhantomColors.GhostCyan else PhantomColors.PhantomPurple.copy(alpha = .45f)
            ),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = if (!standardSelected) PhantomColors.PhantomPurple.copy(alpha = .18f) else Color.Transparent
            )
        ) { Text("UNRESTRICTED", style = PhantomType.label, color = PhantomColors.GhostCyan) }
    }
}

@Composable
fun ThoughtStreamItem(kind: ThoughtKind, text: String) {
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment=Alignment.Top) {
            Text(kind.name,modifier=Modifier.width(94.dp),style=PhantomType.label,color=PhantomColors.GhostCyan)
            Text(text,color=PhantomColors.GhostWhite,fontSize=13.sp,lineHeight=19.sp)
        }
    }
}

@Composable
fun ActionConfirmationDialog(description: String, onConfirm: () -> Unit, onAbort: () -> Unit) {
    Dialog(onDismissRequest=onAbort) {
        GlassCard(Modifier.fillMaxWidth()) {
            Text("ACTION REQUEST",style=PhantomType.label,color=PhantomColors.GhostCyan)
            Spacer(Modifier.height(10.dp))
            Text("PHANTOM GHOST is about to:",color=PhantomColors.GhostWhite,fontWeight=FontWeight.Bold)
            Text(description,color=PhantomColors.GhostWhite)
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                PhantomButton("CONFIRM",Modifier.weight(1f),onClick=onConfirm)
                OutlinedButton(onClick=onAbort,modifier=Modifier.weight(1f)) { Text("ABORT",color=PhantomColors.Muted) }
            }
        }
    }
}

@Composable
fun MemoryProfileScreen() {
    LazyColumn(Modifier.fillMaxSize().padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item { ScreenTitle("PERSONAL DATA","Memory & profile","Your local assistant workspace.") }
        item { GlassCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment=Alignment.CenterVertically) { PhantomMark(Modifier.size(58.dp)); Spacer(Modifier.width(14.dp))
                Column { Text("GHOST PROFILE",style=PhantomType.label,color=PhantomColors.GhostCyan); Text("Local profile",fontSize=19.sp,color=PhantomColors.GhostWhite) } }
        } }
        item { MemoryGraph(Modifier.fillMaxWidth().height(150.dp)) }
        item { Text("RECENT MEMORY",style=PhantomType.label,color=PhantomColors.PhantomPurple) }
        items(listOf("Conversation context","Assistant preferences","Saved local notes")) { title ->
            GlassCard(Modifier.fillMaxWidth()) { Text(title,color=PhantomColors.GhostWhite); Text("Stored in your local profile",fontSize=11.sp,color=PhantomColors.Muted) }
        }
        item { Text("SETTINGS",style=PhantomType.label,color=PhantomColors.PhantomPurple) }
        items(listOf("Voice Lock","Model Selection","Power Mode")) { setting ->
            GlassCard(Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text(setting,color=PhantomColors.GhostWhite); Text("CONFIGURE  ›",style=PhantomType.label,color=PhantomColors.GhostCyan)
            } }
        }
    }
}

@Composable
fun MemoryGraph(modifier: Modifier = Modifier) {
    Canvas(modifier.glassCard().padding(12.dp)) {
        val points=listOf(Offset(size.width*.5f,size.height*.5f),Offset(size.width*.2f,size.height*.25f),
            Offset(size.width*.8f,size.height*.24f),Offset(size.width*.25f,size.height*.78f),Offset(size.width*.76f,size.height*.76f))
        points.drop(1).forEach { drawLine(PhantomColors.GhostCyan.copy(alpha=.45f),points[0],it,2.dp.toPx()) }
        points.forEachIndexed { i,p -> drawCircle(if(i==0) PhantomColors.PhantomPurple else PhantomColors.GhostCyan,
            if(i==0) 9.dp.toPx() else 6.dp.toPx(),p) }
    }
}

@Composable
fun SmartNotificationBanner(text: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp).glassCard()
        .clickable(onClick=onClick).padding(14.dp),verticalAlignment=Alignment.CenterVertically) {
        Box(Modifier.width(3.dp).height(42.dp).background(PhantomColors.GhostCyan))
        Spacer(Modifier.width(12.dp)); PhantomMark(Modifier.size(28.dp))
        Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) {
            Text("GHOST NOTICE",style=PhantomType.label,color=PhantomColors.GhostCyan)
            Text(text,color=PhantomColors.GhostWhite,fontSize=12.sp)
            Text("TAP TO VIEW",style=PhantomType.label,color=PhantomColors.Muted)
        }
    }
}

@Composable
fun ModelManagerScreen(
    status: com.phantom.ghost.model.ModelStatus,
    progress: Float,
    whisperModelLabel: String = "Not loaded",
    voiceProvenance: String = "LJSpeech voice not loaded",
    error: String?,
    onDownloadOrLoad: () -> Unit,
    onForceRedownload: () -> Unit
) {
    var context by remember { mutableFloatStateOf(1024f) }
    val statusText = when (status) {
        com.phantom.ghost.model.ModelStatus.NOT_FOUND -> "NOT FOUND"
        com.phantom.ghost.model.ModelStatus.DOWNLOADING -> "DOWNLOADING · ${(progress * 100).toInt()}%"
        com.phantom.ghost.model.ModelStatus.VERIFYING -> "VERIFYING SHA-256"
        com.phantom.ghost.model.ModelStatus.READY -> "READY · NOT LOADED"
        com.phantom.ghost.model.ModelStatus.LOADED -> "LOADED"
        com.phantom.ghost.model.ModelStatus.ACTIVE -> "ACTIVE"
        com.phantom.ghost.model.ModelStatus.ERROR -> "ERROR"
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement=Arrangement.spacedBy(16.dp)) {
        ScreenTitle("DEVICE / PERFORMANCE","System health","Model files are stored in this app's private storage.")
        GlassCard(Modifier.fillMaxWidth()) {
            Text("MODEL STATUS",style=PhantomType.label,color=PhantomColors.GhostCyan)
            Text(com.phantom.ghost.model.ModelManager.MODEL_NAME,color=PhantomColors.GhostWhite,fontWeight=FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            StatusBadge(statusText, if (status == com.phantom.ghost.model.ModelStatus.ERROR) Color.Red else PhantomColors.GhostCyan)
            Text("Download size: approximately 2.02 GB · requires a stable connection and sufficient free storage.",
                fontSize=12.sp,color=PhantomColors.Muted)
            if (status == com.phantom.ghost.model.ModelStatus.DOWNLOADING || status == com.phantom.ghost.model.ModelStatus.VERIFYING) {
                LinearProgressIndicator(progress={ if (status == com.phantom.ghost.model.ModelStatus.VERIFYING) 1f else progress },
                    modifier=Modifier.fillMaxWidth(), color=PhantomColors.GhostCyan)
            }
            if (error != null) Text(error,color=Color(0xFFFF7777),fontSize=12.sp)
            Spacer(Modifier.height(8.dp))
            PhantomButton(
                when (status) {
                    com.phantom.ghost.model.ModelStatus.NOT_FOUND, com.phantom.ghost.model.ModelStatus.ERROR -> "DOWNLOAD MODEL"
                    com.phantom.ghost.model.ModelStatus.READY -> "LOAD MODEL"
                    com.phantom.ghost.model.ModelStatus.LOADED, com.phantom.ghost.model.ModelStatus.ACTIVE -> "MODEL LOADED"
                    else -> "PLEASE WAIT"
                },
                Modifier.fillMaxWidth(),
                enabled = status == com.phantom.ghost.model.ModelStatus.NOT_FOUND ||
                    status == com.phantom.ghost.model.ModelStatus.ERROR ||
                    status == com.phantom.ghost.model.ModelStatus.READY,
                onClick = onDownloadOrLoad
            )
            OutlinedButton(onClick=onForceRedownload, modifier=Modifier.fillMaxWidth(),
                enabled=status != com.phantom.ghost.model.ModelStatus.DOWNLOADING &&
                    status != com.phantom.ghost.model.ModelStatus.VERIFYING) {
                Text("FORCE RE-DOWNLOAD",color=PhantomColors.Muted)
            }
        }
        GlassCard(Modifier.fillMaxWidth()) {
            Text("WHISPER / EARS",style=PhantomType.label,color=PhantomColors.GhostCyan)
            Text(whisperModelLabel,color=PhantomColors.GhostWhite,fontSize=13.sp)
            Text("English base is preferred; multilingual base is selected automatically if needed.",fontSize=11.sp,color=PhantomColors.Muted)
        }
        GlassCard(Modifier.fillMaxWidth()) {
            Text("PIPER / VOICE OUTPUT",style=PhantomType.label,color=PhantomColors.GhostCyan)
            Text(voiceProvenance,color=PhantomColors.GhostWhite,fontSize=12.sp)
            Text("LJSpeech medium is primary; high is the fallback. The model revision and dataset provenance are recorded when a voice loads.",fontSize=11.sp,color=PhantomColors.Muted)
        }
        GlassCard(Modifier.fillMaxWidth()) {
            Text("CONTEXT WINDOW  ${context.toInt()} TOKENS",style=PhantomType.label,color=PhantomColors.GhostCyan)
            Slider(value=context,onValueChange={context=it},valueRange=512f..2048f,steps=2)
            Text("A 3B Q4 model can exceed available memory on some 4 GB devices. Loading is explicit; use a smaller model if Android reports memory pressure.",
                fontSize=12.sp,color=PhantomColors.Muted)
        }
    }
}

@Composable
fun StatusBadge(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(text,modifier=Modifier.border(1.dp,color.copy(alpha=.5f),RoundedCornerShape(20.dp))
        .background(color.copy(alpha=.08f),RoundedCornerShape(20.dp)).padding(horizontal=10.dp,vertical=6.dp),
        style=PhantomType.label,color=color)
}

@Composable
fun PhantomButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(onClick=onClick,modifier=modifier.heightIn(min=48.dp),
        enabled=enabled,shape=RoundedCornerShape(24.dp),
        border=androidx.compose.foundation.BorderStroke(1.dp,PhantomColors.PhantomPurple),
        colors=ButtonDefaults.outlinedButtonColors(contentColor=PhantomColors.GhostWhite,disabledContentColor=PhantomColors.Muted)) {
        Text(text,style=PhantomType.label)
    }
}
