/*
 * SPDX-FileCopyrightText: 2023-2024 The LineageOS Project
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
import org.lineageos.glimpse.models.MediaStoreMedia
import org.lineageos.glimpse.models.MediaType
import org.lineageos.glimpse.query.*
import org.lineageos.glimpse.utils.MediaStoreBuckets
import org.lineageos.glimpse.utils.PickerUtils

class AlbumFlow(
    private val context: Context,
    private val bucketId: Int,
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
                    ContentResolver.QUERY_ARG_SQL_LIMIT to 1,
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

    override fun flowData() = flowCursor().map {
        mutableListOf<Album>().apply {
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

                val cursorSize = it.count
                val cursorNotEmpty = it.moveToFirst()

                val media = if (cursorNotEmpty) {
                    val id = it.getLong(idIndex)
                    val mediaBucketId = it.getInt(bucketIdIndex)
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

                    MediaStoreMedia.fromMediaStore(
                        id,
                        mediaBucketId,
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
                } else {
                    null
                }

                val bucketDisplayName = if (cursorNotEmpty) {
                    it.getString(bucketDisplayNameIndex)
                } else {
                    null
                }

                val album = Album(
                    bucketId,
                    when (bucketId) {
                        MediaStoreBuckets.MEDIA_STORE_BUCKET_FAVORITES.id -> context.getString(
                            R.string.album_favorites
                        )

                        MediaStoreBuckets.MEDIA_STORE_BUCKET_TRASH.id -> context.getString(
                            R.string.album_trash
                        )

                        MediaStoreBuckets.MEDIA_STORE_BUCKET_REELS.id -> context.getString(
                            R.string.album_reels
                        )

                        MediaStoreBuckets.MEDIA_STORE_BUCKET_PHOTOS.id -> context.getString(
                            R.string.album_photos
                        )

                        MediaStoreBuckets.MEDIA_STORE_BUCKET_VIDEOS.id -> context.getString(
                            R.string.album_videos
                        )

                        else -> bucketDisplayName ?: Build.MODEL
                    },
                    media,
                    cursorSize,
                )

                add(album)
            }
        }.toList()
    }
}
