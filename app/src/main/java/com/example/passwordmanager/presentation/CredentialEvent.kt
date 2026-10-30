package com.example.passwordmanager.presentation

import com.example.passwordmanager.data.Credential

sealed interface CredentialEvent {
    object SortCredential: CredentialEvent

    data class DeleteCredential( var credential: Credential): CredentialEvent
    /** Re-inserts a deleted credential with its original id. */
    data class RestoreCredential(val credential: Credential): CredentialEvent
    data class SaveCredential(
        var website: String,
        var username: String,
        var password: String,
        var note: String?
    ): CredentialEvent

    /** [credential] carries the edited fields with the original id and dataCreate. */
    data class UpdateCredential(val credential: Credential): CredentialEvent

    data class FavoriteCredential(var credential: Credential): CredentialEvent

    data class CopyUsername(val credential: Credential): CredentialEvent
    data class CopyPassword(val credential: Credential): CredentialEvent

    data class OnSearchTextChange(val text: String): CredentialEvent

    object OpenAddSheet: CredentialEvent
    data class ChangeAddDraft(val draft: CredentialDraft): CredentialEvent
    object CloseAddSheet: CredentialEvent
}