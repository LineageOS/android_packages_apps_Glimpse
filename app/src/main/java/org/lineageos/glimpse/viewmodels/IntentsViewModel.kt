/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.viewmodels

import android.app.Application
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import okhttp3.OkHttpClient
import okhttp3.Request
import org.lineageos.glimpse.ViewActivity
import org.lineageos.glimpse.ext.applicationContext
import org.lineageos.glimpse.ext.asArray
import org.lineageos.glimpse.ext.executeAsync
import org.lineageos.glimpse.ext.getParcelable
import org.lineageos.glimpse.ext.getSerializable
import org.lineageos.glimpse.models.Album
import org.lineageos.glimpse.models.AlbumType
import org.lineageos.glimpse.models.Media
import org.lineageos.glimpse.models.MediaItem
import org.lineageos.glimpse.models.MediaType
import org.lineageos.glimpse.models.RequestStatus
import org.lineageos.glimpse.models.RequestStatus.Companion.map
import org.lineageos.glimpse.utils.MimeUtils
import java.util.Date

/**
 * A view model used by activities to handle intents.
 */
class IntentsViewModel(application: Application) : GlimpseViewModel(application) {
    private data class UriMetadata(
        val mimeType: String,
        val displayName: String?,
        val width: Int,
        val height: Int,
        val sizeBytes: Long,
    )

    sealed class ParsedIntent {
        /**
         * Open the app's home page.
         */
        class MainIntent : ParsedIntent()

        /**
         * View a content.
         *
         * @param medias The items to show
         */
        class ViewIntent(
            val medias: List<Media>,
        ) : ParsedIntent()

        /**
         * Review a content.
         *
         * @param albumRequest The [AlbumViewModel.AlbumRequest] to show
         * @param initialMedia The [Media] from which we should start
         */
        class ReviewIntent(
            val albumRequest: AlbumViewModel.AlbumRequest? = null,
            val initialMedia: Media? = null,
        ) : ParsedIntent()

        /**
         * Review content securely.
         *
         * @param medias The list of [Media] to show
         */
        class SecureReviewIntent(
            val medias: List<Media>,
        ) : ParsedIntent()

        /**
         * Pick a content.
         *
         * @param mediaType The file type to select, null to avoid filtering
         * @param mimeType The type to select, null to avoid filtering
         * @param multiple Whether multiple items can be selected
         */
        class PickIntent(
            val mediaType: MediaType? = null,
            val mimeType: String? = null,
            val multiple: Boolean = false,
        ) : ParsedIntent()

        /**
         * Pick a photo to be used as wallpaper.
         */
        class SetWallpaperIntent : ParsedIntent()

        private var handled = false

        suspend fun handle(
            consumer: suspend (parsedIntent: ParsedIntent) -> Unit,
        ) = when (handled) {
            true -> false
            false -> {
                consumer(this)
                handled = true
                true
            }
        }
    }

    private val okHttpClient = OkHttpClient.Builder()
        .build()

    private val currentIntent = MutableStateFlow<Intent?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val parsedIntent = currentIntent
        .mapLatest { currentIntent ->
            val intent = currentIntent ?: run {
                Log.i(LOG_TAG, "No intent")
                return@mapLatest null
            }

            val mediaItems = buildList {
                intent.data?.let { data ->
                    uriToContent(
                        data,
                        intent.type?.let { MimeUtils.mimeTypeToMediaType(it) }
                    )?.let {
                        add(it)
                    }
                }

                intent.clipData?.let { clipData ->
                    // Do a best effort to get a valid media type from the clip data
                    val mediaType =
                        (0 until clipData.description.mimeTypeCount).firstNotNullOfOrNull {
                            MimeUtils.mimeTypeToMediaType(clipData.description.getMimeType(it))
                        }

                    clipData.asArray().forEach { item ->
                        val itemUri = item.uri ?: return@forEach

                        // Skip the URI already handled via intent.data
                        if (itemUri == intent.data) {
                            return@forEach
                        }

                        uriToContent(itemUri, mediaType)?.let {
                            add(it)
                        }
                    }
                }
            }

            val mimeType = intent.type?.let {
                when (it) {
                    MediaStore.Images.Media.CONTENT_TYPE -> MimeUtils.MIME_TYPE_IMAGE_ANY
                    MediaStore.Video.Media.CONTENT_TYPE -> MimeUtils.MIME_TYPE_VIDEO_ANY

                    MimeUtils.MIME_TYPE_ANY -> null

                    else -> when {
                        it.startsWith("image/") || it.startsWith("video/") -> it

                        else -> null
                    }
                }
            }

            when (intent.action) {
                null,
                Intent.ACTION_MAIN -> ParsedIntent.MainIntent()

                Intent.ACTION_VIEW -> ParsedIntent.ViewIntent(mediaItems.filterIsInstance<Media>())

                MediaStore.ACTION_REVIEW -> ParsedIntent.ReviewIntent(
                    AlbumViewModel.AlbumRequest(
                        intent.extras?.getSerializable(
                            ViewActivity.EXTRA_ALBUM_TYPE, AlbumType::class
                        ),
                        intent.extras?.getParcelable(
                            ViewActivity.EXTRA_ALBUM_URI, Uri::class
                        ) ?: mediaItems.let {
                            if (it.size != 1) {
                                return@let null
                            }

                            when (val mediaItem = it.first()) {
                                is Album -> mediaItem.uri
                                is Media -> mediaItem.albumUri
                            }
                        },
                        intent.extras?.getSerializable(
                            ViewActivity.EXTRA_MEDIA_TYPE, MediaType::class
                        ),
                        intent.extras?.getString(ViewActivity.EXTRA_MIME_TYPE),
                    ),
                    mediaItems.filterIsInstance<Media>().firstOrNull(),
                )

                MediaStore.ACTION_REVIEW_SECURE -> ParsedIntent.SecureReviewIntent(
                    mediaItems.filterIsInstance<Media>(),
                )

                Intent.ACTION_GET_CONTENT,
                Intent.ACTION_PICK -> ParsedIntent.PickIntent(
                    mimeType?.let { MimeUtils.mimeTypeToMediaType(it) },
                    mimeType?.takeUnless { it.endsWith("/*") },
                    intent.extras?.getBoolean(
                        Intent.EXTRA_ALLOW_MULTIPLE, false
                    ) ?: false,
                )

                Intent.ACTION_SET_WALLPAPER -> ParsedIntent.SetWallpaperIntent()

                else -> run {
                    Log.e(LOG_TAG, "Unknown intent action ${intent.action}")
                    return@mapLatest null
                }
            }
        }
        .catch {
            Log.e(LOG_TAG, "Failed to parse intent", it)
            emit(null)
        }
        .flowOn(Dispatchers.IO)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(),
            null,
        )

    /**
     * Whether we are picking items.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val isPicking = parsedIntent
        .mapLatest { it is ParsedIntent.PickIntent }
        .flowOn(Dispatchers.IO)
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            false,
        )

    /**
     * Whether multiple items can be selected.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val allowMultipleSelection = parsedIntent
        .mapLatest {
            when (it) {
                is ParsedIntent.PickIntent -> it.multiple
                else -> true
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            true,
        )

    fun onIntent(intent: Intent?) {
        currentIntent.value = intent
    }

    /**
     * Given a URI and a pre-parsed media type, get a [MediaItem] object.
     */
    private suspend fun uriToContent(uri: Uri, mediaType: MediaType?): MediaItem<*>? = try {
        uriToContentInternal(uri, mediaType)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(LOG_TAG, "Cannot resolve content for $uri", e)
        null
    }

    private suspend fun uriToContentInternal(uri: Uri, mediaType: MediaType?): MediaItem<*>? {
        val type = mediaType ?: uriToType(uri) ?: run {
            Log.e(LOG_TAG, "Cannot get media type of $uri")
            return null
        }

        return when (type) {
            MediaType.ALBUM -> mediaRepository.album(uri).first().map { it.first }
            MediaType.IMAGE,
            MediaType.VIDEO -> mediaRepository.media(uri).first()
        }.let {
            when (it) {
                is RequestStatus.Loading -> throw Exception(
                    "Shouldn't return RequestStatus.Loading"
                )

                is RequestStatus.Success -> it.data

                is RequestStatus.Error -> {
                    when (type) {
                        MediaType.IMAGE,
                        MediaType.VIDEO -> {
                            uriToMediaStoreMedia(uri, type)?.let { media ->
                                return media
                            }

                            // Build a `Media` object with the available data
                            Log.i(
                                LOG_TAG,
                                "Cannot get media object from media provider, trying manual fallback"
                            )

                            val metadata = extractUriMetadata(uri, type) ?: return null

                            Media(
                                uri,
                                type,
                                metadata.mimeType,
                                uri,
                                albumName = null,
                                displayName = metadata.displayName,
                                isFavorite = false,
                                isTrashed = false,
                                dateAdded = Date(),
                                dateModified = Date(),
                                width = metadata.width,
                                height = metadata.height,
                                orientation = 0,
                                sizeBytes = metadata.sizeBytes,
                            )
                        }

                        else -> {
                            Log.e(LOG_TAG, "Cannot build media object for $uri")
                            null
                        }
                    }
                }
            }
        }
    }

    private suspend fun uriToMediaStoreMedia(uri: Uri, mediaType: MediaType): MediaItem<*>? {
        val path = uriToFilePath(uri) ?: return null

        val mediaStoreUri = pathToMediaStoreUri(path, mediaType) ?: run {
            Log.i(LOG_TAG, "$path is not indexed by MediaStore")
            return null
        }

        Log.i(LOG_TAG, "Resolved $uri to $mediaStoreUri")

        return when (val it = mediaRepository.media(mediaStoreUri).first()) {
            is RequestStatus.Success -> it.data
            else -> null
        }
    }

    private fun uriToFilePath(uri: Uri): String? {
        val rawPath = when (uri.scheme) {
            ContentResolver.SCHEME_FILE -> uri.path

            ContentResolver.SCHEME_CONTENT -> uri.path?.let {
                if (it.contains('%')) Uri.decode(it) else it
            }

            else -> null
        } ?: return null

        val path = STORAGE_PATH_PREFIXES.firstNotNullOfOrNull { (prefix, replacement) ->
            rawPath.indexOf(prefix).takeIf { it >= 0 }?.let { index ->
                replacement + rawPath.substring(index + prefix.length)
            }
        } ?: return null

        if (path.split('/').any { it == ".." }) {
            return null
        }

        return path
    }

    private fun pathToMediaStoreUri(path: String, mediaType: MediaType): Uri? {
        val collection = when (mediaType) {
            MediaType.IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            MediaType.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            else -> return null
        }

        return runCatching {
            applicationContext.contentResolver.query(
                collection,
                arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.DATA} = ?",
                arrayOf(path),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    ContentUris.withAppendedId(collection, cursor.getLong(0))
                } else {
                    null
                }
            }
        }.onFailure {
            Log.w(LOG_TAG, "Cannot query MediaStore for $path", it)
        }.getOrNull()
    }

    private fun extractUriMetadata(uri: Uri, mediaType: MediaType): UriMetadata? {
        val contentResolver = applicationContext.contentResolver

        val mimeType = runCatching { contentResolver.getType(uri) }
            .onFailure { Log.w(LOG_TAG, "Cannot query MIME type of $uri", it) }
            .getOrNull()
            ?: run {
                Log.e(LOG_TAG, "Cannot get media type of $uri")
                return null
            }

        var displayName: String? = null
        var sizeBytes = 0L
        var canOpen = false

        runCatching {
            contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val displayNameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE)

                    if (displayNameColumn != -1 && !cursor.isNull(displayNameColumn)) {
                        displayName = cursor.getString(displayNameColumn)
                    }

                    if (sizeColumn != -1 && !cursor.isNull(sizeColumn)) {
                        sizeBytes = cursor.getLong(sizeColumn)
                    }
                }
            }
        }.onFailure {
            Log.w(LOG_TAG, "Cannot query metadata of $uri", it)
        }

        if (displayName == null) {
            displayName = uri.lastPathSegment?.substringAfterLast('/')
        }

        runCatching {
            contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                canOpen = true

                val statSize = afd.length
                if (sizeBytes <= 0L && statSize > 0L) {
                    sizeBytes = statSize
                }
            }
        }.onFailure {
            Log.w(LOG_TAG, "Cannot open file descriptor for uri $uri", it)
        }

        val dimensions = when (mediaType) {
            MediaType.VIDEO -> runCatching {
                MediaMetadataRetriever().use { mmr ->
                    mmr.setDataSource(applicationContext, uri)
                    canOpen = true

                    val width = mmr.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
                    )?.toIntOrNull() ?: 0
                    val height = mmr.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
                    )?.toIntOrNull() ?: 0

                    width to height
                }
            }.getOrElse {
                Log.w(LOG_TAG, "Cannot read video metadata for $uri", it)
                0 to 0
            }

            MediaType.IMAGE -> runCatching {
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    canOpen = true
                    BitmapFactory.decodeStream(inputStream, null, options)
                }
                (options.outWidth.takeIf { it > 0 } ?: 0) to
                        (options.outHeight.takeIf { it > 0 } ?: 0)
            }.getOrElse {
                Log.w(LOG_TAG, "Cannot read image metadata for $uri", it)
                0 to 0
            }

            else -> 0 to 0
        }

        if (!canOpen && uri.scheme in LOCAL_SCHEMES) {
            Log.e(LOG_TAG, "No read access to $uri, the sender didn't grant it")
            return null
        }

        return UriMetadata(
            mimeType = mimeType,
            displayName = displayName,
            width = dimensions.first,
            height = dimensions.second,
            sizeBytes = sizeBytes,
        )
    }

    /**
     * Run the URI over the available data sources and check if one of them understands it.
     * Get the media type of the URI if found.
     */
    private suspend fun uriToType(uri: Uri) = when (val it = mediaRepository.mediaTypeOf(uri)) {
        is RequestStatus.Loading -> throw Exception("Shouldn't return RequestStatus.Loading")

        is RequestStatus.Success -> it.data

        is RequestStatus.Error -> {
            Log.i(
                LOG_TAG,
                "Cannot get media type of $uri, error: ${it.error}, trying manual fallback"
            )

            when (uri.scheme) {
                "content", "file" -> runCatching {
                    applicationContext.contentResolver.getType(uri)
                }.onFailure { e ->
                    Log.w(LOG_TAG, "Cannot query MIME type of $uri", e)
                }.getOrNull()?.let { type ->
                    MimeUtils.mimeTypeToMediaType(type)
                }

                "http", "https" -> try {
                    okHttpClient.newCall(
                        Request.Builder()
                            .url(uri.toString())
                            .head()
                            .build()
                    ).executeAsync().use { response ->
                        response.header("Content-Type")?.let { type ->
                            MimeUtils.mimeTypeToMediaType(type)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(LOG_TAG, "Cannot fetch headers for $uri", e)
                    null
                }

                "rtsp" -> MediaType.VIDEO // This is either audio-only or A/V, fine either way

                else -> null
            } ?: run {
                Log.e(LOG_TAG, "Cannot get media type of $uri")
                null
            }
        }
    }

    companion object {
        private val LOG_TAG = IntentsViewModel::class.simpleName!!

        private val LOCAL_SCHEMES = setOf(
            ContentResolver.SCHEME_CONTENT,
            ContentResolver.SCHEME_FILE,
        )

        private val STORAGE_PATH_PREFIXES = listOf(
            "/storage/" to "/storage/",
            "/sdcard/" to "/storage/emulated/0/",
        )
    }
}
