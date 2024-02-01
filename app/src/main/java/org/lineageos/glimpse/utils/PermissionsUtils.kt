/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.utils

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.lineageos.glimpse.R
import org.lineageos.glimpse.ext.*

/**
 * App's permissions utils.
 */
object PermissionsUtils {
    fun mainPermissionsGranted(context: Context) = permissionsGranted(context, mainPermissions)

    private fun permissionGranted(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun permissionsGranted(context: Context, permissions: Array<String>) = permissions.all {
        permissionGranted(context, it)
    }

    /**
     * Permissions required to run the app
     */
    val mainPermissions = mutableListOf<String>().apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.READ_MEDIA_IMAGES)
            add(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        add(Manifest.permission.ACCESS_MEDIA_LOCATION)
    }.toTypedArray()

    fun showManageMediaPermissionDialogIfNeeded(context: Context) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

        if (canManageMedia(context) || sharedPreferences.manageMediaPermissionDialogDismissed) {
            return
        }

        MaterialAlertDialogBuilder(context).setTitle(R.string.manage_media_permission_title)
            .setMessage(R.string.manage_media_permission_message)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                // If the user already opted this app for media management elsewhere (e.g. Settings)
                // while the dialog was open we can skip requesting it again.
                if (canManageMedia(context)) {
                    return@setPositiveButton
                }

                context.startActivity(Intent(Settings.ACTION_REQUEST_MANAGE_MEDIA).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                })
            }.setNeutralButton(android.R.string.cancel) { _, _ ->
                sharedPreferences.manageMediaPermissionDialogDismissed = true
            }.show()
    }

    private fun canManageMedia(context: Context) =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || MediaStore.canManageMedia(context)
}
