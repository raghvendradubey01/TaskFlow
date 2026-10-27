package com.example.ai

import com.example.data.model.TaskAlertMode
import com.example.data.model.TaskPriority
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ParsedAgentTaskPlan(
    val title: String = "",
    val notes: String = "",
    val priority: String = "MEDIUM",
    val workspaceName: String = "Inbox",
    val delaySeconds: Long = 60L,
    val targetHour24: Int = -1,
    val targetMinute: Int = -1,
    val alertMode: String = "CALL",
    val subtasks: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val agentReply: String = ""
)

data class ResolvedAgentTaskAction(
    val title: String,
    val notes: String,
    val priority: TaskPriority,
    val workspaceName: String,
    val reminderTimeMillis: Long,
    val alertMode: TaskAlertMode,
    val subtasks: List<String>,
    val tags: List<String>,
    val agentReply: String,
    val triggerImmediately: Boolean = false
)

object TaskAgentService {
    private const val MODEL_NAME = "gemini-3.8-flash"
    private const val INFERENCE_TIMEOUT_MS = 60_000L

    private val jsonFormat = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val taskPlanResponseSchema: Schema by lazy {
        Schema.obj(
            mapOf(
                "title" to Schema.str("Concise task title extracted from the user's request."),
                "notes" to Schema.str("Helpful context or reminder notes for the task."),
                "priority" to Schema.enumeration(
                    listOf("LOW", "MEDIUM", "HIGH", "URGENT"),
                    "Task priority level."
                ),
                "workspaceName" to Schema.str("Best matching workspace name for the task."),
                "delaySeconds" to Schema.long(
                    "Number of seconds from now to trigger the alert if relative time is used (e.g. 'in 1 minute' = 60, 'now' = 5). Use 0 if a specific clock time is given."
                ),
                "targetHour24" to Schema.integer(
                    "24-hour clock hour (0-23) if user specified an exact clock time like 'at 5:30 PM', otherwise -1."
                ),
                "targetMinute" to Schema.integer(
                    "Clock minute (0-59) if user specified an exact clock time, otherwise -1."
                ),
                "alertMode" to Schema.enumeration(
                    listOf("CALL", "POPUP", "NOTIFICATION"),
                    "CALL if user asks for a call/ring/phone alert, POPUP if user asks for a pop-up/dialog, or NOTIFICATION for standard alert."
                ),
                "subtasks" to Schema.array(
                    Schema.str("Actionable checklist item"),
                    "2 to 3 actionable checklist steps for the task."
                ),
                "tags" to Schema.array(
                    Schema.str("Short lowercase tag"),
                    "1 or 2 short lowercase tags."
                ),
                "agentReply" to Schema.str(
                    "Friendly confirmation message explaining what task was added, when it will trigger, and whether it will ring as a Call, Pop-Up, or Notification."
                )
            )
        )
    }

    /**
     * Initialized lazily so Firebase.ai.generativeModel is only accessed after
     * configureAppCheck(this, intent) has run in MainActivity.onCreate().
     */
    private val generativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = taskPlanResponseSchema
                temperature = 0.2f
            },
            systemInstruction = content {
                text(
                    """
                    You are the TaskFlow AI Reminder & Call Agent.
                    Extract the user's task, schedule time, and preferred alert style:
                    - If they mention "call", "ring", "getting a call", or "voice", set alertMode to "CALL".
                    - If they mention "pop up", "popup", "modal", or "screen alert", set alertMode to "POPUP".
                    - Otherwise, if they ask to notify/remind without specifying, default to "CALL" or "POPUP" based on urgency.
                    Always generate 2-3 helpful subtasks and a warm, concise agentReply.
                    """.trimIndent()
                )
            }
        )
    }

    suspend fun parseUserReminderRequest(
        userPrompt: String,
        availableWorkspaces: List<String>
    ): ResolvedAgentTaskAction = withContext(Dispatchers.IO) {
        val nowMillis = System.currentTimeMillis()
        val currentTimeFormatted = SimpleDateFormat("HH:mm (h:mm a)", Locale.getDefault()).format(Date(nowMillis))

        try {
            val contextualPrompt = buildString {
                appendLine("Current local time: $currentTimeFormatted")
                appendLine("Available workspaces: ${availableWorkspaces.joinToString(", ")}")
                append("User request: $userPrompt")
            }

            val response = withTimeout(INFERENCE_TIMEOUT_MS) {
                generativeModel.generateContent(contextualPrompt)
            }
            val rawJson = response.text
            if (!rawJson.isNullOrBlank()) {
                val plan = jsonFormat.decodeFromString<ParsedAgentTaskPlan>(rawJson)
                return@withContext convertPlanToAction(plan, nowMillis)
            }
        } catch (_: Exception) {
            // Fall through to deterministic local NLU parser so offline/voice workflows never break
        }

        parseLocally(userPrompt, nowMillis, availableWorkspaces)
    }

    private fun convertPlanToAction(plan: ParsedAgentTaskPlan, nowMillis: Long): ResolvedAgentTaskAction {
        val mode = when (plan.alertMode.uppercase()) {
            "CALL" -> TaskAlertMode.CALL
            "POPUP" -> TaskAlertMode.POPUP
            "NOTIFICATION" -> TaskAlertMode.NOTIFICATION
            else -> TaskAlertMode.POPUP
        }
        val priority = TaskPriority.fromString(plan.priority)
        val reminderMillis = if (plan.targetHour24 in 0..23 && plan.targetMinute in 0..59) {
            val cal = Calendar.getInstance().apply {
                timeInMillis = nowMillis
                set(Calendar.HOUR_OF_DAY, plan.targetHour24)
                set(Calendar.MINUTE, plan.targetMinute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (cal.timeInMillis <= nowMillis) {
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
            cal.timeInMillis
        } else {
            val secs = plan.delaySeconds.coerceAtLeast(3L)
            nowMillis + secs * 1000L
        }

        val cleanTitle = plan.title.ifBlank { "Scheduled Reminder Task" }
        val timeLabel = SimpleDateFormat("h:mm:ss a", Locale.getDefault()).format(Date(reminderMillis))
        val reply = plan.agentReply.ifBlank {
            "Scheduled \"$cleanTitle\" for $timeLabel with ${mode.label} mode."
        }

        return ResolvedAgentTaskAction(
            title = cleanTitle,
            notes = plan.notes,
            priority = priority,
            workspaceName = plan.workspaceName.ifBlank { "Inbox" },
            reminderTimeMillis = reminderMillis,
            alertMode = mode,
            subtasks = plan.subtasks,
            tags = plan.tags.ifEmpty { listOf("ai-agent") },
            agentReply = reply,
            triggerImmediately = plan.delaySeconds in 1L..6L
        )
    }

    private fun parseLocally(
        prompt: String,
        nowMillis: Long,
        availableWorkspaces: List<String>
    ): ResolvedAgentTaskAction {
        val lower = prompt.lowercase()

        val alertMode = when {
            lower.contains("call") || lower.contains("ring") || lower.contains("phone") -> TaskAlertMode.CALL
            lower.contains("pop") || lower.contains("modal") || lower.contains("dialog") -> TaskAlertMode.POPUP
            lower.contains("notif") -> TaskAlertMode.NOTIFICATION
            else -> TaskAlertMode.CALL
        }

        val priority = when {
            lower.contains("urgent") || lower.contains("asap") || lower.contains("critical") -> TaskPriority.URGENT
            lower.contains("high") || lower.contains("important") -> TaskPriority.HIGH
            lower.contains("low") -> TaskPriority.LOW
            else -> TaskPriority.HIGH
        }

        val triggerNow = lower.contains("now") || lower.contains("instant") || lower.contains("test")

        // Parse "in X sec/min/hour" or clock times like "at 5:30 PM", "5:30 PM", "17:45", "for 8 AM"
        val secRegex = Regex("in\\s+(\\d+)\\s*(sec|second|seconds|s)\\b", RegexOption.IGNORE_CASE)
        val minRegex = Regex("in\\s+(\\d+)\\s*(min|minute|minutes|m)\\b", RegexOption.IGNORE_CASE)
        val hourRegex = Regex("in\\s+(\\d+)\\s*(hr|hour|hours|h)\\b", RegexOption.IGNORE_CASE)
        val clockColonRegex = Regex("(?:(?:at|for|on|by)\\s+)?\\b(\\d{1,2}):(\\d{2})\\s*(am|pm)?\\b", RegexOption.IGNORE_CASE)
        val clockAmPmRegex = Regex("(?:(?:at|for|on|by)\\s+)?\\b(\\d{1,2})\\s*(am|pm)\\b", RegexOption.IGNORE_CASE)
        val clockAtHourRegex = Regex("(?:at|for|by)\\s+(\\d{1,2})\\b", RegexOption.IGNORE_CASE)

        fun buildClockMillis(rawHour: Int, rawMinute: Int, ampmRaw: String): Long {
            var hour = rawHour
            val ampm = ampmRaw.lowercase()
            if (ampm == "pm" && hour < 12) hour += 12
            if (ampm == "am" && hour == 12) hour = 0
            val cal = Calendar.getInstance().apply {
                timeInMillis = nowMillis
                set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
                set(Calendar.MINUTE, rawMinute.coerceIn(0, 59))
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (cal.timeInMillis <= nowMillis || lower.contains("tomorrow")) {
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
            return cal.timeInMillis
        }

        val reminderTimeMillis = when {
            triggerNow -> nowMillis + 4_000L
            clockColonRegex.containsMatchIn(prompt) -> {
                val match = clockColonRegex.find(prompt)!!
                val hour = match.groupValues[1].toIntOrNull() ?: 9
                val minute = match.groupValues[2].toIntOrNull() ?: 0
                val ampm = match.groupValues[3]
                buildClockMillis(hour, minute, ampm)
            }
            clockAmPmRegex.containsMatchIn(prompt) -> {
                val match = clockAmPmRegex.find(prompt)!!
                val hour = match.groupValues[1].toIntOrNull() ?: 9
                val ampm = match.groupValues[2]
                buildClockMillis(hour, 0, ampm)
            }
            secRegex.containsMatchIn(prompt) -> {
                val secs = secRegex.find(prompt)?.groupValues?.get(1)?.toLongOrNull() ?: 15L
                nowMillis + secs.coerceAtLeast(3L) * 1000L
            }
            minRegex.containsMatchIn(prompt) -> {
                val mins = minRegex.find(prompt)?.groupValues?.get(1)?.toLongOrNull() ?: 1L
                nowMillis + mins.coerceAtLeast(1L) * 60_000L
            }
            hourRegex.containsMatchIn(prompt) -> {
                val hours = hourRegex.find(prompt)?.groupValues?.get(1)?.toLongOrNull() ?: 1L
                nowMillis + hours.coerceAtLeast(1L) * 3_600_000L
            }
            clockAtHourRegex.containsMatchIn(prompt) -> {
                val match = clockAtHourRegex.find(prompt)!!
                val hour = match.groupValues[1].toIntOrNull() ?: 9
                buildClockMillis(hour, 0, "")
            }
            else -> nowMillis + 60_000L // Default 1 minute
        }

        // Clean title from conversational boilerplate
        var cleanedTitle = prompt
            .replace(Regex("^(please\\s+)?(remind\\s+me\\s+to|notify\\s+me\\s+to|call\\s+me\\s+to|add\\s+a?\\s*task\\s+to|schedule\\s+a?\\s*task\\s+to|set\\s+a?\\s*reminder\\s+to)\\s+", RegexOption.IGNORE_CASE), "")
            .replace(secRegex, "")
            .replace(minRegex, "")
            .replace(hourRegex, "")
            .replace(clockColonRegex, "")
            .replace(clockAmPmRegex, "")
            .replace(clockAtHourRegex, "")
            .replace(Regex("with\\s+(a\\s+)?(incoming\\s+)?(call|pop-?up|notification|alert).*", RegexOption.IGNORE_CASE), "")
            .trim()
            .removeSuffix("now")
            .trim()

        if (cleanedTitle.isBlank()) {
            cleanedTitle = prompt.trim().take(80).ifBlank { "Scheduled AI Reminder" }
        } else {
            cleanedTitle = cleanedTitle.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }

        val workspace = availableWorkspaces.firstOrNull { ws ->
            ws != "All" && lower.contains(ws.lowercase())
        } ?: if (lower.contains("work") || lower.contains("meeting") || lower.contains("client")) "Work" else "Personal"

        val formattedTime = SimpleDateFormat("h:mm:ss a", Locale.getDefault()).format(Date(reminderTimeMillis))
        val modeDescription = when (alertMode) {
            TaskAlertMode.CALL -> "an Incoming AI Voice Call"
            TaskAlertMode.POPUP -> "a full Pop-Up Alert"
            TaskAlertMode.NOTIFICATION -> "a Push Notification"
            TaskAlertMode.NONE -> "a reminder"
        }

        return ResolvedAgentTaskAction(
            title = cleanedTitle.take(150),
            notes = "Scheduled by TaskFlow AI Agent from command: \"${prompt.trim()}\"",
            priority = priority,
            workspaceName = workspace,
            reminderTimeMillis = reminderTimeMillis,
            alertMode = alertMode,
            subtasks = listOf(
                "Prepare for: ${cleanedTitle.take(60)}",
                "Complete action & mark done"
            ),
            tags = listOf("ai-agent", alertMode.name.lowercase()),
            agentReply = "Got it! I added \"$cleanedTitle\" and scheduled $modeDescription for $formattedTime.",
            triggerImmediately = triggerNow
        )
    }
}
