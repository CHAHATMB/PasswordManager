package com.example.passwordmanager.data.vault

import com.example.passwordmanager.data.crypto.KdfParams

/** The vault key wrapped by the biometric Keystore key; [iv] is the one the Keystore cipher chose. */
class BiometricWrappedKey(val iv: ByteArray, val wrappedKey: ByteArray)

class VaultHeader(
    val version: Int,
    val kdfParams: KdfParams,
    val salt: ByteArray,
    val iv: ByteArray,
    val wrappedKey: ByteArray,
    val biometric: BiometricWrappedKey? = null
) {
    fun withBiometric(biometric: BiometricWrappedKey?) =
        VaultHeader(version, kdfParams, salt, iv, wrappedKey, biometric)

    companion object {
        const val CURRENT_VERSION = 1
    }
}
