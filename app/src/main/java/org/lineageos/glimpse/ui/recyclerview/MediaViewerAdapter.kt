/*
 * SPDX-FileCopyrightText: 2023-2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.ui.recyclerview

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.media3.ui.PlayerControlView
import androidx.media3.ui.PlayerView
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.github.panpf.zoomimage.GlideZoomImageView
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.lineageos.glimpse.R
import org.lineageos.glimpse.ext.doubleTapSeekEnabled
import org.lineageos.glimpse.ext.doubleTapSeekTime
import org.lineageos.glimpse.ext.edgeTapNavigationEnabled
import org.lineageos.glimpse.ext.fade
import org.lineageos.glimpse.ext.hideNativeSeekButtons
import org.lineageos.glimpse.ext.load
import org.lineageos.glimpse.models.Media
import org.lineageos.glimpse.models.MediaType
import org.lineageos.glimpse.models.MotionPhoto
import org.lineageos.glimpse.ui.DoubleTapSeekListener
import org.lineageos.glimpse.ui.EdgeTapNavigationListener
import org.lineageos.glimpse.viewmodels.LocalPlayerViewModel

class MediaViewerAdapter(
    private val localPlayerViewModel: LocalPlayerViewModel,
    private val onNavigate: ((forward: Boolean) -> Unit)? = null
) : ListAdapter<Media, MediaViewerAdapter.MediaViewHolder>(UniqueItemDiffCallback()) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = MediaViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.media_view, parent, false),
    )

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onViewAttachedToWindow(holder: MediaViewHolder) {
        super.onViewAttachedToWindow(holder)

        holder.onViewAttachedToWindow()
    }

    override fun onViewDetachedFromWindow(holder: MediaViewHolder) {
        holder.onViewDetachedFromWindow()

        super.onViewDetachedFromWindow(holder)
    }

    inner class MediaViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        // Views
        private val imageView = view.findViewById<GlideZoomImageView>(R.id.imageView)

        @OptIn(androidx.media3.common.util.UnstableApi::class)
        private val playerControlView =
            view.findViewById<PlayerControlView>(androidx.media3.ui.R.id.exo_controller)
        private val playerView = view.findViewById<PlayerView>(R.id.playerView)
        private val fullscreenButton = view.findViewById<ImageButton>(R.id.fullscreenButton)

        private var media: Media? = null
        private var motionPhoto: MotionPhoto? = null
        private var isCurrentlyDisplayedView = false
        private var doubleTapSeekListener: DoubleTapSeekListener? = null
        private var edgeTapNavigationListener: EdgeTapNavigationListener? = null

        @OptIn(androidx.media3.common.util.UnstableApi::class)
        private val mediaPositionObserver: (Int?) -> Unit = { currentPosition: Int? ->
            isCurrentlyDisplayedView = currentPosition == bindingAdapterPosition

            val isVideo = media?.mediaType == MediaType.VIDEO || motionPhoto != null
            val isNowVideoPlayer = isCurrentlyDisplayedView && isVideo

            imageView.isVisible = !isNowVideoPlayer
            playerView.isVisible = isNowVideoPlayer
            fullscreenButton.isVisible = isNowVideoPlayer

            if (!isNowVideoPlayer || localPlayerViewModel.fullscreenMode.value) {
                playerControlView.hideImmediately()
            } else {
                playerControlView.show()
            }

            val player = when (isNowVideoPlayer) {
                true -> localPlayerViewModel.exoPlayer
                false -> null
            }

            playerView.player = player
            playerControlView.player = player

            // Update double-tap listener
            updateDoubleTapListener(isNowVideoPlayer)
            
            // Update edge tap navigation listener
            updateEdgeTapListener()
            
            // Update native seek buttons visibility
            if (isNowVideoPlayer) {
                updateNativeSeekButtons()
            }
        }

        private val sheetsHeightObserver = { sheetsHeight: Pair<Int, Int> ->
            if (!localPlayerViewModel.fullscreenMode.value) {
                val (topHeight, bottomHeight) = sheetsHeight

                // Place the player controls between the two sheets
                playerControlView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    topMargin = topHeight
                    bottomMargin = bottomHeight
                }
            }
        }

        @OptIn(androidx.media3.common.util.UnstableApi::class)
        private val fullscreenModeObserver = { fullscreenMode: Boolean ->
            if (media?.mediaType == MediaType.VIDEO) {
                playerControlView.fade(!fullscreenMode)
            }
        }

        private val displayedMediaToMotionPhotoObserver = { it: Pair<Media?, MotionPhoto?> ->
            val (displayedMedia, motionPhoto) = it
            this.motionPhoto = motionPhoto
            // Trigger a refresh of the UI
            mediaPositionObserver(localPlayerViewModel.mediaPosition.value)
        }

        private var observersJob: Job? = null

        init {
            imageView.setOnClickListener {
                localPlayerViewModel.toggleFullscreenMode()
            }
            playerView.setOnClickListener {
                localPlayerViewModel.toggleFullscreenMode()
            }
            fullscreenButton.setOnClickListener {
                localPlayerViewModel.toggleFullscreenMode()
            }
        }

        @OptIn(androidx.media3.common.util.UnstableApi::class)
        private fun updateDoubleTapListener(isVideoPlayer: Boolean) {
            val context = itemView.context
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
            val isEnabled = sharedPreferences.doubleTapSeekEnabled
            val seekTime = sharedPreferences.doubleTapSeekTime

            if (isVideoPlayer && isEnabled) {
                if (doubleTapSeekListener == null) {
                    doubleTapSeekListener = DoubleTapSeekListener(
                        context,
                        localPlayerViewModel.exoPlayer,
                        seekTime
                    ) { forward, milliseconds ->
                        val seconds = (milliseconds / 1000).toInt()
                        val messageRes = if (forward) {
                            R.string.double_tap_seek_forward
                        } else {
                            R.string.double_tap_seek_backward
                        }
                        val message = context.getString(messageRes, seconds)
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                }
                // Combine with edge tap listener if enabled
                val edgeEnabled = sharedPreferences.edgeTapNavigationEnabled
                if (edgeEnabled && onNavigate != null && edgeTapNavigationListener != null) {
                    playerView.setOnTouchListener { view, event ->
                        // Try edge tap first (single tap), then double tap
                        val edgeHandled = edgeTapNavigationListener?.onTouch(view, event) ?: false
                        val doubleTapHandled = doubleTapSeekListener?.onTouch(view, event) ?: false
                        edgeHandled || doubleTapHandled
                    }
                } else {
                    playerView.setOnTouchListener(doubleTapSeekListener)
                }
            } else {
                doubleTapSeekListener = null
                // Check if edge tap should still be active for video
                val edgeEnabled = sharedPreferences.edgeTapNavigationEnabled
                if (isVideoPlayer && edgeEnabled && onNavigate != null && edgeTapNavigationListener != null) {
                    playerView.setOnTouchListener(edgeTapNavigationListener)
                } else {
                    playerView.setOnTouchListener(null)
                }
            }
        }

        @OptIn(androidx.media3.common.util.UnstableApi::class)
        private fun updateNativeSeekButtons() {
            val context = itemView.context
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
            val hideButtons = sharedPreferences.hideNativeSeekButtons
            
            // Update PlayerView to show/hide rewind and fast-forward buttons
            playerView.setShowRewindButton(!hideButtons)
            playerView.setShowFastForwardButton(!hideButtons)
        }

        private fun updateEdgeTapListener() {
            val context = itemView.context
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
            val isEnabled = sharedPreferences.edgeTapNavigationEnabled

            if (isEnabled && onNavigate != null) {
                if (edgeTapNavigationListener == null) {
                    edgeTapNavigationListener = EdgeTapNavigationListener(context, onNavigate)
                }
                // Attach to imageView for images
                // Use a combined touch listener that doesn't interfere with click listener
                imageView.setOnTouchListener { view, event ->
                    val handled = edgeTapNavigationListener?.onTouch(view, event) ?: false
                    // Return false to allow click listener to work
                    false
                }
            } else {
                edgeTapNavigationListener = null
                imageView.setOnTouchListener(null)
            }
        }

        fun bind(media: Media) {
            this.media = media

            imageView.load(media.uri)
            
            // Initialize edge tap navigation listener immediately
            updateEdgeTapListener()
        }

        fun onViewAttachedToWindow() {
            // Initialize edge tap listener when view is attached and ready
            updateEdgeTapListener()
            
            observersJob = itemView.findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
                launch {
                    localPlayerViewModel.mediaPosition.collectLatest(mediaPositionObserver)
                }
                launch {
                    localPlayerViewModel.sheetsHeight.collectLatest(sheetsHeightObserver)
                }
                launch {
                    localPlayerViewModel.fullscreenMode.collectLatest(fullscreenModeObserver)
                }
                launch {
                    localPlayerViewModel.displayedMediaToMotionPhoto.collectLatest(
                        displayedMediaToMotionPhotoObserver
                    )
                }
            }
        }

        @OptIn(androidx.media3.common.util.UnstableApi::class)
        fun onViewDetachedFromWindow() {
            observersJob?.cancel()
            observersJob = null

            playerView.player = null
            playerControlView.player = null
        }
    }
}
