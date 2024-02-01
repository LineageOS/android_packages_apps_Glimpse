/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse

import android.app.WallpaperManager
import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class SetWallpaperActivity : AppCompatActivity(R.layout.activity_set_wallpaper) {
    // Views
    private val wallpaperImageView by lazy { findViewById<ImageView>(R.id.wallpaperImageView)!! }
    private val setWallpaperButton by lazy { findViewById<MaterialButton>(R.id.setWallpaperButton)!! }

    // System services
    private val wallpaperManager by lazy { getSystemService(WallpaperManager::class.java) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load wallpaper from intent
        val wallpaperUri = intent.data ?: run {
            Toast.makeText(this, R.string.intent_media_not_found, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // Check if the wallpaper can be changed
        if (!wallpaperManager.isWallpaperSupported && !wallpaperManager.isSetWallpaperAllowed) {
            Toast.makeText(
                this, R.string.intent_wallpaper_cannot_be_changed, Toast.LENGTH_LONG
            ).show()
            finish()
            return
        }

        wallpaperImageView.setImageURI(wallpaperUri)

        // Set wallpaper
        setWallpaperButton.setOnClickListener {
            contentResolver.openInputStream(wallpaperUri)?.use {
                wallpaperManager.setStream(it)
            }

            finish()
        }
    }
}
