package com.ninerouter.android.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Encrypted credential store backed by the Android Keystore.
 *
 * Used for the secrets the embedded 9Router instance needs (JWT secret, initial
 * admin password, endpoint API key, API_KEY_SECRET, MACHINE_ID_SALT). Values are
 * AES-256 encrypted with a Keystore-wrapped master key; keys and values are both
 * encrypted (AES256_SIV + AES256_GCM). Nothing is logged or written to git.
 */
class SecureStorage private constructor(private val prefs: SharedPreferences) {

    companion object {
        private const val FILE = "secure_prefs"

        @Volatile
        private var instance: SecureStorage? = null

        fun get(context: Context): SecureStorage =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        private fun build(context: Context): SecureStorage {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val prefs = EncryptedSharedPreferences.create(
                context,
                FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
            return SecureStorage(prefs)
        }
    }

    // Keys for secrets generated at runtime
    private val kEndpointApiKey = "secret_endpoint_api_key"
    private val kJwtSecret = "secret_jwt"
    private val kInitialPassword = "secret_initial_password"
    private val kApiKeySecret = "secret_api_key_secret"
    private val kMachineIdSalt = "secret_machine_id_salt"

    /** Endpoint API key shown to the user and used by local clients. */
    fun endpointApiKey(): String =
        prefs.getString(kEndpointApiKey, null) ?: SecretGenerator.randomEndpointApiKey()
            .also { prefs.edit().putString(kEndpointApiKey, it).apply() }

    fun jwtSecret(): String =
        prefs.getString(kJwtSecret, null) ?: SecretGenerator.randomHex(32)
            .also { prefs.edit().putString(kJwtSecret, it).apply() }

    fun initialPassword(): String =
        prefs.getString(kInitialPassword, null) ?: SecretGenerator.randomHex(16)
            .also { prefs.edit().putString(kInitialPassword, it).apply() }

    fun apiKeySecret(): String =
        prefs.getString(kApiKeySecret, null) ?: SecretGenerator.randomHex(32)
            .also { prefs.edit().putString(kApiKeySecret, it).apply() }

    fun machineIdSalt(): String =
        prefs.getString(kMachineIdSalt, null) ?: SecretGenerator.randomSalt()
            .also { prefs.edit().putString(kMachineIdSalt, it).apply() }

    /** Write a fresh set of secrets for a re-initialized instance. */
    fun rotateSecrets() {
        val edit = prefs.edit()
        edit.putString(kEndpointApiKey, SecretGenerator.randomEndpointApiKey())
        edit.putString(kJwtSecret, SecretGenerator.randomHex(32))
        edit.putString(kInitialPassword, SecretGenerator.randomHex(16))
        edit.putString(kApiKeySecret, SecretGenerator.randomHex(32))
        edit.putString(kMachineIdSalt, SecretGenerator.randomSalt())
        edit.apply()
        Log.d("SecureStorage", "Rotated embedded instance secrets (values not logged)")
    }

    /** Clear all stored credentials. Used by "Reinitialize Local Instance". */
    fun clear() {
        prefs.edit().clear().apply()
    }
}
