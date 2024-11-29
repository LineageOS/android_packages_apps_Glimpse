/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.viewmodels

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import okhttp3.OkHttpClient
import okhttp3.Request
import org.lineageos.glimpse.ext.applicationContext
import org.lineageos.glimpse.ext.asArray
import org.lineageos.glimpse.ext.executeAsync
import org.lineageos.glimpse.models.MediaType
import org.lineageos.glimpse.models.RequestStatus
import org.lineageos.glimpse.utils.MimeUtils

/**
 * A view model used by activities to handle intents.
 */
class IntentsViewModel(application: Application) : GlimpseViewModel(application) {
    sealed class ParsedIntent {
        /**
         * Open the app's home page.
         */
        class MainIntent : ParsedIntent()

        /**
         * View a content.
         *
         * @param contents The items to show
         * @param secure Whether we can show item with a locked status
         */
        class ViewIntent(
            val contents: List<Content>,
            val secure: Boolean = false,
        ) : ParsedIntent()

        /**
         * Pick a content.
         *
         * @param mimeType The type to select, null to avoid filtering
         * @param multiple Whether multiple items can be selected
         */
        class PickIntent(
            val mimeType: String? = null,
            val multiple: Boolean = false,
        ) : ParsedIntent()

        /**
         * Pick a photo to be used as wallpaper.
         */
        class SetWallpaperIntent : ParsedIntent()

        data class Content(
            val uri: Uri,
            val type: MediaType,
        )

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

            val contents = mutableListOf<ParsedIntent.Content>().apply {
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
                    var mediaType: MediaType? = null
                    for (i in 0 until clipData.description.mimeTypeCount) {
                        val mimeType = clipData.description.getMimeType(i)
                        MimeUtils.mimeTypeToMediaType(mimeType)?.let { type ->
                            mediaType = type
                        }
                    }

                    clipData.asArray().forEach { item ->
                        uriToContent(item.uri, mediaType)?.let {
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

                Intent.ACTION_VIEW,
                MediaStore.ACTION_REVIEW,
                MediaStore.ACTION_REVIEW_SECURE -> ParsedIntent.ViewIntent(
                    contents,
                    intent.action == MediaStore.ACTION_REVIEW_SECURE
                )

                Intent.ACTION_GET_CONTENT,
                Intent.ACTION_PICK -> ParsedIntent.PickIntent(
                    mimeType,
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
        .flowOn(Dispatchers.IO)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(),
            null,
        )

    fun onIntent(intent: Intent?) {
        currentIntent.value = intent
    }

    /**
     * Given a URI and a pre-parsed media type, get a [ParsedIntent.Content] object.
     */
    private suspend fun uriToContent(uri: Uri, mediaType: MediaType?): ParsedIntent.Content? {
        val type = mediaType ?: uriToType(uri) ?: run {
            Log.e(LOG_TAG, "Cannot get media type of $uri")
            return null
        }

        return ParsedIntent.Content(uri, type)
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
                "content", "file" -> applicationContext.contentResolver.getType(uri)?.let { type ->
                    MimeUtils.mimeTypeToMediaType(type)
                }

                "http", "https" -> okHttpClient.newCall(
                    Request.Builder()
                        .url(uri.toString())
                        .head()
                        .build()
                ).executeAsync().use { response ->
                    response.header("Content-Type")?.let { type ->
                        MimeUtils.mimeTypeToMediaType(type)
                    }
                }

                "rtsp" -> MediaType.MEDIA // This is either audio-only or A/V, fine either way

                else -> null
            } ?: run {
                Log.e(LOG_TAG, "Cannot get media type of $uri")
                null
            }
        }
    }

    companion object {
        private val LOG_TAG = IntentsViewModel::class.simpleName!!
    }
}
