/*
 * SPDX-FileCopyrightText: 2025 Guidix
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.ui

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.media3.common.Player

/**
 * Double-tap gesture listener for video seeking.
 * Tapping on the left side seeks backward, right side seeks forward.
 */

class MediaGestureListener(
    context: Context,
) : View.OnTouchListener {

    private var currentView: View? = null

    var seekTimeSeconds: Int = 10
    var player: Player? = null

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
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

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                // Allow single tap to propagate (for fullscreen toggle)
                return false
            }
        }
    )

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        currentView = v
        if (v.width <= 0 || v.height <= 0) {
            return false
        }

        gestureDetector.onTouchEvent(event)
        // Return false to allow other touch listeners to handle the event.
        return false
    }
}
