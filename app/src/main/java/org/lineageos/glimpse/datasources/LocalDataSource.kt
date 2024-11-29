/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.datasources

import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.os.bundleOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.mapLatest
import org.lineageos.glimpse.ext.mapEachRow
import org.lineageos.glimpse.ext.queryFlow
import org.lineageos.glimpse.models.Album
import org.lineageos.glimpse.models.ColumnIndexCache
import org.lineageos.glimpse.models.Media
import org.lineageos.glimpse.models.MediaType
import org.lineageos.glimpse.models.RequestStatus
import org.lineageos.glimpse.models.Thumbnail
import org.lineageos.glimpse.query.Query
import org.lineageos.glimpse.query.and
import org.lineageos.glimpse.query.eq
import org.lineageos.glimpse.query.or
import org.lineageos.glimpse.query.query
import org.lineageos.glimpse.utils.MimeUtils
import java.util.Date

/**
 * [MediaStore.Files] backed data source.
 *
 * @param contentResolver The [ContentResolver]
 * @param volumeName The volume name
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LocalDataSource(
    private val contentResolver: ContentResolver,
    private val volumeName: String,
) : MediaDataSource {
    private val filesUri = MediaStore.Files.getContentUri(volumeName)
    private val imagesUri = MediaStore.Images.Media.getContentUri(volumeName)
    private val videosUri = MediaStore.Video.Media.getContentUri(volumeName)

    private val albumsUri = filesUri.buildUpon()
        .appendPath(ALBUMS_PATH)
        .build()

    private val mapAlbum = { columnIndexCache: ColumnIndexCache ->
        val id = columnIndexCache.getLong(MediaStore.Files.FileColumns._ID)
        val bucketId = columnIndexCache.getLong(MediaStore.Files.FileColumns.BUCKET_ID)
        val bucketDisplayName = columnIndexCache.getStringOrNull(
            MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME
        )
        val count = columnIndexCache.getInt(ID_SQL_COUNT)

        val uri = ContentUris.withAppendedId(albumsUri, bucketId)
        val thumbnailUri = ContentUris.withAppendedId(filesUri, id)

        Album(
            uri,
            bucketDisplayName ?: Build.MODEL,
            Thumbnail(uri = thumbnailUri),
            mediaCount = count,
        )
    }

    private val mapMedia = { columnIndexCache: ColumnIndexCache ->
        val id = columnIndexCache.getLong(MediaStore.Files.FileColumns._ID)
        val bucketId = columnIndexCache.getLong(MediaStore.Files.FileColumns.BUCKET_ID)
        val bucketDisplayName = columnIndexCache.getString(
            MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME
        )
        val displayName = columnIndexCache.getString(MediaStore.Files.FileColumns.DISPLAY_NAME)
        val isFavorite = columnIndexCache.getBoolean(MediaStore.Files.FileColumns.IS_FAVORITE)
        val isTrashed = columnIndexCache.getBoolean(MediaStore.Files.FileColumns.IS_TRASHED)
        val mediaType = columnIndexCache.getInt(MediaStore.Files.FileColumns.MEDIA_TYPE)
        val mimeType = columnIndexCache.getString(MediaStore.Files.FileColumns.MIME_TYPE)
        val dateAdded = columnIndexCache.getLong(MediaStore.Files.FileColumns.DATE_ADDED)
        val dateModified = columnIndexCache.getLong(MediaStore.Files.FileColumns.DATE_MODIFIED)
        val width = columnIndexCache.getInt(MediaStore.Files.FileColumns.WIDTH)
        val height = columnIndexCache.getInt(MediaStore.Files.FileColumns.HEIGHT)
        val orientation = columnIndexCache.getInt(MediaStore.Files.FileColumns.ORIENTATION)

        val typedMediaType = when (mediaType) {
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE -> MediaType.IMAGE
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> MediaType.VIDEO
            else -> throw Exception("Unknown media type $mediaType")
        }

        val uri = when (typedMediaType) {
            MediaType.IMAGE -> ContentUris.withAppendedId(imagesUri, id)
            MediaType.VIDEO -> ContentUris.withAppendedId(videosUri, id)
            else -> throw Exception("Invalid media type $typedMediaType")
        }
        val albumUri = ContentUris.withAppendedId(albumsUri, bucketId)

        Media(
            uri,
            typedMediaType,
            mimeType,
            albumUri,
            bucketDisplayName,
            displayName,
            isFavorite,
            isTrashed,
            Date(dateAdded * 1000),
            Date(dateModified * 1000),
            width,
            height,
            orientation,
        )
    }

    override fun isMediaItemCompatible(mediaItemUri: Uri) = listOf(
        filesUri,
        imagesUri,
        videosUri,
    ).any {
        mediaItemUri.toString().startsWith(it.toString())
    }

    override suspend fun mediaTypeOf(mediaItemUri: Uri) = with(mediaItemUri.toString()) {
        when {
            startsWith(albumsUri.toString()) -> MediaType.ALBUM
            startsWith(filesUri.toString()) -> contentResolver.getType(mediaItemUri)?.let {
                MimeUtils.mimeTypeToMediaType(it)
            }

            startsWith(imagesUri.toString()) -> MediaType.IMAGE
            startsWith(videosUri.toString()) -> MediaType.VIDEO
            else -> null
        }?.let {
            RequestStatus.Success<_, MediaError>(it)
        } ?: RequestStatus.Error(MediaError.NOT_FOUND)
    }

    override fun reels(mediaType: MediaType?, mimeType: String?) = contentResolver.queryFlow(
        filesUri,
        mediaProjection,
        bundleOf(
            ContentResolver.QUERY_ARG_SQL_SELECTION to query {
                val mediaTypeSelection = mediaType.filterQuery
                val mimeTypeSelection = MediaStore.Files.FileColumns.MIME_TYPE eq Query.ARG

                mimeType?.let {
                    mediaTypeSelection and mimeTypeSelection
                } ?: mediaTypeSelection
            },
            ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS to listOfNotNull(
                mimeType,
            ).toTypedArray(),
            ContentResolver.QUERY_ARG_SORT_COLUMNS to arrayOf(
                "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC",
            ),
        )
    ).mapEachRow(mapMedia).mapLatest {
        RequestStatus.Success<_, MediaError>(it)
    }

    override fun favorites() = contentResolver.queryFlow(
        filesUri,
        mediaProjection,
        bundleOf(
            ContentResolver.QUERY_ARG_SQL_SELECTION to query {
                isImageOrVideo and isFavorite
            },
            ContentResolver.QUERY_ARG_SORT_COLUMNS to arrayOf(
                "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC",
            ),
            MediaStore.QUERY_ARG_MATCH_FAVORITE to MediaStore.MATCH_ONLY,
        )
    ).mapEachRow(mapMedia).mapLatest {
        RequestStatus.Success<_, MediaError>(it)
    }

    override fun trash() = contentResolver.queryFlow(
        filesUri,
        mediaProjection,
        bundleOf(
            ContentResolver.QUERY_ARG_SQL_SELECTION to query {
                isImageOrVideo and isTrashed
            },
            ContentResolver.QUERY_ARG_SORT_COLUMNS to arrayOf(
                "${MediaStore.Files.FileColumns.DATE_EXPIRES} DESC",
            ),
            MediaStore.QUERY_ARG_MATCH_TRASHED to MediaStore.MATCH_ONLY,
        )
    ).mapEachRow(mapMedia).mapLatest {
        RequestStatus.Success<_, MediaError>(it)
    }

    override fun albums(mediaType: MediaType?, mimeType: String?) = contentResolver.queryFlow(
        filesUri,
        albumProjection,
        bundleOf(
            ContentResolver.QUERY_ARG_SQL_SELECTION to query {
                val mediaTypeSelection = mediaType.filterQuery
                val mimeTypeSelection = MediaStore.Files.FileColumns.MIME_TYPE eq Query.ARG

                mimeType?.let {
                    mediaTypeSelection and mimeTypeSelection
                } ?: mediaTypeSelection
            },
            ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS to listOfNotNull(
                mimeType,
            ).toTypedArray(),
            ContentResolver.QUERY_ARG_GROUP_COLUMNS to arrayOf(
                MediaStore.Files.FileColumns.BUCKET_ID,
            ),
            ContentResolver.QUERY_ARG_SORT_COLUMNS to arrayOf(
                "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC",
            ),
        )
    ).mapEachRow(mapAlbum).mapLatest {
        RequestStatus.Success<_, MediaError>(it)
    }

    override fun album(albumUri: Uri) = contentResolver.queryFlow(
        filesUri,
        mediaProjection,
        bundleOf(
            ContentResolver.QUERY_ARG_SQL_SELECTION to query {
                (MediaStore.Files.FileColumns.BUCKET_ID eq Query.ARG) and isImageOrVideo
            },
            ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS to arrayOf(
                albumUri.lastPathSegment!!,
            ),
            ContentResolver.QUERY_ARG_SORT_COLUMNS to arrayOf(
                "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC",
            ),
        )
    ).mapEachRow(mapMedia).mapLatest {
        it.firstOrNull()?.let { media ->
            val album = Album(
                media.albumUri,
                media.albumName,
                Thumbnail(uri = media.uri),
                it.size,
            )

            RequestStatus.Success<_, MediaError>(album to it)
        } ?: RequestStatus.Error(MediaError.NOT_FOUND)
    }

    override fun media(mediaUri: Uri) = contentResolver.queryFlow(
        filesUri,
        mediaProjection,
        bundleOf(
            ContentResolver.QUERY_ARG_SQL_SELECTION to query {
                (MediaStore.Files.FileColumns._ID eq Query.ARG) and isImageOrVideo
            },
            ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS to arrayOf(
                mediaUri.lastPathSegment!!,
            ),
            ContentResolver.QUERY_ARG_LIMIT to 1,
        ),
    ).mapEachRow(mapMedia).mapLatest {
        it.firstOrNull()?.let { media ->
            RequestStatus.Success<_, MediaError>(media)
        } ?: RequestStatus.Error(MediaError.NOT_FOUND)
    }

    companion object {
        private const val ALBUMS_PATH = "albums"

        private const val ID_SQL_COUNT = "COUNT(${MediaStore.Files.FileColumns._ID})"
        private const val MAX_DATE_MODIFIED = "MAX(${MediaStore.Files.FileColumns.DATE_MODIFIED})"

        private val albumProjection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.BUCKET_ID,
            MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME,
            ID_SQL_COUNT,

            // GIANT HACK TO GET SQLITE TO ORDER BY DATE_MODIFIED
            MAX_DATE_MODIFIED,
        )

        private val mediaProjection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.BUCKET_ID,
            MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME,
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

        private val isImage =
            MediaStore.Files.FileColumns.MEDIA_TYPE eq
                    MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString()

        private val isVideo = MediaStore.Files.FileColumns.MEDIA_TYPE eq
                MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()

        private val isImageOrVideo = isImage or isVideo

        private val isFavorite = MediaStore.Files.FileColumns.IS_FAVORITE eq "1"

        private val isTrashed = MediaStore.Files.FileColumns.IS_TRASHED eq "1"

        private val MediaType?.filterQuery: Query
            get() = when (this) {
                MediaType.IMAGE -> isImage
                MediaType.VIDEO -> isVideo
                null -> isImageOrVideo
                else -> throw Exception("Invalid media type $this")
            }
    }
}
