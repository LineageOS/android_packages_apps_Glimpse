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

// Double-tap to seek video
private const val DOUBLE_TAP_SEEK_ENABLED_KEY = "double_tap_seek_enabled"
private const val DOUBLE_TAP_SEEK_ENABLED_DEFAULT = false
var SharedPreferences.doubleTapSeekEnabled: Boolean
    get() = getBoolean(
        DOUBLE_TAP_SEEK_ENABLED_KEY,
        DOUBLE_TAP_SEEK_ENABLED_DEFAULT
    )
    set(value) = edit {
        putBoolean(DOUBLE_TAP_SEEK_ENABLED_KEY, value)
    }

// Double-tap seek time in seconds (5, 10, 15, 30)
private const val DOUBLE_TAP_SEEK_TIME_KEY = "double_tap_seek_time"
private const val DOUBLE_TAP_SEEK_TIME_DEFAULT = 10
var SharedPreferences.doubleTapSeekTime: Int
    get() = getInt(
        DOUBLE_TAP_SEEK_TIME_KEY,
        DOUBLE_TAP_SEEK_TIME_DEFAULT
    )
    set(value) = edit {
        putInt(DOUBLE_TAP_SEEK_TIME_KEY, value)
    }
