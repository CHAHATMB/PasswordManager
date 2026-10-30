package com.example.passwordmanager.presentation

import com.example.passwordmanager.data.Credential

data class CredentialState(
    val credentials: List<Credential> = emptyList()
)
