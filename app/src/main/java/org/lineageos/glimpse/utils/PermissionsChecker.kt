/*
 * SPDX-FileCopyrightText: 2023-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.utils

import android.app.Activity
import android.content.Context
import android.widget.Toast
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import org.lineageos.glimpse.R
import org.lineageos.glimpse.ext.permissionsFlow
import org.lineageos.glimpse.ext.permissionsGranted

/**
 * A coroutine-based class that checks main app permissions.
 */
class PermissionsChecker private constructor(
    caller: ActivityResultCaller,
    private val getContext: () -> Context,
    private val getActivity: () -> Activity,
    private val getLifecycle: () -> Lifecycle,
    private val permissions: Array<String>,
) {
    constructor(
        fragment: Fragment,
        permissions: Array<String>,
    ) : this(
        fragment,
        fragment::requireContext,
        fragment::requireActivity,
        fragment::lifecycle,
        permissions,
    )

    constructor(
        activity: AppCompatActivity,
        permissions: Array<String>,
    ) : this(
        activity,
        { activity },
        { activity },
        activity::lifecycle,
        permissions,
    )

    private val channel = Channel<Unit>(1)

    private val activityResultLauncher = caller.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val context = getContext()

        if (it.isNotEmpty()) {
            if (!context.permissionsGranted(permissions)) {
                Toast.makeText(
                    context,
                    R.string.app_permissions_toast,
                    Toast.LENGTH_SHORT
                ).show()
                getActivity().finish()
            } else {
                channel.trySend(Unit)
            }
        }
    }

    suspend fun withPermissionsGranted(unit: suspend () -> Unit) = getContext().permissionsFlow(
        getLifecycle(), permissions
    ).distinctUntilChanged().collectLatest {
        val (_, denied) = it

        if (denied.isNotEmpty()) {
            checkPermissions()
        } else {
            unit()
        }
    }

    private suspend fun checkPermissions() {
        activityResultLauncher.launch(permissions)

        return channel.receive()
    }
}
