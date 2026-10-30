package com.example.passwordmanager.data.settings

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AutoLockTimeout(val millis: Long) {
    IMMEDIATELY(0),
    SECONDS_30(30_000),
    MINUTE_1(60_000),
    MINUTES_5(300_000),
    MINUTES_15(900_000);

    companion object {
        val DEFAULT = MINUTE_1
    }
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK;

    companion object {
        val DEFAULT = SYSTEM
    }
}

enum class NotesLayout {
    GRID,
    LIST;

    companion object {
        val DEFAULT = GRID
    }
}

class SettingsStore(private val prefs: SharedPreferences) {

    var autoLockTimeout: AutoLockTimeout
        get() {
            val name = prefs.getString(KEY_AUTO_LOCK_TIMEOUT, null)
            return AutoLockTimeout.entries.firstOrNull { it.name == name } ?: AutoLockTimeout.DEFAULT
        }
        set(value) {
            prefs.edit().putString(KEY_AUTO_LOCK_TIMEOUT, value.name).apply()
        }

    private val _themeMode = MutableStateFlow(
        prefs.getString(KEY_THEME_MODE, null).let { name ->
            ThemeMode.entries.firstOrNull { it.name == name } ?: ThemeMode.DEFAULT
        }
    )
    /** Observable so a change restyles the app at once, also on the Setup and Unlock screens. */
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _themeMode.value = mode
    }

    private val _notesLayout = MutableStateFlow(
        prefs.getString(KEY_NOTES_LAYOUT, null).let { name ->
            NotesLayout.entries.firstOrNull { it.name == name } ?: NotesLayout.DEFAULT
        }
    )
    val notesLayout: StateFlow<NotesLayout> = _notesLayout.asStateFlow()

    fun setNotesLayout(layout: NotesLayout) {
        prefs.edit().putString(KEY_NOTES_LAYOUT, layout.name).apply()
        _notesLayout.value = layout
    }

    var isOnboardingDone: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_DONE, false)
        set(value) {
            prefs.edit().putBoolean(KEY_ONBOARDING_DONE, value).apply()
        }

    companion object {
        const val PREFS_NAME = "settings"
        private const val KEY_AUTO_LOCK_TIMEOUT = "auto_lock_timeout"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_NOTES_LAYOUT = "notes_layout"
        private const val KEY_ONBOARDING_DONE = "onboarding_done"
    }
}
