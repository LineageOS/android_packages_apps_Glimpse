/*
 * SPDX-FileCopyrightText: 2025 Guidix
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.ui

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View

/**
 * Edge tap navigation listener for navigating between media.
 * Tapping on the left edge navigates to previous, right edge navigates to next.
 */
class EdgeTapNavigationListener(
    context: Context,
    private val onNavigate: (forward: Boolean) -> Unit
) : View.OnTouchListener {

    // Define edge zones (20% of screen width on each side)
    private val edgePercent = 0.2f
    private var currentView: View? = null

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                val view = currentView ?: return false
                val viewWidth = view.width

                if (viewWidth <= 0) {
                    return false
                }

                val leftEdgeThreshold = viewWidth * edgePercent
                val rightEdgeThreshold = viewWidth * (1 - edgePercent)

                when {
                    e.x < leftEdgeThreshold -> {
                        // Left edge - navigate to previous
                        onNavigate(false)
                        return true
                    }
                    e.x > rightEdgeThreshold -> {
                        // Right edge - navigate to next
                        onNavigate(true)
                        return true
                    }
                }

                return false
            }
        }
    )

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        currentView = v
        // Ensure view has valid dimensions before processing
        if (v.width > 0 && v.height > 0) {
            gestureDetector.onTouchEvent(event)
        }
        // Return false to allow other touch listeners to handle the event
        return false
    }
}
