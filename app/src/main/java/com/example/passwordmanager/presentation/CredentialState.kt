package com.example.passwordmanager.presentation

import com.example.passwordmanager.data.Credential

data class CredentialState(
    val credentials: List<Credential> = emptyList(),
    /** False until the first database emission, so an empty list isn't mistaken for an empty vault. */
    val isLoaded: Boolean = false
)

/** Input of the add/edit form. */
data class CredentialDraft(
    val website: String = "",
    val username: String = "",
    val password: String = "",
    val note: String = "",
    val isGeneratorOpen: Boolean = false
) {
    constructor(credential: Credential?) : this(
        website = credential?.website.orEmpty(),
        username = credential?.username.orEmpty(),
        password = credential?.password.orEmpty(),
        note = credential?.note.orEmpty()
    )
}
