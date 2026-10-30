package com.example.passwordmanager.util

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

interface ClipboardAccess {
    /** Null when the clipboard is empty or unreadable (Android 10+ while the app is in the background). */
    fun primaryClipLabel(): CharSequence?
    fun setSensitiveClip(label: String, text: String)
    fun clear()
}

class SystemClipboard(context: Context) : ClipboardAccess {

    private val manager = context.getSystemService(ClipboardManager::class.java)

    override fun primaryClipLabel(): CharSequence? = manager.primaryClipDescription?.label

    override fun setSensitiveClip(label: String, text: String) {
        val clip = ClipData.newPlainText(label, text)
        clip.description.extras = PersistableBundle().apply {
            putBoolean(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ClipDescription.EXTRA_IS_SENSITIVE
                } else {
                    IS_SENSITIVE_KEY
                },
                true
            )
        }
        manager.setPrimaryClip(clip)
    }

    override fun clear() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            manager.clearPrimaryClip()
        } else {
            manager.setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }

    private companion object {
        // Honoured by keyboards and clipboard UIs before the API 33 constant existed.
        const val IS_SENSITIVE_KEY = "android.content.extra.IS_SENSITIVE"
    }
}

/**
 * Copies secrets and clears them [CLEAR_DELAY_MILLIS] after the latest copy. [scope] must outlive
 * the UI and run on the main thread, which serialises every call.
 */
class SecureClipboard(
    private val clipboard: ClipboardAccess,
    private val scope: CoroutineScope
) {
    private var clearJob: Job? = null

    fun copy(text: String) {
        clipboard.setSensitiveClip(CLIP_LABEL, text)
        clearJob?.cancel()
        clearJob = scope.launch {
            delay(CLEAR_DELAY_MILLIS)
            clearOwnClip()
        }
    }

    /** Clears a copy still waiting for its timer; does nothing if there is none. */
    fun clearNow() {
        val job = clearJob ?: return
        job.cancel()
        clearOwnClip()
    }

    private fun clearOwnClip() {
        clearJob = null
        if (isOwnClip(clipboard.primaryClipLabel())) clipboard.clear()
    }

    companion object {
        const val CLIP_LABEL = "Vaultly"
        const val CLEAR_DELAY_MILLIS = 30_000L

        // An unreadable clipboard counts as ours: in the background Android hides the clip, and
        // leaving a password behind is worse than clearing someone else's newer clip.
        internal fun isOwnClip(label: CharSequence?): Boolean = label == null || label.toString() == CLIP_LABEL
    }
}
