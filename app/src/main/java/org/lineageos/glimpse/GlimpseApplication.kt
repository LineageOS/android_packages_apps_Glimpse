/*
 * SPDX-FileCopyrightText: 2023-2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse

import android.app.Application
import com.google.android.material.color.DynamicColors
import org.lineageos.glimpse.repository.MediaRepository

class GlimpseApplication : Application() {
    val mediaRepository by lazy { MediaRepository(applicationContext) }

    override fun onCreate() {
        super.onCreate()

        // Observe dynamic colors changes
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
