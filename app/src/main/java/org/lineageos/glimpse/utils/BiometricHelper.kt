/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.utils

import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

object BiometricHelper {

    fun authenticate(
        fragment: Fragment,
        onSuccess: () -> Unit,
        onUseCustomPin: () -> Unit,
        onError: (String) -> Unit
    ) {
        val context = fragment.requireContext()
        val executor = ContextCompat.getMainExecutor(context)

        val biometricPrompt = BiometricPrompt(fragment, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    // If they click the negative button, launch our custom PIN screen!
                    if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        onUseCustomPin()
                    } else {
                        onError(errString.toString())
                    }
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Secure Vault")
            .setSubtitle("Confirm your fingerprint")
            // ONLY allow biometrics.
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .setNegativeButtonText("Use Vault PIN")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }
}
