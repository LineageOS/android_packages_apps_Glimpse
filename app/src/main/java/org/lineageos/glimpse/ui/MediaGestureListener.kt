/*
 * SPDX-FileCopyrightText: 2025 Guidix
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.ui

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.media3.common.Player

/**
 * Unified media gesture listener.
 * - Single tap on screen edges navigates between media.
 * - Double tap seeks backward or forward for video playback.
 */
class MediaGestureListener(
    context: Context,
    private val onNavigate: (forward: Boolean) -> Unit,
) : View.OnTouchListener {
    private val edgePercent = 0.2f
    private var currentView: View? = null

    var edgeTapNavigationEnabled: Boolean = false
    var doubleTapSeekEnabled: Boolean = false
    var seekTimeSeconds: Int = 10
    var player: Player? = null

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (!edgeTapNavigationEnabled) {
                    return false
                }

                val view = currentView ?: return false
                val viewWidth = view.width
                if (viewWidth <= 0) {
                    return false
                }

                val leftEdgeThreshold = viewWidth * edgePercent
                val rightEdgeThreshold = viewWidth * (1 - edgePercent)

                return when {
                    e.x < leftEdgeThreshold -> {
                        onNavigate(false)
                        true
                    }

                    e.x > rightEdgeThreshold -> {
                        onNavigate(true)
                        true
                    }

                    else -> false
                }
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (!doubleTapSeekEnabled) {
                    return false
                }

                val player = player ?: return false
                val view = currentView ?: return false

                if (view.width <= 0 || player.duration <= 0) {
                    return false
                }

                val seekTimeMs = seekTimeSeconds * 1000L
                val isLeftSide = e.x < view.width / 2f
                val currentPosition = player.currentPosition
                val duration = player.duration

                val newPosition = if (isLeftSide) {
                    (currentPosition - seekTimeMs).coerceAtLeast(0)
                } else {
                    (currentPosition + seekTimeMs).coerceAtMost(duration)
                }

                player.seekTo(newPosition)
                return true
            }
        }
    )

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        currentView = v
        if (v.width <= 0 || v.height <= 0) {
            return false
        }

        val handled = gestureDetector.onTouchEvent(event)

        // Do not consume ACTION_DOWN so regular pressed/click behavior still works.
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> false
            else -> handled
        }
    }
}
