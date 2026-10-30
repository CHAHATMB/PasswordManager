package com.example.passwordmanager.data.settings

import android.content.SharedPreferences

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

class SettingsStore(private val prefs: SharedPreferences) {

    var autoLockTimeout: AutoLockTimeout
        get() {
            val name = prefs.getString(KEY_AUTO_LOCK_TIMEOUT, null)
            return AutoLockTimeout.entries.firstOrNull { it.name == name } ?: AutoLockTimeout.DEFAULT
        }
        set(value) {
            prefs.edit().putString(KEY_AUTO_LOCK_TIMEOUT, value.name).apply()
        }

    companion object {
        const val PREFS_NAME = "settings"
        private const val KEY_AUTO_LOCK_TIMEOUT = "auto_lock_timeout"
    }
}
