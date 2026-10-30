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

/** Times are [SystemClock.elapsedRealtime] values, so wall-clock changes can't skip the lock. */
fun shouldLock(stoppedAt: Long, now: Long, timeout: AutoLockTimeout): Boolean =
    now - stoppedAt >= timeout.millis

/** Locks the vault when the app returns after the auto-lock timeout, and when the screen turns off. */
class AutoLockObserver(
    private val session: VaultSession,
    private val settings: SettingsStore
) : DefaultLifecycleObserver {

    private var stoppedAt: Long? = null

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

    override fun onStop(owner: LifecycleOwner) {
        stoppedAt = SystemClock.elapsedRealtime()
    }

    override fun onStart(owner: LifecycleOwner) {
        val stopped = stoppedAt ?: return
        stoppedAt = null
        if (shouldLock(stopped, SystemClock.elapsedRealtime(), settings.autoLockTimeout)) session.lock()
    }
}
