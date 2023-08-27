/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.ext

import android.view.View
import androidx.core.view.isVisible

/**
 * Get the system's default short animation time.
 */
val View.shortAnimTime
    get() = resources.getInteger(android.R.integer.config_shortAnimTime)

/**
 * Update the [View]'s visibility using a fade animation.
 * @param visible Whether the view should be visible or not.
 */
fun View.fade(visible: Boolean) {
    with(animate()) {
        cancel()

        if (visible && !isVisible) {
            isVisible = true
        }

        alpha(
            when (visible) {
                true -> 1f
                false -> 0f
            }
        )

        duration = shortAnimTime.toLong()

        setListener(null)

        withEndAction {
            isVisible = visible
        }
    }
}
