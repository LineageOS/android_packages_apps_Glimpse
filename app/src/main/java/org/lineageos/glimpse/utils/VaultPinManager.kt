/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.utils

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

class VaultPinManager(context: Context) {

    private val prefs = context.getSharedPreferences("secure_vault_pin_prefs", Context.MODE_PRIVATE)
    private val keyAlias = "VaultPinHmacKey"

    init {
        // Generate a secure, hardware-backed key the first time this runs
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        if (!keyStore.containsAlias(keyAlias)) {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore")
            keyGenerator.init(
                KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_SIGN).build()
            )
            keyGenerator.generateKey()
        }
    }

    // Hashes the PIN using the hardware Keystore so it cannot be cracked offline
    private fun hashPin(pin: String): String {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        val secretKey = keyStore.getKey(keyAlias, null) as SecretKey
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(secretKey)
        val hashBytes = mac.doFinal(pin.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(hashBytes, Base64.NO_WRAP)
    }

    fun hasPinSet(): Boolean {
        return prefs.contains("vault_pin_hash")
    }

    fun savePin(pin: String) {
        prefs.edit().putString("vault_pin_hash", hashPin(pin)).apply()
    }

    fun verifyPin(pin: String): Boolean {
        val savedHash = prefs.getString("vault_pin_hash", null) ?: return false
        return savedHash == hashPin(pin)
    }
}
