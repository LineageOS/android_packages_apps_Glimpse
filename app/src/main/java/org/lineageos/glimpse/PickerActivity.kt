/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.util.Consumer
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.lineageos.glimpse.ext.updateMargin
import org.lineageos.glimpse.models.MediaType
import org.lineageos.glimpse.utils.MimeUtils
import org.lineageos.glimpse.viewmodels.IntentsViewModel

class PickerActivity : AppCompatActivity(R.layout.activity_picker) {
    // View models
    private val intentsViewModel by viewModels<IntentsViewModel>()

    // Views
    private val toolbar by lazy { findViewById<MaterialToolbar>(R.id.toolbar)!! }

    // Intents
    private val intentListener = Consumer<Intent> { intentsViewModel.onIntent(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge
        enableEdgeToEdge()

        // Insets
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { _, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )

            toolbar.updateMargin(
                insets,
                start = true,
                end = true,
            )

            windowInsets
        }

        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        intentListener.accept(intent)
        addOnNewIntentListener(intentListener)

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                loadData()
            }
        }
    }

    override fun onDestroy() {
        removeOnNewIntentListener(intentListener)

        super.onDestroy()
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        android.R.id.home -> {
            onBackPressedDispatcher.onBackPressed()
            true
        }

        else -> {
            super.onOptionsItemSelected(item)
        }
    }

    private suspend fun loadData() {
        intentsViewModel.parsedIntent.collectLatest {
            it?.handle { parsedIntent ->
                when (parsedIntent) {
                    is IntentsViewModel.ParsedIntent.PickIntent -> {
                        val mediaType = parsedIntent.mimeType?.let { mimeType ->
                            MimeUtils.mimeTypeToMediaType(mimeType)
                        }

                        toolbar.setTitle(
                            when (mediaType) {
                                MediaType.IMAGE -> R.string.pick_a_photo
                                MediaType.VIDEO -> R.string.pick_a_video
                                else -> R.string.pick_a_media
                            }
                        )
                    }

                    else -> {
                        Toast.makeText(
                            this, R.string.intent_action_not_supported, Toast.LENGTH_SHORT
                        ).show()
                        finish()
                    }
                }
            }
        }
    }
}
