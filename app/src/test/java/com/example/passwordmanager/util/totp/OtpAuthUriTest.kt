package com.example.passwordmanager.util.totp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class OtpAuthUriTest {

    @Test
    fun parsesAFullUri() {
        val account = parseTotp(
            "otpauth://totp/ACME%20Co:bob?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ&issuer=ACME%20Co&algorithm=SHA256&digits=8&period=60"
        )

        assertEquals("ACME Co", account.issuer)
        assertEquals("bob", account.accountName)
        assertEquals("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", account.secret)
        assertEquals(TotpAlgorithm.SHA256, account.algorithm)
        assertEquals(8, account.digits)
        assertEquals(60, account.period)
    }

    @Test
    fun missingParametersUseTheDefaults() {
        val account = parseTotp("otpauth://totp/alice@example.com?secret=JBSWY3DPEHPK3PXP")

        assertEquals("", account.issuer)
        assertEquals("alice@example.com", account.accountName)
        assertEquals(TotpAlgorithm.SHA1, account.algorithm)
        assertEquals(6, account.digits)
        assertEquals(30, account.period)
    }

    @Test
    fun issuerParameterWinsOverTheLabelPrefix() {
        assertEquals("Real", parseTotp("otpauth://totp/Label:alice?secret=JBSWY3DPEHPK3PXP&issuer=Real").issuer)
        assertEquals("Label", parseTotp("otpauth://totp/Label:alice?secret=JBSWY3DPEHPK3PXP").issuer)
        assertEquals("Label", parseTotp("otpauth://totp/Label:alice?secret=JBSWY3DPEHPK3PXP&issuer=").issuer)
    }

    @Test
    fun encodedSeparatorSpacesAndPlusSignsAreDecoded() {
        val encoded = parseTotp("otpauth://totp/Big%20Corp%3A%20jane%20doe?secret=JBSWY3DPEHPK3PXP")
        val plus = parseTotp("otpauth://totp/Big+Corp:jane+doe?secret=JBSWY3DPEHPK3PXP&issuer=Big+Corp")

        println("%3A/%20 -> ${encoded.issuer}|${encoded.accountName}, + -> ${plus.issuer}|${plus.accountName}")
        assertEquals("Big Corp" to "jane doe", encoded.issuer to encoded.accountName)
        assertEquals("Big Corp" to "jane doe", plus.issuer to plus.accountName)
    }

    @Test
    fun schemeTypeAndAlgorithmAreCaseInsensitive() {
        val account = parseTotp("OTPAUTH://TOTP/x?secret=jbswy3dpehpk3pxp&algorithm=sha512")

        assertEquals(TotpAlgorithm.SHA512, account.algorithm)
        assertEquals("JBSWY3DPEHPK3PXP", account.secret)
    }

    @Test
    fun secretIsNormalised() {
        assertEquals("JBSWY3DPEHPK3PXP", parseTotp("otpauth://totp/x?secret=jbsw%20y3dp-ehpk-3pxp%3D%3D").secret)
    }

    @Test
    fun unknownParametersAndRawSpacesInTheLabelAreAccepted() {
        val account = parseTotp("otpauth://totp/My Site:me?secret=JBSWY3DPEHPK3PXP&image=https%3A%2F%2Fx.test%2Fa.png")
        assertEquals("My Site" to "me", account.issuer to account.accountName)
    }

    @Test
    fun missingOrEmptySecretIsMissingSecret() {
        assertSame(OtpAuthResult.MissingSecret, OtpAuthUri.parse("otpauth://totp/x?issuer=A"))
        assertSame(OtpAuthResult.MissingSecret, OtpAuthUri.parse("otpauth://totp/x?secret=&issuer=A"))
        assertSame(OtpAuthResult.MissingSecret, OtpAuthUri.parse("otpauth://totp/x"))
    }

    @Test
    fun invalidOrOversizedSecretIsInvalidSecret() {
        listOf("ABC1!", "JBSW0", "A", "A".repeat(208)).forEach { secret ->
            assertSame(secret, OtpAuthResult.InvalidSecret, OtpAuthUri.parse("otpauth://totp/x?secret=$secret"))
        }
    }

    @Test
    fun invalidDigitsPeriodAlgorithmOrEncodingIsInvalidParameter() {
        val base = "otpauth://totp/x?secret=JBSWY3DPEHPK3PXP"
        listOf("&digits=7", "&digits=six", "&period=0", "&period=301", "&period=-30", "&algorithm=MD5", "&issuer=%zz")
            .forEach { suffix ->
                assertSame(suffix, OtpAuthResult.InvalidParameter, OtpAuthUri.parse(base + suffix))
            }
        assertSame(OtpAuthResult.InvalidParameter, OtpAuthUri.parse("otpauth://steam/x?secret=JBSWY3DPEHPK3PXP"))
    }

    @Test
    fun hotpIsUnsupported() {
        assertSame(OtpAuthResult.UnsupportedType, OtpAuthUri.parse("otpauth://hotp/x?secret=JBSWY3DPEHPK3PXP&counter=1"))
    }

    @Test
    fun otherTextIsNotOtpAuth() {
        listOf(
            "https://example.com",
            "otpauth-migration://offline?data=Ci4K",
            "JBSWY3DPEHPK3PXP",
            "",
            "otpauth://totp/x?secret=JBSWY3DPEHPK3PXP&pad=" + "a".repeat(4096)
        ).forEach { text ->
            assertSame(text.take(40), OtpAuthResult.NotOtpAuth, OtpAuthUri.parse(text))
        }
    }

    @Test
    fun buildThenParseRoundTrips() {
        val accounts = listOf(
            parseTotp("otpauth://totp/ACME%20Co:bob%2Bwork@example.com?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ&issuer=ACME%20Co&algorithm=SHA512&digits=8&period=45"),
            parseTotp("otpauth://totp/only-account?secret=JBSWY3DPEHPK3PXP")
        )
        accounts.forEach { account ->
            val uri = OtpAuthUri.build(account)
            println("build -> $uri")
            assertFalse(uri, uri.contains('+'))
            val parsed = parseTotp(uri)
            assertEquals(
                listOf(account.issuer, account.accountName, account.secret, account.algorithm, account.digits, account.period),
                listOf(parsed.issuer, parsed.accountName, parsed.secret, parsed.algorithm, parsed.digits, parsed.period)
            )
        }
    }

    @Test
    fun toStringDoesNotRevealTheSecret() {
        val text = parseTotp("otpauth://totp/x?secret=JBSWY3DPEHPK3PXP").toString()
        assertFalse(text, text.contains("JBSWY3DPEHPK3PXP"))
    }

    private fun parseTotp(uri: String): OtpAuthAccount {
        val result = OtpAuthUri.parse(uri)
        assertTrue("$uri -> $result", result is OtpAuthResult.Totp)
        return (result as OtpAuthResult.Totp).account
    }
}
