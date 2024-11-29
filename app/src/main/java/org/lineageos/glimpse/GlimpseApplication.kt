/*
 * SPDX-FileCopyrightText: 2023-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.memory.MemoryCache
import coil3.video.VideoFrameDecoder
import com.google.android.material.color.DynamicColors
import kotlinx.coroutines.MainScope
import org.lineageos.glimpse.repository.MediaRepository
import org.lineageos.glimpse.ui.coil.ThumbnailMapper

class GlimpseApplication : Application(), SingletonImageLoader.Factory {
    val mediaRepository by lazy { MediaRepository(applicationContext, MainScope()) }

    override fun onCreate() {
        super.onCreate()

        // Observe dynamic colors changes
        DynamicColors.applyToActivitiesIfAvailable(this)
    }

    override fun newImageLoader(context: PlatformContext) = ImageLoader.Builder(this)
        .components {
            add(AnimatedImageDecoder.Factory())
            add(VideoFrameDecoder.Factory())
            add(ThumbnailMapper)
        }
        .memoryCache {
            MemoryCache.Builder().maxSizePercent(context, 0.25).build()
        }
        .build()
}
