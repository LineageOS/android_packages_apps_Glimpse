/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse

import android.app.WallpaperManager
import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

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
            MaterialAlertDialogBuilder(this).setTitle(R.string.set_wallpaper_dialog_title)
                .setSingleChoiceItems(R.array.set_wallpaper_items, -1) { _, which ->
                    val flags = POSITION_TO_FLAG[which] ?: throw Exception("Invalid position")
                    setWallpaper(wallpaperUri, flags)
                    finish()
                }.show()
        }
    }

    private fun setWallpaper(uri: Uri, flags: Int) {
        val wallpaperStream = contentResolver.openInputStream(uri)
        wallpaperManager.setStream(wallpaperStream, null, true, flags)
    }

    companion object {
        private val POSITION_TO_FLAG = mapOf(
            0 to WallpaperManager.FLAG_SYSTEM,
            1 to WallpaperManager.FLAG_LOCK,
            2 to (WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
        )
    }
}
