/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.flow

import android.content.ContentUris
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
import org.lineageos.glimpse.R
import org.lineageos.glimpse.models.Album
import org.lineageos.glimpse.query.*
import org.lineageos.glimpse.utils.MediaStoreBuckets
import org.lineageos.glimpse.utils.MediaStoreRequests

class AlbumsFlow(private val context: Context) {
    fun flow() = callbackFlow {
        val projection = MediaQuery.AlbumsProjection
        val imageOrVideo =
            (MediaStore.Files.FileColumns.MEDIA_TYPE eq MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE) or
                    (MediaStore.Files.FileColumns.MEDIA_TYPE eq MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO)
        val sortOrder = MediaStore.Files.FileColumns.DATE_ADDED + " DESC"
        val loader = GlimpseCursorLoader(
            context,
            MediaStore.Files.getContentUri("external"),
            projection,
            imageOrVideo.build(),
            null,
            sortOrder,
            bundleOf().apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE)
                }
            })

        val onLoadCompleteListener = OnLoadCompleteListener<Cursor> { _, data: Cursor? ->
            if (!isActive) return@OnLoadCompleteListener
            launch(Dispatchers.IO) {
                val albums = mutableMapOf<Int, Album>().apply {
                    data?.let {
                        val idIndex = it.getColumnIndex(MediaStore.Files.FileColumns._ID)
                        val isFavoriteIndex =
                            it.getColumnIndex(MediaStore.Files.FileColumns.IS_FAVORITE)
                        val isTrashedIndex =
                            it.getColumnIndex(MediaStore.Files.FileColumns.IS_TRASHED)
                        val mediaTypeIndex =
                            it.getColumnIndex(MediaStore.Files.FileColumns.MEDIA_TYPE)
                        val bucketIdIndex =
                            it.getColumnIndex(MediaStore.Files.FileColumns.BUCKET_ID)
                        val bucketDisplayNameIndex =
                            it.getColumnIndex(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)

                        it.moveToFirst()
                        while (!it.isAfterLast) {
                            val contentUri = when (it.getInt(mediaTypeIndex)) {
                                MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE ->
                                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI

                                MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO ->
                                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI

                                else -> continue
                            }

                            val bucketIds = listOfNotNull(
                                when (it.getInt(isTrashedIndex)) {
                                    1 -> MediaStoreBuckets.MEDIA_STORE_BUCKET_TRASH.id
                                    else -> it.getInt(bucketIdIndex)
                                },
                                MediaStoreBuckets.MEDIA_STORE_BUCKET_FAVORITES.id.takeIf { _ ->
                                    it.getInt(isFavoriteIndex) == 1
                                },
                            )

                            for (bucketId in bucketIds) {
                                this[bucketId]?.also { album ->
                                    album.size += 1
                                } ?: run {
                                    this[bucketId] = Album(
                                        bucketId,
                                        when (bucketId) {
                                            MediaStoreBuckets.MEDIA_STORE_BUCKET_FAVORITES.id ->
                                                context.getString(R.string.album_favorites)

                                            MediaStoreBuckets.MEDIA_STORE_BUCKET_TRASH.id ->
                                                context.getString(R.string.album_trash)

                                            else ->
                                                it.getString(bucketDisplayNameIndex) ?: Build.MODEL
                                        },
                                        ContentUris.withAppendedId(contentUri, it.getLong(idIndex)),
                                    ).apply { size += 1 }
                                }
                            }
                            it.moveToNext()
                        }
                    }
                }.values.toList()

                send(albums)
            }
        }

        loader.registerListener(
            MediaStoreRequests.MEDIA_STORE_ALBUMS_LOADER_ID.ordinal,
            onLoadCompleteListener
        )
        launch(Dispatchers.IO) {
            loader.startLoading()
        }

        awaitClose { loader.stopLoading() }
    }
}
