package com.example.passwordmanager.presentation

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.example.passwordmanager.data.Credential

class CredentialState (
    var credentials: List<Credential> = emptyList(),
    var website: MutableState<String> = mutableStateOf(""),
    var username: MutableState<String> = mutableStateOf(""),
    var isFav: MutableState<Boolean> = mutableStateOf(false),
    var password: MutableState<String> = mutableStateOf(""),
    var note: MutableState<String> = mutableStateOf(""),
    var iv: MutableState<String> = mutableStateOf("")
) {
    // following code is unnecessary since every class in kotlin has inbuilt copy method
    fun copy( credentials: List<Credential>): CredentialState {
        return CredentialState(credentials = credentials!!)
    }

    fun copy(website: MutableState<String>?, username: MutableState<String>?, password: MutableState<String>?, note: MutableState<String>?): CredentialState {
        return CredentialState(website=website!!, username = username!!, password = password!!, note= note!! )
    }
}