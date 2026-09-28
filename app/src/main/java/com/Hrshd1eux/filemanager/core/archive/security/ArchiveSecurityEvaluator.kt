package com.Hrshd1eux.filemanager.core.archive.security

import java.security.SecureRandom

/**
 * Categorized password strength tiers for archive encryption.
 */
enum class PasswordStrength {
    VERY_WEAK,
    WEAK,
    FAIR,
    STRONG,
    VERY_STRONG
}

/**
 * Result of evaluating password strength with score, strength level, and actionable warning message.
 */
data class PasswordStrengthResult(
    val score: Float,
    val strength: PasswordStrength,
    val label: String,
    val warningMessage: String?
)

/**
 * Security utilities for archive encryption:
 * - Real-time password strength evaluation and vulnerability warnings.
 * - Cryptographically secure password generation (CSPRNG).
 */
object ArchiveSecurityEvaluator {

    private val COMMON_WEAK_PASSWORDS = setOf(
        "123456", "password", "12345678", "qwerty", "123456789", "12345",
        "1234", "111111", "1234567", "dragon", "welcome", "ninja",
        "admin", "secret", "master", "android", "archive", "backup"
    )

    private const val UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val LOWERCASE = "abcdefghijklmnopqrstuvwxyz"
    private const val DIGITS = "0123456789"
    private const val SYMBOLS = "!@#$%^&*()-_=+[]{}|;:,.<>?"
    private const val ALL_CHARS = UPPERCASE + LOWERCASE + DIGITS + SYMBOLS

    private val secureRandom = SecureRandom()

    /**
     * Evaluates the strength of a given password and provides warnings for weak passwords.
     */
    fun evaluatePassword(password: CharSequence): PasswordStrengthResult {
        if (password.isEmpty()) {
            return PasswordStrengthResult(
                score = 0f,
                strength = PasswordStrength.VERY_WEAK,
                label = "Empty",
                warningMessage = null
            )
        }

        val passStr = password.toString()
        val lower = passStr.lowercase()

        // Check common dictionary patterns
        if (COMMON_WEAK_PASSWORDS.contains(lower)) {
            return PasswordStrengthResult(
                score = 0.1f,
                strength = PasswordStrength.VERY_WEAK,
                label = "Very Weak",
                warningMessage = "Common password! Extremely vulnerable to dictionary attacks."
            )
        }

        val len = passStr.length
        var hasLower = false
        var hasUpper = false
        var hasDigit = false
        var hasSymbol = false

        for (ch in passStr) {
            when {
                ch in 'a'..'z' -> hasLower = true
                ch in 'A'..'Z' -> hasUpper = true
                ch in '0'..'9' -> hasDigit = true
                else -> hasSymbol = true
            }
        }

        val charClassesCount = (if (hasLower) 1 else 0) +
                (if (hasUpper) 1 else 0) +
                (if (hasDigit) 1 else 0) +
                (if (hasSymbol) 1 else 0)

        val uniqueChars = passStr.toSet().size
        val varietyRatio = uniqueChars.toFloat() / len.coerceAtLeast(1)

        // Length contribution (up to 0.4)
        val lengthScore = (len.toFloat() / 16f).coerceIn(0f, 0.4f)
        // Diversity contribution (up to 0.45)
        val diversityScore = (charClassesCount * 0.1125f)
        // Variety penalty / bonus (up to 0.15)
        val varietyScore = (varietyRatio * 0.15f)

        var totalScore = (lengthScore + diversityScore + varietyScore).coerceIn(0f, 1f)

        // Heavily penalize repetitive strings (e.g. "aaaaaaaa", "11111111")
        if (uniqueChars <= 2 && len > 3) {
            totalScore = (totalScore * 0.3f).coerceAtMost(0.2f)
        }

        return when {
            len < 6 || totalScore < 0.25f -> {
                PasswordStrengthResult(
                    score = totalScore,
                    strength = PasswordStrength.VERY_WEAK,
                    label = "Very Weak",
                    warningMessage = "Very weak: Minimum 8 characters recommended for archive encryption."
                )
            }
            len < 8 || charClassesCount < 2 || totalScore < 0.45f -> {
                val warning = if (len < 8) {
                    "Short password: Use at least 8 characters with numbers or symbols."
                } else {
                    "Add numbers, uppercase letters, or symbols to strengthen."
                }
                PasswordStrengthResult(
                    score = totalScore,
                    strength = PasswordStrength.WEAK,
                    label = "Weak",
                    warningMessage = warning
                )
            }
            totalScore < 0.70f -> {
                PasswordStrengthResult(
                    score = totalScore,
                    strength = PasswordStrength.FAIR,
                    label = "Fair",
                    warningMessage = null
                )
            }
            totalScore < 0.90f -> {
                PasswordStrengthResult(
                    score = totalScore,
                    strength = PasswordStrength.STRONG,
                    label = "Strong",
                    warningMessage = null
                )
            }
            else -> {
                PasswordStrengthResult(
                    score = totalScore,
                    strength = PasswordStrength.VERY_STRONG,
                    label = "Very Strong",
                    warningMessage = null
                )
            }
        }
    }

    /**
     * Generates a cryptographically strong random password using CSPRNG.
     * Guaranteed to contain at least 1 character from each active character class.
     *
     * @param length Desired password length (8..64, default 24).
     * @param includeLetters Whether to include letters (both uppercase A-Z and lowercase a-z).
     * @param includeNumbers Whether to include digits (0-9).
     * @param includeSymbols Whether to include symbols (!@#$%^&*...).
     * @throws IllegalArgumentException if all character classes are set to false.
     */
    fun generateSecurePassword(
        length: Int = 24,
        includeLetters: Boolean = true,
        includeNumbers: Boolean = true,
        includeSymbols: Boolean = true
    ): String {
        require(includeLetters || includeNumbers || includeSymbols) {
            "At least one character class (Letters, Numbers, or Symbols) must be enabled."
        }

        val safeLen = length.coerceIn(8, 64)
        val chars = CharArray(safeLen)
        var writeIndex = 0

        // Build active alphabet pool
        val pool = StringBuilder()
        if (includeLetters) {
            pool.append(UPPERCASE).append(LOWERCASE)
            chars[writeIndex++] = UPPERCASE[secureRandom.nextInt(UPPERCASE.length)]
            chars[writeIndex++] = LOWERCASE[secureRandom.nextInt(LOWERCASE.length)]
        }
        if (includeNumbers) {
            pool.append(DIGITS)
            chars[writeIndex++] = DIGITS[secureRandom.nextInt(DIGITS.length)]
        }
        if (includeSymbols) {
            pool.append(SYMBOLS)
            chars[writeIndex++] = SYMBOLS[secureRandom.nextInt(SYMBOLS.length)]
        }

        val poolStr = pool.toString()

        // Fill remaining length from active character pool
        for (i in writeIndex until safeLen) {
            chars[i] = poolStr[secureRandom.nextInt(poolStr.length)]
        }

        // Fisher-Yates shuffle with CSPRNG
        for (i in safeLen - 1 downTo 1) {
            val j = secureRandom.nextInt(i + 1)
            val temp = chars[i]
            chars[i] = chars[j]
            chars[j] = temp
        }

        return String(chars)
    }
}
