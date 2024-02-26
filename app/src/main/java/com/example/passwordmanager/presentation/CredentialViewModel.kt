package com.example.passwordmanager.presentation

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.passwordmanager.data.Credential
import com.example.passwordmanager.data.CredentialDao
import com.example.passwordmanager.util.CryptoManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CredentialViewModel ( private var dao:CredentialDao): ViewModel() {

    private val cryptoManager = CryptoManager()
    private var isSortedByDateCreate = MutableStateFlow(true)

    private var credentials  = isSortedByDateCreate.flatMapLatest { value ->
        if(value){
            dao.getOrderedByDateCreated()
        } else {
            dao.getOrderedByWebsite()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    var _state = MutableStateFlow(CredentialState())
    var state = combine(_state, isSortedByDateCreate, credentials){ state, isSortedByDateCreate, credentials ->
        state.copy(credentials = credentials)

    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CredentialState())

    fun onEvent( event: CredentialEvent ){
        when(event){

            is CredentialEvent.DeleteCredential -> {
                viewModelScope.launch{
                    dao.deleteCredential(event.credential)
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
                    dao.upsertCredential(credential)
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
                    dao.upsertCredential(event.credential)
                    // Manually refresh credentials flow
                    val updatedCredentials = dao.getOrderedByDateCreated().first() // Replace with your preferred sorting
                    _state.value = _state.value.copy(credentials = updatedCredentials)
                }
            }
            else -> {}
        }
    }
}