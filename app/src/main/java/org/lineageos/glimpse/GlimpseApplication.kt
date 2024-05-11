/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.VideoFrameDecoder
import coil.memory.MemoryCache
import com.github.awxkee.avifcoil.decoder.HeifDecoder
import com.google.android.material.color.DynamicColors

class GlimpseApplication : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()

        // Observe dynamic colors changes
        DynamicColors.applyToActivitiesIfAvailable(this)
    }

    override fun newImageLoader() = ImageLoader.Builder(this).components {
        add(ImageDecoderDecoder.Factory())
        add(GifDecoder.Factory())
        add(VideoFrameDecoder.Factory())

        // AVIF
        add(HeifDecoder.Factory(this@GlimpseApplication))
    }.memoryCache {
        MemoryCache.Builder(this).maxSizePercent(0.25).build()
    }.build()
}
