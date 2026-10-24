package com.example.ui.components

import android.speech.tts.TextToSpeech
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.TaskAlertMode
import com.example.data.model.TaskItem
import com.example.notifications.IncomingAlertController
import com.example.notifications.TaskReminderManager
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.ElectricIndigo
import com.example.ui.theme.EmeraldSync
import com.example.ui.theme.PriorityUrgentColor
import com.example.ui.viewmodel.AgentChatMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun IncomingTaskCallOverlay(
    task: TaskItem,
    onCompleteTask: () -> Unit,
    onSnoozeMinutes: (Int) -> Unit,
    onDismissCall: () -> Unit
) {
    BackHandler { onDismissCall() }

    val context = LocalContext.current
    val autoAnswerFromNotification by IncomingAlertController.autoAnswerCall.collectAsStateWithLifecycle()
    var isCallAnswered by remember(task.id) { mutableStateOf(autoAnswerFromNotification) }
    var callDurationSeconds by remember(task.id) { mutableIntStateOf(0) }
    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }
    var ttsReady by remember { mutableStateOf(false) }

    LaunchedEffect(autoAnswerFromNotification) {
        if (autoAnswerFromNotification && !isCallAnswered) {
            isCallAnswered = true
        }
    }

    DisposableEffect(context, task.id) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.language = Locale.getDefault()
                ttsReady = true
            }
        }
        ttsEngine = engine
        onDispose {
            IncomingAlertController.stopRingingAndVibrating(context, task.id)
            engine?.stop()
            engine?.shutdown()
        }
    }

    // Ringing loop while unanswered; call timer & voice briefing once answered
    LaunchedEffect(isCallAnswered, ttsReady) {
        if (!isCallAnswered) {
            while (true) {
                TaskReminderManager.playAlertTone(isCall = true)
                TaskReminderManager.triggerVibration(context, isCall = true)
                delay(2400L)
            }
        } else {
            IncomingAlertController.onCallAnswered(context, task.id)
            val subtasksSpeech = task.decodedSubtasks()
                .filter { !it.completed }
                .joinToString(", ") { it.title }
            val speechScript = buildString {
                append("Hello! This is your TaskFlow AI Agent calling with a scheduled reminder. ")
                append("Task: ${task.title}. ")
                if (task.notes.isNotBlank()) {
                    append("Notes: ${task.notes}. ")
                }
                if (subtasksSpeech.isNotBlank()) {
                    append("Pending steps: $subtasksSpeech.")
                }
            }
            if (ttsReady) {
                ttsEngine?.speak(speechScript, TextToSpeech.QUEUE_FLUSH, null, "task_call_${task.id}")
            }
            while (true) {
                delay(1000L)
                callDurationSeconds += 1
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "call_rings")
    val ringScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ring_pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF090D16),
                        Color(0xFF1E1B4B),
                        Color(0xFF090D16)
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp)
            .testTag("incoming_call_overlay")
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Call Status Header
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Surface(
                    color = if (isCallAnswered) EmeraldSync.copy(alpha = 0.2f) else CyanAccent.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(50)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isCallAnswered) Icons.AutoMirrored.Filled.VolumeUp else Icons.Default.PhoneInTalk,
                            contentDescription = null,
                            tint = if (isCallAnswered) EmeraldSync else CyanAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isCallAnswered) {
                                val mins = callDurationSeconds / 60
                                val secs = callDurationSeconds % 60
                                String.format(Locale.getDefault(), "AI VOICE BRIEFING • %02d:%02d", mins, secs)
                            } else {
                                "INCOMING AI AGENT CALL"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "TaskFlow Reminder Agent",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.78f)
                )
            }

            // Center Pulsing Avatar + Task Card
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(150.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(
                            color = ElectricIndigo.copy(alpha = 0.22f),
                            radius = (size.minDimension / 2f) * ringScale
                        )
                        drawCircle(
                            color = EmeraldSync.copy(alpha = 0.30f),
                            radius = (size.minDimension / 2.6f) * ringScale
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(ElectricIndigo, CyanAccent)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isCallAnswered) Icons.Default.AutoAwesome else Icons.Default.Call,
                            contentDescription = "Caller Avatar",
                            tint = Color.White,
                            modifier = Modifier.size(42.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = task.title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = priorityColor(task.priorityEnum()).copy(alpha = 0.25f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "${task.priorityEnum().label.uppercase()} PRIORITY",
                            style = MaterialTheme.typography.labelSmall,
                            color = priorityColor(task.priorityEnum()),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    Surface(
                        color = Color.White.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = task.workspaceName,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                if (task.notes.isNotBlank() || task.subtasks.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White.copy(alpha = 0.1f)
                        ),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (task.notes.isNotBlank()) {
                                Text(
                                    text = task.notes,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                            }
                            val decoded = task.decodedSubtasks()
                            if (decoded.isNotEmpty()) {
                                decoded.take(3).forEach { step ->
                                    Text(
                                        text = "• ${step.title}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = CyanAccent
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Call Controls
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = { onSnoozeMinutes(5) },
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("snooze_call_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Snooze,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Snooze 5m", color = Color.White)
                    }

                    Button(
                        onClick = onCompleteTask,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldSync,
                            contentColor = Color(0xFF022C22)
                        ),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("complete_from_call_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Mark Done", fontWeight = FontWeight.Bold)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Decline / End Call Button
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        FilledIconButton(
                            onClick = onDismissCall,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = PriorityUrgentColor,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .size(68.dp)
                                .testTag("decline_call_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CallEnd,
                                contentDescription = "End or Decline Call",
                                modifier = Modifier.size(30.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (isCallAnswered) "End Call" else "Decline",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }

                    // Answer / Replay Voice Briefing Button
                    if (!isCallAnswered) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            FilledIconButton(
                                onClick = { isCallAnswered = true },
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = EmeraldSync,
                                    contentColor = Color(0xFF022C22)
                                ),
                                modifier = Modifier
                                    .size(68.dp)
                                    .testTag("answer_call_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Call,
                                    contentDescription = "Answer Call",
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Answer Call",
                                style = MaterialTheme.typography.labelMedium,
                                color = EmeraldSync
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TaskPopupAlertDialog(
    task: TaskItem,
    onCompleteTask: () -> Unit,
    onSnoozeMinutes: (Int) -> Unit,
    onSwitchToCall: () -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler { onDismiss() }

    val context = LocalContext.current
    LaunchedEffect(task.id) {
        TaskReminderManager.playAlertTone(isCall = false)
        TaskReminderManager.triggerVibration(context, isCall = false)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(ElectricIndigo.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = "Pop-Up Task Alert",
                    tint = ElectricIndigo,
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "SCHEDULED POP-UP ALERT",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = priorityColor(task.priorityEnum()).copy(alpha = 0.16f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = task.priorityEnum().label.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = priorityColor(task.priorityEnum()),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = task.workspaceName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                if (task.notes.isNotBlank()) {
                    Text(
                        text = task.notes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val steps = task.decodedSubtasks()
                if (steps.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            steps.take(3).forEach { s ->
                                Text(
                                    text = "• ${s.title}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onSnoozeMinutes(5) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("popup_snooze_button")
                    ) {
                        Icon(Icons.Default.Snooze, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Snooze 5m")
                    }
                    OutlinedButton(
                        onClick = onSwitchToCall,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("popup_switch_to_call_button")
                    ) {
                        Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Voice Call")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onCompleteTask,
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldSync,
                    contentColor = Color(0xFF022C22)
                ),
                modifier = Modifier.testTag("popup_complete_button")
            ) {
                Text("Mark Complete", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiTaskAgentSheet(
    messages: List<AgentChatMessage>,
    isProcessing: Boolean,
    onSendPrompt: (String) -> Unit,
    onTriggerCallPreview: (TaskItem) -> Unit,
    onTriggerPopupPreview: (TaskItem) -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler { onDismiss() }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var promptInput by remember { mutableStateOf("") }

    val quickPrompts = listOf(
        "Call me in 10 seconds to join the product standup",
        "Pop up in 1 minute to review Q4 budget",
        "Call me now to test incoming AI voice call",
        "Notify me in 5 minutes to stretch and drink water"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .imePadding()
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(ElectricIndigo, CyanAccent)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "TaskFlow AI Reminder Agent",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "Schedules notifications, pop-ups & incoming voice calls",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Quick Prompt Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                quickPrompts.forEachIndexed { idx, sample ->
                    AssistChip(
                        onClick = { onSendPrompt(sample) },
                        label = { Text(sample, maxLines = 1) },
                        leadingIcon = {
                            Icon(
                                imageVector = if (sample.contains("Call", ignoreCase = true)) {
                                    Icons.Default.Call
                                } else {
                                    Icons.Default.Alarm
                                },
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.testTag("agent_quick_prompt_$idx")
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Chat Messages Stream
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    val isUser = msg.isUser
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
                    ) {
                        Surface(
                            color = if (isUser) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            shape = RoundedCornerShape(
                                topStart = 16.dp,
                                topEnd = 16.dp,
                                bottomStart = if (isUser) 16.dp else 4.dp,
                                bottomEnd = if (isUser) 4.dp else 16.dp
                            ),
                            modifier = Modifier.fillMaxWidth(0.88f)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = msg.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isUser) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )

                                val createdTask = msg.createdTask
                                if (createdTask != null) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.surface,
                                        shape = MaterialTheme.shapes.small,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            val timeText = if (createdTask.reminderTimeMillis > 0L) {
                                                SimpleDateFormat("h:mm:ss a", Locale.getDefault())
                                                    .format(Date(createdTask.reminderTimeMillis))
                                            } else "No time set"

                                            Text(
                                                text = "Scheduled: ${createdTask.title}",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Mode: ${createdTask.alertModeEnum().label} • Fires at $timeText",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                OutlinedButton(
                                                    onClick = { onTriggerPopupPreview(createdTask) },
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Text("Pop-Up Now", style = MaterialTheme.typography.labelSmall)
                                                }
                                                Button(
                                                    onClick = { onTriggerCallPreview(createdTask) },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = EmeraldSync,
                                                        contentColor = Color(0xFF022C22)
                                                    ),
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .testTag("agent_preview_call_button")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Call,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Call Me Now", style = MaterialTheme.typography.labelSmall)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Input bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = promptInput,
                    onValueChange = { promptInput = it },
                    placeholder = {
                        Text("Tell agent: \"Call me in 1 min to call Alex\"…")
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("agent_prompt_input")
                )
                Spacer(modifier = Modifier.width(10.dp))
                FilledIconButton(
                    onClick = {
                        if (promptInput.isNotBlank() && !isProcessing) {
                            val toSend = promptInput.trim()
                            promptInput = ""
                            onSendPrompt(toSend)
                        }
                    },
                    enabled = promptInput.isNotBlank() && !isProcessing,
                    modifier = Modifier
                        .size(52.dp)
                        .testTag("agent_send_button")
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send to AI Agent"
                        )
                    }
                }
            }
        }
    }
}
