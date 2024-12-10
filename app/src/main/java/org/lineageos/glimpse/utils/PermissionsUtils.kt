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
import org.lineageos.glimpse.ext.manageMediaPermissionDialogDismissed

// Permission + required
typealias Permission = Pair<String, Boolean>

/**
 * App's permissions utils.
 */
object PermissionsUtils {
    fun requiredPermissionsGranted(context: Context) =
        permissionsGranted(
            context,
            permissions.filter {
                it.second
            }.toTypedArray()
        )

    private fun permissionGranted(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun permissionsGranted(context: Context, permissions: Array<Permission>) =
        permissions.all { permissionGranted(context, it.first) }

    /**
     * Permissions to run the app
     */
    val permissions = mutableListOf<Permission>().apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            add(Manifest.permission.READ_MEDIA_IMAGES to false)
            add(Manifest.permission.READ_MEDIA_VIDEO to false)
            add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED to true)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.READ_MEDIA_IMAGES to true)
            add(Manifest.permission.READ_MEDIA_VIDEO to true)
        } else {
            add(Manifest.permission.READ_EXTERNAL_STORAGE to true)
        }

        add(Manifest.permission.ACCESS_MEDIA_LOCATION to true)
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
