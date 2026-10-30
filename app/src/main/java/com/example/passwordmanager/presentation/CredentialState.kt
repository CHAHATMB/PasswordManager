package com.example.passwordmanager.presentation

import com.example.passwordmanager.data.Credential

data class CredentialState(
    val credentials: List<Credential> = emptyList(),
    /** False until the first database emission, so an empty list isn't mistaken for an empty vault. */
    val isLoaded: Boolean = false
)
