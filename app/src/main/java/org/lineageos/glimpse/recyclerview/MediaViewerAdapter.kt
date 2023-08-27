/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.recyclerview

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerControlView
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import org.lineageos.glimpse.R
import org.lineageos.glimpse.ext.fade
import org.lineageos.glimpse.models.Media
import org.lineageos.glimpse.models.MediaType
import org.lineageos.glimpse.viewmodels.MediaViewerViewModel

@androidx.media3.common.util.UnstableApi
class MediaViewerAdapter(
    private val exoPlayer: Lazy<ExoPlayer>,
    private val mediaViewerViewModel: MediaViewerViewModel,
) : RecyclerView.Adapter<MediaViewerAdapter.MediaViewHolder>() {
    var data: Array<Media> = arrayOf()
        set(value) {
            if (value.contentEquals(field)) {
                return
            }

            field = value

            field.let {
                @Suppress("NotifyDataSetChanged") notifyDataSetChanged()
            }
        }

    init {
        setHasStableIds(true)
    }

    override fun getItemCount() = data.size

    override fun getItemId(position: Int) = data[position].id

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = MediaViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.media_view, parent, false),
        exoPlayer, mediaViewerViewModel
    )

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        holder.bind(data[position], position)
    }

    override fun onViewAttachedToWindow(holder: MediaViewHolder) {
        super.onViewAttachedToWindow(holder)
        holder.onViewAttachedToWindow()
    }

    override fun onViewDetachedFromWindow(holder: MediaViewHolder) {
        super.onViewDetachedFromWindow(holder)
        holder.onViewDetachedFromWindow()
    }

    fun getItemAtPosition(position: Int) = data[position]

    class MediaViewHolder(
        private val view: View,
        private val exoPlayer: Lazy<ExoPlayer>,
        private val mediaViewerViewModel: MediaViewerViewModel,
    ) : RecyclerView.ViewHolder(view) {
        // Views
        private val imageView = view.findViewById<ImageView>(R.id.imageView)
        private val playerControlView = view.findViewById<PlayerControlView>(R.id.exo_controller)
        private val playerView = view.findViewById<PlayerView>(R.id.playerView)

        private lateinit var media: Media
        private var position = -1

        private val mediaPositionObserver = { currentPosition: Int ->
            val isNowVideoPlayer = currentPosition == position && media.mediaType == MediaType.VIDEO

            imageView.isVisible = !isNowVideoPlayer
            playerView.isVisible = isNowVideoPlayer

            if (!isNowVideoPlayer || mediaViewerViewModel.fullscreenModeLiveData.value == true) {
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

        private val sheetsHeightObserver = { sheetsHeight: Pair<Int, Int> ->
            if (mediaViewerViewModel.fullscreenModeLiveData.value != true) {
                val (topHeight, bottomHeight) = sheetsHeight

                // Place the player controls between the two sheets
                playerControlView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    topMargin = topHeight
                    bottomMargin = bottomHeight
                }
            }
        }

        private val fullscreenModeObserver = { fullscreenMode: Boolean ->
            if (media.mediaType == MediaType.VIDEO) {
                playerControlView.fade(!fullscreenMode)
            }
        }

        init {
            imageView.setOnClickListener {
                mediaViewerViewModel.toggleFullscreenMode()
            }
            playerView.setOnClickListener {
                mediaViewerViewModel.toggleFullscreenMode()
            }
        }

        fun bind(media: Media, position: Int) {
            this.media = media
            this.position = position
            imageView.load(media.externalContentUri) {
                memoryCacheKey("full_${media.id}")
                placeholderMemoryCacheKey("thumbnail_${media.id}")
            }
        }

        fun onViewAttachedToWindow() {
            view.findViewTreeLifecycleOwner()?.let {
                mediaViewerViewModel.mediaPositionLiveData.observe(it, mediaPositionObserver)
                mediaViewerViewModel.sheetsHeightLiveData.observe(it, sheetsHeightObserver)
                mediaViewerViewModel.fullscreenModeLiveData.observe(it, fullscreenModeObserver)
            }
        }

        fun onViewDetachedFromWindow() {
            mediaViewerViewModel.mediaPositionLiveData.removeObserver(mediaPositionObserver)
            mediaViewerViewModel.sheetsHeightLiveData.removeObserver(sheetsHeightObserver)
            mediaViewerViewModel.fullscreenModeLiveData.removeObserver(fullscreenModeObserver)
        }
    }
}
