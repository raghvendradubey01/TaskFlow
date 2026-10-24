package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.model.TaskAlertMode
import com.example.data.model.TaskItem

class TaskReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(TaskReminderManager.EXTRA_TASK_ID) ?: return

        if (intent.action == TaskReminderManager.ACTION_DECLINE_CALL) {
            IncomingAlertController.clearIncomingCall(context)
            TaskReminderManager.cancelNotification(context, taskId)
            return
        }

        val title = intent.getStringExtra(TaskReminderManager.EXTRA_TASK_TITLE) ?: "Scheduled Task"
        val notes = intent.getStringExtra(TaskReminderManager.EXTRA_TASK_NOTES).orEmpty()
        val workspace = intent.getStringExtra(TaskReminderManager.EXTRA_TASK_WORKSPACE) ?: "Inbox"
        val alertMode = intent.getStringExtra(TaskReminderManager.EXTRA_ALERT_MODE) ?: "NOTIFICATION"
        val reminderMillis = intent.getLongExtra(TaskReminderManager.EXTRA_REMINDER_MILLIS, System.currentTimeMillis())
        val subtasks = intent.getStringArrayListExtra(TaskReminderManager.EXTRA_SUBTASKS_RAW)?.toList().orEmpty()

        val task = TaskItem(
            id = taskId,
            title = title,
            notes = notes,
            workspaceName = workspace,
            alertMode = alertMode,
            reminderTimeMillis = reminderMillis,
            dueDateMillis = reminderMillis,
            subtasks = subtasks
        )

        val modeEnum = TaskAlertMode.fromString(alertMode)
        when (modeEnum) {
            TaskAlertMode.CALL -> {
                IncomingAlertController.triggerIncomingCall(
                    context = context,
                    task = task,
                    autoAnswer = false
                )
                TaskReminderManager.showSystemNotification(
                    context = context,
                    taskId = taskId,
                    title = title,
                    notes = notes,
                    workspace = workspace,
                    alertMode = alertMode,
                    reminderMillis = reminderMillis,
                    subtasks = subtasks
                )
                TaskReminderManager.launchActivityImmediate(
                    context = context,
                    task = task,
                    callAction = TaskReminderManager.CALL_ACTION_SHOW
                )
            }
            TaskAlertMode.POPUP -> {
                IncomingAlertController.triggerIncomingPopup(context, task)
                TaskReminderManager.showSystemNotification(
                    context = context,
                    taskId = taskId,
                    title = title,
                    notes = notes,
                    workspace = workspace,
                    alertMode = alertMode,
                    reminderMillis = reminderMillis,
                    subtasks = subtasks
                )
                TaskReminderManager.launchActivityImmediate(
                    context = context,
                    task = task,
                    callAction = TaskReminderManager.CALL_ACTION_SHOW
                )
            }
            TaskAlertMode.NOTIFICATION, TaskAlertMode.NONE -> {
                IncomingAlertController.triggerIncomingPopup(context, task)
                TaskReminderManager.showSystemNotification(
                    context = context,
                    taskId = taskId,
                    title = title,
                    notes = notes,
                    workspace = workspace,
                    alertMode = alertMode,
                    reminderMillis = reminderMillis,
                    subtasks = subtasks
                )
                TaskReminderManager.triggerVibration(context, isCall = false)
            }
        }
    }
}
