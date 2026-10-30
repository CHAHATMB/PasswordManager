package com.example.passwordmanager.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.passwordmanager.PasswordManagerApp
import com.example.passwordmanager.data.Credential
import com.example.passwordmanager.data.CredentialRepository
import com.example.passwordmanager.data.vault.VaultException
import com.example.passwordmanager.data.vault.VaultState
import com.example.passwordmanager.util.SecureClipboard
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class CredentialViewModel(
    private val repository: CredentialRepository,
    private val clipboard: SecureClipboard,
    vaultState: StateFlow<VaultState>
) : ViewModel() {

    private val isSortedByWebsite = MutableStateFlow(false)

    private val _searchText = MutableStateFlow("")
    val searchText = _searchText.asStateFlow()

    val state = combine(_searchText, isSortedByWebsite) { query, sortByWebsite -> query to sortByWebsite }
        .flatMapLatest { (query, sortByWebsite) -> repository.search(query, sortByWebsite) }
        .map { CredentialState(credentials = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CredentialState())

    val favorites = isSortedByWebsite
        .flatMapLatest { repository.getFavorites(it) }
        .map { CredentialState(credentials = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CredentialState())

    private val _deletedCredentials = Channel<Credential>(Channel.BUFFERED)
    /** Each credential right after it is deleted, so the UI can offer Undo. */
    val deletedCredentials = _deletedCredentials.receiveAsFlow()

    init {
        // A delete still buffered at lock time must not offer Undo after the next unlock.
        viewModelScope.launch {
            vaultState.collect { state ->
                if (state !is VaultState.Unlocked) {
                    while (_deletedCredentials.tryReceive().isSuccess) Unit
                }
            }
        }
    }

    fun credential(id: Int): Flow<Credential?> = repository.getById(id)

    fun onEvent( event: CredentialEvent ){
        when(event){

            is CredentialEvent.DeleteCredential -> {
                write {
                    repository.deleteCredential(event.credential)
                    _deletedCredentials.send(event.credential)
                }
            }
            is CredentialEvent.RestoreCredential -> {
                write { repository.upsertCredential(event.credential) }
            }
            is CredentialEvent.SaveCredential -> {
                if (!isCredentialFormValid(event.website, event.password)) return
                val credential = Credential(
                    website = event.website, username = event.username, password = event.password,
                    note = event.note.orEmpty(), dataCreate = System.currentTimeMillis()
                )
                write { repository.upsertCredential(credential) }
            }
            is CredentialEvent.UpdateCredential -> {
                val credential = event.credential
                if (!isCredentialFormValid(credential.website, credential.password)) return
                write { repository.upsertCredential(credential) }
            }
            is CredentialEvent.SortCredential -> {
                isSortedByWebsite.value = !isSortedByWebsite.value
            }
            is CredentialEvent.FavoriteCredential ->{
                write { repository.upsertCredential(event.credential.copy(isFav = !event.credential.isFav)) }
            }

            is CredentialEvent.CopyUsername -> clipboard.copy(event.credential.username)
            is CredentialEvent.CopyPassword -> clipboard.copy(event.credential.password)

            is CredentialEvent.OnSearchTextChange -> {
                _searchText.value = event.text
            }
        }
    }

    private fun write(action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                action()
            } catch (e: VaultException.Locked) {
                // The vault locked before or during the write; the write is dropped.
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as PasswordManagerApp).container
                CredentialViewModel(container.credentialRepository, container.secureClipboard, container.vaultSession.state)
            }
        }
    }
}