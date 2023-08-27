/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.ext

import android.view.Window
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

private val Window.windowInsetsController
    get() = WindowInsetsControllerCompat(this, decorView)

var Window.isAppearanceLightStatusBars
    get() = windowInsetsController.isAppearanceLightStatusBars
    set(value) {
        windowInsetsController.isAppearanceLightStatusBars = value
    }

fun Window.resetStatusBarAppearance() {
    windowInsetsController.isAppearanceLightStatusBars =
        context.resources.configuration.isNightMode != true
}

fun Window.setBarsVisibility(
    systemBars: Boolean? = null,
    statusBars: Boolean? = null,
    navigationBars: Boolean? = null,
) {
    // Configure the behavior of the hidden bars
    windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT

    val systemBarsType = WindowInsetsCompat.Type.systemBars()
    val statusBarsType = WindowInsetsCompat.Type.statusBars()
    val navigationBarsType = WindowInsetsCompat.Type.navigationBars()

    // Set the system bars visibility
    systemBars?.let {
        when (it) {
            true -> windowInsetsController.show(systemBarsType)
            false -> windowInsetsController.hide(systemBarsType)
        }
    }

    // Set the status bars visibility
    statusBars?.let {
        when (it) {
            true -> windowInsetsController.show(statusBarsType)
            false -> windowInsetsController.hide(statusBarsType)
        }
    }

    // Set the navigation bars visibility
    navigationBars?.let {
        when (it) {
            true -> windowInsetsController.show(navigationBarsType)
            false -> windowInsetsController.hide(navigationBarsType)
        }
    }
}
