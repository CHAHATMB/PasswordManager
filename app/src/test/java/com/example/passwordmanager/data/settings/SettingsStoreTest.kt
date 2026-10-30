package com.example.passwordmanager.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsStoreTest {

    private lateinit var prefs: SharedPreferences

    @Before
    fun setUp() {
        prefs = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences(SettingsStore.PREFS_NAME, Context.MODE_PRIVATE)
    }

    @Test
    fun defaultTimeoutIsOneMinute() {
        assertEquals(AutoLockTimeout.MINUTE_1, SettingsStore(prefs).autoLockTimeout)
    }

    @Test
    fun everyTimeoutSurvivesRoundTrip() {
        AutoLockTimeout.entries.forEach { timeout ->
            SettingsStore(prefs).autoLockTimeout = timeout

            assertEquals("read back by a new store", timeout, SettingsStore(prefs).autoLockTimeout)
        }
    }

    @Test
    fun unknownStoredValueFallsBackToDefault() {
        prefs.edit().putString("auto_lock_timeout", "FOREVER").commit()

        assertEquals(AutoLockTimeout.DEFAULT, SettingsStore(prefs).autoLockTimeout)
    }

    @Test
    fun defaultThemeModeIsSystem() {
        assertEquals(ThemeMode.SYSTEM, SettingsStore(prefs).themeMode.value)
    }

    @Test
    fun everyThemeModeSurvivesRoundTrip() {
        ThemeMode.entries.forEach { mode ->
            val store = SettingsStore(prefs)
            store.setThemeMode(mode)

            assertEquals("observed by the same store", mode, store.themeMode.value)
            assertEquals("read back by a new store", mode, SettingsStore(prefs).themeMode.value)
        }
    }

    @Test
    fun unknownStoredThemeModeFallsBackToSystem() {
        prefs.edit().putString("theme_mode", "SEPIA").commit()

        assertEquals(ThemeMode.SYSTEM, SettingsStore(prefs).themeMode.value)
    }
}
