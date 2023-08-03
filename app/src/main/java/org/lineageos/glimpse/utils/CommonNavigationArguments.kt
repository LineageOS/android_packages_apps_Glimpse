/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.utils

import android.os.Bundle
import androidx.core.os.bundleOf

/**
 * Common navigation arguments for all fragments.
 * It is recommended to create a bundle from there and then add fragment-specific
 * arguments to it.
 */
data class CommonNavigationArguments(
    var title: String? = null,
) {
    fun toBundle() = bundleOf(
        KEY_TITLE to title,
    )

    companion object {
        private const val KEY_TITLE = "title"

        fun fromBundle(bundle: Bundle) = CommonNavigationArguments(
            bundle.getString(KEY_TITLE),
        )
    }
}
