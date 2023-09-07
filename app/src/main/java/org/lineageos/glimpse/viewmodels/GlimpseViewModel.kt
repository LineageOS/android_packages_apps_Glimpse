/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.viewmodels

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel

abstract class GlimpseViewModel(application: Application) : AndroidViewModel(application) {
    protected val context: Context
        get() = getApplication<Application>().applicationContext
}
