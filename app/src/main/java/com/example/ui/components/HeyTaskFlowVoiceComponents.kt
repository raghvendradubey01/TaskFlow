package com.example.ui.components

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.TaskItem
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.ElectricIndigo
import com.example.ui.theme.EmeraldSync
import com.example.voice.VoiceAssistantState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HeyTaskFlowVoiceBanner(
    isServiceEnabled: Boolean,
    assistantState: VoiceAssistantState,
    statusMessage: String,
    micRmsLevel: Float = 0f,
    onToggleBackgroundService: (Boolean) -> Unit,
    onSpeakNowClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "voice_banner_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val dynamicBoost = (micRmsLevel * 0.45f).coerceIn(0f, 0.45f)

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("hey_taskflow_voice_banner")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = if (isServiceEnabled) {
                            listOf(
                                Color(0xFF0F172A),
                                Color(0xFF1E1B4B),
                                Color(0xFF064E3B)
                            )
                        } else {
                            listOf(
                                Color(0xFF1E293B),
                                Color(0xFF0F172A)
                            )
                        }
                    )
                )
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSpeakNowClick() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                if (isServiceEnabled) {
                                    CyanAccent.copy(alpha = (pulseAlpha * 0.3f + dynamicBoost).coerceAtMost(0.9f))
                                } else {
                                    Color.White.copy(alpha = 0.1f)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (assistantState) {
                                VoiceAssistantState.SPEAKING_RESPONSE -> Icons.AutoMirrored.Filled.VolumeUp
                                VoiceAssistantState.AWAITING_COMMAND -> Icons.Default.RecordVoiceOver
                                else -> if (isServiceEnabled) Icons.Default.GraphicEq else Icons.Default.Mic
                            },
                            contentDescription = "Hey TaskFlow Voice Assistant",
                            tint = if (isServiceEnabled) CyanAccent else Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "\"Hey TaskFlow\" Voice AI",
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = if (isServiceEnabled) {
                                    EmeraldSync.copy(alpha = 0.22f)
                                } else {
                                    Color.White.copy(alpha = 0.12f)
                                },
                                shape = RoundedCornerShape(50)
                            ) {
                                Text(
                                    text = when {
                                        assistantState == VoiceAssistantState.AWAITING_COMMAND -> "TAKE COMMAND"
                                        assistantState == VoiceAssistantState.SPEAKING_RESPONSE -> "SPEAKING"
                                        isServiceEnabled -> "LISTENING"
                                        else -> "OFF"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isServiceEnabled) EmeraldSync else Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = statusMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilledIconButton(
                        onClick = onSpeakNowClick,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = CyanAccent,
                            contentColor = Color(0xFF042F2E)
                        ),
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("speak_task_now_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Wake Hey TaskFlow & Speak Command",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Switch(
                        checked = isServiceEnabled,
                        onCheckedChange = onToggleBackgroundService,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = EmeraldSync
                        ),
                        modifier = Modifier.testTag("hey_taskflow_background_switch")
                    )
                }
            }
        }
    }
}

@Composable
fun HeyTaskFlowOrbOverlay(
    assistantState: VoiceAssistantState,
    liveTranscript: String,
    statusMessage: String,
    micRmsLevel: Float = 0f,
    lastCreatedTask: TaskItem?,
    onStartListeningNow: () -> Unit,
    onLaunchSystemVoiceRecognizer: (() -> Unit)? = null,
    onSubmitVoiceCommandText: (String) -> Unit,
    onPreviewCallForTask: (TaskItem) -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler { onDismiss() }

    var fallbackText by remember { mutableStateOf("") }

    val infiniteTransition = rememberInfiniteTransition(label = "siri_orb_transition")
    val outerWave by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(950, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "outer_wave"
    )
    val innerWave by infiniteTransition.animateFloat(
        initialValue = 1.15f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "inner_wave"
    )

    val voiceBoost = 1f + (micRmsLevel * 0.45f)

    val quickVoiceExamples = listOf(
        "Call me in 1 minute to review client report",
        "Call me at 5:30 PM for team standup",
        "Pop up alert in 2 minutes to take medicine",
        "Call me now to test my urgent presentation briefing"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xD9090D16))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(20.dp)
            .testTag("hey_taskflow_orb_overlay"),
        contentAlignment = Alignment.BottomCenter
    ) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF111827)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = CyanAccent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Hey TaskFlow Voice Assistant",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_voice_overlay_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Voice Assistant",
                            tint = Color.White.copy(alpha = 0.75f)
                        )
                    }
                }

                // State Badge (Siri / Alexa Mode Indicator)
                Surface(
                    color = when (assistantState) {
                        VoiceAssistantState.AWAITING_COMMAND -> EmeraldSync.copy(alpha = 0.2f)
                        VoiceAssistantState.SPEAKING_RESPONSE -> CyanAccent.copy(alpha = 0.2f)
                        else -> ElectricIndigo.copy(alpha = 0.25f)
                    },
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = when (assistantState) {
                            VoiceAssistantState.AWAITING_COMMAND -> "ACTIVE • SPEAK YOUR COMMAND NOW"
                            VoiceAssistantState.SPEAKING_RESPONSE -> "SPEAKING AUDIO RESPONSE..."
                            VoiceAssistantState.PROCESSING -> "SCHEDULING YOUR TASK..."
                            else -> "WAKE WORD READY • SAY \"HEY TASKFLOW\""
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = when (assistantState) {
                            VoiceAssistantState.AWAITING_COMMAND -> EmeraldSync
                            VoiceAssistantState.SPEAKING_RESPONSE -> CyanAccent
                            else -> Color.White
                        },
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }

                // Glowing Siri / Alexa Multi-Wave Orb reactive to live microphone audio
                Box(
                    modifier = Modifier
                        .size(132.dp)
                        .clickable { onStartListeningNow() },
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val baseRadius = size.minDimension / 2.2f
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    CyanAccent.copy(alpha = 0.45f),
                                    ElectricIndigo.copy(alpha = 0.05f)
                                )
                            ),
                            radius = baseRadius * outerWave * voiceBoost
                        )
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    EmeraldSync.copy(alpha = 0.48f),
                                    ElectricIndigo.copy(alpha = 0.12f)
                                )
                            ),
                            radius = baseRadius * innerWave * voiceBoost
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(74.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(ElectricIndigo, CyanAccent, EmeraldSync)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (assistantState) {
                                VoiceAssistantState.OFF, VoiceAssistantState.ERROR -> Icons.Default.MicOff
                                VoiceAssistantState.SPEAKING_RESPONSE -> Icons.AutoMirrored.Filled.VolumeUp
                                else -> Icons.Default.Mic
                            },
                            contentDescription = "Tap to Speak",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }

                // Status & Live Speech Transcript
                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = CyanAccent,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.SemiBold
                )

                if (liveTranscript.isNotBlank()) {
                    Surface(
                        color = Color.White.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "\"$liveTranscript\"",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }
                } else {
                    Text(
                        text = "Say: \"Hey TaskFlow\" -> wait for chime -> \"Call me at 5:00 PM for meeting\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.68f),
                        textAlign = TextAlign.Center
                    )
                }

                // Action Row: Re-Wake / System Voice Recognizer Option
                if (onLaunchSystemVoiceRecognizer != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = onStartListeningNow,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.RecordVoiceOver,
                                contentDescription = null,
                                tint = CyanAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Wake & Listen", color = Color.White)
                        }
                        OutlinedButton(
                            onClick = onLaunchSystemVoiceRecognizer,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = null,
                                tint = EmeraldSync,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("System Voice Mic", color = Color.White)
                        }
                    }
                }

                // Created Task Confirmation Card
                if (lastCreatedTask != null) {
                    val timeFmt = remember { SimpleDateFormat("h:mm:ss a", Locale.getDefault()) }
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = EmeraldSync.copy(alpha = 0.14f)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "TASK SCHEDULED VIA VOICE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldSync,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${lastCreatedTask.alertModeEnum().label} • ${timeFmt.format(Date(lastCreatedTask.reminderTimeMillis))}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CyanAccent
                                )
                            }
                            Text(
                                text = lastCreatedTask.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Button(
                                    onClick = {
                                        onDismiss()
                                        onPreviewCallForTask(lastCreatedTask)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = EmeraldSync,
                                        contentColor = Color(0xFF022C22)
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Call,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Start Call Now", fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = onStartListeningNow,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Mic,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Add Another", color = Color.White)
                                }
                            }
                        }
                    }
                }

                // Quick Voice Simulation Chips (helpful in quiet spaces or emulator testing)
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Or tap a voice command to execute immediately:",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        quickVoiceExamples.forEach { sample ->
                            AssistChip(
                                onClick = { onSubmitVoiceCommandText(sample) },
                                label = {
                                    Text(
                                        text = sample,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White
                                    )
                                }
                            )
                        }
                    }
                }

                // Direct Voice-Fallback Input Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = fallbackText,
                        onValueChange = { fallbackText = it },
                        placeholder = {
                            Text(
                                "Or type a 'Hey TaskFlow' voice command...",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.5f)
                            )
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = CyanAccent,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.25f)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("voice_fallback_input")
                    )

                    FilledIconButton(
                        onClick = {
                            if (fallbackText.isNotBlank()) {
                                onSubmitVoiceCommandText(fallbackText)
                                fallbackText = ""
                            } else {
                                onStartListeningNow()
                            }
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = CyanAccent,
                            contentColor = Color(0xFF042F2E)
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("voice_fallback_send_button")
                    ) {
                        Icon(
                            imageVector = if (fallbackText.isNotBlank()) {
                                Icons.AutoMirrored.Filled.Send
                            } else {
                                Icons.Default.Mic
                            },
                            contentDescription = "Send or Speak"
                        )
                    }
                }
            }
        }
    }
}
