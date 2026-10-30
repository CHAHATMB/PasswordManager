package com.example.passwordmanager.presentation

const val MIN_MASTER_PASSWORD_LENGTH = 12

enum class MasterPasswordError { TooShort, Mismatch }

fun validateMasterPassword(password: String, confirmation: String): MasterPasswordError? = when {
    password.length < MIN_MASTER_PASSWORD_LENGTH -> MasterPasswordError.TooShort
    password != confirmation -> MasterPasswordError.Mismatch
    else -> null
}

fun isCredentialFormValid(website: String, password: String): Boolean =
    website.isNotBlank() && password.isNotBlank()
