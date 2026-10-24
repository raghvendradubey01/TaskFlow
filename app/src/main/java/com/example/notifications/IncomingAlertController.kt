package com.example.notifications

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import com.example.data.model.TaskItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Process-wide controller that immediately activates the full-screen Incoming Call UI
 * or Pop-Up Alert Dialog when an alarm fires or when triggered from the background,
 * without waiting for the user to tap a notification.
 */
object IncomingAlertController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _incomingCallTask = MutableStateFlow<TaskItem?>(null)
    val incomingCallTask: StateFlow<TaskItem?> = _incomingCallTask.asStateFlow()

    private val _autoAnswerCall = MutableStateFlow(false)
    val autoAnswerCall: StateFlow<Boolean> = _autoAnswerCall.asStateFlow()

    private val _incomingPopupTask = MutableStateFlow<TaskItem?>(null)
    val incomingPopupTask: StateFlow<TaskItem?> = _incomingPopupTask.asStateFlow()

    private var activeRingtone: Ringtone? = null
    private var backgroundRingingJob: Job? = null

    fun triggerIncomingCall(
        context: Context,
        task: TaskItem,
        autoAnswer: Boolean = false
    ) {
        _incomingPopupTask.value = null
        _autoAnswerCall.value = autoAnswer
        _incomingCallTask.value = task
        if (!autoAnswer) {
            startRingingAndVibrating(context.applicationContext)
        } else {
            stopRingingAndVibrating(context.applicationContext, task.id)
        }
    }

    fun triggerIncomingPopup(
        context: Context,
        task: TaskItem
    ) {
        if (_incomingCallTask.value == null) {
            _incomingPopupTask.value = task
            TaskReminderManager.playAlertTone(isCall = false)
            TaskReminderManager.triggerVibration(context.applicationContext, isCall = false)
        }
    }

    fun onCallAnswered(context: Context, taskId: String) {
        _autoAnswerCall.value = true
        stopRingingAndVibrating(context.applicationContext, taskId)
    }

    fun clearIncomingCall(context: Context? = null) {
        val currentId = _incomingCallTask.value?.id
        _incomingCallTask.value = null
        _autoAnswerCall.value = false
        stopRingingAndVibrating(context?.applicationContext, currentId)
    }

    fun clearIncomingPopup() {
        _incomingPopupTask.value = null
    }

    fun startRingingAndVibrating(context: Context) {
        stopRingingOnly()
        try {
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(context, ringtoneUri)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                ringtone?.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ringtone?.isLooping = true
            }
            ringtone?.play()
            activeRingtone = ringtone
        } catch (_: Exception) {
            // Fallback handled in coroutine loop below
        }

        backgroundRingingJob?.cancel()
        backgroundRingingJob = scope.launch {
            while (isActive && _incomingCallTask.value != null && !_autoAnswerCall.value) {
                if (activeRingtone?.isPlaying != true) {
                    try {
                        activeRingtone?.play()
                    } catch (_: Exception) {
                    }
                    TaskReminderManager.playAlertTone(isCall = true)
                }
                TaskReminderManager.triggerVibration(context, isCall = true)
                delay(2200L)
            }
        }
    }

    private fun stopRingingOnly() {
        backgroundRingingJob?.cancel()
        backgroundRingingJob = null
        try {
            activeRingtone?.stop()
        } catch (_: Exception) {
        }
        activeRingtone = null
    }

    fun stopRingingAndVibrating(context: Context?, taskId: String? = null) {
        stopRingingOnly()
        if (context != null && !taskId.isNullOrBlank()) {
            TaskReminderManager.cancelNotification(context, taskId)
        }
    }
}
