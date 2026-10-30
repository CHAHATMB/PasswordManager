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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class CredentialViewModel(private val repository: CredentialRepository) : ViewModel() {

    private var isSortedByDateCreate = MutableStateFlow(true)

    val state = isSortedByDateCreate.flatMapLatest { value ->
        if(value){
            repository.getOrderedByDateCreated()
        } else {
            repository.getOrderedByWebsite()
        }
    }.map { CredentialState(credentials = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CredentialState())

    // region ::searching states
    //first state whether the search is happening or not
    private val _isSearching = MutableStateFlow(false)
    val isSearching = _isSearching.asStateFlow()

    //second state the text typed by the user
    private val _searchText = MutableStateFlow("")
    val searchText = _searchText.asStateFlow()

    //third state the list to be filtered
    private val _countriesList = MutableStateFlow("")
    val countriesList = searchText
        .combine(_countriesList) { text, countries ->//combine searchText with _contriesList
            if (text.isBlank()) { //return the entery list of countries if not is typed
                countries
            }
            countries.filter { country ->// filter and return a list of countries based on the text the user typed
                country.uppercase().contains(text.trim().uppercase())
            }
        }.stateIn(//basically convert the Flow returned from combine operator to StateFlow
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),//it will allow the StateFlow survive 5 seconds before it been canceled
            initialValue = _countriesList.value
        )
    // endregion ::searching state

    fun onEvent( event: CredentialEvent ){
        when(event){

            is CredentialEvent.DeleteCredential -> {
                write { repository.deleteCredential(event.credential) }
            }
            is CredentialEvent.SaveCredential -> {
                if (!isCredentialFormValid(event.website, event.password)) return
                val credential = Credential(
                    website = event.website, username = event.username, password = event.password,
                    note = event.note.orEmpty(), dataCreate = System.currentTimeMillis()
                )
                write { repository.upsertCredential(credential) }
            }
            is CredentialEvent.SortCredentail -> {
                isSortedByDateCreate.value = !isSortedByDateCreate.value
            }
            is CredentialEvent.FavoriteCredential ->{
                write { repository.upsertCredential(event.credential.copy(isFav = !event.credential.isFav)) }
            }

            is CredentialEvent.OnSearchTextChange -> {
                _searchText.value = event.text
            }
            is CredentialEvent.OnToogleSearch ->{
                _isSearching.value = !_isSearching.value
                if(!_isSearching.value){
                    _searchText.value = ""
                }
            }

            else -> {}
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
                CredentialViewModel((this[APPLICATION_KEY] as PasswordManagerApp).container.credentialRepository)
            }
        }
    }
}