package com.example.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.data.model.TaskItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class VoiceAssistantState {
    OFF,
    LISTENING_WAKE_WORD,
    AWAITING_COMMAND,
    PROCESSING,
    SPEAKING_RESPONSE,
    ERROR
}

object VoiceAssistantController {
    private val _isServiceEnabled = MutableStateFlow(false)
    val isServiceEnabled: StateFlow<Boolean> = _isServiceEnabled.asStateFlow()

    private val _assistantState = MutableStateFlow(VoiceAssistantState.OFF)
    val assistantState: StateFlow<VoiceAssistantState> = _assistantState.asStateFlow()

    private val _liveTranscript = MutableStateFlow("")
    val liveTranscript: StateFlow<String> = _liveTranscript.asStateFlow()

    private val _statusMessage = MutableStateFlow(
        "Say \"Hey TaskFlow\" anytime to wake voice assistant"
    )
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _lastCreatedTask = MutableStateFlow<TaskItem?>(null)
    val lastCreatedTask: StateFlow<TaskItem?> = _lastCreatedTask.asStateFlow()

    private val _isOverlayVisible = MutableStateFlow(false)
    val isOverlayVisible: StateFlow<Boolean> = _isOverlayVisible.asStateFlow()

    private val _micRmsLevel = MutableStateFlow(0f)
    val micRmsLevel: StateFlow<Float> = _micRmsLevel.asStateFlow()

    // Exact & phonetic wake-word phrases recognized by Android SpeechRecognizer
    private val wakeWordPatterns = listOf(
        "hey taskflow",
        "hey task flow",
        "hey task floor",
        "hey task for",
        "hey task full",
        "hey task pro",
        "hey task slow",
        "hi taskflow",
        "hi task flow",
        "okay taskflow",
        "ok taskflow",
        "okay task flow",
        "ok task flow",
        "hello taskflow",
        "hello task flow",
        "hey tax flow",
        "hey tax floor",
        "hey test flow",
        "hey text flow",
        "hey tech flow",
        "hey pass flow",
        "hey fast flow",
        "hey ask flow",
        "a task flow",
        "hey task",
        "hi task",
        "ok task",
        "okay task",
        "taskflow",
        "task flow",
        "hey siri",
        "ok google",
        "alexa"
    )

    // Fuzzy regex to catch any accent/phonetic transcription of "Hey TaskFlow"
    private val fuzzyWakeWordRegex = Regex(
        pattern = """(?i)\b(?:(?:hey|hi|hello|ok|okay|yo|a)\s+)?(?:task|tax|test|tech|text|pass|fast|ask|das)\s*(?:flow|floor|for|full|pro|slow|go|low|glow)\b|\b(?:hey|hi|hello|ok|okay)\s+(?:task|tax|test|tech)\b"""
    )

    fun hasRecordAudioPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun startBackgroundAssistant(context: Context, wakeImmediately: Boolean = false) {
        if (!hasRecordAudioPermission(context)) {
            _assistantState.value = VoiceAssistantState.ERROR
            _statusMessage.value = "Grant Microphone permission to enable \"Hey TaskFlow\""
            return
        }
        val appContext = context.applicationContext
        val intent = Intent(appContext, TaskFlowVoiceService::class.java).apply {
            action = if (wakeImmediately) {
                TaskFlowVoiceService.ACTION_WAKE_NOW
            } else {
                TaskFlowVoiceService.ACTION_START_LISTENING
            }
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(appContext, intent)
            } else {
                appContext.startService(intent)
            }
            _isServiceEnabled.value = true
            if (wakeImmediately) {
                _isOverlayVisible.value = true
            }
        } catch (e: Exception) {
            _assistantState.value = VoiceAssistantState.ERROR
            _statusMessage.value = "Could not start voice service: ${e.localizedMessage ?: "Blocked"}"
        }
    }

    fun stopBackgroundAssistant(context: Context) {
        val appContext = context.applicationContext
        try {
            appContext.stopService(Intent(appContext, TaskFlowVoiceService::class.java))
        } catch (_: Exception) {
        }
        _isServiceEnabled.value = false
        _assistantState.value = VoiceAssistantState.OFF
        _liveTranscript.value = ""
        _micRmsLevel.value = 0f
        _isOverlayVisible.value = false
        _statusMessage.value = "Tap to enable \"Hey TaskFlow\" hands-free voice assistant"
    }

    fun triggerWakeNow(context: Context) {
        _isOverlayVisible.value = true
        _lastCreatedTask.value = null
        if (!hasRecordAudioPermission(context)) {
            _assistantState.value = VoiceAssistantState.ERROR
            _statusMessage.value = "Please grant Microphone permission to record voice tasks"
            return
        }
        startBackgroundAssistant(context, wakeImmediately = true)
    }

    fun submitSimulatedOrTypedVoiceCommand(context: Context, commandText: String) {
        if (commandText.isBlank()) return
        _isOverlayVisible.value = true
        val appContext = context.applicationContext
        val intent = Intent(appContext, TaskFlowVoiceService::class.java).apply {
            action = TaskFlowVoiceService.ACTION_PROCESS_TEXT_COMMAND
            putExtra(TaskFlowVoiceService.EXTRA_COMMAND_TEXT, commandText)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(appContext, intent)
            } else {
                appContext.startService(intent)
            }
        } catch (_: Exception) {
        }
    }

    fun dismissOverlay() {
        _isOverlayVisible.value = false
        _lastCreatedTask.value = null
        _micRmsLevel.value = 0f
        if (_isServiceEnabled.value && _assistantState.value == VoiceAssistantState.AWAITING_COMMAND) {
            _assistantState.value = VoiceAssistantState.LISTENING_WAKE_WORD
            _statusMessage.value = "Listening for \"Hey TaskFlow\" in background..."
            _liveTranscript.value = ""
        }
    }

    internal fun updateState(
        state: VoiceAssistantState,
        status: String,
        transcript: String = _liveTranscript.value,
        showOverlay: Boolean? = null,
        createdTask: TaskItem? = _lastCreatedTask.value
    ) {
        _assistantState.value = state
        _statusMessage.value = status
        _liveTranscript.value = transcript
        if (showOverlay != null) {
            _isOverlayVisible.value = showOverlay
        }
        _lastCreatedTask.value = createdTask
    }

    internal fun updateMicRms(rmsdB: Float) {
        val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
        _micRmsLevel.value = normalized
    }

    internal fun setServiceRunning(running: Boolean) {
        _isServiceEnabled.value = running
    }

    /**
     * Checks if spoken text contains "Hey TaskFlow" (or common STT phonetic variants).
     * Returns a Pair(matchedWakeWord: Boolean, extractedCommandAfterWakeWord: String).
     */
    fun extractWakeWordCommand(spokenText: String): Pair<Boolean, String> {
        val normalized = spokenText.trim()
        if (normalized.isBlank()) return false to ""
        val lower = normalized.lowercase()

        for (pattern in wakeWordPatterns) {
            val idx = lower.indexOf(pattern)
            if (idx >= 0) {
                val afterIndex = idx + pattern.length
                val remainder = normalized.substring(afterIndex)
                    .trimStart(',', '.', '!', '?', ':', ';', '-', ' ')
                    .trim()
                return true to remainder
            }
        }

        val regexMatch = fuzzyWakeWordRegex.find(normalized)
        if (regexMatch != null) {
            val afterIndex = regexMatch.range.last + 1
            val remainder = if (afterIndex < normalized.length) {
                normalized.substring(afterIndex)
                    .trimStart(',', '.', '!', '?', ':', ';', '-', ' ')
                    .trim()
            } else {
                ""
            }
            return true to remainder
        }

        return false to ""
    }
}
