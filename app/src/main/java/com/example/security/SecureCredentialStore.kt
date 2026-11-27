package com.example.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts sensitive admin-supplied third-party provider credentials using Android KeyStore AES-GCM.
 * Keys never leave the hardware-backed KeyStore and are never stored in plaintext.
 */
class SecureCredentialStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun saveProviderToken(token: String) {
        if (token.isBlank()) {
            clearProviderToken()
            return
        }
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
            val iv = cipher.iv
            val ciphertext = cipher.doFinal(token.trim().toByteArray(StandardCharsets.UTF_8))
            prefs.edit()
                .putString(KEY_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
                .putString(KEY_TOKEN, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
                .commit()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to encrypt provider token", e)
        }
    }

    fun getProviderToken(): String {
        val ivB64 = prefs.getString(KEY_IV, null) ?: return ""
        val cipherB64 = prefs.getString(KEY_TOKEN, null) ?: return ""
        return try {
            val iv = Base64.decode(ivB64, Base64.NO_WRAP)
            val encrypted = Base64.decode(cipherB64, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(encrypted), StandardCharsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt provider token", e)
            ""
        }
    }

    fun hasProviderToken(): Boolean = !prefs.getString(KEY_TOKEN, null).isNullOrBlank()

    fun maskedTokenPreview(): String {
        val token = getProviderToken()
        return if (token.length <= 8) {
            if (token.isEmpty()) "Not set" else "••••••••"
        } else {
            "${token.take(4)}••••••••${token.takeLast(4)}"
        }
    }

    fun clearProviderToken() {
        prefs.edit().remove(KEY_IV).remove(KEY_TOKEN).commit()
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private companion object {
        const val TAG = "SecureCredentialStore"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "cartoon_master_provider_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val PREFS_NAME = "cartoon_master_secure_prefs"
        const val KEY_IV = "provider_token_iv"
        const val KEY_TOKEN = "provider_token_cipher"
    }
}
