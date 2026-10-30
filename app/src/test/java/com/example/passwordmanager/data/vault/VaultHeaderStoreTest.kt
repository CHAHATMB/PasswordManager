package com.example.passwordmanager.data.vault

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.example.passwordmanager.data.crypto.KdfParams
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VaultHeaderStoreTest {

    private lateinit var prefs: SharedPreferences
    private lateinit var store: VaultHeaderStore

    private val header = VaultHeader(
        version = VaultHeader.CURRENT_VERSION,
        kdfParams = KdfParams(memoryKib = 65536, iterations = 3, parallelism = 4),
        salt = ByteArray(16) { it.toByte() },
        iv = ByteArray(12) { (it + 100).toByte() },
        wrappedKey = ByteArray(48) { (it * 3).toByte() }
    )

    @Before
    fun setUp() {
        prefs = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences(VaultHeaderStore.PREFS_NAME, Context.MODE_PRIVATE)
        store = VaultHeaderStore(prefs)
    }

    @Test
    fun loadWithoutHeaderReturnsNull() {
        assertFalse(store.exists())
        assertNull(store.load())
    }

    @Test
    fun savedHeaderSurvivesRoundTrip() {
        store.save(header)

        val loaded = VaultHeaderStore(prefs).load()!!

        assertTrue(store.exists())
        assertEquals(header.version, loaded.version)
        assertEquals(header.kdfParams, loaded.kdfParams)
        assertArrayEquals(header.salt, loaded.salt)
        assertArrayEquals(header.iv, loaded.iv)
        assertArrayEquals(header.wrappedKey, loaded.wrappedKey)
        assertNull("A header saved without biometrics must load without them", loaded.biometric)
    }

    @Test
    fun biometricFieldsSurviveRoundTrip() {
        val biometric = BiometricWrappedKey(iv = ByteArray(12) { (it + 7).toByte() }, wrappedKey = ByteArray(48) { it.toByte() })
        store.save(header.withBiometric(biometric))

        val loaded = VaultHeaderStore(prefs).load()!!

        assertArrayEquals(biometric.iv, loaded.biometric!!.iv)
        assertArrayEquals(biometric.wrappedKey, loaded.biometric!!.wrappedKey)
        assertArrayEquals("Password-wrapped key must be untouched", header.wrappedKey, loaded.wrappedKey)
    }

    @Test
    fun savingWithoutBiometricRemovesIt() {
        store.save(header.withBiometric(BiometricWrappedKey(ByteArray(12), ByteArray(48))))

        store.save(store.load()!!.withBiometric(null))

        assertNull(store.load()!!.biometric)
        assertFalse("bio_iv should be deleted from prefs", prefs.contains("bio_iv"))
        assertFalse("wrapped_vk_bio should be deleted from prefs", prefs.contains("wrapped_vk_bio"))
    }

    @Test
    fun incompleteBiometricBlobLoadsAsDisabled() {
        store.save(header.withBiometric(BiometricWrappedKey(ByteArray(12), ByteArray(48))))
        prefs.edit().remove("wrapped_vk_bio").commit()

        val loaded = store.load()!!

        assertNull("Half a biometric blob must not block the master password", loaded.biometric)
        assertArrayEquals(header.wrappedKey, loaded.wrappedKey)
    }

    @Test
    fun missingFieldThrowsCorruptHeader() {
        store.save(header)
        prefs.edit().remove("wrapped_key").commit()

        assertThrows(VaultException.CorruptHeader::class.java) { store.load() }
    }

    @Test
    fun missingKdfParamThrowsCorruptHeader() {
        store.save(header)
        prefs.edit().remove("kdf_iterations").commit()

        assertThrows(VaultException.CorruptHeader::class.java) { store.load() }
    }

    @Test
    fun garbledBase64ThrowsCorruptHeader() {
        store.save(header)
        prefs.edit().putString("salt", "not base64 !!").commit()

        assertThrows(VaultException.CorruptHeader::class.java) { store.load() }
    }

    @Test
    fun wrongFieldTypeThrowsCorruptHeader() {
        store.save(header)
        prefs.edit().putString("kdf_memory_kib", "65536").commit()

        assertThrows(VaultException.CorruptHeader::class.java) { store.load() }
    }

    @Test
    fun wrongIvLengthThrowsCorruptHeader() {
        store.save(VaultHeader(header.version, header.kdfParams, header.salt, ByteArray(4), header.wrappedKey))

        assertThrows(VaultException.CorruptHeader::class.java) { store.load() }
    }

    @Test
    fun unsupportedVersionThrowsCorruptHeader() {
        store.save(header)
        prefs.edit().putInt("version", 99).commit()

        assertThrows(VaultException.CorruptHeader::class.java) { store.load() }
    }
}
