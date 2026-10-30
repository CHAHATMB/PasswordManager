package com.example.passwordmanager.presentation.LockScreen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.passwordmanager.PasswordManagerApp
import com.example.passwordmanager.data.vault.VaultException
import com.example.passwordmanager.data.vault.VaultSession
import com.example.passwordmanager.data.vault.VaultState
import com.example.passwordmanager.presentation.MasterPasswordError
import com.example.passwordmanager.presentation.validateMasterPassword
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class LockError { PasswordTooShort, PasswordMismatch, WrongPassword, CorruptVault }

data class LockState(val isWorking: Boolean = false, val error: LockError? = null)

class LockViewModel(private val session: VaultSession) : ViewModel() {

    val vaultState: StateFlow<VaultState> = session.state

    private val _state = MutableStateFlow(LockState())
    val state: StateFlow<LockState> = _state.asStateFlow()

    fun setup(password: String, confirmation: String) {
        when (validateMasterPassword(password, confirmation)) {
            MasterPasswordError.TooShort -> _state.value = LockState(error = LockError.PasswordTooShort)
            MasterPasswordError.Mismatch -> _state.value = LockState(error = LockError.PasswordMismatch)
            null -> submit(password, session::setup)
        }
    }

    fun unlock(password: String) = submit(password, session::unlock)

    private fun submit(password: String, action: suspend (CharArray) -> Unit) {
        if (_state.value.isWorking) return
        _state.value = LockState(isWorking = true)
        val passwordChars = password.toCharArray()
        viewModelScope.launch {
            try {
                action(passwordChars)
                _state.value = LockState()
            } catch (e: VaultException.WrongPassword) {
                _state.value = LockState(error = LockError.WrongPassword)
            } catch (e: VaultException.CorruptHeader) {
                _state.value = LockState(error = LockError.CorruptVault)
            } finally {
                passwordChars.fill('\u0000')
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                LockViewModel((this[APPLICATION_KEY] as PasswordManagerApp).container.vaultSession)
            }
        }
    }
}
