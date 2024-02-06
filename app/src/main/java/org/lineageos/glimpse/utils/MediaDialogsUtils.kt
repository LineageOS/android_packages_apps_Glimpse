/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.utils

import android.content.Context
import android.view.View
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import org.lineageos.glimpse.R

object MediaDialogsUtils {
    private fun <T> openDialog(
        context: Context,
        vararg uris: T,
        onPositiveCallback: (uris: Array<out T>) -> Unit,
        @StringRes titleStringRes: Int,
        @PluralsRes confirmMessagePluralsRes: Int,
    ) {
        val count = uris.size

        MaterialAlertDialogBuilder(context)
            .setTitle(titleStringRes)
            .setMessage(
                context.resources.getQuantityString(
                    confirmMessagePluralsRes, count, count
                )
            ).setPositiveButton(android.R.string.ok) { _, _ ->
                onPositiveCallback(uris)
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                // Do nothing
            }
            .show()
    }

    private fun showResultSnackbar(
        context: Context,
        view: View,
        succeeded: Boolean,
        count: Int,
        anchorView: View? = null,
        undoActionCallback: (() -> Unit)? = null,
        @PluralsRes titleSuccessfulPluralsRes: Int,
        @PluralsRes titleUnsuccessfulPluralsRes: Int,
    ) = Snackbar.make(
        view,
        context.resources.getQuantityString(
            if (succeeded) {
                titleSuccessfulPluralsRes
            } else {
                titleUnsuccessfulPluralsRes
            },
            count, count
        ),
        Snackbar.LENGTH_LONG,
    ).apply {
        anchorView?.let {
            this.anchorView = it
        }

        undoActionCallback?.takeIf { succeeded }?.let {
            setAction(R.string.file_action_undo_action) { it() }
        }

        show()
    }.also {
        if (succeeded) {
            PermissionsUtils.showManageMediaPermissionDialogIfNeeded(context)
        }
    }

    // Move to trash

    fun showMoveToTrashResultSnackbar(
        context: Context,
        view: View,
        succeeded: Boolean,
        count: Int,
        anchorView: View? = null,
        actionCallback: (() -> Unit)? = null,
    ) = showResultSnackbar(
        context, view, succeeded, count, anchorView, actionCallback,
        titleSuccessfulPluralsRes = R.plurals.move_file_to_trash_successful,
        titleUnsuccessfulPluralsRes = R.plurals.move_file_to_trash_unsuccessful,
    )

    // Restore from trash

    fun showRestoreFromTrashResultSnackbar(
        context: Context,
        view: View,
        succeeded: Boolean,
        count: Int,
        anchorView: View? = null,
        actionCallback: (() -> Unit)? = null,
    ) = showResultSnackbar(
        context, view, succeeded, count, anchorView, actionCallback,
        titleSuccessfulPluralsRes = R.plurals.restore_file_from_trash_successful,
        titleUnsuccessfulPluralsRes = R.plurals.restore_file_from_trash_unsuccessful,
    )

    // Delete forever

    fun <T> openDeleteForeverDialog(
        context: Context,
        vararg uris: T,
        onPositiveCallback: (uris: Array<out T>) -> Unit,
    ) = openDialog(
        context, *uris, onPositiveCallback = onPositiveCallback,
        titleStringRes = R.string.file_action_delete_forever,
        confirmMessagePluralsRes = R.plurals.delete_file_forever_confirm_message,
    )

    fun showDeleteForeverResultSnackbar(
        context: Context,
        view: View,
        succeeded: Boolean,
        count: Int,
        anchorView: View? = null,
    ) = showResultSnackbar(
        context, view, succeeded, count, anchorView,
        titleSuccessfulPluralsRes = R.plurals.delete_file_forever_successful,
        titleUnsuccessfulPluralsRes = R.plurals.delete_file_forever_unsuccessful,
    )
}
