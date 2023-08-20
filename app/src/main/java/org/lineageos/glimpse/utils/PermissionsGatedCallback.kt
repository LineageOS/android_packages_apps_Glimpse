/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.utils

import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import org.lineageos.glimpse.R

/**
 * A class that checks main app permissions before starting the callback.
 */
class PermissionsGatedCallback(
    fragment: Fragment, private val callback: () -> Unit
) {
    private val permissionsUtils by lazy { PermissionsUtils(fragment.requireContext()) }
    private val mainPermissionsRequestLauncher = fragment.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (it.isNotEmpty()) {
            if (!permissionsUtils.mainPermissionsGranted()) {
                Toast.makeText(
                    fragment.requireContext(), R.string.app_permissions_toast, Toast.LENGTH_SHORT
                ).show()
                fragment.requireActivity().finish()
            } else {
                callback()
            }
        }
    }

    fun runAfterPermissionsCheck() {
        if (!permissionsUtils.mainPermissionsGranted()) {
            mainPermissionsRequestLauncher.launch(PermissionsUtils.mainPermissions)
        } else {
            callback()
        }
    }
}
