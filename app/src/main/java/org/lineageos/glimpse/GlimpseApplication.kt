/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse

import android.app.Application
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.VideoFrameDecoder
import coil.memory.MemoryCache
import com.google.android.material.color.DynamicColors
import org.lineageos.glimpse.repository.MediaRepository

class GlimpseApplication : Application(), ImageLoaderFactory {
    val mediaRepository = MediaRepository(this)

    override fun onCreate() {
        super.onCreate()

        // Observe dynamic colors changes
        DynamicColors.applyToActivitiesIfAvailable(this)
    }

    override fun newImageLoader() = ImageLoader.Builder(this).components {
        add(ImageDecoderDecoder.Factory())
        add(GifDecoder.Factory())
        add(VideoFrameDecoder.Factory())
    }.memoryCache {
        MemoryCache.Builder(this).maxSizePercent(0.25).build()
    }.build()
}
