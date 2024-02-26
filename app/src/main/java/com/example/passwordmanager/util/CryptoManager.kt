package com.example.passwordmanager.util

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.annotation.RequiresApi
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

@RequiresApi(Build.VERSION_CODES.M)
class CryptoManager {

    val keygen = KeyGenerator.getInstance(ALGORITHM).apply{
        this.init(256)
    }
//    keygen.init(256)
    val key: SecretKey = keygen.generateKey()

    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply {
        load(null)
    }

    private val encryptCipher get() = Cipher.getInstance(TRANSFORMATION).apply {
        init(Cipher.ENCRYPT_MODE, key)
    }
//    private fun encryptCipherFun(iv: ByteArray): Cipher{
//        return Cipher.getInstance(TRANSFORMATION).apply {
//            init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv))
//        }
//    }

    private fun getDecryptCipherForIv(iv: ByteArray): Cipher {
        return Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))
        }
    }

//    private fun getKey(): SecretKey {
//        val existingKey = keyStore.getEntry("secret", null) as? KeyStore.SecretKeyEntry
//        val key = existingKey?.secretKey ?: createKey()
//        println("your key $key")
//        return key
//    }
//
//    private fun createKey(): SecretKey {
//        return KeyGenerator.getInstance(ALGORITHM).apply {
//            init(
//                KeyGenParameterSpec.Builder(
//                    "secret",
//                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
//                )
//                    .setBlockModes(BLOCK_MODE)
//                    .setEncryptionPaddings(PADDING)
//                    .setUserAuthenticationRequired(false)
//                    .setRandomizedEncryptionRequired(true)
//                    .build()
//            )
//        }.generateKey()
//    }


    fun encrypt(bytes: ByteArray, outputStream: OutputStream): ByteArray {
        val encryptedBytes = encryptCipher.doFinal(bytes)
        outputStream.use {
            it.write(encryptCipher.iv.size)
            it.write(encryptCipher.iv)
            it.write(encryptedBytes.size)
            it.write(encryptedBytes)
        }
        return encryptedBytes
    }

    fun decrypt(inputStream: InputStream): ByteArray {
        return inputStream.use {
            val ivSize = it.read()
            val iv = ByteArray(ivSize)
            it.read(iv)

            val encryptedBytesSize = it.read()
            val encryptedBytes = ByteArray(encryptedBytesSize)
            it.read(encryptedBytes)

            getDecryptCipherForIv(iv).doFinal(encryptedBytes)
        }
    }

    fun encryptString(plainText: String): Pair<String,String> {
        val encryptedBytes = encryptCipher.doFinal(plainText.encodeToByteArray())
        println("iv before ency - ${encryptCipher.iv.toString()}")
//        return Pair(encryptCipher.iv.toString(), encryptedBytes.toString())
        return Pair(Base64.encodeToString(encryptCipher.iv, Base64.DEFAULT), Base64.encodeToString(encryptedBytes, Base64.DEFAULT))
    }
    fun encryptString3(plainText: String): Pair<ByteArray,String> {
        val encryptedBytes = encryptCipher.doFinal(plainText.encodeToByteArray())
        println("iv before ency - ${encryptCipher.iv.toString()}")
//        return Pair(encryptCipher.iv.toString(), encryptedBytes.toString())
        return Pair(encryptCipher.iv, Base64.encodeToString(encryptedBytes, Base64.DEFAULT))
    }
//    fun encryptString2(plainText: String, iv: String): Pair<String,String> {
//        val encryptedBytes = encryptCipherFun(iv.encodeToByteArray()).doFinal(plainText.encodeToByteArray())
//        println("iv before ency - ${encryptCipher.iv.toString()}")
//        return Pair(encryptCipher.iv.toString(), encryptedBytes.toString())
//        return Pair(Base64.encodeToString(encryptCipher.iv, Base64.DEFAULT), Base64.encodeToString(encryptedBytes, Base64.DEFAULT))
//    }

    fun decryptString(ivString: String, encryptedTextString: String): String{
        val iv = Base64.decode(ivString, Base64.DEFAULT)
        val encryptedBytes = Base64.decode(encryptedTextString, Base64.DEFAULT)

        println("iv after dency - ${iv}")
        return getDecryptCipherForIv(iv).doFinal(encryptedBytes).toString()
    }
    fun decryptString3(ivByte: ByteArray, encryptedTextString: String): String{
//        val iv = Base64.decode(ivString, Base64.DEFAULT)
        val encryptedBytes = Base64.decode(encryptedTextString, Base64.DEFAULT)

        println("iv after dency - ${ivByte}")
        return getDecryptCipherForIv(ivByte).doFinal(encryptedBytes).toString()
    }

    fun encryptString5(plainText: String): Pair<ByteArray, ByteArray> {
        val encryptedBytes = encryptCipher.doFinal("chahatbag".encodeToByteArray())
        println("your iv ${encryptCipher.iv}")
        println("your decrypt message ${decryptString5(encryptCipher.iv, encryptedBytes)}")
        return Pair(encryptCipher.iv, encryptedBytes)
    }

    fun decryptString5(iv: ByteArray, encryptedBytes: ByteArray): String {
        println("your iv back ${iv}")
        val cipher = getDecryptCipherForIv(iv)
        return String(cipher.doFinal(encryptedBytes))
    }
    fun encryptString10(plainText: String): String {
        val secretKeySpec = SecretKeySpec("sceret12312313232132313455".toByteArray(), "AES")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val encryptedBytes = cipher.doFinal(plainText.toByteArray())
        return Base64.encodeToString(encryptedBytes, Base64.DEFAULT)
    }
    fun decryptString10(encryptedText: String): String {
        val secretKeySpec = SecretKeySpec("sceret123".toByteArray(), "AES")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key)
        val encryptedBytes = Base64.decode(encryptedText, Base64.DEFAULT)
        val decryptedBytes = cipher.doFinal(encryptedBytes)
        return String(decryptedBytes)
    }
    companion object {
        private const val ALGORITHM = KeyProperties.KEY_ALGORITHM_AES
        private const val BLOCK_MODE = KeyProperties.BLOCK_MODE_CBC
        private const val PADDING = KeyProperties.ENCRYPTION_PADDING_PKCS7
        private const val TRANSFORMATION = "$ALGORITHM/$BLOCK_MODE/$PADDING"
    }

}