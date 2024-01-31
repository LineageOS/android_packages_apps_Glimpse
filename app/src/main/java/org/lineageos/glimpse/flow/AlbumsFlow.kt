/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.flow

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.core.os.bundleOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.lineageos.glimpse.ext.queryFlow
import org.lineageos.glimpse.models.Album
import org.lineageos.glimpse.models.MediaStoreMedia
import org.lineageos.glimpse.models.MediaType
import org.lineageos.glimpse.query.*
import org.lineageos.glimpse.utils.PickerUtils

class AlbumsFlow(
    private val context: Context,
    private val mimeType: String? = null,
) : QueryFlow<Album>() {
    override fun flowCursor(): Flow<Cursor?> {
        val uri = MediaQuery.MediaStoreFileUri
        val projection = MediaQuery.AlbumsProjection
        val imageOrVideo = PickerUtils.mediaTypeFromGenericMimeType(mimeType)?.let {
            when (it) {
                MediaType.IMAGE -> MediaQuery.Selection.image

                MediaType.VIDEO -> MediaQuery.Selection.video
            }
        } ?: MediaQuery.Selection.imageOrVideo
        val rawMimeType = mimeType?.takeIf { PickerUtils.isMimeTypeNotGeneric(it) }
        val mimeTypeQuery = rawMimeType?.let {
            MediaStore.Files.FileColumns.MIME_TYPE eq Query.ARG
        }

        // Join all the non-null queries
        val selection = listOfNotNull(
            mimeTypeQuery,
            imageOrVideo,
        ).join(Query::and)

        val selectionArgs = listOfNotNull(
            rawMimeType,
        ).toTypedArray()

        val sortOrder = MediaStore.Files.FileColumns.DATE_ADDED + " DESC"

        val queryArgs = Bundle().apply {
            putAll(
                bundleOf(
                    ContentResolver.QUERY_ARG_SQL_SELECTION to selection.build(),
                    ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS to selectionArgs,
                    ContentResolver.QUERY_ARG_SQL_SORT_ORDER to sortOrder,
                )
            )
        }

        return context.contentResolver.queryFlow(
            uri,
            projection,
            queryArgs,
        )
    }

    override fun flowData() = flowCursor().map {
        mutableMapOf<Int, Album>().apply {
            it?.use {
                val idIndex = it.getColumnIndex(MediaStore.Files.FileColumns._ID)
                val bucketIdIndex = it.getColumnIndex(MediaStore.Files.FileColumns.BUCKET_ID)
                val displayNameIndex = it.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val isFavoriteIndex =
                    it.getColumnIndex(MediaStore.Files.FileColumns.IS_FAVORITE)
                val isTrashedIndex = it.getColumnIndex(MediaStore.Files.FileColumns.IS_TRASHED)
                val mediaTypeIndex = it.getColumnIndex(MediaStore.Files.FileColumns.MEDIA_TYPE)
                val mimeTypeIndex = it.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE)
                val dateAddedIndex = it.getColumnIndex(MediaStore.Files.FileColumns.DATE_ADDED)
                val dateModifiedIndex =
                    it.getColumnIndex(MediaStore.Files.FileColumns.DATE_MODIFIED)
                val widthIndex = it.getColumnIndex(MediaStore.Files.FileColumns.WIDTH)
                val heightIndex = it.getColumnIndex(MediaStore.Files.FileColumns.HEIGHT)
                val orientationIndex =
                    it.getColumnIndex(MediaStore.Files.FileColumns.ORIENTATION)
                val bucketDisplayNameIndex =
                    it.getColumnIndex(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)

                if (!it.moveToFirst()) {
                    return@use
                }

                while (!it.isAfterLast) {
                    val bucketId = it.getInt(bucketIdIndex)

                    this[bucketId]?.also { album ->
                        album.size += 1
                    } ?: run {
                        val id = it.getLong(idIndex)
                        val displayName = it.getString(displayNameIndex)
                        val isFavorite = it.getInt(isFavoriteIndex)
                        val isTrashed = it.getInt(isTrashedIndex)
                        val mediaType = it.getInt(mediaTypeIndex)
                        val mimeType = it.getString(mimeTypeIndex)
                        val dateAdded = it.getLong(dateAddedIndex)
                        val dateModified = it.getLong(dateModifiedIndex)
                        val width = it.getInt(widthIndex)
                        val height = it.getInt(heightIndex)
                        val orientation = it.getInt(orientationIndex)
                        val bucketDisplayName = it.getString(bucketDisplayNameIndex)

                        this[bucketId] = Album(
                            bucketId,
                            bucketDisplayName ?: Build.MODEL,
                            MediaStoreMedia.fromMediaStore(
                                id,
                                bucketId,
                                displayName,
                                isFavorite,
                                isTrashed,
                                mediaType,
                                mimeType,
                                dateAdded,
                                dateModified,
                                width,
                                height,
                                orientation,
                            )
                        ).apply { size += 1 }
                    }

                    it.moveToNext()
                }
            }
        }.values.toList()
    }
}
