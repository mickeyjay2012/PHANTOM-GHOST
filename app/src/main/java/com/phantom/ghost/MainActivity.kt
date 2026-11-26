package com.phantom.ghost

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phantom.ghost.ui.*
import com.phantom.ghost.ai.LlamaEngine
import com.phantom.ghost.ai.TokenCounter
import com.phantom.ghost.ai.ConversationSummarizer
import com.phantom.ghost.model.ModelManager
import com.phantom.ghost.model.ModelStatus
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val engine = LlamaEngine(
            tokenCounter = TokenCounter { text -> (text.length / 4).coerceAtLeast(1) },
            summarizer = ConversationSummarizer { turns ->
                turns.joinToString(" ") { "${it.role}: ${it.content}" }.take(1400)
            }
        )
        val modelManager = ModelManager(applicationContext)
        setContent { PhantomGhostApp(engine, modelManager) }
    }
}

@Composable
private fun PhantomGhostApp(engine: LlamaEngine, modelManager: ModelManager) {
    val modelStatus by modelManager.modelState.collectAsState()
    val downloadProgress by modelManager.downloadProgress.collectAsState()
    val modelError by modelManager.lastError.collectAsState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(modelManager) { modelManager.checkModelPresence() }
    var screen by remember { mutableStateOf(PhantomScreen.ASSISTANT) }
    var showAction by remember { mutableStateOf(false) }
    var showBanner by remember { mutableStateOf(true) }
    PhantomDesignSystem {
        Surface(Modifier.fillMaxSize(), color=PhantomColors.PhantomBlack) {
            Column(Modifier.fillMaxSize().background(PhantomColors.PhantomBlack)) {
                if (screen != PhantomScreen.ASSISTANT && showBanner) {
                    SmartNotificationBanner("PHANTOM GHOST is standing by.") { showBanner=false }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when(screen) {
                        PhantomScreen.ONBOARDING -> OnboardingScreen { screen=PhantomScreen.VOICE }
                        PhantomScreen.VOICE -> VoiceprintEnrollmentScreen { screen=PhantomScreen.ASSISTANT }
                        PhantomScreen.ASSISTANT -> MainAssistantScreen(
                            onConfirmAction={showAction=true},
                            onOpenMemory={screen=PhantomScreen.MEMORY},
                            onOpenModels={screen=PhantomScreen.MODELS},
                            canSend = modelStatus == ModelStatus.LOADED || modelStatus == ModelStatus.ACTIVE,
                            modelStatusLabel = when (modelStatus) {
                                ModelStatus.NOT_FOUND -> "Model not installed · open SYSTEM"
                                ModelStatus.DOWNLOADING -> "Downloading model…"
                                ModelStatus.VERIFYING -> "Verifying model…"
                                ModelStatus.READY -> "Model verified · open SYSTEM to load"
                                ModelStatus.LOADED, ModelStatus.ACTIVE -> "Neural core ready"
                                ModelStatus.ERROR -> "Model error · open SYSTEM"
                            },
                            onSend={input, mode -> engine.generateResponse(input, mode)}
                        )
                        PhantomScreen.MEMORY -> MemoryProfileScreen()
                        PhantomScreen.MODELS -> ModelManagerScreen(
                            status = modelStatus,
                            progress = downloadProgress,
                            error = modelError,
                            onDownloadOrLoad = {
                                scope.launch {
                                    runCatching {
                                        when (modelStatus) {
                                            ModelStatus.READY -> modelManager.loadModel(engine)
                                            ModelStatus.NOT_FOUND, ModelStatus.ERROR -> {
                                                modelManager.downloadModel()
                                                modelManager.loadModel(engine)
                                            }
                                            else -> Unit
                                        }
                                    }
                                }
                            },
                            onForceRedownload = {
                                scope.launch { modelManager.forceRedownload() }
                            }
                        )
                    }
                }
                if(screen != PhantomScreen.ASSISTANT) {
                    NavigationBar(containerColor=PhantomColors.PhantomCharcoal, tonalElevation=0.dp) {
                        listOf(
                            Triple(PhantomScreen.ONBOARDING,"SETUP","⌂"),
                            Triple(PhantomScreen.VOICE,"VOICE","◉"),
                            Triple(PhantomScreen.ASSISTANT,"GHOST","✧"),
                            Triple(PhantomScreen.MEMORY,"MEMORY","◇"),
                            Triple(PhantomScreen.MODELS,"SYSTEM","▦")
                        ).forEach { (target,label,icon) ->
                            NavigationBarItem(selected=screen==target,onClick={screen=target},
                                icon={Text(icon,color=if(screen==target) PhantomColors.GhostCyan else PhantomColors.Muted)},
                                label={Text(label,style=PhantomType.label,fontSize=8.sp)},
                                colors=NavigationBarItemDefaults.colors(
                                    selectedIconColor=PhantomColors.GhostCyan,selectedTextColor=PhantomColors.GhostCyan,
                                    indicatorColor=PhantomColors.GhostCyan.copy(alpha=.10f),
                                    unselectedTextColor=PhantomColors.Muted))
                        }
                    }
                }
            }
            if(showAction) ActionConfirmationDialog(
                "Open an app or navigate using an approved Android action.",
                onConfirm={showAction=false},onAbort={showAction=false}
            )
        }
    }
}
