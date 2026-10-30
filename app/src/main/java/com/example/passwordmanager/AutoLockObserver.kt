package com.example.passwordmanager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.passwordmanager.data.settings.AutoLockTimeout
import com.example.passwordmanager.data.settings.SettingsStore
import com.example.passwordmanager.data.vault.VaultSession

/** The longest an app-started trip (a file picker) may last before the vault locks anyway. */
const val APP_TRIP_MAX_MILLIS = 5 * 60_000L

/**
 * Times are [SystemClock.elapsedRealtime] values, so wall-clock changes can't skip the lock.
 * An [isAppTrip] gets at least [APP_TRIP_MAX_MILLIS], so short timeouts don't break the picker.
 */
fun shouldLock(stoppedAt: Long, now: Long, timeout: AutoLockTimeout, isAppTrip: Boolean = false): Boolean {
    val limit = if (isAppTrip) maxOf(timeout.millis, APP_TRIP_MAX_MILLIS) else timeout.millis
    return now - stoppedAt >= limit
}

/** Locks the vault when the app returns after the auto-lock timeout, and when the screen turns off. */
class AutoLockObserver(
    private val session: VaultSession,
    private val settings: SettingsStore
) : DefaultLifecycleObserver {

    private var stoppedAt: Long? = null
    private var isAppTripExpected = false
    private var isAppTrip = false

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = session.lock()
    }

    fun start(context: Context) {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        ContextCompat.registerReceiver(
            context,
            screenOffReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    /** Call right before the app opens another app's screen (a file picker); covers the next trip only. */
    fun expectAppTrip() {
        isAppTripExpected = true
    }

    /** Call when that screen returns a result, in case it closed before the app was ever stopped. */
    fun endAppTrip() {
        isAppTripExpected = false
    }

    override fun onStop(owner: LifecycleOwner) {
        stoppedAt = SystemClock.elapsedRealtime()
        isAppTrip = isAppTripExpected
        isAppTripExpected = false
    }

    override fun onStart(owner: LifecycleOwner) {
        val stopped = stoppedAt ?: return
        stoppedAt = null
        if (shouldLock(stopped, SystemClock.elapsedRealtime(), settings.autoLockTimeout, isAppTrip)) session.lock()
        isAppTrip = false
    }
}
