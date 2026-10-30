package com.example.passwordmanager.data.crypto

import com.example.passwordmanager.data.vault.VaultException
import com.example.passwordmanager.data.vault.VaultHeader
import java.nio.CharBuffer
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

const val SALT_SIZE_BYTES = 16
const val IV_SIZE_BYTES = 12
private const val TAG_SIZE_BITS = 128
private const val TRANSFORMATION = "AES/GCM/NoPadding"

class CreatedVault(val header: VaultHeader, val vaultKey: ByteArray)

class VaultCrypto(
    private val keyDerivation: KeyDerivation,
    private val random: SecureRandom = SecureRandom()
) {

    fun createVault(password: CharArray, params: KdfParams): CreatedVault {
        val salt = randomBytes(SALT_SIZE_BYTES)
        val iv = randomBytes(IV_SIZE_BYTES)
        val vaultKey = randomBytes(KEY_SIZE_BYTES)
        val version = VaultHeader.CURRENT_VERSION
        val passwordKey = derivePasswordKey(password, salt, params)
        val wrappedKey = try {
            cipher(Cipher.ENCRYPT_MODE, passwordKey, iv, version).doFinal(vaultKey)
        } finally {
            passwordKey.fill(0)
        }
        return CreatedVault(VaultHeader(version, params, salt, iv, wrappedKey), vaultKey)
    }

    /** Returns the vault key. The caller owns it and must zero it after use. */
    fun unlock(password: CharArray, header: VaultHeader): ByteArray {
        val passwordKey = derivePasswordKey(password, header.salt, header.kdfParams)
        try {
            return cipher(Cipher.DECRYPT_MODE, passwordKey, header.iv, header.version)
                .doFinal(header.wrappedKey)
        } catch (e: AEADBadTagException) {
            throw VaultException.WrongPassword(e)
        } finally {
            passwordKey.fill(0)
        }
    }

    private fun derivePasswordKey(password: CharArray, salt: ByteArray, params: KdfParams): ByteArray {
        val passwordBytes = password.encodeUtf8()
        try {
            return keyDerivation.deriveKey(passwordBytes, salt, params)
        } finally {
            passwordBytes.fill(0)
        }
    }

    private fun cipher(mode: Int, key: ByteArray, iv: ByteArray, version: Int): Cipher =
        Cipher.getInstance(TRANSFORMATION).apply {
            init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_SIZE_BITS, iv))
            updateAAD("passwordmanager-vault-v$version".toByteArray())
        }

    private fun randomBytes(size: Int): ByteArray = ByteArray(size).also(random::nextBytes)

    private fun CharArray.encodeUtf8(): ByteArray {
        val buffer = Charsets.UTF_8.encode(CharBuffer.wrap(this))
        val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
        buffer.array().fill(0)
        return bytes
    }
}
