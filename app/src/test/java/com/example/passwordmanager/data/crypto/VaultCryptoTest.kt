package com.example.passwordmanager.data.crypto

import com.example.passwordmanager.data.vault.VaultException
import com.example.passwordmanager.data.vault.VaultHeader
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class VaultCryptoTest {

    private val crypto = VaultCrypto(Pbkdf2KeyDerivation())
    private val password = "correct horse battery staple"

    @Test
    fun unlockWithSetupPasswordReturnsSameVaultKey() {
        val created = crypto.createVault(password.toCharArray(), TEST_KDF_PARAMS)

        val unlockedKey = crypto.unlock(password.toCharArray(), created.header)

        assertEquals(KEY_SIZE_BYTES, created.vaultKey.size)
        assertArrayEquals(created.vaultKey, unlockedKey)
    }

    @Test
    fun unlockWithWrongPasswordThrowsWrongPassword() {
        val created = crypto.createVault(password.toCharArray(), TEST_KDF_PARAMS)

        assertThrows(VaultException.WrongPassword::class.java) {
            crypto.unlock("correct horse battery stapler".toCharArray(), created.header)
        }
    }

    @Test
    fun unlockWithTamperedIvFails() {
        val header = crypto.createVault(password.toCharArray(), TEST_KDF_PARAMS).header

        assertThrows(VaultException.WrongPassword::class.java) {
            crypto.unlock(password.toCharArray(), header.copyWith(iv = header.iv.flipFirstBit()))
        }
    }

    @Test
    fun unlockWithTamperedSaltFails() {
        val header = crypto.createVault(password.toCharArray(), TEST_KDF_PARAMS).header

        assertThrows(VaultException.WrongPassword::class.java) {
            crypto.unlock(password.toCharArray(), header.copyWith(salt = header.salt.flipFirstBit()))
        }
    }

    @Test
    fun unlockWithTamperedCiphertextFails() {
        val header = crypto.createVault(password.toCharArray(), TEST_KDF_PARAMS).header

        assertThrows(VaultException.WrongPassword::class.java) {
            crypto.unlock(password.toCharArray(), header.copyWith(wrappedKey = header.wrappedKey.flipFirstBit()))
        }
    }

    @Test
    fun twoSetupsUseDifferentSaltIvAndVaultKey() {
        val first = crypto.createVault(password.toCharArray(), TEST_KDF_PARAMS)
        val second = crypto.createVault(password.toCharArray(), TEST_KDF_PARAMS)

        assertFalse(first.header.salt.contentEquals(second.header.salt))
        assertFalse(first.header.iv.contentEquals(second.header.iv))
        assertFalse(first.vaultKey.contentEquals(second.vaultKey))
    }

    private fun ByteArray.flipFirstBit(): ByteArray =
        copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }

    private fun VaultHeader.copyWith(
        salt: ByteArray = this.salt,
        iv: ByteArray = this.iv,
        wrappedKey: ByteArray = this.wrappedKey
    ) = VaultHeader(version, kdfParams, salt, iv, wrappedKey)
}
