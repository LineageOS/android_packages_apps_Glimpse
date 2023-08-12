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
import org.lineageos.glimpse.R
import org.lineageos.glimpse.ext.queryFlow
import org.lineageos.glimpse.models.Album
import org.lineageos.glimpse.models.Media
import org.lineageos.glimpse.query.*
import org.lineageos.glimpse.utils.MediaStoreBuckets

class AlbumsFlow(private val context: Context) : QueryFlow<Album>() {
    override fun flowCursor(): Flow<Cursor?> {
        val uri = MediaQuery.MediaStoreFileUri
        val projection = MediaQuery.AlbumsProjection
        val imageOrVideo =
            (MediaStore.Files.FileColumns.MEDIA_TYPE eq MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE) or
                    (MediaStore.Files.FileColumns.MEDIA_TYPE eq MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO)
        val sortOrder = MediaStore.Files.FileColumns.DATE_ADDED + " DESC"
        val queryArgs = Bundle().apply {
            putAll(
                bundleOf(
                    ContentResolver.QUERY_ARG_SQL_SELECTION to imageOrVideo.build(),
                    ContentResolver.QUERY_ARG_SQL_SORT_ORDER to sortOrder,
                )
            )
            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE)
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
                val orientationIndex =
                    it.getColumnIndex(MediaStore.Files.FileColumns.ORIENTATION)
                val bucketDisplayNameIndex =
                    it.getColumnIndex(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)

                if (!it.moveToFirst()) {
                    return@use
                }

                while (!it.isAfterLast) {
                    val bucketId = it.getInt(bucketIdIndex)

                    val bucketIds = listOfNotNull(
                        when (it.getInt(isTrashedIndex)) {
                            1 -> MediaStoreBuckets.MEDIA_STORE_BUCKET_TRASH.id
                            else -> bucketId
                        },
                        MediaStoreBuckets.MEDIA_STORE_BUCKET_FAVORITES.id.takeIf { _ ->
                            it.getInt(isFavoriteIndex) == 1
                        },
                    )

                    for (displayedBucketId in bucketIds) {
                        this[displayedBucketId]?.also { album ->
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
                            val orientation = it.getInt(orientationIndex)
                            val bucketDisplayName = it.getString(bucketDisplayNameIndex)

                            this[displayedBucketId] = Album(
                                displayedBucketId,
                                when (displayedBucketId) {
                                    MediaStoreBuckets.MEDIA_STORE_BUCKET_FAVORITES.id -> context.getString(
                                        R.string.album_favorites
                                    )

                                    MediaStoreBuckets.MEDIA_STORE_BUCKET_TRASH.id -> context.getString(
                                        R.string.album_trash
                                    )

                                    else -> bucketDisplayName ?: Build.MODEL
                                },
                                Media.fromMediaStore(
                                    id,
                                    bucketId,
                                    displayName,
                                    isFavorite,
                                    isTrashed,
                                    mediaType,
                                    mimeType,
                                    dateAdded,
                                    dateModified,
                                    orientation,
                                )
                            ).apply { size += 1 }
                        }
                    }
                    it.moveToNext()
                }
            }
        }.values.toList()
    }
}
