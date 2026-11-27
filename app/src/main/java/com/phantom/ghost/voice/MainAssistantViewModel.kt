package com.phantom.ghost.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phantom.ghost.ai.ChatMessage
import com.phantom.ghost.ai.LlamaEngine
import com.phantom.ghost.ai.PersonaMode
import com.phantom.ghost.audio.AudioRecorder
import com.phantom.ghost.audio.PiperEngine
import com.phantom.ghost.audio.PiperVoiceProvenance
import com.phantom.ghost.audio.VadEvent
import com.phantom.ghost.audio.VoiceActivityDetector
import com.phantom.ghost.audio.WhisperEngine
import com.phantom.ghost.audio.WhisperModelManager
import com.phantom.ghost.memory.LocalMemoryManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

data class VoiceUiState(
    val preparing: Boolean = false,
    val listening: Boolean = false,
    val transcribing: Boolean = false,
    val transcript: String = "",
    val response: String = "",
    val error: String? = null,
    val activeWhisperModel: String = "Not loaded",
    val thoughtEvents: List<String> = emptyList(),
    val ttsProvenance: String = "LJSpeech voice not loaded",
    val snackbarMessage: String? = null
)

class MainAssistantViewModel(
    private val llama: LlamaEngine,
    private val whisper: WhisperEngine,
    private val models: WhisperModelManager,
    private val recorder: AudioRecorder,
    private val piper: PiperEngine,
    private val voiceModelDirectory: File,
    private val memoryManager: LocalMemoryManager? = null
) : ViewModel() {
    private val vad = VoiceActivityDetector()
    private val finishing = AtomicBoolean(false)
    private var persona = PersonaMode.STANDARD
    private val mutableState = MutableStateFlow(VoiceUiState())
    val state: StateFlow<VoiceUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            llama.memoryEvents.collect { event ->
                addThoughtEvent("${event.type} · ${event.facts.joinToString(" | ")}")
            }
        }
    }

    fun pinMessage(message: ChatMessage) {
        viewModelScope.launch {
            val manager = memoryManager
            if (manager == null) {
                mutableState.value = mutableState.value.copy(snackbarMessage = "Memory system is not ready.")
                return@launch
            }
            runCatching { manager.pin(message.content) }
                .onSuccess { mutableState.value = mutableState.value.copy(snackbarMessage = "Memory Pinned") }
                .onFailure { error ->
                    mutableState.value = mutableState.value.copy(
                        snackbarMessage = error.message ?: "Could not pin this message."
                    )
                }
        }
    }

    fun consumeSnackbar() { mutableState.value = mutableState.value.copy(snackbarMessage = null) }

    private fun addThoughtEvent(event: String) {
        val events = (mutableState.value.thoughtEvents + event).takeLast(40)
        mutableState.value = mutableState.value.copy(thoughtEvents = events)
    }

    private fun onVoiceLoaded(provenance: PiperVoiceProvenance) {
        mutableState.value = mutableState.value.copy(
            ttsProvenance = "${provenance.modelName} · revision ${provenance.revision} · ${provenance.datasetStatus}"
        )
        addThoughtEvent("TTS_MODEL_LOADED · ${provenance.modelName}")
    }

    fun startListening(mode: PersonaMode) {
        if (mutableState.value.preparing || mutableState.value.listening || mutableState.value.transcribing) return
        persona = mode
        finishing.set(false)
        mutableState.value = mutableState.value.copy(
            preparing = true, transcript = "", response = "", error = null,
            listening = false, transcribing = false, thoughtEvents = emptyList()
        )
        viewModelScope.launch {
            try {
                val version = models.ensureModelLoaded(whisper)
                mutableState.value = mutableState.value.copy(activeWhisperModel = version)
                vad.reset()
                recorder.start(viewModelScope) { frame ->
                    when (vad.accept(frame)) {
                        VadEvent.SPEECH_STARTED -> mutableState.value = mutableState.value.copy(listening = true)
                        VadEvent.SPEECH_ENDED -> finishSpeech()
                        VadEvent.NO_SPEECH_TIMEOUT -> finishSilence()
                        VadEvent.NONE -> Unit
                    }
                }
                mutableState.value = mutableState.value.copy(preparing = false, listening = true)
            } catch (error: Throwable) {
                recorder.cancel()
                mutableState.value = mutableState.value.copy(
                    preparing = false, listening = false,
                    error = error.message ?: "Could not start microphone."
                )
            }
        }
    }

    fun stopListening() { if (mutableState.value.listening) finishSpeech() }

    private fun finishSpeech() {
        if (!finishing.compareAndSet(false, true)) return
        viewModelScope.launch {
            val pcm = recorder.stopAndSnapshot()
            mutableState.value = mutableState.value.copy(listening = false, transcribing = true)
            try {
                check(pcm.isNotEmpty()) { "No audio was captured." }
                val text = withContext(Dispatchers.IO) { whisper.transcribeSamples(pcm) }.trim()
                check(text.isNotBlank()) { "No speech was recognized. Try again closer to the microphone." }
                mutableState.value = mutableState.value.copy(transcribing = false, transcript = text)
                val speechChannel = Channel<String>(capacity = 64)
                val speechFlow = speechChannel.receiveAsFlow()
                val speechJob = launch(start = CoroutineStart.UNDISPATCHED) {
                    try {
                        piper.speakResponse(
                            tokens = speechFlow, modelDirectory = voiceModelDirectory,
                            onLowConfidence = { word -> addThoughtEvent("LOW_CONFIDENCE_PHONEME · $word") },
                            onVoiceLoaded = ::onVoiceLoaded
                        )
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (ttsError: Throwable) {
                        addThoughtEvent("TTS_UNAVAILABLE · ${ttsError.message ?: "voice model error"}")
                        speechFlow.collect { }
                    }
                }
                val response = StringBuilder()
                try {
                    llama.generateResponse(text, persona).collect { token ->
                        speechChannel.send(token)
                        response.append(token)
                        mutableState.value = mutableState.value.copy(response = response.toString())
                    }
                } finally { speechChannel.close() }
                speechJob.join()
            } catch (error: Throwable) {
                mutableState.value = mutableState.value.copy(
                    listening = false, transcribing = false,
                    error = error.message ?: "Voice processing failed."
                )
            } finally {
                finishing.set(false)
                vad.reset()
            }
        }
    }

    private fun finishSilence() {
        if (!finishing.compareAndSet(false, true)) return
        viewModelScope.launch {
            recorder.stopAndSnapshot()
            mutableState.value = mutableState.value.copy(
                preparing = false, listening = false,
                error = "No speech detected after 5 seconds. Tap the microphone to try again."
            )
            finishing.set(false)
            vad.reset()
        }
    }

    override fun onCleared() {
        recorder.cancel()
        piper.close()
        super.onCleared()
    }
}
