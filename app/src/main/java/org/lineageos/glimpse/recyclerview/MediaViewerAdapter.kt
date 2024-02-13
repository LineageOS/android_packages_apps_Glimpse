/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.recyclerview

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerControlView
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.github.panpf.zoomimage.CoilZoomImageView
import org.lineageos.glimpse.R
import org.lineageos.glimpse.ext.fade
import org.lineageos.glimpse.models.Media
import org.lineageos.glimpse.models.MediaStoreMedia
import org.lineageos.glimpse.models.MediaType
import org.lineageos.glimpse.viewmodels.MediaViewerUIViewModel
import org.lineageos.glimpse.viewmodels.MediaViewerViewModel
import kotlin.reflect.safeCast

class MediaViewerAdapter(
    private val exoPlayer: Lazy<ExoPlayer>,
    private val mediaViewerViewModel: MediaViewerViewModel,
    private val mediaViewerUIViewModel: MediaViewerUIViewModel,
) : ListAdapter<Media, MediaViewerAdapter.MediaViewHolder>(DATA_TYPE_COMPARATOR) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = MediaViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.media_view, parent, false),
        exoPlayer, mediaViewerViewModel, mediaViewerUIViewModel
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

    class MediaViewHolder(
        private val view: View,
        private val exoPlayer: Lazy<ExoPlayer>,
        private val mediaViewerViewModel: MediaViewerViewModel,
        private val mediaViewerUIViewModel: MediaViewerUIViewModel,
    ) : RecyclerView.ViewHolder(view) {
        // Views
        private val imageView = view.findViewById<CoilZoomImageView>(R.id.imageView)

        @androidx.media3.common.util.UnstableApi
        private val playerControlView = view.findViewById<PlayerControlView>(R.id.exo_controller)
        private val playerView = view.findViewById<PlayerView>(R.id.playerView)

        private var media: Media? = null
        private var isCurrentlyDisplayedView = false

        @androidx.media3.common.util.UnstableApi
        private val mediaPositionObserver: (Int) -> Unit = { currentPosition: Int ->
            isCurrentlyDisplayedView = currentPosition == bindingAdapterPosition

            updateDisplayedMedia()

            val isNowVideoPlayer = isCurrentlyDisplayedView && media?.mediaType == MediaType.VIDEO

            imageView.isVisible = !isNowVideoPlayer
            playerView.isVisible = isNowVideoPlayer

            if (!isNowVideoPlayer || mediaViewerUIViewModel.fullscreenModeLiveData.value == true) {
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
            if (mediaViewerUIViewModel.fullscreenModeLiveData.value != true) {
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
            if (media?.mediaType == MediaType.VIDEO) {
                playerControlView.fade(!fullscreenMode)
            }
        }

        init {
            imageView.setOnClickListener {
                mediaViewerUIViewModel.toggleFullscreenMode()
            }
            playerView.setOnClickListener {
                mediaViewerUIViewModel.toggleFullscreenMode()
            }
        }

        fun bind(media: Media) {
            this.media = media

            updateDisplayedMedia()

            imageView.load(media.uri) {
                MediaStoreMedia::class.safeCast(media)?.let {
                    memoryCacheKey("full_${it.id}")
                    placeholderMemoryCacheKey("thumbnail_${it.id}")
                }
            }
        }

        @androidx.media3.common.util.UnstableApi
        fun onViewAttachedToWindow() {
            view.findViewTreeLifecycleOwner()?.let {
                mediaViewerViewModel.mediaPositionLiveData.observe(it, mediaPositionObserver)
                mediaViewerUIViewModel.sheetsHeightLiveData.observe(it, sheetsHeightObserver)
                mediaViewerUIViewModel.fullscreenModeLiveData.observe(it, fullscreenModeObserver)
            }
        }

        @androidx.media3.common.util.UnstableApi
        fun onViewDetachedFromWindow() {
            mediaViewerViewModel.mediaPositionLiveData.removeObserver(mediaPositionObserver)
            mediaViewerUIViewModel.sheetsHeightLiveData.removeObserver(sheetsHeightObserver)
            mediaViewerUIViewModel.fullscreenModeLiveData.removeObserver(fullscreenModeObserver)
        }

        /**
         * If this is the currently displayed view, push the shown media to the view model.
         */
        private fun updateDisplayedMedia() {
            if (isCurrentlyDisplayedView) {
                mediaViewerUIViewModel.displayedMedia.value = media
            }
        }
    }

    companion object {
        val DATA_TYPE_COMPARATOR = object : DiffUtil.ItemCallback<Media>() {
            override fun areItemsTheSame(oldItem: Media, newItem: Media) = when {
                oldItem is MediaStoreMedia && newItem is MediaStoreMedia ->
                    oldItem.id == newItem.id

                else -> oldItem.uri == oldItem.uri
            }

            override fun areContentsTheSame(oldItem: Media, newItem: Media) = when {
                oldItem is MediaStoreMedia && newItem is MediaStoreMedia ->
                    oldItem.id == newItem.id &&
                            oldItem.dateModified == newItem.dateModified &&
                            oldItem.isFavorite == newItem.isFavorite

                else -> oldItem.uri == oldItem.uri
            }
        }
    }
}
