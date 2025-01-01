/*
 * SPDX-FileCopyrightText: 2024-2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse

import android.app.WallpaperManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.util.Consumer
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.lineageos.glimpse.ext.load
import org.lineageos.glimpse.ext.updateMargin

class SetWallpaperActivity : AppCompatActivity(R.layout.activity_set_wallpaper) {
    // Views
    private val setWallpaperButton by lazy { findViewById<MaterialButton>(R.id.setWallpaperButton) }
    private val wallpaperImageView by lazy { findViewById<ImageView>(R.id.wallpaperImageView) }

    // System services
    private val wallpaperManager by lazy { getSystemService(WallpaperManager::class.java) }

    // Intents
    private val intentListener = Consumer<Intent> { handleIntent(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge
        enableEdgeToEdge()

        // Insets
        ViewCompat.setOnApplyWindowInsetsListener(setWallpaperButton) { _, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )

            setWallpaperButton.updateMargin(
                insets,
                bottom = true,
            )

            windowInsets
        }

        intentListener.accept(intent)
        addOnNewIntentListener(intentListener)
    }

    override fun onDestroy() {
        removeOnNewIntentListener(intentListener)

        super.onDestroy()
    }

    private fun handleIntent(intent: Intent) {
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

        wallpaperImageView.load(wallpaperUri)

        // Set wallpaper
        setWallpaperButton.setOnClickListener {
            MaterialAlertDialogBuilder(this, R.style.Theme_Glimpse_SetWallpaperDialog)
                .setTitle(R.string.set_wallpaper_dialog_title)
                .setItems(R.array.set_wallpaper_items) { _, which ->
                    val flags = positionToFlag[which]
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
        private val positionToFlag = arrayOf(
            WallpaperManager.FLAG_SYSTEM,
            WallpaperManager.FLAG_LOCK,
            WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK,
        )
    }
}
