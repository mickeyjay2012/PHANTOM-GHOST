package com.phantom.ghost

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.phantom.ghost.ui.*
import com.phantom.ghost.auth.AuthFlowScreen
import com.phantom.ghost.auth.SessionManager
import com.phantom.ghost.auth.SupabaseAuthRepository
import com.phantom.ghost.auth.AuthRepository
import com.phantom.ghost.actions.ActionConfirmationOverlay
import com.phantom.ghost.actions.ActionExecutor
import com.phantom.ghost.actions.GhostPlan
import com.phantom.ghost.actions.GhostPlanParser
import com.phantom.ghost.monitor.ProactiveMonitor
import com.phantom.ghost.ai.LlamaEngine
import com.phantom.ghost.ai.TokenCounter
import com.phantom.ghost.ai.ConversationSummarizer
import com.phantom.ghost.ai.PersonaMode
import com.phantom.ghost.ai.PersonaManager
import com.phantom.ghost.audio.AudioRecorder
import com.phantom.ghost.audio.KotlinPhonemizer
import com.phantom.ghost.audio.PiperEngine
import com.phantom.ghost.audio.PiperOnnxBackend
import com.phantom.ghost.audio.WhisperEngine
import com.phantom.ghost.audio.WhisperModelManager
import com.phantom.ghost.model.ModelManager
import com.phantom.ghost.model.ModelStatus
import com.phantom.ghost.voice.MainAssistantViewModel
import com.phantom.ghost.memory.EmbeddingEngine
import com.phantom.ghost.memory.LocalMemoryManager
import com.phantom.ghost.memory.ProfileManager
import com.phantom.ghost.memory.MemoryWorkerScheduler
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val profileManager = ProfileManager(applicationContext)
        val sessionManager = SessionManager(applicationContext)
        val authRepository = SupabaseAuthRepository(sessionManager)
        val embeddingEngine = EmbeddingEngine(applicationContext)
        val memoryManager = LocalMemoryManager(applicationContext, embeddingEngine, profileManager)
        if (profileManager.autoLearnEnabled) MemoryWorkerScheduler.schedule(applicationContext)
        val engine = LlamaEngine(
            tokenCounter = TokenCounter { text -> (text.length / 4).coerceAtLeast(1) },
            summarizer = ConversationSummarizer { turns ->
                turns.joinToString(" ") { "${it.role}: ${it.content}" }.take(1400)
            },
            personaManager = PersonaManager(profileManager.ghostName),
            memoryManager = memoryManager
        )
        val models = ModelManager(applicationContext)
        val actionExecutor = ActionExecutor(applicationContext)
        val proactiveMonitor = ProactiveMonitor(applicationContext)
        val voiceDirectory = File(getExternalFilesDir(null) ?: filesDir, "voices").apply { mkdirs() }
        val piper = PiperEngine(PiperOnnxBackend(phonemizer = KotlinPhonemizer(applicationContext)))
        val voiceVm = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                MainAssistantViewModel(engine, WhisperEngine(), WhisperModelManager(applicationContext), AudioRecorder(), piper, voiceDirectory, memoryManager) as T
        })[MainAssistantViewModel::class.java]
        setContent { PhantomGhostApp(engine, models, voiceVm, actionExecutor, proactiveMonitor, memoryManager, profileManager, authRepository, sessionManager) }
    }
}

@Composable
private fun PhantomGhostApp(engine: LlamaEngine, modelManager: ModelManager, voiceVm: MainAssistantViewModel, actionExecutor: ActionExecutor, proactiveMonitor: ProactiveMonitor, memoryManager: LocalMemoryManager, profileManager: ProfileManager, authRepository: AuthRepository, sessionManager: SessionManager) {
    val context = LocalContext.current
    val modelStatus by modelManager.modelState.collectAsState()
    val downloadProgress by modelManager.downloadProgress.collectAsState()
    val modelError by modelManager.lastError.collectAsState()
    val voiceState by voiceVm.state.collectAsState()
    val scope = rememberCoroutineScope()
    var pendingPersona by remember { mutableStateOf(PersonaMode.STANDARD) }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) voiceVm.startListening(pendingPersona)
    }
    var firstRunStage by remember {
        mutableStateOf(
            if (sessionManager.load() != null) {
                if (profileManager.onboardingComplete) FirstRunStage.COMPLETE else FirstRunStage.NAME_GHOST
            } else FirstRunStage.AUTH
        )
    }
    val setupMicPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        profileManager.voiceInputEnabled = granted
        firstRunStage = FirstRunStage.ALWAYS_AVAILABLE
    }
    val proactivePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val calendarGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
        if (calendarGranted) proactiveMonitor.schedule()
    }
    LaunchedEffect(modelManager) { modelManager.checkModelPresence() }
    var screen by remember { mutableStateOf(PhantomScreen.ASSISTANT) }
    var showAction by remember { mutableStateOf(false) }
    var pendingPlan by remember { mutableStateOf<GhostPlan?>(null) }
    var showBanner by remember { mutableStateOf(true) }
    PhantomDesignSystem {
        Surface(Modifier.fillMaxSize(), color=PhantomColors.PhantomBlack) {
            Column(Modifier.fillMaxSize().background(PhantomColors.PhantomBlack)) {
                if (firstRunStage == FirstRunStage.COMPLETE && screen != PhantomScreen.ASSISTANT && showBanner) SmartNotificationBanner("PHANTOM GHOST is standing by.") { showBanner=false }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (firstRunStage != FirstRunStage.COMPLETE) {
                        when (firstRunStage) {
                            FirstRunStage.AUTH -> AuthFlowScreen(authRepository) { firstRunStage = FirstRunStage.NAME_GHOST }
                            FirstRunStage.NAME_GHOST -> GhostNameSetupScreen { name ->
                                profileManager.ghostName = name
                                engine.updateGhostName(name)
                                firstRunStage = FirstRunStage.LOGO_REVEAL
                            }
                            FirstRunStage.LOGO_REVEAL -> PhantomLogoRevealScreen { firstRunStage = FirstRunStage.VOICE_SETUP }
                            FirstRunStage.VOICE_SETUP -> VoiceSetupChoiceScreen(
                                onEnableVoiceInput = {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                        profileManager.voiceInputEnabled = true
                                        firstRunStage = FirstRunStage.ALWAYS_AVAILABLE
                                    } else setupMicPermission.launch(Manifest.permission.RECORD_AUDIO)
                                },
                                onSkip = { profileManager.voiceInputEnabled = false; firstRunStage = FirstRunStage.ALWAYS_AVAILABLE }
                            )
                            FirstRunStage.ALWAYS_AVAILABLE -> AlwaysAvailableSetupScreen { requested ->
                                profileManager.handsFreeRequested = requested
                                firstRunStage = FirstRunStage.PERSONALIZE
                            }
                            FirstRunStage.PERSONALIZE -> PersonalAddressSetupScreen { preferred, reference ->
                                profileManager.preferredName = preferred
                                profileManager.selfReference = reference
                                engine.updateUserAddress(preferred, reference)
                                profileManager.onboardingComplete = true
                                firstRunStage = FirstRunStage.COMPLETE
                            }
                            FirstRunStage.COMPLETE -> Unit
                        }
                    } else when(screen) {
                        PhantomScreen.ONBOARDING -> OnboardingScreen { screen=PhantomScreen.VOICE }
                        PhantomScreen.VOICE -> VoiceprintEnrollmentScreen { screen=PhantomScreen.ASSISTANT }
                        PhantomScreen.ASSISTANT -> MainAssistantScreen(
                            onConfirmAction={showAction=true},
                            onOpenMemory={screen=PhantomScreen.MEMORY},
                            onOpenModels={screen=PhantomScreen.MODELS},
                            onOpenSettings={screen=PhantomScreen.SETTINGS},
                            canSend = modelStatus == ModelStatus.LOADED || modelStatus == ModelStatus.ACTIVE,
                            modelStatusLabel = when (modelStatus) {
                                ModelStatus.NOT_FOUND -> "Model not installed · open SYSTEM"
                                ModelStatus.DOWNLOADING -> "Downloading model…"
                                ModelStatus.VERIFYING -> "Verifying model…"
                                ModelStatus.READY -> "Model verified · open SYSTEM to load"
                                ModelStatus.LOADED, ModelStatus.ACTIVE -> "Neural core ready"
                                ModelStatus.ERROR -> "Model error · open SYSTEM"
                            },
                            voiceState=voiceState,
                            onStartListening={ mode ->
                                pendingPersona=mode
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) voiceVm.startListening(mode)
                                else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                            },
                            onStopListening={voiceVm.stopListening()},
                            onSend={input, mode -> engine.generateResponse(input, mode)},
                            onPlanCandidate={ raw ->
                                GhostPlanParser.parse(raw)?.let { plan -> pendingPlan = plan; showAction = true }
                            },
                            onPinMessage={ voiceVm.pinMessage(it) },
                            snackbarMessage=voiceState.snackbarMessage,
                            onSnackbarConsumed={ voiceVm.consumeSnackbar() }
                        )
                        PhantomScreen.MEMORY -> MemoryVaultScreen(
                            memoryManager = memoryManager,
                            profileManager = profileManager,
                            onAutoLearnChanged = { enabled ->
                                if (enabled) MemoryWorkerScheduler.schedule(context) else MemoryWorkerScheduler.cancel(context)
                            },
                            onRunNow = { MemoryWorkerScheduler.runNow(context) }
                        )
                        PhantomScreen.SETTINGS -> GhostSettingsScreen(
                            profileManager = profileManager,
                            onSave = { ghost, preferred, reference, handsFree ->
                                profileManager.ghostName = ghost
                                profileManager.preferredName = preferred
                                profileManager.selfReference = reference
                                profileManager.handsFreeRequested = handsFree
                                engine.updateGhostName(ghost)
                                engine.updateUserAddress(preferred, reference)
                            },
                            onSignOut = {
                                scope.launch {
                                    authRepository.signOut()
                                    profileManager.onboardingComplete = false
                                    firstRunStage = FirstRunStage.AUTH
                                    screen = PhantomScreen.ASSISTANT
                                }
                            }
                        )
                        PhantomScreen.MODELS -> ModelManagerScreen(
                            status=modelStatus, progress=downloadProgress, error=modelError,
                            whisperModelLabel=voiceState.activeWhisperModel,
                            voiceProvenance=voiceState.ttsProvenance,
                            onDownloadOrLoad={
                                scope.launch {
                                    runCatching {
                                        when(modelStatus) {
                                            ModelStatus.READY -> modelManager.loadModel(engine)
                                            ModelStatus.NOT_FOUND, ModelStatus.ERROR -> { modelManager.downloadModel(); modelManager.loadModel(engine) }
                                            else -> Unit
                                        }
                                    }
                                }
                            },
                            onForceRedownload={scope.launch { modelManager.forceRedownload() }}
                        )
                    }
                }
                if(firstRunStage == FirstRunStage.COMPLETE && screen != PhantomScreen.ASSISTANT) {
                    NavigationBar(containerColor=PhantomColors.PhantomCharcoal, tonalElevation=0.dp) {
                        listOf(
                            Triple(PhantomScreen.ONBOARDING,"SETUP","⌂"),
                            Triple(PhantomScreen.VOICE,"VOICE","◉"),
                            Triple(PhantomScreen.ASSISTANT,"GHOST","✧"),
                            Triple(PhantomScreen.MEMORY,"MEMORY","◇"),
                            Triple(PhantomScreen.MODELS,"SYSTEM","▦"),
                            Triple(PhantomScreen.SETTINGS,"SETTINGS","⚙")
                        ).forEach { (target,label,icon) ->
                            NavigationBarItem(selected=screen==target,onClick={screen=target},
                                icon={Text(icon,color=if(screen==target) PhantomColors.GhostCyan else PhantomColors.Muted)},
                                label={Text(label,style=PhantomType.label,fontSize=8.sp)},
                                colors=NavigationBarItemDefaults.colors(
                                    selectedIconColor=PhantomColors.GhostCyan,selectedTextColor=PhantomColors.GhostCyan,
                                    indicatorColor=PhantomColors.GhostCyan.copy(alpha=.10f),unselectedTextColor=PhantomColors.Muted))
                        }
                    }
                }
            }
            if (firstRunStage == FirstRunStage.COMPLETE && showAction) ActionConfirmationOverlay(
                executor = actionExecutor,
                plan = pendingPlan,
                onEnableProactiveMonitor = {
                    val permissions = buildList {
                        add(Manifest.permission.READ_CALENDAR)
                        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                    }.toTypedArray()
                    proactivePermission.launch(permissions)
                },
                onDisableProactiveMonitor = { proactiveMonitor.cancel() },
                onObservations = { execution ->
                    scope.launch {
                        engine.recordToolObservations(execution.observations.map {
                            "Step ${it.step}: ${it.action}. ${if (it.ok) "Dispatched" else "Failed"}. ${it.observation}"
                        })
                    }
                },
                onDismiss = { showAction = false; pendingPlan = null }
            )
        }
    }
}

private enum class FirstRunStage {
    AUTH, NAME_GHOST, LOGO_REVEAL, VOICE_SETUP, ALWAYS_AVAILABLE, PERSONALIZE, COMPLETE
}
