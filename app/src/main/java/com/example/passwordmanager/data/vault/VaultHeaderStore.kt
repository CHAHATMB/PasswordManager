package com.example.passwordmanager.data.vault

import android.content.SharedPreferences
import com.example.passwordmanager.data.crypto.IV_SIZE_BYTES
import com.example.passwordmanager.data.crypto.KdfParams
import com.example.passwordmanager.data.crypto.SALT_SIZE_BYTES
import java.io.IOException
import java.util.Base64

class VaultHeaderStore(private val prefs: SharedPreferences) {

    fun exists(): Boolean = prefs.contains(KEY_VERSION)

    /** Returns null when no vault has been set up yet. */
    fun load(): VaultHeader? {
        if (!exists()) return null
        try {
            val version = requireInt(KEY_VERSION)
            if (version != VaultHeader.CURRENT_VERSION) {
                throw VaultException.CorruptHeader("Unsupported vault header version $version")
            }
            val header = VaultHeader(
                version = version,
                kdfParams = KdfParams(
                    memoryKib = requireInt(KEY_KDF_MEMORY_KIB),
                    iterations = requireInt(KEY_KDF_ITERATIONS),
                    parallelism = requireInt(KEY_KDF_PARALLELISM)
                ),
                salt = requireBytes(KEY_SALT),
                iv = requireBytes(KEY_IV),
                wrappedKey = requireBytes(KEY_WRAPPED_KEY)
            )
            if (header.salt.size != SALT_SIZE_BYTES || header.iv.size != IV_SIZE_BYTES) {
                throw VaultException.CorruptHeader("Invalid salt or IV length")
            }
            return header
        } catch (e: ClassCastException) {
            throw VaultException.CorruptHeader("Vault header field has the wrong type", e)
        } catch (e: IllegalArgumentException) {
            throw VaultException.CorruptHeader("Vault header field is not valid Base64", e)
        }
    }

    fun save(header: VaultHeader) {
        val committed = prefs.edit()
            .putInt(KEY_VERSION, header.version)
            .putInt(KEY_KDF_MEMORY_KIB, header.kdfParams.memoryKib)
            .putInt(KEY_KDF_ITERATIONS, header.kdfParams.iterations)
            .putInt(KEY_KDF_PARALLELISM, header.kdfParams.parallelism)
            .putString(KEY_SALT, encoder.encodeToString(header.salt))
            .putString(KEY_IV, encoder.encodeToString(header.iv))
            .putString(KEY_WRAPPED_KEY, encoder.encodeToString(header.wrappedKey))
            .commit()
        if (!committed) throw IOException("Failed to write vault header")
    }

    private fun requireInt(key: String): Int {
        if (!prefs.contains(key)) throw VaultException.CorruptHeader("Vault header is missing $key")
        return prefs.getInt(key, 0)
    }

    private fun requireBytes(key: String): ByteArray {
        val value = prefs.getString(key, null)
            ?: throw VaultException.CorruptHeader("Vault header is missing $key")
        return decoder.decode(value)
    }

    companion object {
        const val PREFS_NAME = "vault_header"
        private const val KEY_VERSION = "version"
        private const val KEY_KDF_MEMORY_KIB = "kdf_memory_kib"
        private const val KEY_KDF_ITERATIONS = "kdf_iterations"
        private const val KEY_KDF_PARALLELISM = "kdf_parallelism"
        private const val KEY_SALT = "salt"
        private const val KEY_IV = "iv"
        private const val KEY_WRAPPED_KEY = "wrapped_key"
        private val encoder = Base64.getEncoder()
        private val decoder = Base64.getDecoder()
    }
}
