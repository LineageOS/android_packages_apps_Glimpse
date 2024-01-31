/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.shape.MaterialShapeDrawable
import org.lineageos.glimpse.models.MediaType
import org.lineageos.glimpse.utils.PickerUtils

class PickerActivity : AppCompatActivity(R.layout.activity_picker) {
    // Views
    private val appBarLayout by lazy { findViewById<AppBarLayout>(R.id.appBarLayout)!! }
    private val toolbar by lazy { findViewById<MaterialToolbar>(R.id.toolbar)!! }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Setup edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)

        appBarLayout.statusBarForeground = MaterialShapeDrawable.createWithElevationOverlay(this)

        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        // Parse intent
        if (intent.action !in supportedIntentActions) {
            Toast.makeText(
                this, R.string.intent_action_not_supported, Toast.LENGTH_SHORT
            ).show()
            finish()
            return
        }

        val mimeType = PickerUtils.translateMimeType(intent) ?: run {
            Toast.makeText(
                this, R.string.intent_media_type_not_supported, Toast.LENGTH_SHORT
            ).show()
            finish()
            return
        }

        val mediaType = MediaType.fromMimeType(mimeType)

        toolbar.setTitle(
            when (mediaType) {
                MediaType.IMAGE -> R.string.pick_a_photo
                MediaType.VIDEO -> R.string.pick_a_video
                else -> R.string.pick_a_media
            }
        )
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

    companion object {
        private val supportedIntentActions = listOf(
            Intent.ACTION_GET_CONTENT,
            Intent.ACTION_PICK,
            Intent.ACTION_SET_WALLPAPER,
        )
    }
}
