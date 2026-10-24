package com.example.data.model

import android.os.Build
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class TaskPriority(val label: String, val sortWeight: Int) {
    URGENT("Urgent", 4),
    HIGH("High", 3),
    MEDIUM("Medium", 2),
    LOW("Low", 1);

    companion object {
        fun fromString(value: String): TaskPriority {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: MEDIUM
        }
    }
}

enum class TaskAlertMode(val label: String, val description: String) {
    NONE("No Alert", "Silent task"),
    NOTIFICATION("Notification", "System push notification"),
    POPUP("Pop-Up Alert", "Full-card pop-up modal"),
    CALL("Incoming Call", "AI Agent voice call ring");

    companion object {
        fun fromString(value: String): TaskAlertMode {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: NONE
        }
    }
}

data class SubtaskEntry(
    val completed: Boolean = false,
    val title: String = ""
) {
    fun encode(): String {
        val cleanTitle = title.replace("|", " ").trim().take(190)
        return "${if (completed) "1" else "0"}|$cleanTitle"
    }

    companion object {
        fun decode(raw: String): SubtaskEntry {
            val splitIndex = raw.indexOf('|')
            return if (splitIndex > 0) {
                val isDone = raw.substring(0, splitIndex) == "1"
                val text = raw.substring(splitIndex + 1)
                SubtaskEntry(completed = isDone, title = text)
            } else {
                SubtaskEntry(completed = false, title = raw)
            }
        }
    }
}

@IgnoreExtraProperties
data class TaskItem(
    @DocumentId val id: String = "",
    val title: String = "",
    val notes: String = "",
    val completed: Boolean = false,
    val priority: String = TaskPriority.MEDIUM.name,
    val workspaceName: String = "Inbox",
    val dueDateMillis: Long = 0L,
    val reminderTimeMillis: Long = 0L,
    val alertMode: String = TaskAlertMode.NONE.name,
    val subtasks: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val pinned: Boolean = false,
    val deviceName: String = defaultDeviceName(),
    val userId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun decodedSubtasks(): List<SubtaskEntry> = subtasks.map { SubtaskEntry.decode(it) }

    fun priorityEnum(): TaskPriority = TaskPriority.fromString(priority)

    fun alertModeEnum(): TaskAlertMode = TaskAlertMode.fromString(alertMode)

    fun hasActiveReminder(): Boolean =
        !completed && reminderTimeMillis > 0L && alertModeEnum() != TaskAlertMode.NONE

    companion object {
        fun defaultDeviceName(): String {
            val manufacturer = Build.MANUFACTURER?.trim().orEmpty()
            val model = Build.MODEL?.trim().orEmpty()
            val combined = when {
                model.isEmpty() -> "Android Device"
                model.startsWith(manufacturer, ignoreCase = true) -> model
                manufacturer.isNotEmpty() -> "$manufacturer $model"
                else -> model
            }.take(95)
            return combined.ifBlank { "Android Device" }
        }
    }
}

@IgnoreExtraProperties
data class WorkspaceItem(
    @DocumentId val id: String = "",
    val name: String = "",
    val colorHex: String = "#6366F1",
    val iconName: String = "folder",
    val userId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
