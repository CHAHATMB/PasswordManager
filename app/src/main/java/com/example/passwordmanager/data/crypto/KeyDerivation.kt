package com.example.passwordmanager.data.crypto

import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.Argon2Mode
import java.nio.ByteBuffer

const val KEY_SIZE_BYTES = 32

data class KdfParams(val memoryKib: Int, val iterations: Int, val parallelism: Int) {
    companion object {
        val DEFAULT = KdfParams(memoryKib = 65536, iterations = 3, parallelism = 4)
        val LOW_RAM = KdfParams(memoryKib = 19456, iterations = 2, parallelism = 1)
    }
}

fun interface KeyDerivation {
    fun deriveKey(password: ByteArray, salt: ByteArray, params: KdfParams): ByteArray
}

class Argon2KeyDerivation(private val argon2: Argon2Kt = Argon2Kt()) : KeyDerivation {

    override fun deriveKey(password: ByteArray, salt: ByteArray, params: KdfParams): ByteArray {
        // The ByteArray overload copies the password into direct buffers it never wipes,
        // so we hand over our own buffers and wipe them.
        val passwordBuffer = password.toDirectBuffer()
        val saltBuffer = salt.toDirectBuffer()
        val result = try {
            argon2.hash(
                mode = Argon2Mode.ARGON2_ID,
                password = passwordBuffer,
                salt = saltBuffer,
                tCostInIterations = params.iterations,
                mCostInKibibyte = params.memoryKib,
                parallelism = params.parallelism,
                hashLengthInBytes = KEY_SIZE_BYTES
            )
        } finally {
            passwordBuffer.wipe()
        }
        return try {
            result.rawHashAsByteArray()
        } finally {
            result.rawHash.wipe()
            result.encodedOutput.wipe()
        }
    }

    // Absolute puts leave position/limit untouched, avoiding flip()/clear(), whose
    // covariant return types are missing on older Android versions.
    private fun ByteArray.toDirectBuffer(): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(size)
        forEachIndexed { index, byte -> buffer.put(index, byte) }
        return buffer
    }

    private fun ByteBuffer.wipe() {
        if (isReadOnly) return
        for (index in 0 until capacity()) put(index, 0)
    }
}
