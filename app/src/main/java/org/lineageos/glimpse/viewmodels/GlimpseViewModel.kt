/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import org.lineageos.glimpse.GlimpseApplication

open class GlimpseViewModel(application: Application) : AndroidViewModel(application) {
    protected val mediaRepository = getApplication<GlimpseApplication>().mediaRepository

    @Suppress("EmptyMethod")
    final override fun <T : Application> getApplication() = super.getApplication<T>()
}
