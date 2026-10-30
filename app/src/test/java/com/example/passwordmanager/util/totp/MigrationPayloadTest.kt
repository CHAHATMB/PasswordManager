package com.example.passwordmanager.util.totp

import com.example.passwordmanager.data.totp.toTotpAccount
import java.net.URLEncoder
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MigrationPayloadTest {

    // research/v2-features.md: Example/alice@example.com SHA1/6 and ACME Co/bob SHA256/8, batch 0 of 1, id 123456.
    private val researchVector = "otpauth-migration://offline?data=Ci4KCkhlbGxvId6tvu8SEWFsaWNlQGV4YW1wbGUuY29tGgdFeGFtcGxlIAEoATACCioKFDEyMzQ1Njc4OTAxMjM0NTY3ODkwEgNib2IaB0FDTUUgQ28gAigCMAIQARgBIAAowMQH"

    @Test
    fun researchVectorDecodesToBothAccounts() {
        val batch = parseBatch(researchVector)

        assertEquals(
            listOf(
                listOf("Example", "alice@example.com", "JBSWY3DPEHPK3PXP", TotpAlgorithm.SHA1, 6, 30),
                listOf("ACME Co", "bob", "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", TotpAlgorithm.SHA256, 8, 30)
            ),
            batch.accounts.map { listOf(it.issuer, it.accountName, it.secret, it.algorithm, it.digits, it.period) }
        )
        assertEquals(listOf(0, 0, 1, 123456), listOf(batch.skipped, batch.batchIndex, batch.batchSize, batch.batchId))
    }

    @Test
    fun accountsMapToTotpAccountsWithTheRightCodes() {
        val totp = parseBatch(researchVector).accounts.map { it.toTotpAccount(createdAt = 1000L) }
        println("Mapped: $totp")

        // RFC 6238 section 4 / research: JBSWY3DPEHPK3PXP at t = 59 is 996554.
        assertEquals("996554", totp[0].codeAt(59))
        assertEquals(
            Totp.generate("12345678901234567890".encodeToByteArray(), 59, TotpAlgorithm.SHA256, 8, 30),
            totp[1].codeAt(59)
        )
        assertEquals(listOf(1000L, 1000L), totp.map { it.createdAt })
        assertEquals(listOf("Example", "ACME Co"), totp.map { it.title })
    }

    @Test
    fun percentEncodedRawUrlSafeAndUnpaddedDataAllDecode() {
        // The secret starts 3-byte aligned at offset 6, so FB EF FF encodes as "++//"; 31 bytes in all need "==".
        val secret = byteArrayOf(0, 0, 0xFB.toByte(), 0xEF.toByte(), 0xFF.toByte()) + "Hello".encodeToByteArray()
        val bytes = payload(otp(secret = secret, name = "x", issuer = "", type = TYPE_TOTP))
        val base64 = Base64.getEncoder().encodeToString(bytes)
        println("Base64: $base64")
        assertTrue(base64.contains('+') && base64.contains('/') && base64.endsWith("="))

        listOf(
            URLEncoder.encode(base64, "UTF-8"),
            base64,
            base64.trimEnd('='),
            base64.replace('+', '-').replace('/', '_').trimEnd('=')
        ).forEach { data ->
            val batch = parseBatch("otpauth-migration://offline?data=$data")
            assertEquals(data, Base32.encode(secret).trimEnd('='), batch.accounts.single().secret)
        }
    }

    @Test
    fun severalAccountsAndBatchFieldsAreRead() {
        val bytes = payload(
            otp(secret = SECRET_A, name = "one", issuer = "A", type = TYPE_TOTP),
            otp(secret = SECRET_B, name = "two", issuer = "B", type = TYPE_TOTP, algorithm = 3, digits = 2),
            otp(secret = SECRET_C, name = "three", issuer = "C", type = TYPE_TOTP, algorithm = 0, digits = 0),
            batchSize = 3,
            batchIndex = 1,
            batchId = -7
        )
        val batch = parseBatch(uri(bytes))

        assertEquals(listOf("one", "two", "three"), batch.accounts.map { it.accountName })
        assertEquals(listOf(TotpAlgorithm.SHA1, TotpAlgorithm.SHA512, TotpAlgorithm.SHA1), batch.accounts.map { it.algorithm })
        assertEquals(listOf(6, 8, 6), batch.accounts.map { it.digits })
        assertEquals(listOf(1, 3, -7), listOf(batch.batchIndex, batch.batchSize, batch.batchId))
    }

    @Test
    fun hotpMd5UnspecifiedTypeAndInvalidEntriesAreSkippedAndCounted() {
        val bytes = payload(
            otp(secret = SECRET_A, name = "totp", issuer = "", type = TYPE_TOTP),
            otp(secret = SECRET_B, name = "hotp", issuer = "", type = 1),
            otp(secret = SECRET_C, name = "md5", issuer = "", type = TYPE_TOTP, algorithm = 4),
            otp(secret = SECRET_C, name = "no type", issuer = "", type = 0),
            otp(secret = SECRET_C, name = "odd digits", issuer = "", type = TYPE_TOTP, digits = 3),
            otp(secret = ByteArray(0), name = "empty secret", issuer = "", type = TYPE_TOTP)
        )
        val batch = parseBatch(uri(bytes))

        assertEquals(listOf("totp"), batch.accounts.map { it.accountName })
        assertEquals(5, batch.skipped)
    }

    @Test
    fun issuerPrefixInTheNameIsSplitOff() {
        val bytes = payload(
            otp(secret = SECRET_A, name = "GitHub:alice", issuer = "", type = TYPE_TOTP),
            otp(secret = SECRET_B, name = "Google:bob@gmail.com", issuer = "Google", type = TYPE_TOTP),
            otp(secret = SECRET_C, name = "plain", issuer = "", type = TYPE_TOTP)
        )
        val batch = parseBatch(uri(bytes))

        assertEquals(
            listOf("GitHub" to "alice", "Google" to "bob@gmail.com", "" to "plain"),
            batch.accounts.map { it.issuer to it.accountName }
        )
    }

    @Test
    fun unknownFieldsAreSkipped() {
        val unknown = varintField(15, 99) + lengthField(16, "future".encodeToByteArray()) +
            varint((17 shl 3 or WIRE_FIXED32).toLong()) + ByteArray(4) +
            varint((18 shl 3 or WIRE_FIXED64).toLong()) + ByteArray(8)
        val bytes = payload(
            otp(secret = SECRET_A, name = "alice", issuer = "Example", type = TYPE_TOTP, extra = unknown + varintField(7, 5)),
            extra = unknown + varintField(2, 1)
        )
        val batch = parseBatch(uri(bytes))

        assertEquals("alice", batch.accounts.single().accountName)
        assertEquals(0, batch.skipped)
    }

    @Test
    fun truncatedOrGarbageDataIsMalformed() {
        val valid = researchVector.substringAfter("data=")
        listOf(
            "otpauth-migration://offline?data=${valid.dropLast(2)}",
            "otpauth-migration://offline?data=${valid.dropLast(4)}",
            "otpauth-migration://offline?data=${valid.take(20)}",
            "otpauth-migration://offline?data=%%%",
            "otpauth-migration://offline?data=A",
            "otpauth-migration://offline?data=",
            "otpauth-migration://offline",
            "otpauth-migration://offline?other=1",
            "otpauth-migration://offline?data=${Base64.getEncoder().encodeToString(ByteArray(64) { 0xFF.toByte() })}",
            "otpauth-migration://offline?data=${"A".repeat(9000)}",
            uri(payload(*Array(101) { otp(secret = SECRET_A, name = "n$it", issuer = "", type = TYPE_TOTP) }))
        ).forEach { text ->
            val result = MigrationPayload.parse(text)
            println("${text.take(60)} -> $result")
            assertSame(text.take(60), MigrationResult.MalformedPayload, result)
        }
    }

    @Test
    fun otherTextIsNotAMigrationUri() {
        listOf(
            "otpauth://totp/x?secret=JBSWY3DPEHPK3PXP",
            "https://example.com/?data=Ci4K",
            "otpauth-migration:offline?data=Ci4K",
            ""
        ).forEach { text ->
            assertSame(text, MigrationResult.NotMigrationUri, MigrationPayload.parse(text))
        }
    }

    @Test
    fun schemeIsCaseInsensitiveAndSurroundingWhitespaceIgnored() {
        val text = "  OTPAUTH-MIGRATION://offline?data=" + researchVector.substringAfter("data=") + "\n"
        assertEquals(2, parseBatch(text).accounts.size)
    }

    private fun parseBatch(text: String): MigrationResult.Batch {
        val result = MigrationPayload.parse(text)
        assertTrue("${text.take(60)} -> $result", result is MigrationResult.Batch)
        return result as MigrationResult.Batch
    }

    private fun uri(bytes: ByteArray) =
        "otpauth-migration://offline?data=" + URLEncoder.encode(Base64.getEncoder().encodeToString(bytes), "UTF-8")

    private fun payload(
        vararg accounts: ByteArray,
        batchSize: Long = 1,
        batchIndex: Long = 0,
        batchId: Long = 1,
        extra: ByteArray = ByteArray(0)
    ): ByteArray = accounts.fold(ByteArray(0)) { bytes, account -> bytes + lengthField(1, account) } +
        varintField(3, batchSize) + varintField(4, batchIndex) + varintField(5, batchId) + extra

    private fun otp(
        secret: ByteArray,
        name: String,
        issuer: String,
        type: Long,
        algorithm: Long = 1,
        digits: Long = 1,
        extra: ByteArray = ByteArray(0)
    ): ByteArray = lengthField(1, secret) + lengthField(2, name.encodeToByteArray()) +
        lengthField(3, issuer.encodeToByteArray()) + varintField(4, algorithm) + varintField(5, digits) +
        varintField(6, type) + extra

    private fun lengthField(field: Int, value: ByteArray) =
        varint((field shl 3 or WIRE_LENGTH_DELIMITED).toLong()) + varint(value.size.toLong()) + value

    private fun varintField(field: Int, value: Long) = varint((field shl 3 or WIRE_VARINT).toLong()) + varint(value)

    private fun varint(value: Long): ByteArray {
        val bytes = mutableListOf<Byte>()
        var rest = value
        do {
            val low = (rest and 0x7f).toInt()
            rest = rest ushr 7
            bytes += (if (rest != 0L) low or 0x80 else low).toByte()
        } while (rest != 0L)
        return bytes.toByteArray()
    }

    private companion object {
        const val TYPE_TOTP = 2L
        val SECRET_A = "Hello!".encodeToByteArray()
        val SECRET_B = "12345678901234567890".encodeToByteArray()
        val SECRET_C = "abcdefghij".encodeToByteArray()
    }
}
