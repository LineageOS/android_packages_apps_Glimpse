/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.ext

import android.content.res.Configuration
import android.os.Build

val Configuration.isNightMode
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        isNightModeActive
    } else when (uiMode.and(Configuration.UI_MODE_NIGHT_MASK)) {
        Configuration.UI_MODE_NIGHT_UNDEFINED -> null
        Configuration.UI_MODE_NIGHT_NO -> false
        Configuration.UI_MODE_NIGHT_YES -> true
        else -> null
    }
