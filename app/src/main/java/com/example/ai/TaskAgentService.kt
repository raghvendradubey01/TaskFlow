package com.example.ai

import com.example.BuildConfig
import com.example.data.model.TaskAlertMode
import com.example.data.model.TaskPriority
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

@Serializable
data class GenerateContentRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null,
    val systemInstruction: Content? = null
)

@Serializable
data class Content(
    val parts: List<Part>
)

@Serializable
data class Part(
    val text: String? = null
)

@Serializable
data class GenerationConfig(
    val responseMimeType: String? = null,
    val responseSchema: JsonObject? = null,
    val temperature: Float? = null
)

@Serializable
data class GenerateContentResponse(
    val candidates: List<Candidate> = emptyList()
)

@Serializable
data class Candidate(
    val content: Content? = null
)

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

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

object TaskAgentService {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val jsonFormat = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    private val apiService: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(jsonFormat.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GeminiApiService::class.java)
    }

    suspend fun parseUserReminderRequest(
        userPrompt: String,
        availableWorkspaces: List<String>
    ): ResolvedAgentTaskAction = withContext(Dispatchers.IO) {
        val nowMillis = System.currentTimeMillis()
        val currentTimeFormatted = SimpleDateFormat("HH:mm (h:mm a)", Locale.getDefault()).format(Date(nowMillis))
        val apiKey = BuildConfig.GEMINI_API_KEY

        val isKeyConfigured = apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY"
        if (isKeyConfigured) {
            try {
                val schema = buildJsonObject {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("title") {
                            put("type", "STRING")
                            put("description", "Concise task title extracted from the user's request.")
                        }
                        putJsonObject("notes") {
                            put("type", "STRING")
                            put("description", "Helpful context or reminder notes for the task.")
                        }
                        putJsonObject("priority") {
                            put("type", "STRING")
                            put("description", "One of LOW, MEDIUM, HIGH, URGENT.")
                        }
                        putJsonObject("workspaceName") {
                            put("type", "STRING")
                            put("description", "Best matching workspace name from ${availableWorkspaces.joinToString()}.")
                        }
                        putJsonObject("delaySeconds") {
                            put("type", "INTEGER")
                            put("description", "Number of seconds from now to trigger the alert if relative time is used (e.g. 'in 1 minute' = 60, 'now' = 5). Use 0 if specific clock time is given.")
                        }
                        putJsonObject("targetHour24") {
                            put("type", "INTEGER")
                            put("description", "24-hour clock hour (0-23) if user specified an exact clock time like 'at 5:30 PM', otherwise -1.")
                        }
                        putJsonObject("targetMinute") {
                            put("type", "INTEGER")
                            put("description", "Clock minute (0-59) if user specified an exact clock time, otherwise -1.")
                        }
                        putJsonObject("alertMode") {
                            put("type", "STRING")
                            put("description", "One of CALL (if user asks for a call/ring/phone alert), POPUP (if user asks for pop-up/dialog), or NOTIFICATION (standard alert).")
                        }
                        putJsonObject("subtasks") {
                            put("type", "ARRAY")
                            putJsonObject("items") { put("type", "STRING") }
                            put("description", "2 to 3 actionable checklist steps for the task.")
                        }
                        putJsonObject("tags") {
                            put("type", "ARRAY")
                            putJsonObject("items") { put("type", "STRING") }
                            put("description", "1 or 2 short lowercase tags.")
                        }
                        putJsonObject("agentReply") {
                            put("type", "STRING")
                            put("description", "Friendly confirmation message explaining what task was added, when it will trigger, and whether it will ring as a Call, Pop-Up, or Notification.")
                        }
                    }
                    putJsonArray("required") {
                        add(kotlinx.serialization.json.JsonPrimitive("title"))
                        add(kotlinx.serialization.json.JsonPrimitive("notes"))
                        add(kotlinx.serialization.json.JsonPrimitive("priority"))
                        add(kotlinx.serialization.json.JsonPrimitive("workspaceName"))
                        add(kotlinx.serialization.json.JsonPrimitive("delaySeconds"))
                        add(kotlinx.serialization.json.JsonPrimitive("targetHour24"))
                        add(kotlinx.serialization.json.JsonPrimitive("targetMinute"))
                        add(kotlinx.serialization.json.JsonPrimitive("alertMode"))
                        add(kotlinx.serialization.json.JsonPrimitive("subtasks"))
                        add(kotlinx.serialization.json.JsonPrimitive("tags"))
                        add(kotlinx.serialization.json.JsonPrimitive("agentReply"))
                    }
                }

                val systemPrompt = """
                    You are the TaskFlow AI Reminder & Call Agent.
                    Current local time is $currentTimeFormatted.
                    Available workspaces: ${availableWorkspaces.joinToString(", ")}.
                    Extract the user's task, schedule time, and preferred alert style:
                    - If they mention "call", "ring", "getting a call", or "voice", set alertMode to "CALL".
                    - If they mention "pop up", "popup", "modal", or "screen alert", set alertMode to "POPUP".
                    - Otherwise, if they ask to notify/remind without specifying, default to "POPUP" or "CALL" based on urgency.
                    Always generate 2-3 helpful subtasks and a warm, concise agentReply.
                """.trimIndent()

                val request = GenerateContentRequest(
                    contents = listOf(Content(parts = listOf(Part(text = userPrompt)))),
                    generationConfig = GenerationConfig(
                        responseMimeType = "application/json",
                        responseSchema = schema,
                        temperature = 0.2f
                    ),
                    systemInstruction = Content(parts = listOf(Part(text = systemPrompt)))
                )

                val response = apiService.generateContent(apiKey, request)
                val rawJson = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
                if (!rawJson.isNullOrBlank()) {
                    val plan = jsonFormat.decodeFromString<ParsedAgentTaskPlan>(rawJson)
                    return@withContext convertPlanToAction(plan, nowMillis)
                }
            } catch (_: Exception) {
                // Fall through to deterministic local NLU parser so user workflow never breaks
            }
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

        // Parse "in X sec/min/hour" or "at HH:MM"
        val secRegex = Regex("in\\s+(\\d+)\\s*(sec|second|seconds|s)\\b", RegexOption.IGNORE_CASE)
        val minRegex = Regex("in\\s+(\\d+)\\s*(min|minute|minutes|m)\\b", RegexOption.IGNORE_CASE)
        val hourRegex = Regex("in\\s+(\\d+)\\s*(hr|hour|hours|h)\\b", RegexOption.IGNORE_CASE)
        val clockRegex = Regex("at\\s+(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?", RegexOption.IGNORE_CASE)

        val reminderTimeMillis = when {
            triggerNow -> nowMillis + 4_000L
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
            clockRegex.containsMatchIn(prompt) -> {
                val match = clockRegex.find(prompt)!!
                var hour = match.groupValues[1].toIntOrNull() ?: 9
                val minute = match.groupValues[2].toIntOrNull() ?: 0
                val ampm = match.groupValues[3].lowercase()
                if (ampm == "pm" && hour < 12) hour += 12
                if (ampm == "am" && hour == 12) hour = 0
                val cal = Calendar.getInstance().apply {
                    timeInMillis = nowMillis
                    set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
                    set(Calendar.MINUTE, minute.coerceIn(0, 59))
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                if (cal.timeInMillis <= nowMillis) {
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                }
                cal.timeInMillis
            }
            else -> nowMillis + 60_000L // Default 1 minute
        }

        // Clean title from conversational boilerplate
        var cleanedTitle = prompt
            .replace(Regex("^(please\\s+)?(remind\\s+me\\s+to|notify\\s+me\\s+to|call\\s+me\\s+to|add\\s+a?\\s*task\\s+to|schedule\\s+a?\\s*task\\s+to|set\\s+a?\\s*reminder\\s+to)\\s+", RegexOption.IGNORE_CASE), "")
            .replace(secRegex, "")
            .replace(minRegex, "")
            .replace(hourRegex, "")
            .replace(clockRegex, "")
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
