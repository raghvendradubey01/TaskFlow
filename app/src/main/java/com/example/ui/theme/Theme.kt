package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = ElectricIndigo,
    onPrimary = Color.White,
    primaryContainer = ElectricIndigoContainer,
    onPrimaryContainer = OnElectricIndigoContainer,
    secondary = CyanAccent,
    onSecondary = Color(0xFF042F2E),
    secondaryContainer = CyanContainer,
    onSecondaryContainer = OnCyanContainer,
    tertiary = EmeraldSync,
    onTertiary = Color(0xFF022C22),
    tertiaryContainer = EmeraldContainer,
    onTertiaryContainer = OnEmeraldContainer,
    background = ObsidianBackground,
    onBackground = Color(0xFFF1F5F9),
    surface = ObsidianSurface,
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF334155),
    error = Color(0xFFF87171)
)

private val LightColorScheme = lightColorScheme(
    primary = DeepIndigo,
    onPrimary = Color.White,
    primaryContainer = DeepIndigoContainer,
    onPrimaryContainer = OnDeepIndigoContainer,
    secondary = DeepCyan,
    onSecondary = Color.White,
    secondaryContainer = DeepCyanContainer,
    onSecondaryContainer = OnDeepCyanContainer,
    tertiary = DeepEmerald,
    onTertiary = Color.White,
    tertiaryContainer = DeepEmeraldContainer,
    onTertiaryContainer = OnDeepEmeraldContainer,
    background = AlabasterBackground,
    onBackground = Color(0xFF0F172A),
    surface = AlabasterSurface,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = AlabasterSurfaceVariant,
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1),
    error = Color(0xFFDC2626)
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
