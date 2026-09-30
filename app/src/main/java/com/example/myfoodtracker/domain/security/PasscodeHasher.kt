package com.example.myfoodtracker.domain.security

import java.security.MessageDigest
import java.security.SecureRandom

object PasscodeHasher {

    fun hashPasscode(passcode: String): String {
        val random = SecureRandom()
        val saltBytes = ByteArray(16)
        random.nextBytes(saltBytes)
        val saltHex = saltBytes.joinToString("") { "%02x".format(it) }

        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest((saltHex + passcode).toByteArray(Charsets.UTF_8))
        val hashHex = digest.joinToString("") { "%02x".format(it) }
        return "$saltHex:$hashHex"
    }

    fun verifyPasscode(passcode: String, storedHash: String): Boolean {
        val parts = storedHash.split(":")
        if (parts.size != 2) return false
        val saltHex = parts[0]
        val expectedHashHex = parts[1]

        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest((saltHex + passcode).toByteArray(Charsets.UTF_8))
        val computedHashHex = digest.joinToString("") { "%02x".format(it) }

        return MessageDigest.isEqual(
            computedHashHex.toByteArray(Charsets.UTF_8),
            expectedHashHex.toByteArray(Charsets.UTF_8)
        )
    }
}
