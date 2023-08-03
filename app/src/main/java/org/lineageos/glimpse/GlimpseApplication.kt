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
    }.build()
}
