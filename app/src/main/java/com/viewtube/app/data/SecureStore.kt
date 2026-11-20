package com.viewtube.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Stores secrets (sign-in tokens) encrypted with a key that never leaves the Android Keystore. */
class SecureStore(context: Context) {
    private val prefs = context.getSharedPreferences("secure_store", Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(
                ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    fun put(name: String, value: String?) {
        if (value == null) {
            prefs.edit().remove(name).apply()
            return
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val packed = cipher.iv + cipher.doFinal(value.toByteArray())
        prefs.edit().putString(name, Base64.encodeToString(packed, Base64.NO_WRAP)).apply()
    }

    fun get(name: String): String? = runCatching {
        val packed = Base64.decode(prefs.getString(name, null) ?: return null, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, packed.copyOfRange(0, 12)))
        String(cipher.doFinal(packed.copyOfRange(12, packed.size)))
    }.getOrNull()

    fun clear() {
        prefs.edit().clear().apply()
        runCatching {
            KeyStore.getInstance("AndroidKeyStore").apply {
                load(null)
                deleteEntry(ALIAS)
            }
        }
    }

    private companion object {
        const val ALIAS = "viewtube_secrets"
    }
}
