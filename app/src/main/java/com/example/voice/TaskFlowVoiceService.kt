package com.example.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.ai.TaskAgentService
import com.example.data.model.SubtaskEntry
import com.example.data.model.TaskAlertMode
import com.example.data.model.TaskItem
import com.example.data.model.TaskPriority
import com.example.data.repository.TaskRepository
import com.example.notifications.IncomingAlertController
import com.example.notifications.TaskReminderManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TaskFlowVoiceService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var speechRecognizer: SpeechRecognizer? = null
    private var ttsEngine: TextToSpeech? = null
    private var ttsReady = false
    private var isListeningActive = false
    private var restartJob: Job? = null
    private var reminderTickerJob: Job? = null
    private var cachedTasks: List<TaskItem> = emptyList()
    private val firedBackgroundReminders = mutableMapOf<String, Long>()
    private val serviceStartTimeMillis = System.currentTimeMillis()

    override fun onCreate() {
        super.onCreate()
        ensureVoiceNotificationChannel()
        initTextToSpeech()
        startBackgroundReminderMonitor()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START_LISTENING

        if (action == ACTION_STOP_SERVICE) {
            VoiceAssistantController.setServiceRunning(false)
            VoiceAssistantController.updateState(
                state = VoiceAssistantState.OFF,
                status = "Tap to enable \"Hey TaskFlow\" hands-free voice assistant",
                transcript = "",
                showOverlay = false
            )
            stopSelf()
            return START_NOT_STICKY
        }

        if (!VoiceAssistantController.hasRecordAudioPermission(this)) {
            VoiceAssistantController.setServiceRunning(false)
            VoiceAssistantController.updateState(
                state = VoiceAssistantState.ERROR,
                status = "Grant Microphone permission to enable \"Hey TaskFlow\""
            )
            stopSelf()
            return START_NOT_STICKY
        }

        startAsForeground()
        VoiceAssistantController.setServiceRunning(true)

        when (action) {
            ACTION_WAKE_NOW -> {
                playAssistantChime()
                bringAppToForegroundForVoice()
                VoiceAssistantController.updateState(
                    state = VoiceAssistantState.AWAITING_COMMAND,
                    status = "I'm listening — say your task and when to notify or call you...",
                    transcript = "",
                    showOverlay = true,
                    createdTask = null
                )
                updateForegroundNotification("Listening for your task command...")
                restartSpeechListening(delayMs = 150L)
            }
            ACTION_PROCESS_TEXT_COMMAND -> {
                val cmd = intent?.getStringExtra(EXTRA_COMMAND_TEXT).orEmpty().trim()
                if (cmd.isNotBlank()) {
                    processSpokenTaskCommand(cmd)
                }
            }
            else -> {
                if (VoiceAssistantController.assistantState.value == VoiceAssistantState.OFF ||
                    VoiceAssistantController.assistantState.value == VoiceAssistantState.ERROR
                ) {
                    VoiceAssistantController.updateState(
                        state = VoiceAssistantState.LISTENING_WAKE_WORD,
                        status = "Listening for \"Hey TaskFlow\" in background...",
                        transcript = ""
                    )
                }
                updateForegroundNotification("Say \"Hey TaskFlow\" anytime to add a task")
                restartSpeechListening(delayMs = 200L)
            }
        }

        return START_STICKY
    }

    private fun startAsForeground() {
        val notification = buildForegroundNotification("Say \"Hey TaskFlow\" anytime to add a task")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (_: Exception) {
            // Ignore if foreground start restricted in headless test
        }
    }

    private fun initTextToSpeech() {
        ttsEngine = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsEngine?.language = Locale.getDefault()
                ttsReady = true
            }
        }
    }

    /**
     * Observes the current user's tasks in Firestore and checks every second so that
     * if a CALL or POPUP task becomes due while the user is on the Home screen or another app,
     * the Foreground Service immediately launches the full-screen Incoming Call UI without
     * waiting for the user to click a notification.
     */
    private fun startBackgroundReminderMonitor() {
        val auth = FirebaseAuth.getInstance()
        val uid = auth.currentUser?.uid ?: return
        val databaseId = try {
            getString(R.string.firestore_database_id)
        } catch (_: Exception) {
            ""
        }
        val db = if (databaseId.isNotBlank()) {
            FirebaseFirestore.getInstance(databaseId)
        } else {
            FirebaseFirestore.getInstance()
        }
        val repository = TaskRepository(db, auth)

        serviceScope.launch {
            repository.observeTasks(uid)
                .catch { /* Ignore transient network errors */ }
                .collect { tasks ->
                    cachedTasks = tasks
                }
        }

        reminderTickerJob?.cancel()
        reminderTickerJob = serviceScope.launch {
            while (isActive) {
                delay(1000L)
                val now = System.currentTimeMillis()
                for (task in cachedTasks) {
                    if (task.completed || task.reminderTimeMillis <= 0L) continue
                    val mode = task.alertModeEnum()
                    if (mode == TaskAlertMode.NONE) continue

                    val alreadyFired = firedBackgroundReminders[task.id] == task.reminderTimeMillis
                    val isDue = now >= task.reminderTimeMillis &&
                        (task.reminderTimeMillis >= serviceStartTimeMillis - 60_000L)

                    if (isDue && !alreadyFired) {
                        firedBackgroundReminders[task.id] = task.reminderTimeMillis
                        when (mode) {
                            TaskAlertMode.CALL -> {
                                IncomingAlertController.triggerIncomingCall(
                                    context = this@TaskFlowVoiceService,
                                    task = task,
                                    autoAnswer = false
                                )
                                TaskReminderManager.showSystemNotification(
                                    context = this@TaskFlowVoiceService,
                                    taskId = task.id,
                                    title = task.title,
                                    notes = task.notes,
                                    workspace = task.workspaceName,
                                    alertMode = task.alertMode,
                                    reminderMillis = task.reminderTimeMillis,
                                    subtasks = task.subtasks
                                )
                                TaskReminderManager.launchActivityImmediate(
                                    context = this@TaskFlowVoiceService,
                                    task = task,
                                    callAction = TaskReminderManager.CALL_ACTION_SHOW
                                )
                            }
                            TaskAlertMode.POPUP -> {
                                IncomingAlertController.triggerIncomingPopup(
                                    context = this@TaskFlowVoiceService,
                                    task = task
                                )
                                TaskReminderManager.showSystemNotification(
                                    context = this@TaskFlowVoiceService,
                                    taskId = task.id,
                                    title = task.title,
                                    notes = task.notes,
                                    workspace = task.workspaceName,
                                    alertMode = task.alertMode,
                                    reminderMillis = task.reminderTimeMillis,
                                    subtasks = task.subtasks
                                )
                                TaskReminderManager.launchActivityImmediate(
                                    context = this@TaskFlowVoiceService,
                                    task = task,
                                    callAction = TaskReminderManager.CALL_ACTION_SHOW
                                )
                            }
                            TaskAlertMode.NOTIFICATION -> {
                                TaskReminderManager.showSystemNotification(
                                    context = this@TaskFlowVoiceService,
                                    taskId = task.id,
                                    title = task.title,
                                    notes = task.notes,
                                    workspace = task.workspaceName,
                                    alertMode = task.alertMode,
                                    reminderMillis = task.reminderTimeMillis,
                                    subtasks = task.subtasks
                                )
                            }
                            TaskAlertMode.NONE -> Unit
                        }
                        break
                    }
                }
            }
        }
    }

    private fun setupSpeechRecognizer() {
        if (speechRecognizer != null) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            VoiceAssistantController.updateState(
                state = VoiceAssistantState.LISTENING_WAKE_WORD,
                status = "Voice Assistant active • Tap Mic or type command below"
            )
            return
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListeningActive = true
                }

                override fun onBeginningOfSpeech() {}

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    isListeningActive = false
                }

                override fun onError(error: Int) {
                    isListeningActive = false
                    // Restart listening seamlessly for continuous wake-word detection
                    val currentState = VoiceAssistantController.assistantState.value
                    if (currentState == VoiceAssistantState.PROCESSING ||
                        currentState == VoiceAssistantState.SPEAKING_RESPONSE
                    ) {
                        return
                    }
                    val waitMs = if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 900L else 400L
                    restartSpeechListening(delayMs = waitMs)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        .orEmpty()
                    val partial = matches.firstOrNull()?.trim().orEmpty()
                    if (partial.isBlank()) return

                    val state = VoiceAssistantController.assistantState.value
                    if (state == VoiceAssistantState.AWAITING_COMMAND) {
                        VoiceAssistantController.updateState(
                            state = VoiceAssistantState.AWAITING_COMMAND,
                            status = "Recording your task...",
                            transcript = partial,
                            showOverlay = true
                        )
                    } else if (state == VoiceAssistantState.LISTENING_WAKE_WORD) {
                        val (matched, remainder) = VoiceAssistantController.extractWakeWordCommand(partial)
                        if (matched) {
                            VoiceAssistantController.updateState(
                                state = VoiceAssistantState.LISTENING_WAKE_WORD,
                                status = "Heard \"Hey TaskFlow\" — keep speaking...",
                                transcript = if (remainder.isNotBlank()) remainder else "Hey TaskFlow...",
                                showOverlay = true
                            )
                        }
                    }
                }

                override fun onResults(results: Bundle?) {
                    isListeningActive = false
                    val matches = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        .orEmpty()
                    val spoken = matches.firstOrNull()?.trim().orEmpty()
                    if (spoken.isBlank()) {
                        restartSpeechListening(delayMs = 300L)
                        return
                    }

                    handleRecognizedUtterance(spoken)
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    private fun handleRecognizedUtterance(spoken: String) {
        val currentState = VoiceAssistantController.assistantState.value

        if (currentState == VoiceAssistantState.AWAITING_COMMAND) {
            // Strip wake word if the user repeated it, otherwise use full utterance
            val (matched, remainder) = VoiceAssistantController.extractWakeWordCommand(spoken)
            val commandToRun = if (matched && remainder.isNotBlank()) remainder else spoken
            if (matched && remainder.isBlank()) {
                // User only said "Hey TaskFlow" again; stay in AWAITING_COMMAND
                VoiceAssistantController.updateState(
                    state = VoiceAssistantState.AWAITING_COMMAND,
                    status = "I'm listening — say the task you want to add...",
                    transcript = "",
                    showOverlay = true
                )
                restartSpeechListening(delayMs = 250L)
                return
            }
            processSpokenTaskCommand(commandToRun)
            return
        }

        // In LISTENING_WAKE_WORD state: check if utterance contains "Hey TaskFlow"
        val (matchedWakeWord, commandRemainder) = VoiceAssistantController.extractWakeWordCommand(spoken)
        if (matchedWakeWord) {
            playAssistantChime()
            bringAppToForegroundForVoice()
            if (commandRemainder.length >= 3) {
                // Single-breath command: "Hey TaskFlow remind me in 1 minute to call Alex"
                processSpokenTaskCommand(commandRemainder)
            } else {
                // Two-step wake word: User said "Hey TaskFlow" and paused
                VoiceAssistantController.updateState(
                    state = VoiceAssistantState.AWAITING_COMMAND,
                    status = "Heard \"Hey TaskFlow\"! Speak your task now...",
                    transcript = "",
                    showOverlay = true,
                    createdTask = null
                )
                updateForegroundNotification("Heard \"Hey TaskFlow\" — listening for your task...")
                speakBriefPromptAndListen("I'm listening. What task should I add?")
            }
        } else {
            // Continue listening for "Hey TaskFlow"
            restartSpeechListening(delayMs = 300L)
        }
    }

    private fun processSpokenTaskCommand(commandText: String) {
        stopSpeechListening()
        VoiceAssistantController.updateState(
            state = VoiceAssistantState.PROCESSING,
            status = "Understanding & scheduling \"$commandText\"...",
            transcript = commandText,
            showOverlay = true
        )
        updateForegroundNotification("Adding task: $commandText")

        serviceScope.launch {
            val auth = FirebaseAuth.getInstance()
            val uid = auth.currentUser?.uid
            if (uid == null) {
                VoiceAssistantController.updateState(
                    state = VoiceAssistantState.ERROR,
                    status = "Please sign in to TaskFlow first so I can save your voice tasks.",
                    transcript = commandText,
                    showOverlay = true
                )
                restartSpeechListening(delayMs = 2000L)
                return@launch
            }

            val databaseId = try {
                getString(R.string.firestore_database_id)
            } catch (_: Exception) {
                ""
            }
            val db = if (databaseId.isNotBlank()) {
                FirebaseFirestore.getInstance(databaseId)
            } else {
                FirebaseFirestore.getInstance()
            }
            val repository = TaskRepository(db, auth)

            val parsedAction = TaskAgentService.parseUserReminderRequest(
                userPrompt = commandText,
                availableWorkspaces = listOf("Inbox", "Work", "Personal")
            )
            val subtaskEntries = parsedAction.subtasks.map { SubtaskEntry(false, it) }

            val createResult = repository.createTask(
                title = parsedAction.title,
                notes = parsedAction.notes,
                priority = parsedAction.priority,
                workspaceName = parsedAction.workspaceName,
                dueDateMillis = parsedAction.reminderTimeMillis,
                reminderTimeMillis = parsedAction.reminderTimeMillis,
                alertMode = parsedAction.alertMode,
                subtasks = subtaskEntries,
                tags = parsedAction.tags,
                pinned = parsedAction.priority == TaskPriority.URGENT
            )

            createResult.fold(
                onSuccess = { taskId ->
                    val createdTask = TaskItem(
                        id = taskId,
                        title = parsedAction.title,
                        notes = parsedAction.notes,
                        completed = false,
                        priority = parsedAction.priority.name,
                        workspaceName = parsedAction.workspaceName,
                        dueDateMillis = parsedAction.reminderTimeMillis,
                        reminderTimeMillis = parsedAction.reminderTimeMillis,
                        alertMode = parsedAction.alertMode.name,
                        subtasks = subtaskEntries.map { it.encode() },
                        tags = parsedAction.tags,
                        pinned = parsedAction.priority == TaskPriority.URGENT,
                        userId = uid
                    )

                    TaskReminderManager.scheduleReminderAlarm(
                        context = this@TaskFlowVoiceService,
                        task = createdTask
                    )

                    VoiceAssistantController.updateState(
                        state = VoiceAssistantState.SPEAKING_RESPONSE,
                        status = parsedAction.agentReply,
                        transcript = commandText,
                        showOverlay = true,
                        createdTask = createdTask
                    )
                    updateForegroundNotification("Added: ${createdTask.title}")

                    if (parsedAction.triggerImmediately) {
                        firedBackgroundReminders[taskId] = parsedAction.reminderTimeMillis
                        if (parsedAction.alertMode == TaskAlertMode.CALL) {
                            IncomingAlertController.triggerIncomingCall(
                                context = this@TaskFlowVoiceService,
                                task = createdTask,
                                autoAnswer = false
                            )
                            TaskReminderManager.launchActivityImmediate(
                                context = this@TaskFlowVoiceService,
                                task = createdTask
                            )
                        } else {
                            IncomingAlertController.triggerIncomingPopup(
                                context = this@TaskFlowVoiceService,
                                task = createdTask
                            )
                            TaskReminderManager.launchActivityImmediate(
                                context = this@TaskFlowVoiceService,
                                task = createdTask
                            )
                        }
                    } else if (ttsReady) {
                        ttsEngine?.speak(
                            parsedAction.agentReply,
                            TextToSpeech.QUEUE_FLUSH,
                            null,
                            "voice_confirm_$taskId"
                        )
                    }

                    delay(3800L)
                    if (VoiceAssistantController.isServiceEnabled.value) {
                        VoiceAssistantController.updateState(
                            state = VoiceAssistantState.LISTENING_WAKE_WORD,
                            status = "Listening for \"Hey TaskFlow\" in background...",
                            transcript = "",
                            createdTask = createdTask
                        )
                        updateForegroundNotification("Say \"Hey TaskFlow\" anytime to add a task")
                        restartSpeechListening(delayMs = 400L)
                    }
                },
                onFailure = { err ->
                    VoiceAssistantController.updateState(
                        state = VoiceAssistantState.LISTENING_WAKE_WORD,
                        status = "Failed to save task: ${err.localizedMessage ?: "Try again"}",
                        transcript = commandText,
                        showOverlay = true
                    )
                    restartSpeechListening(delayMs = 1500L)
                }
            )
        }
    }

    private fun speakBriefPromptAndListen(prompt: String) {
        stopSpeechListening()
        serviceScope.launch {
            if (ttsReady) {
                ttsEngine?.speak(prompt, TextToSpeech.QUEUE_FLUSH, null, "wake_prompt")
                delay(1700L)
            } else {
                delay(300L)
            }
            restartSpeechListening(delayMs = 100L)
        }
    }

    private fun restartSpeechListening(delayMs: Long = 300L) {
        restartJob?.cancel()
        restartJob = serviceScope.launch {
            delay(delayMs)
            // Pause microphone listening while an incoming call is actively ringing or speaking
            if (IncomingAlertController.incomingCallTask.value != null) {
                delay(2000L)
                restartSpeechListening(delayMs = 1500L)
                return@launch
            }
            startSpeechListeningInternal()
        }
    }

    private fun startSpeechListeningInternal() {
        if (!VoiceAssistantController.hasRecordAudioPermission(this)) return
        setupSpeechRecognizer()
        val recognizer = speechRecognizer ?: return
        if (isListeningActive) {
            try {
                recognizer.cancel()
            } catch (_: Exception) {
            }
            isListeningActive = false
        }

        val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
        }

        try {
            recognizer.startListening(recognizerIntent)
        } catch (_: Exception) {
            restartSpeechListening(delayMs = 1000L)
        }
    }

    private fun stopSpeechListening() {
        restartJob?.cancel()
        isListeningActive = false
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
        } catch (_: Exception) {
        }
    }

    private fun bringAppToForegroundForVoice() {
        try {
            val launchIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_VOICE_WAKE_TRIGGERED, true)
            }
            startActivity(launchIntent)
        } catch (_: Exception) {
        }
    }

    private fun playAssistantChime() {
        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
            toneGen.startTone(ToneGenerator.TONE_PROP_ACK, 180)
        } catch (_: Exception) {
        }
    }

    private fun ensureVoiceNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                VOICE_CHANNEL_ID,
                "Hey TaskFlow Voice Assistant",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Hands-free background wake-word listener for Hey TaskFlow"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(statusText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            201,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val wakeNowIntent = Intent(this, TaskFlowVoiceService::class.java).apply {
            action = ACTION_WAKE_NOW
        }
        val wakeNowPendingIntent = PendingIntent.getService(
            this,
            202,
            wakeNowIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, TaskFlowVoiceService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            203,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, VOICE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Hey TaskFlow Assistant Active")
            .setContentText(statusText)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(openPendingIntent)
            .addAction(
                android.R.drawable.ic_btn_speak_now,
                "Speak Task Now",
                wakeNowPendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Turn Off",
                stopPendingIntent
            )
            .build()
    }

    private fun updateForegroundNotification(statusText: String) {
        if (!TaskReminderManager.hasNotificationPermission(this)) return
        try {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.notify(NOTIFICATION_ID, buildForegroundNotification(statusText))
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        stopSpeechListening()
        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        }
        speechRecognizer = null
        try {
            ttsEngine?.stop()
            ttsEngine?.shutdown()
        } catch (_: Exception) {
        }
        ttsEngine = null
        serviceScope.cancel()
        VoiceAssistantController.setServiceRunning(false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val VOICE_CHANNEL_ID = "taskflow_voice_assistant_channel"
        const val NOTIFICATION_ID = 8842
        const val ACTION_START_LISTENING = "com.aistudio.tasksync.ACTION_START_LISTENING"
        const val ACTION_WAKE_NOW = "com.aistudio.tasksync.ACTION_WAKE_NOW"
        const val ACTION_PROCESS_TEXT_COMMAND = "com.aistudio.tasksync.ACTION_PROCESS_TEXT_COMMAND"
        const val ACTION_STOP_SERVICE = "com.aistudio.tasksync.ACTION_STOP_SERVICE"
        const val EXTRA_COMMAND_TEXT = "extra_command_text"
        const val EXTRA_VOICE_WAKE_TRIGGERED = "extra_voice_wake_triggered"
    }
}
