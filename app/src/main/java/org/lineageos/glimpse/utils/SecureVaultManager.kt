/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.utils

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.lineageos.glimpse.models.Media
import java.io.File
import java.io.FileOutputStream

class SecureVaultManager(private val context: Context) {

    // This directory is strictly private to the app.
    private val vaultDirectory: File
        get() {
            val dir = File(context.filesDir, "secure_vault")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }

    /**
     * Copies the media from public MediaStore into the hidden internal storage.
     * Note: We still need to ask permission to delete the original public file after this!
     */
    suspend fun copyToVault(media: Media): Boolean = withContext(Dispatchers.IO) {
        try {
            val fileName = media.displayName ?: "${System.currentTimeMillis()}"
            val destinationFile = File(vaultDirectory, fileName)

            context.contentResolver.openInputStream(media.uri)?.use { input ->
                FileOutputStream(destinationFile).use { output ->
                    input.copyTo(output)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Retrieves all files currently locked in the vault.
     */
    suspend fun getVaultFiles(): List<File> = withContext(Dispatchers.IO) {
        vaultDirectory.listFiles()?.toList() ?: emptyList()
    }
}
