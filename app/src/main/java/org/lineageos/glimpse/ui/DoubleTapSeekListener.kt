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
import androidx.media3.common.util.UnstableApi

/**
 * Double-tap gesture listener for video seeking.
 * Tapping on the left side seeks backward, right side seeks forward.
 */
@UnstableApi
class DoubleTapSeekListener(
    context: Context,
    private val player: Player?,
    private val seekTimeSeconds: Int = 10,
    private val onSeek: (forward: Boolean, milliseconds: Long) -> Unit
) : View.OnTouchListener {

    private val seekTimeMs = seekTimeSeconds * 1000L
    private var currentView: View? = null

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (player == null || !player.isPlaying) {
                    return false
                }

                val view = currentView ?: return false
                val isLeftSide = e.x < view.width / 2

                val currentPosition = player.currentPosition
                val duration = player.duration

                if (duration <= 0) {
                    return false
                }

                val newPosition = if (isLeftSide) {
                    // Seek backward
                    (currentPosition - seekTimeMs).coerceAtLeast(0)
                } else {
                    // Seek forward
                    (currentPosition + seekTimeMs).coerceAtMost(duration)
                }

                player.seekTo(newPosition)
                onSeek(!isLeftSide, seekTimeMs)

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
        gestureDetector.onTouchEvent(event)
        // Return false to allow other touch listeners to handle the event
        return false
    }
}
