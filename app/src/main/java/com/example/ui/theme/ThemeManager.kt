package com.example.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

data class AccentColorPreset(
    val id: String,
    val name: String,
    val hex: String,
    val description: String,
    val isDefault: Boolean = false
)

object ThemeManager {
    const val DEFAULT_ACCENT_HEX = "#1B4D3E" // Jaiti Forest Green

    val PRESET_ACCENT_COLORS = listOf(
        AccentColorPreset(
            id = "jaiti_emerald",
            name = "Jaiti Emerald",
            hex = "#1B4D3E",
            description = "Official Jaiti Foundation brand green",
            isDefault = true
        ),
        AccentColorPreset(
            id = "ocean_sapphire",
            name = "Ocean Sapphire",
            hex = "#1D4ED8",
            description = "Professional cobalt blue"
        ),
        AccentColorPreset(
            id = "vibrant_teal",
            name = "Teal Emerald",
            hex = "#0F766E",
            description = "Calming and modern seafoam teal"
        ),
        AccentColorPreset(
            id = "royal_violet",
            name = "Royal Violet",
            hex = "#7C3AED",
            description = "Creative and vibrant purple"
        ),
        AccentColorPreset(
            id = "warm_terracotta",
            name = "Warm Terracotta",
            hex = "#C2410C",
            description = "Earthy and energetic rust orange"
        ),
        AccentColorPreset(
            id = "berry_crimson",
            name = "Berry Crimson",
            hex = "#BE123C",
            description = "Warm, elegant ruby rose"
        ),
        AccentColorPreset(
            id = "midnight_indigo",
            name = "Midnight Indigo",
            hex = "#312E81",
            description = "Focused deep classic navy"
        ),
        AccentColorPreset(
            id = "golden_amber",
            name = "Golden Amber",
            hex = "#D97706",
            description = "Warm sunny amber"
        ),
        AccentColorPreset(
            id = "nordic_cyan",
            name = "Nordic Cyan",
            hex = "#0284C7",
            description = "Fresh and clean sky azure"
        ),
        AccentColorPreset(
            id = "minimal_slate",
            name = "Modern Slate",
            hex = "#334155",
            description = "Neutral and sleek graphite"
        )
    )

    private const val PREFS_NAME = "jaiti_theme_prefs"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_ACCENT_HEX = "theme_accent_hex"
    private const val KEY_TEACHER_TITLE = "teacher_local_title"
    private const val KEY_TEACHER_NOTE = "teacher_local_note"

    private val _themeMode = MutableStateFlow(AppThemeMode.SYSTEM)
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    private val _accentHex = MutableStateFlow(DEFAULT_ACCENT_HEX)
    val accentHex: StateFlow<String> = _accentHex.asStateFlow()

    private var sharedPreferences: SharedPreferences? = null

    fun initialize(context: Context) {
        if (sharedPreferences == null) {
            sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val savedModeName = sharedPreferences?.getString(KEY_THEME_MODE, AppThemeMode.SYSTEM.name)
            val initialMode = try {
                AppThemeMode.valueOf(savedModeName ?: AppThemeMode.SYSTEM.name)
            } catch (e: Exception) {
                AppThemeMode.SYSTEM
            }
            _themeMode.value = initialMode

            val savedHex = sharedPreferences?.getString(KEY_ACCENT_HEX, DEFAULT_ACCENT_HEX) ?: DEFAULT_ACCENT_HEX
            _accentHex.value = sanitizeHex(savedHex)
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        _themeMode.value = mode
        sharedPreferences?.edit()?.putString(KEY_THEME_MODE, mode.name)?.apply()
    }

    fun setAccentHex(hex: String) {
        val sanitized = sanitizeHex(hex)
        _accentHex.value = sanitized
        sharedPreferences?.edit()?.putString(KEY_ACCENT_HEX, sanitized)?.apply()
    }

    fun resetToDefault() {
        setThemeMode(AppThemeMode.SYSTEM)
        setAccentHex(DEFAULT_ACCENT_HEX)
    }

    fun getTeacherLocalTitle(): String {
        return sharedPreferences?.getString(KEY_TEACHER_TITLE, "Primary Educator") ?: "Primary Educator"
    }

    fun setTeacherLocalTitle(title: String) {
        sharedPreferences?.edit()?.putString(KEY_TEACHER_TITLE, title.trim())?.apply()
    }

    fun getTeacherLocalNote(): String {
        return sharedPreferences?.getString(KEY_TEACHER_NOTE, "") ?: ""
    }

    fun setTeacherLocalNote(note: String) {
        sharedPreferences?.edit()?.putString(KEY_TEACHER_NOTE, note.trim())?.apply()
    }

    fun parseColor(hex: String): Color {
        return try {
            val cleanHex = hex.removePrefix("#").trim()
            val colorInt = when (cleanHex.length) {
                6 -> (0xFF000000 or cleanHex.toLong(16)).toInt()
                8 -> cleanHex.toLong(16).toInt()
                else -> 0xFF1B4D3E.toInt()
            }
            Color(colorInt)
        } catch (e: Exception) {
            Color(0xFF1B4D3E)
        }
    }

    private fun sanitizeHex(raw: String): String {
        var clean = raw.trim()
        if (!clean.startsWith("#")) {
            clean = "#$clean"
        }
        return if (clean.matches(Regex("^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{8})$"))) {
            clean
        } else {
            DEFAULT_ACCENT_HEX
        }
    }
}

val LocalThemeMode = compositionLocalOf { AppThemeMode.SYSTEM }
val LocalAccentHex = compositionLocalOf { ThemeManager.DEFAULT_ACCENT_HEX }
