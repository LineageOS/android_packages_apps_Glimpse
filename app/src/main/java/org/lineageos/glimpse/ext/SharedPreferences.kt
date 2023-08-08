/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.ext

import android.content.SharedPreferences
import androidx.core.content.edit

// All files access dialog dismissed
private const val MANAGE_MEDIA_PERMISSION_DIALOG_DISMISSED_KEY =
    "manage_media_permission_dialog_dismissed"
private const val MANAGE_MEDIA_PERMISSION_DIALOG_DISMISSED_DEFAULT = false
var SharedPreferences.manageMediaPermissionDialogDismissed: Boolean
    get() = getBoolean(
        MANAGE_MEDIA_PERMISSION_DIALOG_DISMISSED_KEY,
        MANAGE_MEDIA_PERMISSION_DIALOG_DISMISSED_DEFAULT
    )
    set(value) = edit {
        putBoolean(MANAGE_MEDIA_PERMISSION_DIALOG_DISMISSED_KEY, value)
    }
