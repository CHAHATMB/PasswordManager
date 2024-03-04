package com.example.passwordmanager.presentation

import com.example.passwordmanager.data.Credential

sealed interface CredentialEvent {
    object SortCredentail: CredentialEvent

    data class DeleteCredential( var credential: Credential): CredentialEvent
    data class SaveCredential(
        var website: String,
        var username: String,
        var password: String,
        var note: String?
    ): CredentialEvent

    data class FavoriteCredential(var credential: Credential): CredentialEvent

    data class OnSearchTextChange(var text: String): CredentialEvent
    data object OnToogleSearch : CredentialEvent
}