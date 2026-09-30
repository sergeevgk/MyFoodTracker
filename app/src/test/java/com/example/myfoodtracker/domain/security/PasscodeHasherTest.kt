package com.example.myfoodtracker.domain.security

import org.junit.Assert.*
import org.junit.Test

class PasscodeHasherTest {

    @Test
    fun hashPasscode_producesValidSaltedHashFormat() {
        val hash = PasscodeHasher.hashPasscode("1234")
        val parts = hash.split(":")
        assertEquals(2, parts.size)
        assertEquals(32, parts[0].length) // 16 bytes = 32 hex chars
        assertEquals(64, parts[1].length) // 32 bytes SHA-256 = 64 hex chars
    }

    @Test
    fun hashPasscode_differentSaltsProduceDifferentHashes() {
        val hash1 = PasscodeHasher.hashPasscode("1234")
        val hash2 = PasscodeHasher.hashPasscode("1234")
        assertNotEquals(hash1, hash2)
    }

    @Test
    fun verifyPasscode_correctPasscode_returnsTrue() {
        val hash = PasscodeHasher.hashPasscode("5678")
        assertTrue(PasscodeHasher.verifyPasscode("5678", hash))
    }

    @Test
    fun verifyPasscode_wrongPasscode_returnsFalse() {
        val hash = PasscodeHasher.hashPasscode("5678")
        assertFalse(PasscodeHasher.verifyPasscode("0000", hash))
    }

    @Test
    fun verifyPasscode_malformedHash_returnsFalse() {
        assertFalse(PasscodeHasher.verifyPasscode("1234", "invalid_hash_string"))
        assertFalse(PasscodeHasher.verifyPasscode("1234", ""))
    }
}
