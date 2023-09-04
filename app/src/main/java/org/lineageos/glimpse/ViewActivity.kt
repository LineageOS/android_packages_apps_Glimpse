/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.lineageos.glimpse.fragments.MediaViewerFragment
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_view)

        // Setup edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)

        handleIntent(intent)

        addOnNewIntentListener {
            handleIntent(it)
        }
    }

    private fun handleIntent(intent: Intent) {
        ioScope.launch {
            val uri = intent.data ?: run {
                runOnUiThread {
                    Toast.makeText(
                        this@ViewActivity, R.string.intent_media_not_found, Toast.LENGTH_SHORT
                    ).show()
                    finish()
                }

                return@launch
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

                return@launch
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

                return@launch
            }

            runOnUiThread {
                supportFragmentManager
                    .beginTransaction()
                    .replace(
                        R.id.navHostFragment, MediaViewerFragment.newInstance(
                            null, null, MediaUri(uri, uriType, dataType)
                        )
                    )
                    .commit()
            }
        }
    }

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
