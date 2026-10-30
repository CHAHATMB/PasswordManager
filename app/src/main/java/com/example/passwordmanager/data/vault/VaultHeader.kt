package com.example.passwordmanager.data.vault

import com.example.passwordmanager.data.crypto.KdfParams

class VaultHeader(
    val version: Int,
    val kdfParams: KdfParams,
    val salt: ByteArray,
    val iv: ByteArray,
    val wrappedKey: ByteArray
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}
