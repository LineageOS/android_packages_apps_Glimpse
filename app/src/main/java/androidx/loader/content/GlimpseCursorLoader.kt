/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package androidx.loader.content

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import androidx.core.os.CancellationSignal
import androidx.core.os.OperationCanceledException
import androidx.core.os.bundleOf

/**
 * A custom [CursorLoader] that uses the new [ContentResolver.query]'s
 * queryArgs argument.
 */
class GlimpseCursorLoader(
    context: Context,
    uri: Uri,
    projection: Array<out String>?,
    selection: String?,
    selectionArgs: Array<out String>?,
    sortOrder: String?,
    private val queryArgs: Bundle = Bundle()
) : CursorLoader(context, uri, projection, selection, selectionArgs, sortOrder) {
    init {
        queryArgs.putAll(
            bundleOf(
                ContentResolver.QUERY_ARG_SQL_SELECTION to selection,
                ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS to selectionArgs,
                ContentResolver.QUERY_ARG_SQL_SORT_ORDER to sortOrder,
            )
        )
    }

    override fun onLoadInBackground(): Cursor? {
        synchronized(this) {
            if (isLoadInBackgroundCanceled) {
                throw OperationCanceledException()
            }
            mCancellationSignal = CancellationSignal()
        }

        return try {
            context.contentResolver.query(
                mUri, mProjection, queryArgs,
                mCancellationSignal.cancellationSignalObject as android.os.CancellationSignal?
            )?.also {
                try {
                    // Ensure the cursor window is filled.
                    it.count
                    it.registerContentObserver(mObserver)
                } catch (ex: RuntimeException) {
                    it.close()
                    throw ex
                }
            }
        } finally {
            synchronized(this) { mCancellationSignal = null }
        }
    }
}
