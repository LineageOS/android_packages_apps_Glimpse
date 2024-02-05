/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse

import android.app.WallpaperManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SetWallpaperActivity : AppCompatActivity(R.layout.activity_set_wallpaper) {
    // Views
    private val contentView by lazy { findViewById<View>(android.R.id.content)!! }
    private val wallpaperImageView by lazy { findViewById<ImageView>(R.id.wallpaperImageView)!! }
    private val setWallpaperButton by lazy { findViewById<MaterialButton>(R.id.setWallpaperButton)!! }

    // System services
    private val wallpaperManager by lazy { getSystemService(WallpaperManager::class.java) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Setup edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)

        ViewCompat.setOnApplyWindowInsetsListener(contentView) { _, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )

            setWallpaperButton.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = insets.bottom
            }

            windowInsets
        }

        // Load wallpaper from intent
        val wallpaperUri = intent.data ?: run {
            Toast.makeText(this, R.string.intent_media_not_found, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // Try to offload this task to styles and wallpaper
        runCatching {
            startActivity(wallpaperManager.getCropAndSetWallpaperIntent(wallpaperUri))
            finish()
            return
        }

        // If we reached this point, we have to do stuff on our own

        // Check if the wallpaper can be changed
        if (!wallpaperManager.isWallpaperSupported || !wallpaperManager.isSetWallpaperAllowed) {
            Toast.makeText(
                this, R.string.intent_wallpaper_cannot_be_changed, Toast.LENGTH_LONG
            ).show()
            finish()
            return
        }

        wallpaperImageView.setImageURI(wallpaperUri)

        // Set wallpaper
        setWallpaperButton.setOnClickListener {
            MaterialAlertDialogBuilder(this, R.style.Theme_Glimpse_SetWallpaperDialog)
                .setTitle(R.string.set_wallpaper_dialog_title)
                .setItems(R.array.set_wallpaper_items) { _, which ->
                    val flags = POSITION_TO_FLAG[which] ?: throw Exception("Invalid position")
                    setWallpaper(wallpaperUri, flags)
                    finish()
                }.show()
        }
    }

    private fun setWallpaper(uri: Uri, flags: Int) {
        contentResolver.openInputStream(uri)?.use {
            wallpaperManager.setStream(it, null, true, flags)
        }
    }

    companion object {
        private val POSITION_TO_FLAG = mapOf(
            0 to WallpaperManager.FLAG_SYSTEM,
            1 to WallpaperManager.FLAG_LOCK,
            2 to (WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
        )
    }
}
