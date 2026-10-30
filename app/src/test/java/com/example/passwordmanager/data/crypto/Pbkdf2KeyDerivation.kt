package com.example.passwordmanager.data.crypto

import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Fast JVM stand-in for Argon2, which needs a native library unavailable in unit tests. */
class Pbkdf2KeyDerivation : KeyDerivation {
    override fun deriveKey(password: ByteArray, salt: ByteArray, params: KdfParams): ByteArray {
        val chars = String(password, Charsets.UTF_8).toCharArray()
        val spec = PBEKeySpec(chars, salt, params.iterations, KEY_SIZE_BYTES * 8)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }
}

val TEST_KDF_PARAMS = KdfParams(memoryKib = 0, iterations = 1000, parallelism = 1)
