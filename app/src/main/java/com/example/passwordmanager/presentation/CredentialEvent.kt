package com.example.passwordmanager.presentation

import com.example.passwordmanager.data.Credential

sealed interface CredentialEvent {
    object SortCredential: CredentialEvent

    data class DeleteCredential( var credential: Credential): CredentialEvent
    data class SaveCredential(
        var website: String,
        var username: String,
        var password: String,
        var note: String?
    ): CredentialEvent

    data class FavoriteCredential(var credential: Credential): CredentialEvent

    data class OnSearchTextChange(val text: String): CredentialEvent
}