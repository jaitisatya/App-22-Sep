package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils

fun buildDynamicLightColorScheme(primaryColor: Color): ColorScheme {
    val argb = primaryColor.toArgb()

    // Generate harmonious containers and surface tones
    val containerArgb = ColorUtils.blendARGB(argb, 0xFFFFFFFF.toInt(), 0.88f)
    val onContainerArgb = ColorUtils.blendARGB(argb, 0xFF000000.toInt(), 0.35f)
    val secondaryArgb = ColorUtils.blendARGB(argb, 0xFF475569.toInt(), 0.30f)
    val secondaryContainerArgb = ColorUtils.blendARGB(argb, 0xFFFFFFFF.toInt(), 0.94f)
    val surfaceVariantArgb = ColorUtils.blendARGB(argb, 0xFFFFFFFF.toInt(), 0.92f)

    return lightColorScheme(
        primary = primaryColor,
        onPrimary = Color.White,
        primaryContainer = Color(containerArgb),
        onPrimaryContainer = Color(onContainerArgb),
        secondary = Color(secondaryArgb),
        onSecondary = Color.White,
        secondaryContainer = Color(secondaryContainerArgb),
        onSecondaryContainer = Color(onContainerArgb),
        tertiary = JaitiAccentAmber,
        background = Color.White,
        surface = Color.White,
        surfaceVariant = Color(surfaceVariantArgb),
        onSurface = Color(0xFF1E293B),
        onSurfaceVariant = Color(0xFF64748B),
        outline = Color(0xFFCBD5E1),
        outlineVariant = Color(0xFFE2E8F0)
    )
}

fun buildDynamicDarkColorScheme(primaryColor: Color): ColorScheme {
    val argb = primaryColor.toArgb()

    // Brighten slightly for dark theme visibility
    val brightPrimaryArgb = ColorUtils.blendARGB(argb, 0xFFFFFFFF.toInt(), 0.25f)
    val darkContainerArgb = ColorUtils.blendARGB(argb, 0xFF000000.toInt(), 0.60f)
    val onDarkContainerArgb = ColorUtils.blendARGB(argb, 0xFFFFFFFF.toInt(), 0.85f)
    val secondaryArgb = ColorUtils.blendARGB(argb, 0xFF94A3B8.toInt(), 0.30f)

    return darkColorScheme(
        primary = Color(brightPrimaryArgb),
        onPrimary = Color.White,
        primaryContainer = Color(darkContainerArgb),
        onPrimaryContainer = Color(onDarkContainerArgb),
        secondary = Color(secondaryArgb),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFF1E293B),
        onSecondaryContainer = Color(0xFFE2E8F0),
        tertiary = JaitiAccentAmber,
        background = Color(0xFF0F172A),
        surface = Color(0xFF1E293B),
        surfaceVariant = Color(0xFF334155),
        onSurface = Color(0xFFF8FAFC),
        onSurfaceVariant = Color(0xFF94A3B8),
        outline = Color(0xFF475569),
        outlineVariant = Color(0xFF334155)
    )
}

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit,
) {
    val themeMode by ThemeManager.themeMode.collectAsState()
    val accentHex by ThemeManager.accentHex.collectAsState()

    val systemDark = isSystemInDarkTheme()
    val useDarkTheme = when (themeMode) {
        AppThemeMode.SYSTEM -> systemDark
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }

    val primaryColor = ThemeManager.parseColor(accentHex)
    val colorScheme = if (useDarkTheme) {
        buildDynamicDarkColorScheme(primaryColor)
    } else {
        buildDynamicLightColorScheme(primaryColor)
    }

    CompositionLocalProvider(
        LocalThemeMode provides themeMode,
        LocalAccentHex provides accentHex
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

@Composable
fun JaitiAttendanceTheme(content: @Composable () -> Unit) {
    MyApplicationTheme(content = content)
}

