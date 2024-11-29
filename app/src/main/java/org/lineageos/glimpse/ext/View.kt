/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.ext

import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.Insets
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams

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

/**
 * Return whether [View.getLayoutDirection] is [View.LAYOUT_DIRECTION_RTL].
 */
val View.isRtl: Boolean
    get() = layoutDirection == View.LAYOUT_DIRECTION_RTL

/**
 * Updates the padding of the view based on the insets.
 * Layout direction is taken into account.
 *
 * @param insets The insets to apply
 * @param start Whether the start padding should be applied
 * @param top Whether the top padding should be applied
 * @param end Whether the end padding should be applied
 * @param bottom Whether the bottom padding should be applied
 */
fun View.updatePadding(
    insets: Insets,
    start: Boolean = false,
    top: Boolean = false,
    end: Boolean = false,
    bottom: Boolean = false
) {
    val (left, right) = when (isRtl) {
        true -> end to start
        false -> start to end
    }

    setPadding(
        insets.left.takeIf { left } ?: paddingLeft,
        insets.top.takeIf { top } ?: paddingTop,
        insets.right.takeIf { right } ?: paddingRight,
        insets.bottom.takeIf { bottom } ?: paddingBottom
    )
}

/**
 * Updates the margin of the view based on the insets.
 * Layout direction is taken into account.
 *
 * @param insets The insets to apply
 * @param start Whether the start padding should be applied
 * @param top Whether the top padding should be applied
 * @param end Whether the end padding should be applied
 * @param bottom Whether the bottom padding should be applied
 */
fun View.updateMargin(
    insets: Insets,
    start: Boolean = false,
    top: Boolean = false,
    end: Boolean = false,
    bottom: Boolean = false
) {
    val (left, right) = when (isRtl) {
        true -> end to start
        false -> start to end
    }

    updateLayoutParams<ViewGroup.MarginLayoutParams> {
        leftMargin = insets.left.takeIf { left } ?: leftMargin
        topMargin = insets.top.takeIf { top } ?: topMargin
        rightMargin = insets.right.takeIf { right } ?: rightMargin
        bottomMargin = insets.bottom.takeIf { bottom } ?: bottomMargin
    }
}
