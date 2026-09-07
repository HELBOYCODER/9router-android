package com.ninerouter.android.security

import java.security.SecureRandom
import java.util.Base64

/**
 * Cryptographically-strong random secret generation.
 *
 * Every freshly initialized instance gets its own secrets; nothing is hard-coded.
 * All values are generated with [SecureRandom] and then stored via [SecureStorage]
 * (Android Keystore-backed), never in source, git, or plaintext logs.
 */
object SecretGenerator {

    private val random = SecureRandom()

    /** 32 random bytes, hex encoded (64 hex chars). Suitable for passwords/JWT. */
    fun randomHex(nBytes: Int = 32): String {
        val bytes = ByteArray(nBytes)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /** URL-safe base64 encoded random bytes. */
    fun randomBase64Url(nBytes: Int = 32): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes(nBytes))

    /** Human-friendly endpoint API key with a "9r_" prefix. */
    fun randomEndpointApiKey(): String = "9r_" + randomHex(16)

    /**
     * Used to salt machine-id derived values in 9Router. 16 bytes is plenty.
     */
    fun randomSalt(): String = randomHex(16)

    private fun randomBytes(n: Int): ByteArray = ByteArray(n).also { random.nextBytes(it) }
}
