package com.example.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.AlarmManagerCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.TaskAlertMode
import com.example.data.model.TaskItem

object TaskReminderManager {
    const val CHANNEL_ID = "taskflow_reminders_channel"
    const val CALL_CHANNEL_ID = "taskflow_incoming_calls_channel"

    const val ACTION_FIRE_REMINDER = "com.aistudio.tasksync.ACTION_FIRE_REMINDER"
    const val ACTION_DECLINE_CALL = "com.aistudio.tasksync.ACTION_DECLINE_CALL"

    const val EXTRA_TASK_ID = "extra_task_id"
    const val EXTRA_TASK_TITLE = "extra_task_title"
    const val EXTRA_TASK_NOTES = "extra_task_notes"
    const val EXTRA_TASK_WORKSPACE = "extra_task_workspace"
    const val EXTRA_ALERT_MODE = "extra_alert_mode"
    const val EXTRA_REMINDER_MILLIS = "extra_reminder_millis"
    const val EXTRA_SUBTASKS_RAW = "extra_subtasks_raw"
    const val EXTRA_CALL_ACTION = "extra_call_action"

    const val CALL_ACTION_ANSWER = "ANSWER"
    const val CALL_ACTION_SHOW = "SHOW"

    fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            val reminderChannel = NotificationChannel(
                CHANNEL_ID,
                "TaskFlow Reminders & Pop-Ups",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Scheduled task notifications and pop-up alerts"
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            val callChannel = NotificationChannel(
                CALL_CHANNEL_ID,
                "TaskFlow Incoming AI Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Full-screen incoming AI voice calls for scheduled tasks"
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setBypassDnd(true)
            }

            manager.createNotificationChannel(reminderChannel)
            manager.createNotificationChannel(callChannel)
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun scheduleReminderAlarm(context: Context, task: TaskItem) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, TaskReminderReceiver::class.java).apply {
            action = ACTION_FIRE_REMINDER
            putExtra(EXTRA_TASK_ID, task.id)
            putExtra(EXTRA_TASK_TITLE, task.title)
            putExtra(EXTRA_TASK_NOTES, task.notes)
            putExtra(EXTRA_TASK_WORKSPACE, task.workspaceName)
            putExtra(EXTRA_ALERT_MODE, task.alertMode)
            putExtra(EXTRA_REMINDER_MILLIS, task.reminderTimeMillis)
            putStringArrayListExtra(EXTRA_SUBTASKS_RAW, ArrayList(task.subtasks))
        }
        val requestCode = task.id.hashCode()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (task.completed || task.reminderTimeMillis <= System.currentTimeMillis() || task.alertModeEnum() == TaskAlertMode.NONE) {
            alarmManager.cancel(pendingIntent)
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setWindow(
                    AlarmManager.RTC_WAKEUP,
                    task.reminderTimeMillis,
                    1_000L,
                    pendingIntent
                )
            } else {
                AlarmManagerCompat.setAndAllowWhileIdle(
                    alarmManager,
                    AlarmManager.RTC_WAKEUP,
                    task.reminderTimeMillis,
                    pendingIntent
                )
            }
        } catch (_: Exception) {
            // Fallback handled by in-app & foreground service ticker
        }
    }

    fun buildLaunchActivityIntent(
        context: Context,
        taskId: String,
        title: String,
        notes: String,
        workspace: String,
        alertMode: String,
        reminderMillis: Long = 0L,
        subtasks: List<String> = emptyList(),
        callAction: String = CALL_ACTION_SHOW
    ): Intent {
        return Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_TASK_TITLE, title)
            putExtra(EXTRA_TASK_NOTES, notes)
            putExtra(EXTRA_TASK_WORKSPACE, workspace)
            putExtra(EXTRA_ALERT_MODE, alertMode)
            putExtra(EXTRA_REMINDER_MILLIS, reminderMillis)
            putStringArrayListExtra(EXTRA_SUBTASKS_RAW, ArrayList(subtasks))
            putExtra(EXTRA_CALL_ACTION, callAction)
        }
    }

    fun launchActivityImmediate(
        context: Context,
        task: TaskItem,
        callAction: String = CALL_ACTION_SHOW
    ) {
        try {
            val launchIntent = buildLaunchActivityIntent(
                context = context,
                taskId = task.id,
                title = task.title,
                notes = task.notes,
                workspace = task.workspaceName,
                alertMode = task.alertMode,
                reminderMillis = task.reminderTimeMillis,
                subtasks = task.subtasks,
                callAction = callAction
            )
            context.startActivity(launchIntent)
        } catch (_: Exception) {
            // FullScreenIntent on the high-priority call notification handles locked/background fallback
        }
    }

    fun showSystemNotification(
        context: Context,
        taskId: String,
        title: String,
        notes: String,
        workspace: String,
        alertMode: String,
        reminderMillis: Long = 0L,
        subtasks: List<String> = emptyList()
    ) {
        ensureNotificationChannel(context)
        if (!hasNotificationPermission(context)) return

        val modeEnum = TaskAlertMode.fromString(alertMode)
        val isCall = modeEnum == TaskAlertMode.CALL
        val isPopup = modeEnum == TaskAlertMode.POPUP

        val showIntent = buildLaunchActivityIntent(
            context = context,
            taskId = taskId,
            title = title,
            notes = notes,
            workspace = workspace,
            alertMode = alertMode,
            reminderMillis = reminderMillis,
            subtasks = subtasks,
            callAction = CALL_ACTION_SHOW
        )
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            taskId.hashCode(),
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val answerIntent = buildLaunchActivityIntent(
            context = context,
            taskId = taskId,
            title = title,
            notes = notes,
            workspace = workspace,
            alertMode = alertMode,
            reminderMillis = reminderMillis,
            subtasks = subtasks,
            callAction = CALL_ACTION_ANSWER
        )
        val answerPendingIntent = PendingIntent.getActivity(
            context,
            taskId.hashCode() + 1,
            answerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val declineIntent = Intent(context, TaskReminderReceiver::class.java).apply {
            action = ACTION_DECLINE_CALL
            putExtra(EXTRA_TASK_ID, taskId)
        }
        val declinePendingIntent = PendingIntent.getBroadcast(
            context,
            taskId.hashCode() + 2,
            declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prefix = when (modeEnum) {
            TaskAlertMode.CALL -> "Incoming AI Task Call"
            TaskAlertMode.POPUP -> "Pop-Up Task Alert"
            else -> "Task Reminder"
        }

        val bodyText = if (notes.isNotBlank()) {
            "[$workspace] $notes"
        } else {
            if (isCall) "TaskFlow AI Agent is calling you now • [$workspace]"
            else "Scheduled in $workspace workspace • Tap to open"
        }

        val channelToUse = if (isCall || isPopup) CALL_CHANNEL_ID else CHANNEL_ID
        val builder = NotificationCompat.Builder(context, channelToUse)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("$prefix: $title")
            .setContentText(bodyText)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(
                if (isCall) NotificationCompat.CATEGORY_CALL
                else NotificationCompat.CATEGORY_ALARM
            )
            .setContentIntent(fullScreenPendingIntent)

        if (isCall) {
            builder
                .setOngoing(true)
                .setAutoCancel(false)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .addAction(
                    android.R.drawable.sym_action_call,
                    "Answer Call",
                    answerPendingIntent
                )
                .addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "Decline",
                    declinePendingIntent
                )
        } else if (isPopup) {
            builder
                .setAutoCancel(true)
                .setFullScreenIntent(fullScreenPendingIntent, true)
        } else {
            builder.setAutoCancel(true)
        }

        try {
            NotificationManagerCompat.from(context).notify(taskId.hashCode(), builder.build())
        } catch (_: SecurityException) {
            // Permission not granted
        }
    }

    fun cancelNotification(context: Context, taskId: String) {
        try {
            NotificationManagerCompat.from(context).cancel(taskId.hashCode())
        } catch (_: Exception) {
        }
    }

    fun playAlertTone(isCall: Boolean) {
        try {
            val toneGen = ToneGenerator(
                if (isCall) AudioManager.STREAM_RING else AudioManager.STREAM_NOTIFICATION,
                if (isCall) 95 else 80
            )
            val tone = if (isCall) {
                ToneGenerator.TONE_SUP_RINGTONE
            } else {
                ToneGenerator.TONE_PROP_BEEP2
            }
            toneGen.startTone(tone, if (isCall) 1400 else 450)
        } catch (_: Exception) {
            // Audio unavailable in headless environment
        }
    }

    fun triggerVibration(context: Context, isCall: Boolean) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            } ?: return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (isCall) {
                    vibrator.vibrate(
                        VibrationEffect.createWaveform(longArrayOf(0, 600, 250, 600), -1)
                    )
                } else {
                    vibrator.vibrate(
                        VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE)
                    )
                }
            }
        } catch (_: Exception) {
            // Ignore vibration failures on emulators without vibrator hardware
        }
    }
}
