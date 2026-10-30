package com.example.passwordmanager.data.vault

sealed class VaultException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class WrongPassword(cause: Throwable) : VaultException("Wrong master password", cause)
    class CorruptHeader(message: String, cause: Throwable? = null) : VaultException(message, cause)
    class Locked(cause: Throwable? = null) : VaultException("Vault is locked", cause)
}
