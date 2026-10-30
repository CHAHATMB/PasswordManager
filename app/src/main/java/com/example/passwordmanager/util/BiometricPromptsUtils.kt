package com.example.passwordmanager.util

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import javax.crypto.Cipher

object BiometricPromptUtils {

    fun canAuthenticate(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    /**
     * Errors (cancel, negative button, lockout) need no handling: the password field stays on
     * screen behind the prompt. A non-matching finger keeps the prompt open.
     */
    fun createBiometricPrompt(
        activity: FragmentActivity,
        onAuthenticated: (Cipher) -> Unit
    ): BiometricPrompt {
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                result.cryptoObject?.cipher?.let(onAuthenticated)
            }
        }
        return BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
    }

    fun unlockPromptInfo(): BiometricPrompt.PromptInfo =
        promptInfo(title = "Unlock vault", negativeButton = "Use master password")

    fun enablePromptInfo(): BiometricPrompt.PromptInfo =
        promptInfo(title = "Turn on fingerprint unlock", negativeButton = "Cancel")

    private fun promptInfo(title: String, negativeButton: String): BiometricPrompt.PromptInfo =
        BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle("Confirm with your fingerprint")
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .setConfirmationRequired(false)
            .setNegativeButtonText(negativeButton)
            .build()
}
