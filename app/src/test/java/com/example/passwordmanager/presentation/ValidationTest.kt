package com.example.passwordmanager.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidationTest {

    @Test
    fun masterPasswordShorterThanMinimumIsTooShort() {
        val password = "a".repeat(MIN_MASTER_PASSWORD_LENGTH - 1)

        assertEquals(MasterPasswordError.TooShort, validateMasterPassword(password, password))
    }

    @Test
    fun masterPasswordWithMismatchedConfirmationIsRejected() {
        val password = "a".repeat(MIN_MASTER_PASSWORD_LENGTH)

        assertEquals(MasterPasswordError.Mismatch, validateMasterPassword(password, password + "b"))
    }

    @Test
    fun masterPasswordAtMinimumLengthWithMatchingConfirmationIsValid() {
        val password = "a".repeat(MIN_MASTER_PASSWORD_LENGTH)

        assertNull(validateMasterPassword(password, password))
    }

    @Test
    fun credentialFormRequiresWebsiteAndPassword() {
        assertTrue(isCredentialFormValid("github.com", "secret"))
        assertFalse(isCredentialFormValid("", "secret"))
        assertFalse(isCredentialFormValid("   ", "secret"))
        assertFalse(isCredentialFormValid("github.com", ""))
        assertFalse(isCredentialFormValid("github.com", "  "))
    }
}
