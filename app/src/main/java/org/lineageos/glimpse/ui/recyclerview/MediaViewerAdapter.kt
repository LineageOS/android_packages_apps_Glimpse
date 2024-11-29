/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.ui.recyclerview

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerControlView
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import com.github.panpf.zoomimage.CoilZoomImageView
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.lineageos.glimpse.R
import org.lineageos.glimpse.ext.fade
import org.lineageos.glimpse.models.FileType
import org.lineageos.glimpse.models.Media
import org.lineageos.glimpse.viewmodels.LocalPlayerViewModel

class MediaViewerAdapter(
    private val exoPlayer: Lazy<ExoPlayer>,
    private val localPlayerViewModel: LocalPlayerViewModel,
) : ListAdapter<Media, MediaViewerAdapter.MediaViewHolder>(UniqueItemDiffCallback()) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = MediaViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.media_view, parent, false),
    )

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    @androidx.media3.common.util.UnstableApi
    override fun onViewAttachedToWindow(holder: MediaViewHolder) {
        super.onViewAttachedToWindow(holder)
        holder.onViewAttachedToWindow()
    }

    @androidx.media3.common.util.UnstableApi
    override fun onViewDetachedFromWindow(holder: MediaViewHolder) {
        super.onViewDetachedFromWindow(holder)
        holder.onViewDetachedFromWindow()
    }

    inner class MediaViewHolder(private val view: View) : RecyclerView.ViewHolder(view) {
        // Views
        private val imageView = view.findViewById<CoilZoomImageView>(R.id.imageView)

        @androidx.media3.common.util.UnstableApi
        private val playerControlView = view.findViewById<PlayerControlView>(R.id.exo_controller)
        private val playerView = view.findViewById<PlayerView>(R.id.playerView)

        private var media: Media? = null
        private var isCurrentlyDisplayedView = false

        @androidx.media3.common.util.UnstableApi
        private val mediaPositionObserver: (Int?) -> Unit = { currentPosition: Int? ->
            isCurrentlyDisplayedView = currentPosition == bindingAdapterPosition

            updateDisplayedMedia()

            val isNowVideoPlayer = isCurrentlyDisplayedView && media?.fileType == FileType.VIDEO

            imageView.isVisible = !isNowVideoPlayer
            playerView.isVisible = isNowVideoPlayer

            if (!isNowVideoPlayer || localPlayerViewModel.fullscreenMode.value) {
                playerControlView.hideImmediately()
            } else {
                playerControlView.show()
            }

            val player = when (isNowVideoPlayer) {
                true -> exoPlayer.value
                false -> null
            }

            playerView.player = player
            playerControlView.player = player
        }

        @androidx.media3.common.util.UnstableApi
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

        @androidx.media3.common.util.UnstableApi
        private val fullscreenModeObserver = { fullscreenMode: Boolean ->
            if (media?.fileType == FileType.VIDEO) {
                playerControlView.fade(!fullscreenMode)
            }
        }

        private var observersJob: Job? = null

        init {
            imageView.setOnClickListener {
                localPlayerViewModel.toggleFullscreenMode()
            }
            playerView.setOnClickListener {
                localPlayerViewModel.toggleFullscreenMode()
            }
        }

        fun bind(media: Media) {
            this.media = media

            updateDisplayedMedia()

            imageView.load(media.uri) {
                memoryCacheKey("full_${media.uri}")
                placeholderMemoryCacheKey("thumbnail_${media.uri}")
            }
        }

        @androidx.media3.common.util.UnstableApi
        fun onViewAttachedToWindow() {
            observersJob = view.findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
                launch {
                    localPlayerViewModel.mediaPosition.collectLatest(mediaPositionObserver)
                }
                launch {
                    localPlayerViewModel.sheetsHeight.collectLatest(sheetsHeightObserver)
                }
                launch {
                    localPlayerViewModel.fullscreenMode.collectLatest(fullscreenModeObserver)
                }
            }
        }

        @androidx.media3.common.util.UnstableApi
        fun onViewDetachedFromWindow() {
            observersJob?.cancel()
            observersJob = null
        }

        /**
         * If this is the currently displayed view, push the shown media to the view model.
         */
        private fun updateDisplayedMedia() {
            if (isCurrentlyDisplayedView) {
                localPlayerViewModel.setDisplayedMedia(media)
            }
        }
    }
}
