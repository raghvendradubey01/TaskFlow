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
        "Tap to enable \"Hey TaskFlow\" hands-free voice assistant"
    )
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _lastCreatedTask = MutableStateFlow<TaskItem?>(null)
    val lastCreatedTask: StateFlow<TaskItem?> = _lastCreatedTask.asStateFlow()

    private val _isOverlayVisible = MutableStateFlow(false)
    val isOverlayVisible: StateFlow<Boolean> = _isOverlayVisible.asStateFlow()

    private val wakeWordPatterns = listOf(
        "hey taskflow",
        "hey task flow",
        "hi taskflow",
        "hi task flow",
        "okay taskflow",
        "ok taskflow",
        "okay task flow",
        "ok task flow",
        "hello taskflow",
        "hello task flow",
        "hey tax flow",
        "hey test flow",
        "hey tech flow",
        "taskflow",
        "task flow"
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
            _statusMessage.value = "Microphone permission is required for 'Hey TaskFlow'"
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

    internal fun setServiceRunning(running: Boolean) {
        _isServiceEnabled.value = running
    }

    /**
     * Checks if spoken text contains "Hey TaskFlow" (or common STT phonetic variants).
     * Returns a Pair(matchedWakeWord: Boolean, extractedCommandAfterWakeWord: String).
     */
    fun extractWakeWordCommand(spokenText: String): Pair<Boolean, String> {
        val normalized = spokenText.trim()
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
        return false to ""
    }
}
