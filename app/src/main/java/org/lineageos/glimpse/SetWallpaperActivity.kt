/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse

import android.app.WallpaperManager
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class SetWallpaperActivity : AppCompatActivity(R.layout.activity_set_wallpaper) {
    // Views
    private val wallpaperImageView by lazy { findViewById<ImageView>(R.id.wallpaperImageView)!! }
    private val setWallpaperButton by lazy { findViewById<MaterialButton>(R.id.setWallpaperButton)!! }

    private val wallpaperManager by lazy { WallpaperManager.getInstance(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!wallpaperManager.isWallpaperSupported && !wallpaperManager.isSetWallpaperAllowed) {
            finish()
            return
        }

        // Load wallpaper from intent
        val wallpaperUri = intent.data
        if (wallpaperUri == null) {
            finish()
            return
        }
        wallpaperImageView.setImageURI(wallpaperUri)

        // Set wallpaper
        setWallpaperButton.setOnClickListener {
            wallpaperManager.setStream(contentResolver.openInputStream(wallpaperUri))
            finish()
        }
    }
}
