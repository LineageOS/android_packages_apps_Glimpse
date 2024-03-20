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
import lineagex.core.ext.mapEachRow
import lineagex.core.ext.queryFlow
import lineagex.core.query.Query
import lineagex.core.query.QueryFlow
import lineagex.core.query.and
import lineagex.core.query.eq
import lineagex.core.query.join
import org.lineageos.glimpse.models.MediaStoreMedia
import org.lineageos.glimpse.models.MediaType
import org.lineageos.glimpse.query.MediaQuery
import org.lineageos.glimpse.utils.MediaStoreBuckets
import org.lineageos.glimpse.utils.PickerUtils

class MediaFlow(
    private val context: Context,
    private val bucketId: Int,
    private val mimeType: String? = null,
) : QueryFlow<MediaStoreMedia> {
    init {
        assert(bucketId != MediaStoreBuckets.MEDIA_STORE_BUCKET_PLACEHOLDER.id) {
            "MEDIA_STORE_BUCKET_PLACEHOLDER found"
        }
    }

    override fun flowCursor(): Flow<Cursor?> {
        val uri = MediaQuery.MediaStoreFileUri
        val projection = MediaQuery.MediaProjection
        val imageOrVideo = PickerUtils.mediaTypeFromGenericMimeType(mimeType)?.let {
            when (it) {
                MediaType.IMAGE -> MediaQuery.Selection.image

                MediaType.VIDEO -> MediaQuery.Selection.video
            }
        } ?: when (bucketId) {
            MediaStoreBuckets.MEDIA_STORE_BUCKET_PHOTOS.id -> MediaQuery.Selection.image

            MediaStoreBuckets.MEDIA_STORE_BUCKET_VIDEOS.id -> MediaQuery.Selection.video

            else -> MediaQuery.Selection.imageOrVideo
        }
        val albumFilter = when (bucketId) {
            MediaStoreBuckets.MEDIA_STORE_BUCKET_FAVORITES.id -> MediaStore.Files.FileColumns.IS_FAVORITE eq 1

            MediaStoreBuckets.MEDIA_STORE_BUCKET_TRASH.id ->
                MediaStore.Files.FileColumns.IS_TRASHED eq 1

            MediaStoreBuckets.MEDIA_STORE_BUCKET_REELS.id,
            MediaStoreBuckets.MEDIA_STORE_BUCKET_PHOTOS.id,
            MediaStoreBuckets.MEDIA_STORE_BUCKET_VIDEOS.id -> null

            else -> MediaStore.Files.FileColumns.BUCKET_ID eq Query.ARG
        }
        val rawMimeType = mimeType?.takeIf { PickerUtils.isMimeTypeNotGeneric(it) }
        val mimeTypeQuery = rawMimeType?.let {
            MediaStore.Files.FileColumns.MIME_TYPE eq Query.ARG
        }

        // Join all the non-null queries
        val selection = listOfNotNull(
            imageOrVideo,
            albumFilter,
            mimeTypeQuery,
        ).join(Query::and)

        val selectionArgs = listOfNotNull(
            bucketId.takeIf {
                MediaStoreBuckets.values().none { bucket -> it == bucket.id }
            }?.toString(),
            rawMimeType,
        ).toTypedArray()

        val sortOrder = when (bucketId) {
            MediaStoreBuckets.MEDIA_STORE_BUCKET_TRASH.id ->
                "${MediaStore.Files.FileColumns.DATE_EXPIRES} DESC"

            else -> "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"
        }

        val queryArgs = Bundle().apply {
            putAll(
                bundleOf(
                    ContentResolver.QUERY_ARG_SQL_SELECTION to selection?.build(),
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
        )
    }

    override fun flowData() = flowCursor().mapEachRow(
        arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.BUCKET_ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.IS_FAVORITE,
            MediaStore.Files.FileColumns.IS_TRASHED,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.WIDTH,
            MediaStore.Files.FileColumns.HEIGHT,
            MediaStore.Files.FileColumns.ORIENTATION,
        )
    ) { it, indexCache ->
        var i = 0

        val id = it.getLong(indexCache[i++])
        val bucketId = it.getInt(indexCache[i++])
        val displayName = it.getString(indexCache[i++])
        val isFavorite = it.getInt(indexCache[i++])
        val isTrashed = it.getInt(indexCache[i++])
        val mediaType = it.getInt(indexCache[i++])
        val mimeType = it.getString(indexCache[i++])
        val dateAdded = it.getLong(indexCache[i++])
        val dateModified = it.getLong(indexCache[i++])
        val width = it.getInt(indexCache[i++])
        val height = it.getInt(indexCache[i++])
        val orientation = it.getInt(indexCache[i++])

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
    }
}
