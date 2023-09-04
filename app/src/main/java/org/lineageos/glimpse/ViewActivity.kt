/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse

import android.app.KeyguardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.core.view.WindowCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.lineageos.glimpse.fragments.MediaViewerFragment
import org.lineageos.glimpse.models.Media
import org.lineageos.glimpse.models.MediaType
import org.lineageos.glimpse.models.MediaUri

/**
 * An activity used to view a specific media.
 */
class ViewActivity : AppCompatActivity() {
    // Coroutines
    private val ioScope = CoroutineScope(Job() + Dispatchers.IO)

    // okhttp
    private val httpClient = OkHttpClient()

    // System services
    private val keyguardManager by lazy { getSystemService(KeyguardManager::class.java) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_view)

        // Setup edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // We only want to show this activity on top of the keyguard if we're being launched with
        // the ACTION_REVIEW_SECURE intent and the system is currently locked.
        if (keyguardManager.isKeyguardLocked && intent.action == MediaStore.ACTION_REVIEW_SECURE) {
            setShowWhenLocked(true)
        }

        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        ioScope.launch {
            when (intent.action) {
                Intent.ACTION_VIEW -> handleView(intent)
                MediaStore.ACTION_REVIEW -> handleReview(intent)
                MediaStore.ACTION_REVIEW_SECURE -> handleReview(intent, true)
                else -> runOnUiThread {
                    Toast.makeText(
                        this@ViewActivity,
                        R.string.intent_action_not_supported,
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                }
            }
        }
    }

    /**
     * Handle a [Intent.ACTION_VIEW] intent (view a single media, controls also read-only).
     * Must be executed on [ioScope].
     * @param intent The received intent
     * @param secure Whether we should show this media in a secure manner
     */
    private fun handleView(intent: Intent, secure: Boolean = false) {
        val uri = intent.data ?: run {
            runOnUiThread {
                Toast.makeText(
                    this@ViewActivity,
                    R.string.intent_media_not_found,
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }

            return
        }

        val dataType = intent.type ?: getContentType(uri) ?: run {
            runOnUiThread {
                Toast.makeText(
                    this@ViewActivity,
                    R.string.intent_media_type_not_found,
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }

            return
        }

        val uriType = MediaType.fromMimeType(dataType) ?: run {
            runOnUiThread {
                Toast.makeText(
                    this@ViewActivity,
                    R.string.intent_media_type_not_supported,
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }

            return
        }

        runOnUiThread {
            supportFragmentManager
                .beginTransaction()
                .replace(
                    R.id.navHostFragment, MediaViewerFragment.newInstance(
                        null, null, MediaUri(uri, uriType, dataType), secure
                    )
                )
                .commit()
        }
    }

    /**
     * Handle a [MediaStore.ACTION_REVIEW] / [MediaStore.ACTION_REVIEW_SECURE] intent
     * (view a media together with medias from the same bucket ID).
     * If uri parsing from [MediaStore] fails, fallback to [handleView].
     * Must be executed on [ioScope].
     * @param intent The received intent
     * @param secure Whether we should review this media in a secure manner
     */
    private fun handleReview(intent: Intent, secure: Boolean = false) {
        intent.data?.let { getMediaStoreMedia(it) }?.also {
            runOnUiThread {
                supportFragmentManager
                    .beginTransaction()
                    .replace(
                        R.id.navHostFragment, MediaViewerFragment.newInstance(
                            it, it.bucketId, null, secure
                        )
                    )
                    .commit()
            }
        } ?: handleView(intent, secure)
    }

    /**
     * Given a [MediaStore] [Uri], parse its information and get a [Media] object.
     * Must be executed on [ioScope].
     * @param uri The [MediaStore] [Uri]
     */
    private fun getMediaStoreMedia(uri: Uri) = runCatching {
        contentResolver.query(
            uri,
            arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.BUCKET_ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.IS_FAVORITE,
                MediaStore.MediaColumns.IS_TRASHED,
                MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.MediaColumns.DATE_ADDED,
                MediaStore.MediaColumns.DATE_MODIFIED,
                MediaStore.MediaColumns.WIDTH,
                MediaStore.MediaColumns.HEIGHT,
                MediaStore.MediaColumns.ORIENTATION,
            ),
            bundleOf(),
            null,
        )?.use {
            val idIndex = it.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val bucketIdIndex = it.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_ID)
            val displayNameIndex = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val isFavoriteIndex = it.getColumnIndexOrThrow(MediaStore.MediaColumns.IS_FAVORITE)
            val isTrashedIndex = it.getColumnIndexOrThrow(MediaStore.MediaColumns.IS_TRASHED)
            val mimeTypeIndex = it.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val dateAddedIndex = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val dateModifiedIndex =
                it.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            val widthIndex = it.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
            val heightIndex = it.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
            val orientationIndex =
                it.getColumnIndexOrThrow(MediaStore.MediaColumns.ORIENTATION)

            if (it.count != 1) {
                return@use null
            }

            it.moveToFirst()

            val id = it.getLong(idIndex)
            val bucketId = it.getInt(bucketIdIndex)
            val displayName = it.getString(displayNameIndex)
            val isFavorite = it.getInt(isFavoriteIndex)
            val isTrashed = it.getInt(isTrashedIndex)
            val mediaType = contentResolver.getType(uri)?.let { type ->
                MediaType.fromMimeType(type)
            } ?: return@use null
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
                mediaType.mediaStoreValue,
                mimeType,
                dateAdded,
                dateModified,
                width,
                height,
                orientation,
            )
        }
    }.getOrNull()

    private fun getContentType(uri: Uri) = when (uri.scheme) {
        "content" -> contentResolver.getType(uri)

        "file" -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(
            MimeTypeMap.getFileExtensionFromUrl(uri.toString())
        )

        "http", "https" -> {
            val request = Request.Builder()
                .head()
                .url(uri.toString())
                .build()

            runCatching {
                httpClient.newCall(request).execute().use { response ->
                    response.header("content-type")
                }
            }.getOrNull()
        }

        "rtsp" -> "video/rtsp" // Made up MIME type, just to get video type

        else -> null
    }
}
