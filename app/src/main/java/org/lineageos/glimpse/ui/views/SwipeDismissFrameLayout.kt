/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.ui.views

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

class SwipeDismissFrameLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    interface Callback {
        fun onDismissed()
        fun onDrag(progress: Float)
    }

    var callback: Callback? = null
    private var startY = 0f
    private var startX = 0f
    private var isDragging = false
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startY = ev.rawY
                startX = ev.rawX
                isDragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaY = ev.rawY - startY
                val deltaX = ev.rawX - startX

                // Intercept if pulling down, and vertical movement is greater than horizontal
                if (deltaY > touchSlop && abs(deltaY) > abs(deltaX)) {
                    // Make sure we aren't zoomed in and panning up.
                    // (ZoomImageView calls requestDisallowInterceptTouchEvent when zoomed,
                    // which naturally prevents this from firing aggressively).
                    if (!canScrollVertically(-1)) {
                        isDragging = true
                        return true
                    }
                }
            }
        }
        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!isDragging) return super.onTouchEvent(ev)

        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                val deltaY = ev.rawY - startY
                if (deltaY > 0) {
                    translationY = deltaY
                    // Calculate progress (0.0 to 1.0) based on screen height
                    val progress = (deltaY / height).coerceIn(0f, 1f)
                    // Scale down to 70% at the bottom of the screen
                    val scale = 1f - (progress * 0.3f)
                    scaleX = scale
                    scaleY = scale
                    callback?.onDrag(progress)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val deltaY = ev.rawY - startY
                // If dragged down more than 20% of the screen, trigger dismiss with an animation
                if (deltaY > height / 5) {
                    animate()
                        .translationY(height.toFloat())
                        .scaleX(0.4f)
                        .scaleY(0.4f)
                        .alpha(0f)
                        .setDuration(250)
                        .withEndAction { callback?.onDismissed() }
                        .start()
                } else {
                    // Otherwise, snap back to original position
                    animate()
                        .translationY(0f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(200)
                        .withEndAction { callback?.onDrag(0f) }
                        .start()
                }
                isDragging = false
            }
        }
        return true
    }
}
