/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.flow

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.os.Bundle
import android.provider.MediaStore
import androidx.core.os.bundleOf
import kotlinx.coroutines.flow.Flow
import org.lineageos.glimpse.ext.mapEachRow
import org.lineageos.glimpse.ext.queryFlow
import org.lineageos.glimpse.models.Media
import org.lineageos.glimpse.query.*
import org.lineageos.glimpse.utils.MediaStoreBuckets

class MediaFlow(private val context: Context, private val bucketId: Int) : QueryFlow<Media>() {
    override fun flowCursor(): Flow<Cursor?> {
        val uri = MediaQuery.MediaStoreFileUri
        val projection = MediaQuery.MediaProjection
        val imageOrVideo =
            (MediaStore.Files.FileColumns.MEDIA_TYPE eq MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE) or
                    (MediaStore.Files.FileColumns.MEDIA_TYPE eq MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO)
        val albumFilter = when (bucketId) {
            MediaStoreBuckets.MEDIA_STORE_BUCKET_FAVORITES.id -> MediaStore.Files.FileColumns.IS_FAVORITE eq 1

            MediaStoreBuckets.MEDIA_STORE_BUCKET_TRASH.id ->
                MediaStore.Files.FileColumns.IS_TRASHED eq 1

            MediaStoreBuckets.MEDIA_STORE_BUCKET_REELS.id -> null

            else -> MediaStore.Files.FileColumns.BUCKET_ID eq Query.ARG
        }
        val selection = albumFilter?.let { imageOrVideo and it } ?: imageOrVideo
        val selectionArgs = bucketId.takeIf {
            MediaStoreBuckets.values().none { bucket -> it == bucket.id }
        }?.let { arrayOf(it.toString()) }
        val sortOrder = MediaStore.Files.FileColumns.DATE_ADDED + " DESC"
        val queryArgs = Bundle().apply {
            putAll(
                bundleOf(
                    ContentResolver.QUERY_ARG_SQL_SELECTION to selection.build(),
                    ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS to selectionArgs,
                    ContentResolver.QUERY_ARG_SQL_SORT_ORDER to sortOrder,
                )
            )

            // Exclude trashed media unless we want data for the trashed album
            putInt(
                MediaStore.QUERY_ARG_MATCH_TRASHED, when (bucketId) {
                    MediaStoreBuckets.MEDIA_STORE_BUCKET_TRASH.id -> MediaStore.MATCH_ONLY

                    else -> MediaStore.MATCH_EXCLUDE
                }
            )
        }

        return context.contentResolver.queryFlow(
            uri,
            projection,
            queryArgs,
            null,
        )
    }

    override fun flowData() = flowCursor().mapEachRow {
        val idIndex = it.getColumnIndex(MediaStore.Files.FileColumns._ID)
        val bucketIdIndex = it.getColumnIndex(MediaStore.MediaColumns.BUCKET_ID)
        val displayNameIndex = it.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
        val isFavoriteIndex = it.getColumnIndex(MediaStore.Files.FileColumns.IS_FAVORITE)
        val isTrashedIndex = it.getColumnIndex(MediaStore.Files.FileColumns.IS_TRASHED)
        val mediaTypeIndex = it.getColumnIndex(MediaStore.Files.FileColumns.MEDIA_TYPE)
        val mimeTypeIndex = it.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE)
        val dateAddedIndex = it.getColumnIndex(MediaStore.Files.FileColumns.DATE_ADDED)
        val dateModifiedIndex = it.getColumnIndex(MediaStore.Files.FileColumns.DATE_MODIFIED)
        val widthIndex = it.getColumnIndex(MediaStore.Files.FileColumns.WIDTH)
        val heightIndex = it.getColumnIndex(MediaStore.Files.FileColumns.HEIGHT)
        val orientationIndex = it.getColumnIndex(MediaStore.Files.FileColumns.ORIENTATION)

        val id = it.getLong(idIndex)
        val bucketId = it.getInt(bucketIdIndex)
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
            width,
            height,
            orientation,
        )
    }
}
