package com.Hrshd1eux.filemanager.core.archive.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveSecurityEvaluatorTest {

    @Test
    fun evaluatePassword_empty_returnsVeryWeak() {
        val result = ArchiveSecurityEvaluator.evaluatePassword("")
        assertEquals(PasswordStrength.VERY_WEAK, result.strength)
        assertEquals(0f, result.score, 0.001f)
        assertNull(result.warningMessage)
    }

    @Test
    fun evaluatePassword_commonDictionaryPasswords_flaggedVeryWeakWithWarning() {
        val common = listOf("123456", "password", "qwerty", "12345678", "admin", "secret")
        for (pwd in common) {
            val result = ArchiveSecurityEvaluator.evaluatePassword(pwd)
            assertEquals("Expected $pwd to be VERY_WEAK", PasswordStrength.VERY_WEAK, result.strength)
            assertNotNull("Expected warning for $pwd", result.warningMessage)
            assertTrue(result.warningMessage!!.contains("vulnerable", ignoreCase = true))
        }
    }

    @Test
    fun evaluatePassword_shortPasswords_flaggedWeakOrVeryWeak() {
        val short = "Ab1!"
        val result = ArchiveSecurityEvaluator.evaluatePassword(short)
        assertEquals(PasswordStrength.VERY_WEAK, result.strength)
        assertNotNull(result.warningMessage)
        assertTrue(result.warningMessage!!.contains("Minimum 8 characters", ignoreCase = true))

        val sixChar = "Abc12!"
        val resultSix = ArchiveSecurityEvaluator.evaluatePassword(sixChar)
        assertEquals(PasswordStrength.WEAK, resultSix.strength)
        assertNotNull(resultSix.warningMessage)
        assertTrue(resultSix.warningMessage!!.contains("at least 8 characters", ignoreCase = true))
    }

    @Test
    fun evaluatePassword_singleClassLongPassword_flaggedWeak() {
        val allLower = "alllowercasestring"
        val result = ArchiveSecurityEvaluator.evaluatePassword(allLower)
        assertEquals(PasswordStrength.WEAK, result.strength)
        assertNotNull(result.warningMessage)
        assertTrue(result.warningMessage!!.contains("Add numbers", ignoreCase = true))
    }

    @Test
    fun evaluatePassword_repetitiveChars_penalized() {
        val repetitive = "aaaaaaaaaaaaaaa"
        val result = ArchiveSecurityEvaluator.evaluatePassword(repetitive)
        assertTrue(result.score < 0.3f)
        assertTrue(result.strength == PasswordStrength.VERY_WEAK || result.strength == PasswordStrength.WEAK)
    }

    @Test
    fun evaluatePassword_strongPassword_returnsStrongWithoutWarning() {
        val strong = "Tr0ub4dor&3"
        val result = ArchiveSecurityEvaluator.evaluatePassword(strong)
        assertTrue(result.score >= 0.70f)
        assertTrue(result.strength == PasswordStrength.STRONG || result.strength == PasswordStrength.VERY_STRONG)
        assertNull(result.warningMessage)
    }

    @Test
    fun evaluatePassword_veryStrongPassword_returnsVeryStrong() {
        val veryStrong = "K#9xP!2mQ$8vL@5z"
        val result = ArchiveSecurityEvaluator.evaluatePassword(veryStrong)
        assertTrue(result.score >= 0.85f)
        assertEquals(PasswordStrength.VERY_STRONG, result.strength)
        assertNull(result.warningMessage)
    }

    @Test
    fun generateSecurePassword_lengthAndCharacterClasses_satisfiesStrictPolicy() {
        val pwd = ArchiveSecurityEvaluator.generateSecurePassword(16)
        assertEquals(16, pwd.length)

        val hasUpper = pwd.any { it in 'A'..'Z' }
        val hasLower = pwd.any { it in 'a'..'z' }
        val hasDigit = pwd.any { it in '0'..'9' }
        val hasSymbol = pwd.any { "!@#$%^&*()-_=+[]{}|;:,.<>?".contains(it) }

        assertTrue("Generated password must contain uppercase", hasUpper)
        assertTrue("Generated password must contain lowercase", hasLower)
        assertTrue("Generated password must contain digit", hasDigit)
        assertTrue("Generated password must contain symbol", hasSymbol)

        // Evaluate generated password
        val eval = ArchiveSecurityEvaluator.evaluatePassword(pwd)
        assertTrue("Generated password score must be strong: ${eval.score}", eval.score >= 0.75f)
        assertTrue(eval.strength == PasswordStrength.STRONG || eval.strength == PasswordStrength.VERY_STRONG)
        assertNull(eval.warningMessage)
    }

    @Test
    fun generateSecurePassword_defaultLength24_containsAllClasses() {
        val pwd = ArchiveSecurityEvaluator.generateSecurePassword()
        assertEquals(24, pwd.length)

        val hasUpper = pwd.any { it in 'A'..'Z' }
        val hasLower = pwd.any { it in 'a'..'z' }
        val hasDigit = pwd.any { it in '0'..'9' }
        val hasSymbol = pwd.any { "!@#$%^&*()-_=+[]{}|;:,.<>?".contains(it) }

        assertTrue("Must contain uppercase", hasUpper)
        assertTrue("Must contain lowercase", hasLower)
        assertTrue("Must contain digit", hasDigit)
        assertTrue("Must contain symbol", hasSymbol)
    }

    @Test
    fun generateSecurePassword_lettersOnly_containsOnlyLetters() {
        val pwd = ArchiveSecurityEvaluator.generateSecurePassword(
            length = 24,
            includeLetters = true,
            includeNumbers = false,
            includeSymbols = false
        )
        assertEquals(24, pwd.length)
        assertTrue(pwd.all { it in 'a'..'z' || it in 'A'..'Z' })
        assertFalse(pwd.any { it in '0'..'9' })
        assertFalse(pwd.any { "!@#$%^&*()-_=+[]{}|;:,.<>?".contains(it) })
    }

    @Test
    fun generateSecurePassword_numbersOnly_containsOnlyDigits() {
        val pwd = ArchiveSecurityEvaluator.generateSecurePassword(
            length = 24,
            includeLetters = false,
            includeNumbers = true,
            includeSymbols = false
        )
        assertEquals(24, pwd.length)
        assertTrue(pwd.all { it in '0'..'9' })
        assertFalse(pwd.any { it in 'a'..'z' || it in 'A'..'Z' })
        assertFalse(pwd.any { "!@#$%^&*()-_=+[]{}|;:,.<>?".contains(it) })
    }

    @Test
    fun generateSecurePassword_symbolsOnly_containsOnlySymbols() {
        val pwd = ArchiveSecurityEvaluator.generateSecurePassword(
            length = 24,
            includeLetters = false,
            includeNumbers = false,
            includeSymbols = true
        )
        assertEquals(24, pwd.length)
        assertTrue(pwd.all { "!@#$%^&*()-_=+[]{}|;:,.<>?".contains(it) })
        assertFalse(pwd.any { it in 'a'..'z' || it in 'A'..'Z' })
        assertFalse(pwd.any { it in '0'..'9' })
    }

    @Test
    fun generateSecurePassword_lettersAndNumbers_containsNoSymbols() {
        val pwd = ArchiveSecurityEvaluator.generateSecurePassword(
            length = 24,
            includeLetters = true,
            includeNumbers = true,
            includeSymbols = false
        )
        assertEquals(24, pwd.length)
        assertTrue(pwd.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' })
        assertFalse(pwd.any { "!@#$%^&*()-_=+[]{}|;:,.<>?".contains(it) })
        assertTrue(pwd.any { it in 'a'..'z' || it in 'A'..'Z' })
        assertTrue(pwd.any { it in '0'..'9' })
    }

    @Test(expected = IllegalArgumentException::class)
    fun generateSecurePassword_noClassesSelected_throwsIllegalArgumentException() {
        ArchiveSecurityEvaluator.generateSecurePassword(
            length = 24,
            includeLetters = false,
            includeNumbers = false,
            includeSymbols = false
        )
    }

    @Test
    fun generateSecurePassword_consecutiveGenerations_areUnique() {
        val passwords = (1..50).map { ArchiveSecurityEvaluator.generateSecurePassword(24) }.toSet()
        assertEquals("All 50 generated passwords must be distinct", 50, passwords.size)
    }
}
