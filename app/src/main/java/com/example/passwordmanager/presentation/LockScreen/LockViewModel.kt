package com.example.passwordmanager.presentation.LockScreen

import android.security.keystore.KeyPermanentlyInvalidatedException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.passwordmanager.PasswordManagerApp
import com.example.passwordmanager.data.crypto.BiometricKeyStore
import com.example.passwordmanager.data.vault.UnlockThrottle
import com.example.passwordmanager.data.vault.VaultException
import com.example.passwordmanager.data.vault.VaultSession
import com.example.passwordmanager.data.vault.VaultState
import com.example.passwordmanager.presentation.MasterPasswordError
import com.example.passwordmanager.presentation.validateMasterPassword
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.ProviderException
import javax.crypto.Cipher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LockError { PasswordTooShort, PasswordMismatch, WrongPassword, CorruptVault, BiometricInvalidated, BiometricFailed, Unexpected }

data class LockState(
    val isWorking: Boolean = false,
    val error: LockError? = null,
    val retryInSeconds: Int = 0
)

class LockViewModel(
    private val session: VaultSession,
    private val throttle: UnlockThrottle,
    private val biometricKeyStore: BiometricKeyStore
) : ViewModel() {

    val vaultState: StateFlow<VaultState> = session.state

    private val _state = MutableStateFlow(LockState())
    val state: StateFlow<LockState> = _state.asStateFlow()

    private var countdown: Job? = null
    private var isBiometricPromptDue = false

    init {
        startCountdown()
        viewModelScope.launch {
            session.state.collect { if (it == VaultState.Locked) isBiometricPromptDue = true }
        }
    }

    fun setup(password: String, confirmation: String) {
        when (validateMasterPassword(password, confirmation)) {
            MasterPasswordError.TooShort -> _state.value = LockState(error = LockError.PasswordTooShort)
            MasterPasswordError.Mismatch -> _state.value = LockState(error = LockError.PasswordMismatch)
            null -> submitPassword(password, session::setup)
        }
    }

    fun unlock(password: String) {
        if (throttle.remainingMillis() > 0) return
        submitPassword(password, session::unlock)
    }

    fun lock() = session.lock()

    /**
     * Returns the DECRYPT cipher for the automatic prompt, once per lock, or null when biometric
     * unlock is off. Any Keystore failure turns biometric unlock off, so a broken key can't
     * crash every launch; the master password still works.
     */
    fun takeBiometricUnlockCipher(): Cipher? {
        if (!isBiometricPromptDue) return null
        isBiometricPromptDue = false
        val iv = try {
            session.biometricIv()
        } catch (e: VaultException.CorruptHeader) {
            null
        } ?: return null
        return try {
            biometricKeyStore.decryptCipher(iv)
        } catch (e: KeyPermanentlyInvalidatedException) {
            turnOffBiometric(LockError.BiometricInvalidated)
        } catch (e: GeneralSecurityException) {
            turnOffBiometric(LockError.BiometricFailed)
        } catch (e: ProviderException) {
            turnOffBiometric(LockError.BiometricFailed)
        }
    }

    private fun turnOffBiometric(error: LockError): Cipher? {
        _state.update { it.copy(error = error) }
        viewModelScope.launch {
            try {
                session.disableBiometric()
            } catch (e: IOException) {
                // The header write failed; the blob stays, so the next biometric attempt retries the disable.
            }
        }
        return null
    }

    fun unlockWithBiometric(cipher: Cipher) = submit { session.unlockWithBiometric(cipher) }

    private fun submitPassword(password: String, action: suspend (CharArray) -> Unit) {
        if (_state.value.isWorking) return
        val passwordChars = password.toCharArray()
        submit {
            try {
                action(passwordChars)
            } finally {
                passwordChars.fill('\u0000')
            }
        }
    }

    private fun submit(action: suspend () -> Unit) {
        if (_state.value.isWorking) return
        _state.value = LockState(isWorking = true)
        viewModelScope.launch {
            try {
                action()
                throttle.onSuccess()
                _state.value = LockState()
            } catch (e: VaultException.WrongPassword) {
                throttle.onFailure()
                _state.value = LockState(error = LockError.WrongPassword, retryInSeconds = secondsUntilRetry())
                startCountdown()
            } catch (e: VaultException.CorruptHeader) {
                _state.value = LockState(error = LockError.CorruptVault)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // SQLCipher, I/O, Keystore or native KDF failures: show an error instead of crashing.
                _state.value = LockState(error = LockError.Unexpected)
            }
        }
    }

    private fun startCountdown() {
        countdown?.cancel()
        countdown = viewModelScope.launch {
            while (true) {
                val remaining = throttle.remainingMillis()
                val seconds = secondsUntilRetry(remaining)
                _state.update { it.copy(retryInSeconds = seconds) }
                if (seconds == 0) break
                // Wake up when the displayed whole-second value changes.
                delay(remaining - (seconds - 1) * 1000L)
            }
        }
    }

    private fun secondsUntilRetry(remaining: Long = throttle.remainingMillis()): Int =
        ((remaining + 999) / 1000).toInt()

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as PasswordManagerApp).container
                LockViewModel(container.vaultSession, container.unlockThrottle, container.biometricKeyStore)
            }
        }
    }
}
