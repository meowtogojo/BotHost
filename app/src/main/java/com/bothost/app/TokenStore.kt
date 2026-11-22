package com.bothost.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object TokenStore {
    private const val PREFS = "bothost_secure_tokens"
    private const val KEY_ALIAS = "BotHostTokenEncryptionKey"

    private fun getKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)

        val existing = keyStore.getKey(KEY_ALIAS, null)
        if (existing is SecretKey) return existing

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )

        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or
                    KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(
                    KeyProperties.ENCRYPTION_PADDING_NONE
                )
                .setRandomizedEncryptionRequired(true)
                .build()
        )

        return generator.generateKey()
    }

    fun saveToken(context: Context, botId: String, token: String) {
        require(token.isNotBlank()) { "Token cannot be empty" }

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getKey())

        val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        val combined = cipher.iv + encrypted

        val encoded = Base64.encodeToString(
            combined,
            Base64.NO_WRAP
        )

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(botId, encoded)
            .apply()
    }

    fun getToken(context: Context, botId: String): String? {
        val encoded = context.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        ).getString(botId, null) ?: return null

        val combined = Base64.decode(encoded, Base64.NO_WRAP)
        require(combined.size > 12) { "Stored token data is invalid" }

        val iv = combined.copyOfRange(0, 12)
        val encrypted = combined.copyOfRange(12, combined.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            getKey(),
            GCMParameterSpec(128, iv)
        )

        return String(
            cipher.doFinal(encrypted),
            Charsets.UTF_8
        )
    }

    fun deleteToken(context: Context, botId: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(botId)
            .apply()
    }
}
