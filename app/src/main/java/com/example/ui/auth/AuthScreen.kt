package com.example.ui.auth

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.auth.attemptAutoSignIn
import com.example.auth.onGoogleSignInClicked
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.ElectricIndigo
import com.example.ui.theme.EmeraldSync

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = onAuthSuccess,
            onUnauthenticated = { /* Stay on interactive sign-in screen */ },
            scope = scope
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Animated Cloud-Sync Hero Graphic
            CloudSyncHeroIllustration(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
                shape = RoundedCornerShape(50),
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudSync,
                        contentDescription = "Real-time cloud sync",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "INSTANT MULTI-DEVICE SYNC",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }

            Text(
                text = stringResource(R.string.auth_headline),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = stringResource(R.string.auth_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Feature highlights
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                ),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    FeatureRow(
                        icon = Icons.Default.Devices,
                        iconTint = ElectricIndigo,
                        title = "Seamless Device Handoff",
                        subtitle = "Start a task on your phone, check off subtasks on your tablet or Chromebook in real time."
                    )
                    FeatureRow(
                        icon = Icons.Default.CheckCircle,
                        iconTint = EmeraldSync,
                        title = "Workspaces, Priorities & Subtasks",
                        subtitle = "Organize deep work with custom workspaces, tags, priority tiers, and step-by-step checklists."
                    )
                    FeatureRow(
                        icon = Icons.Default.Security,
                        iconTint = CyanAccent,
                        title = "Zero-Trust Account Isolation",
                        subtitle = "Protected by your Google account with strict per-user Firestore security rules."
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            if (errorMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(14.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Button(
                onClick = {
                    errorMessage = null
                    isLoading = true
                    onGoogleSignInClicked(
                        context = context,
                        credentialManager = credentialManager,
                        onAuthSuccess = {
                            isLoading = false
                            onAuthSuccess()
                        },
                        onAuthError = { msg ->
                            isLoading = false
                            errorMessage = msg
                        },
                        scope = scope,
                        onAuthCancelled = {
                            isLoading = false
                        }
                    )
                },
                enabled = !isLoading,
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("google_sign_in_button")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Text(
                        text = stringResource(R.string.sign_in_with_google),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun FeatureRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String
) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun CloudSyncHeroIllustration(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "sync_pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val minDim = minOf(size.width, size.height)

        // Outer glowing orbital rings
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    ElectricIndigo.copy(alpha = 0.28f),
                    CyanAccent.copy(alpha = 0.08f),
                    Color.Transparent
                ),
                center = center,
                radius = minDim * 0.52f * pulse
            ),
            radius = minDim * 0.52f * pulse,
            center = center
        )

        drawCircle(
            color = ElectricIndigo.copy(alpha = 0.35f),
            radius = minDim * 0.40f,
            center = center,
            style = Stroke(width = 2.dp.toPx())
        )

        drawCircle(
            color = EmeraldSync.copy(alpha = 0.3f),
            radius = minDim * 0.28f * pulse,
            center = center,
            style = Stroke(width = 1.5.dp.toPx())
        )

        // Left floating device card (Tablet/Laptop representation)
        val leftCardTopLeft = Offset(center.x - minDim * 0.44f, center.y - minDim * 0.16f)
        val leftCardSize = Size(minDim * 0.34f, minDim * 0.26f)
        drawRoundRect(
            color = Color(0xFF1E293B),
            topLeft = leftCardTopLeft,
            size = leftCardSize,
            cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx())
        )
        drawRoundRect(
            color = CyanAccent.copy(alpha = 0.7f),
            topLeft = leftCardTopLeft,
            size = leftCardSize,
            cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
            style = Stroke(width = 2.dp.toPx())
        )

        // Right floating device card (Phone representation)
        val rightCardTopLeft = Offset(center.x + minDim * 0.14f, center.y - minDim * 0.20f)
        val rightCardSize = Size(minDim * 0.28f, minDim * 0.36f)
        drawRoundRect(
            color = Color(0xFF1E293B),
            topLeft = rightCardTopLeft,
            size = rightCardSize,
            cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx())
        )
        drawRoundRect(
            color = EmeraldSync.copy(alpha = 0.7f),
            topLeft = rightCardTopLeft,
            size = rightCardSize,
            cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
            style = Stroke(width = 2.dp.toPx())
        )

        // Central TaskFlow Master Card
        val mainCardWidth = minDim * 0.48f
        val mainCardHeight = minDim * 0.38f
        val mainTopLeft = Offset(center.x - mainCardWidth / 2f, center.y - mainCardHeight / 2f)
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(ElectricIndigo, Color(0xFF312E81)),
                start = mainTopLeft,
                end = Offset(mainTopLeft.x + mainCardWidth, mainTopLeft.y + mainCardHeight)
            ),
            topLeft = mainTopLeft,
            size = Size(mainCardWidth, mainCardHeight),
            cornerRadius = CornerRadius(18.dp.toPx(), 18.dp.toPx())
        )

        // Checkmark & task lines inside central card
        val checkCenter = Offset(mainTopLeft.x + mainCardWidth * 0.26f, center.y)
        drawCircle(
            color = EmeraldSync,
            radius = minDim * 0.075f,
            center = checkCenter
        )
        drawLine(
            color = Color.White,
            start = Offset(checkCenter.x - 6.dp.toPx(), checkCenter.y),
            end = Offset(checkCenter.x - 1.dp.toPx(), checkCenter.y + 5.dp.toPx()),
            strokeWidth = 2.5.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color.White,
            start = Offset(checkCenter.x - 1.dp.toPx(), checkCenter.y + 5.dp.toPx()),
            end = Offset(checkCenter.x + 7.dp.toPx(), checkCenter.y - 5.dp.toPx()),
            strokeWidth = 2.5.dp.toPx(),
            cap = StrokeCap.Round
        )

        // Task bars
        drawRoundRect(
            color = Color.White.copy(alpha = 0.92f),
            topLeft = Offset(mainTopLeft.x + mainCardWidth * 0.42f, center.y - 10.dp.toPx()),
            size = Size(mainCardWidth * 0.44f, 7.dp.toPx()),
            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
        )
        drawRoundRect(
            color = Color.White.copy(alpha = 0.55f),
            topLeft = Offset(mainTopLeft.x + mainCardWidth * 0.42f, center.y + 4.dp.toPx()),
            size = Size(mainCardWidth * 0.30f, 6.dp.toPx()),
            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
        )
    }
}
