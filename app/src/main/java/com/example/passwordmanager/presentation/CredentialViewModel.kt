package com.example.passwordmanager.presentation

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.passwordmanager.PasswordManagerApp
import com.example.passwordmanager.data.Credential
import com.example.passwordmanager.data.CredentialRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class CredentialViewModel(private val repository: CredentialRepository) : ViewModel() {

    private var isSortedByDateCreate = MutableStateFlow(true)

    private var credentials  = isSortedByDateCreate.flatMapLatest { value ->
        if(value){
            repository.getOrderedByDateCreated()
        } else {
            repository.getOrderedByWebsite()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    var _state = MutableStateFlow(CredentialState())
    var state = combine(_state, isSortedByDateCreate, credentials){ state, isSortedByDateCreate, credentials ->
        state.copy(credentials = credentials)

    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CredentialState())

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
                viewModelScope.launch{
                    repository.deleteCredential(event.credential)
                }
            }
            is CredentialEvent.SaveCredential -> {

//                val ik = cryptoManager.decryptString10("ChahatTEXTNe")
//                val dc = cryptoManager.encryptString10(ik)
//                print("your inrypted mssg $ik,, $dc")


//                val iv = "Chahat"
//                val hashPassword = "asd"
//                val (ivByte, passwordByte) = cryptoManager.encryptString5(state.value.password.value)
//                println("both strings are $iv, $ivByte")
                val credential = Credential(
//                    website = event.website, username = event.username, password = event.password, note = event.note, dataCreate = System.currentTimeMillis()
                    website = state.value.website.value, username = state.value.username.value, password = state.value.password.value,
                    note = state.value.note.value, dataCreate = System.currentTimeMillis()
                )
                viewModelScope.launch {
                    repository.upsertCredential(credential)
                }
                // seems not need but see
                _state.update {
                    it.copy( website = mutableStateOf(""), username = mutableStateOf(""), password = mutableStateOf(""), note = mutableStateOf("") )
                }
            }
            is CredentialEvent.SortCredentail -> {
                isSortedByDateCreate.value = !isSortedByDateCreate.value
            }
            is CredentialEvent.FavoriteCredential ->{
                event.credential.isFav = !event.credential.isFav
                viewModelScope.launch {
                    repository.upsertCredential(event.credential)
                    // Manually refresh credentials flow
                    val updatedCredentials = repository.getOrderedByDateCreated().first() // Replace with your preferred sorting
                    _state.value = _state.value.copy(credentials = updatedCredentials)
                }
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

    companion object {
        val Factory = viewModelFactory {
            initializer {
                CredentialViewModel((this[APPLICATION_KEY] as PasswordManagerApp).container.credentialRepository)
            }
        }
    }
}