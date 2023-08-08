/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.flow

import android.content.Context
import android.database.Cursor
import android.os.Build
import android.provider.MediaStore
import androidx.core.os.bundleOf
import androidx.loader.content.GlimpseCursorLoader
import androidx.loader.content.Loader.OnLoadCompleteListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.lineageos.glimpse.models.Media
import org.lineageos.glimpse.query.*
import org.lineageos.glimpse.utils.MediaStoreBuckets
import org.lineageos.glimpse.utils.MediaStoreRequests

class MediaFlow(private val context: Context, private val bucketId: Int?) {
    fun flow() = callbackFlow {
        val projection = MediaQuery.MediaProjection
        val imageOrVideo =
            (MediaStore.Files.FileColumns.MEDIA_TYPE eq MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE) or
                    (MediaStore.Files.FileColumns.MEDIA_TYPE eq MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO)
        val albumFilter = bucketId?.let {
            when (it) {
                MediaStoreBuckets.MEDIA_STORE_BUCKET_FAVORITES.id ->
                    MediaStore.Files.FileColumns.IS_FAVORITE eq 1

                MediaStoreBuckets.MEDIA_STORE_BUCKET_TRASH.id ->
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                        MediaStore.Files.FileColumns.IS_TRASHED eq 1
                    } else {
                        null
                    }

                else -> MediaStore.Files.FileColumns.BUCKET_ID eq Query.ARG
            }
        }
        val selection = albumFilter?.let { imageOrVideo and it } ?: imageOrVideo
        val sortOrder = MediaStore.Files.FileColumns.DATE_ADDED + " DESC"
        val loader = GlimpseCursorLoader(
            context,
            MediaStore.Files.getContentUri("external"),
            projection,
            selection.build(),
            bucketId?.takeIf {
                MediaStoreBuckets.values().none { bucket -> it == bucket.id }
            }?.let { arrayOf(it.toString()) },
            sortOrder,
            bundleOf().apply {
                // Exclude trashed media unless we want data for the trashed album
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    putInt(
                        MediaStore.QUERY_ARG_MATCH_TRASHED,
                        when (bucketId) {
                            MediaStoreBuckets.MEDIA_STORE_BUCKET_TRASH.id -> MediaStore.MATCH_ONLY

                            else -> MediaStore.MATCH_EXCLUDE
                        }
                    )
                }
            })

        val onLoadCompleteListener = OnLoadCompleteListener<Cursor> { _, data: Cursor? ->
            if (!isActive) return@OnLoadCompleteListener
            launch(Dispatchers.IO) {
                val media = mutableListOf<Media>().apply {
                    data?.let {
                        val idIndex = it.getColumnIndex(MediaStore.Files.FileColumns._ID)
                        val isFavoriteIndex =
                            it.getColumnIndex(MediaStore.Files.FileColumns.IS_FAVORITE)
                        val isTrashedIndex =
                            it.getColumnIndex(MediaStore.Files.FileColumns.IS_TRASHED)
                        val mediaTypeIndex =
                            it.getColumnIndex(MediaStore.Files.FileColumns.MEDIA_TYPE)
                        val mimeTypeIndex =
                            it.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE)
                        val dateAddedIndex =
                            it.getColumnIndex(MediaStore.Files.FileColumns.DATE_ADDED)
                        val bucketIdIndex = it.getColumnIndex(MediaStore.MediaColumns.BUCKET_ID)

                        it.moveToFirst()
                        while (!it.isAfterLast) {
                            val id = it.getLong(idIndex)
                            val buckedId = it.getInt(bucketIdIndex)
                            val isFavorite = it.getInt(isFavoriteIndex)
                            val isTrashed = it.getInt(isTrashedIndex)
                            val mediaType = it.getInt(mediaTypeIndex)
                            val mimeType = it.getString(mimeTypeIndex)
                            val dateAdded = it.getLong(dateAddedIndex)

                            add(
                                Media.fromMediaStore(
                                    id,
                                    buckedId,
                                    isFavorite,
                                    isTrashed,
                                    mediaType,
                                    mimeType,
                                    dateAdded,
                                )
                            )
                            it.moveToNext()
                        }
                    }
                }.toList()

                send(media)
            }
        }

        loader.registerListener(
            MediaStoreRequests.MEDIA_STORE_MEDIA_LOADER_ID.ordinal,
            onLoadCompleteListener
        )
        launch(Dispatchers.IO) {
            loader.startLoading()
        }

        awaitClose { loader.stopLoading() }
    }
}
