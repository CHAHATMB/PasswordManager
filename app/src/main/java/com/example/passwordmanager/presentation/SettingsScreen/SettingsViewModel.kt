package com.example.passwordmanager.presentation.SettingsScreen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.passwordmanager.PasswordManagerApp
import com.example.passwordmanager.data.crypto.BiometricKeyStore
import com.example.passwordmanager.data.settings.AutoLockTimeout
import com.example.passwordmanager.data.settings.SettingsStore
import com.example.passwordmanager.data.vault.VaultSession
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.ProviderException
import javax.crypto.Cipher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsState(
    val autoLockTimeout: AutoLockTimeout = AutoLockTimeout.DEFAULT,
    val isBiometricEnabled: Boolean = false,
    val isBiometricError: Boolean = false
)

class SettingsViewModel(
    private val session: VaultSession,
    private val settingsStore: SettingsStore,
    private val biometricKeyStore: BiometricKeyStore
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    /** Re-reads the stores; the Unlock screen may have turned biometrics off in the meantime. */
    fun refresh() {
        _state.value = SettingsState(
            autoLockTimeout = settingsStore.autoLockTimeout,
            isBiometricEnabled = session.biometricIv() != null
        )
    }

    fun setAutoLockTimeout(timeout: AutoLockTimeout) {
        settingsStore.autoLockTimeout = timeout
        _state.update { it.copy(autoLockTimeout = timeout) }
    }

    /** The ENCRYPT cipher for the enable prompt, or null (with an error shown) if no key can be made. */
    fun newBiometricCipher(): Cipher? = try {
        biometricKeyStore.newEncryptCipher()
    } catch (e: GeneralSecurityException) {
        showBiometricError()
        null
    } catch (e: ProviderException) {
        showBiometricError()
        null
    }

    fun enableBiometric(cipher: Cipher) {
        viewModelScope.launch {
            try {
                session.enableBiometric(cipher)
                refresh()
            } catch (e: GeneralSecurityException) {
                showBiometricError()
            } catch (e: IOException) {
                showBiometricError()
            }
        }
    }

    fun disableBiometric() {
        viewModelScope.launch {
            session.disableBiometric()
            refresh()
        }
    }

    private fun showBiometricError() = _state.update { it.copy(isBiometricError = true) }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as PasswordManagerApp).container
                SettingsViewModel(container.vaultSession, container.settingsStore, container.biometricKeyStore)
            }
        }
    }
}
