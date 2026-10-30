package com.example.myfoodtracker.domain.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Passcode hashing with a versioned, self-describing storage format.
 *
 * New hashes use PBKDF2-HMAC-SHA256 with a per-hash random salt, stored as
 * `pbkdf2$<iterations>$<base64-salt>$<base64-hash>` (deferred-work 1.2-D1).
 * The legacy `"<hex-salt>:<hex-sha256>"` format (pre-7.1 profiles) stays
 * verifiable so existing profiles keep logging in — [verifyPasscode] dispatches
 * on the stored format and never migrates anything itself (see
 * [UserRepositoryImpl][com.example.myfoodtracker.data.repository.UserRepositoryImpl]
 * for the login-time upgrade hook; `users.passcode_hash` is unchanged column-wise).
 */
object PasscodeHasher {

    /** Field separator inside the prefixed format. */
    const val FIELD_SEPARATOR = "$"

    /** Header of the prefixed storage format. */
    const val FORMAT_PREFIX = "pbkdf2$"

    /**
     * OWASP Password Storage Cheat Sheet (2026) recommends 600,000 iterations for
     * PBKDF2-HMAC-SHA256. Because the iteration count is stored per hash, raising
     * this default never breaks verification of previously stored hashes.
     * Unit tests should pass a low value (e.g. 1_000) to stay fast; never go
     * below the 100_000 AC floor in production.
     */
    const val DEFAULT_ITERATIONS = 600_000

    /** Upper bound to prevent tampered hashes from locking the UI thread in an ANR. */
    const val MAX_ITERATIONS = 5_000_000

    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val HASH_BITS = 256
    private const val SALT_BYTES = 16

    fun hashPasscode(passcode: String, iterations: Int = DEFAULT_ITERATIONS): String {
        require(iterations > 0) { "Iterations must be positive" }
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val derived = derive(passcode, salt, iterations)
        val encoder = Base64.getEncoder()
        return listOf(
            "pbkdf2",
            iterations.toString(),
            encoder.encodeToString(salt),
            encoder.encodeToString(derived)
        ).joinToString(FIELD_SEPARATOR)
    }

    fun verifyPasscode(passcode: String, storedHash: String): Boolean = when {
        storedHash.startsWith(FORMAT_PREFIX) -> verifyPrefixed(passcode, storedHash)
        isLegacyFormat(storedHash) -> verifyLegacy(passcode, storedHash)
        else -> false // fail-closed: unknown format can never verify
    }

    private fun verifyPrefixed(passcode: String, storedHash: String): Boolean {
        return try {
            val parts = storedHash.split(FIELD_SEPARATOR)
            if (parts.size != 4 || parts[0] != "pbkdf2") return false
            val iterations = parts[1].toInt()
            if (iterations <= 0 || iterations > MAX_ITERATIONS) return false
            val salt = Base64.getDecoder().decode(parts[2])
            val expectedHash = Base64.getDecoder().decode(parts[3])
            val computedHash = derive(passcode, salt, iterations)
            MessageDigest.isEqual(computedHash, expectedHash)
        } catch (_: IllegalArgumentException) {
            false // bad base64 / non-numeric iteration count
        } catch (_: NumberFormatException) {
            false
        } catch (_: java.security.GeneralSecurityException) {
            false
        }
    }

    private fun isLegacyFormat(storedHash: String): Boolean {
        val parts = storedHash.split(":")
        return parts.size == 2 &&
            parts[0].isHexString() &&
            parts[1].isHexString()
    }

    private fun verifyLegacy(passcode: String, storedHash: String): Boolean {
        val (saltHex, expectedHashHex) = storedHash.split(":", limit = 2).map { it.lowercase() }
        val md = MessageDigest.getInstance("SHA-256")
        val digestHex = md.digest((saltHex + passcode).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return MessageDigest.isEqual(
            digestHex.toByteArray(Charsets.UTF_8),
            expectedHashHex.toByteArray(Charsets.UTF_8)
        )
    }

    private fun String.isHexString(): Boolean =
        isNotEmpty() && length % 2 == 0 && all {
            it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F'
        }

    private fun derive(passcode: String, salt: ByteArray, iterations: Int): ByteArray {
        val passcodeChars = passcode.toCharArray()
        val spec = PBEKeySpec(passcodeChars, salt, iterations, HASH_BITS)
        try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            return factory.generateSecret(spec).encoded
        } finally {
            // Wipe the character-array copy of the passcode after deriving
            passcodeChars.fill(' ')
            spec.clearPassword()
        }
    }
}
