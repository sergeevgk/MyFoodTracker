package com.example.myfoodtracker.domain.security

import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest
import java.util.Base64

class PasscodeHasherTest {

    private val fieldSeparator = "${'$'}"

    @Test
    fun hashPasscode_producesPrefixedPbkdf2Format() {
        val hash = PasscodeHasher.hashPasscode("1234", iterations = 1_000)
        val parts = hash.split(fieldSeparator)
        assertEquals(4, parts.size)
        assertEquals("pbkdf2", parts[0])
        assertEquals("1000", parts[1]) // stored iteration count echoes the parameter
        assertEquals(16, Base64.getDecoder().decode(parts[2]).size) // salt: 16 random bytes (AC floor ≥ 8)
        assertEquals(32, Base64.getDecoder().decode(parts[3]).size) // hash: 32-byte SHA-256 output
    }

    @Test
    fun hashPasscode_defaultIterationsRecommendation() {
        // Story 7.1 decision: OWASP 2026 recommends 600k iterations for PBKDF2-HMAC-SHA256
        assertEquals(600_000, PasscodeHasher.DEFAULT_ITERATIONS)
    }

    @Test
    fun hashPasscode_differentSaltsProduceDifferentHashes() {
        val hash1 = PasscodeHasher.hashPasscode("1234", iterations = 1_000)
        val hash2 = PasscodeHasher.hashPasscode("1234", iterations = 1_000)
        assertEquals(hash1.split(fieldSeparator)[1], hash2.split(fieldSeparator)[1]) // same iters
        assertNotEquals(
            hash1.split(fieldSeparator)[2],
            hash2.split(fieldSeparator)[2]
        ) // salts differ
        assertNotEquals(
            hash1.split(fieldSeparator)[3],
            hash2.split(fieldSeparator)[3]
        ) // hashes differ
    }

    @Test
    fun verifyPasscode_correctPasscode_returnsTrue() {
        val hash = PasscodeHasher.hashPasscode("5678", iterations = 1_000)
        assertTrue(PasscodeHasher.verifyPasscode("5678", hash))
    }

    @Test
    fun verifyPasscode_wrongPasscode_returnsFalse() {
        val hash = PasscodeHasher.hashPasscode("5678", iterations = 1_000)
        assertFalse(PasscodeHasher.verifyPasscode("0000", hash))
    }

    @Test
    fun verifyPasscode_lowIterationsParameterHonored() {
        val hash = PasscodeHasher.hashPasscode("5678", iterations = 1_000)
        assertEquals("1000", hash.split(fieldSeparator)[1])
        assertTrue(PasscodeHasher.verifyPasscode("5678", hash))
        assertFalse(PasscodeHasher.verifyPasscode("0000", hash))
    }

    @Test
    fun verifyPasscode_legacyFormat_stillVerifies() {
        val legacy = legacyHash("1234")
        assertTrue(PasscodeHasher.verifyPasscode("1234", legacy))
        assertFalse(PasscodeHasher.verifyPasscode("4321", legacy))
    }

    @Test
    fun verifyPasscode_prefixedAndLegacyHashesAreDistinguishable() {
        val prefixed = PasscodeHasher.hashPasscode("1234", iterations = 1_000)
        val legacy = legacyHash("1234")
        assertFalse(prefixed.startsWith(":"))
        assertTrue(prefixed.startsWith(PasscodeHasher.FORMAT_PREFIX))
        // Legacy format is exactly "hex salt : hex sha256" — no prefix
        assertFalse(legacy.startsWith(PasscodeHasher.FORMAT_PREFIX))
        assertEquals(2, legacy.split(":").size)
    }

    @Test
    fun verifyPasscode_malformedHash_returnsFalse() {
        assertFalse(PasscodeHasher.verifyPasscode("1234", ""))
        assertFalse(PasscodeHasher.verifyPasscode("1234", "invalid_hash_string"))
        assertFalse(PasscodeHasher.verifyPasscode("1234", "a:b:c"))
        assertFalse(
            PasscodeHasher.verifyPasscode(
                "1234",
                listOf(PasscodeHasher.FORMAT_PREFIX.trimEnd('$'), "1000", "!!!!not-base64!!!!", "zzzz")
                    .joinToString(fieldSeparator)
            )
        )
        assertFalse(
            PasscodeHasher.verifyPasscode(
                "1234",
                listOf(PasscodeHasher.FORMAT_PREFIX.trimEnd('$'), "notanumber", "AAA", "AAA")
                    .joinToString(fieldSeparator)
            )
        )
        assertFalse(
            PasscodeHasher.verifyPasscode(
                "1234",
                listOf(PasscodeHasher.FORMAT_PREFIX.trimEnd('$'), "1000", "AAA", "AAA")
                    .joinToString(fieldSeparator)
            ) // decodes but does not match
        )
        assertFalse(
            PasscodeHasher.verifyPasscode(
                "1234",
                listOf(PasscodeHasher.FORMAT_PREFIX.trimEnd('$'), (PasscodeHasher.MAX_ITERATIONS + 1).toString(), "AAA=", "AAA=")
                    .joinToString(fieldSeparator)
            )
        )
    }

    @Test
    fun verifyPasscode_neverThrows() {
        // Fail-closed: any weird stored hash must yield false, never an exception
        assertFalse(PasscodeHasher.verifyPasscode("1234", "}:${'$'}${'$'}${'$'}"))
        assertFalse(PasscodeHasher.verifyPasscode("1234", listOf("", "", "").joinToString(fieldSeparator)))
        assertFalse(PasscodeHasher.verifyPasscode("1234", "saltWithoutHash:"))
    }

    private fun legacyHash(passcode: String, saltHex: String = "0123456789abcdef0123456789abcdef"): String {
        val digest = MessageDigest
            .getInstance("SHA-256")
            .digest((saltHex + passcode).toByteArray(Charsets.UTF_8))
        val hashHex = digest.joinToString("") { "%02x".format(it) }
        return "$saltHex:$hashHex"
    }
}
