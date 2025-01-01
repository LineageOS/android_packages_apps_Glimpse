/*
 * SPDX-FileCopyrightText: 2023-2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.ui.recyclerview

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.media3.ui.PlayerControlView
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.github.panpf.zoomimage.GlideZoomImageView
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.lineageos.glimpse.R
import org.lineageos.glimpse.ext.fade
import org.lineageos.glimpse.ext.load
import org.lineageos.glimpse.models.Media
import org.lineageos.glimpse.models.MediaType
import org.lineageos.glimpse.viewmodels.LocalPlayerViewModel

class MediaViewerAdapter(
    private val localPlayerViewModel: LocalPlayerViewModel,
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

        private var media: Media? = null
        private var isCurrentlyDisplayedView = false

        @OptIn(androidx.media3.common.util.UnstableApi::class)
        private val mediaPositionObserver: (Int?) -> Unit = { currentPosition: Int? ->
            isCurrentlyDisplayedView = currentPosition == bindingAdapterPosition

            val isNowVideoPlayer = isCurrentlyDisplayedView && media?.mediaType == MediaType.VIDEO

            imageView.isVisible = !isNowVideoPlayer
            playerView.isVisible = isNowVideoPlayer

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

            imageView.load(media.uri)
        }

        fun onViewAttachedToWindow() {
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
