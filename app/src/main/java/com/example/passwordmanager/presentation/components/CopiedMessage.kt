package com.example.passwordmanager.presentation.components

import android.content.Context
import android.os.Build
import android.widget.Toast
import com.example.passwordmanager.util.SecureClipboard

/** Android 13+ shows its own confirmation for every copy, so this only runs below it. */
fun showCopiedMessage(context: Context, what: String) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
    val seconds = SecureClipboard.CLEAR_DELAY_MILLIS / 1000
    Toast.makeText(context, "$what copied, clears in $seconds s", Toast.LENGTH_SHORT).show()
}
