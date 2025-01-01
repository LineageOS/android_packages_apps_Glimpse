/*
 * SPDX-FileCopyrightText: 2023-2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.ui.recyclerview

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.view.isVisible
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.recyclerview.selection.ItemDetailsLookup
import androidx.recyclerview.selection.ItemKeyProvider
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.lineageos.glimpse.R
import org.lineageos.glimpse.ext.load
import org.lineageos.glimpse.models.Media
import org.lineageos.glimpse.models.MediaType
import org.lineageos.glimpse.viewmodels.AlbumViewModel
import java.util.Date
import kotlin.reflect.safeCast

class ThumbnailAdapter : ListAdapter<AlbumViewModel.AlbumContent, RecyclerView.ViewHolder>(
    UniqueItemDiffCallback()
) {
    private val inSelectionMode = MutableLiveData(false)

    // We store a reverse lookup list for performance reasons
    private var mediaToIndex: Map<Media, Int>? = null

    var selectionTracker: SelectionTracker<Media>? = null

    val itemKeyProvider = object : ItemKeyProvider<Media>(SCOPE_CACHED) {
        override fun getKey(position: Int) = getItem(position).let {
            AlbumViewModel.AlbumContent.MediaItem::class.safeCast(it)?.media
        }

        override fun getPosition(key: Media) = mediaToIndex?.get(key) ?: -1
    }

    private var onItemSelected: (media: Media) -> Unit = {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        LayoutInflater.from(parent.context).let { layoutInflater ->
            when (viewType) {
                AlbumViewModel.AlbumContent.ViewType.THUMBNAIL.ordinal -> ThumbnailViewHolder(
                    layoutInflater.inflate(R.layout.thumbnail_view, parent, false)
                )

                AlbumViewModel.AlbumContent.ViewType.DATE_HEADER.ordinal -> DateHeaderViewHolder(
                    layoutInflater.inflate(R.layout.date_header_view, parent, false)
                )

                else -> throw Exception("Unknown view type $viewType")
            }
        }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder.itemViewType) {
            AlbumViewModel.AlbumContent.ViewType.THUMBNAIL.ordinal -> {
                val thumbnailViewHolder = holder as ThumbnailViewHolder
                val media = (getItem(position) as AlbumViewModel.AlbumContent.MediaItem).media
                thumbnailViewHolder.bind(
                    media, selectionTracker?.isSelected(media) == true,
                )
            }

            AlbumViewModel.AlbumContent.ViewType.DATE_HEADER.ordinal -> {
                val dateHeaderViewHolder = holder as DateHeaderViewHolder
                dateHeaderViewHolder.bind((getItem(position) as AlbumViewModel.AlbumContent.DateHeader).date)
            }
        }
    }

    override fun onCurrentListChanged(
        previousList: MutableList<AlbumViewModel.AlbumContent>,
        currentList: MutableList<AlbumViewModel.AlbumContent>
    ) {
        super.onCurrentListChanged(previousList, currentList)

        val dataTypeToIndex = mutableMapOf<Media, Int>()
        for (i in currentList.indices) {
            AlbumViewModel.AlbumContent.MediaItem::class.safeCast(currentList[i])?.let {
                dataTypeToIndex[it.media] = i
            }
        }
        this.mediaToIndex = dataTypeToIndex.toMap()
    }

    override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
        super.onViewAttachedToWindow(holder)

        ThumbnailViewHolder::class.safeCast(holder)?.onViewAttachedToWindow()
    }

    override fun onViewDetachedFromWindow(holder: RecyclerView.ViewHolder) {
        ThumbnailViewHolder::class.safeCast(holder)?.onViewDetachedFromWindow()

        super.onViewDetachedFromWindow(holder)
    }

    override fun getItemViewType(position: Int) = getItem(position).viewType

    fun setInSelectionMode(inSelectionMode: Boolean) {
        this.inSelectionMode.value = inSelectionMode
    }

    fun setOnItemSelected(onItemSelected: ((media: Media) -> Unit)?) {
        this.onItemSelected = onItemSelected ?: {}
    }

    inner class ThumbnailViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        // Views
        private val selectionCheckedImageView =
            itemView.findViewById<ImageView>(R.id.selectionCheckedImageView)
        private val selectionScrimView = itemView.findViewById<View>(R.id.selectionScrimView)
        private val videoOverlayImageView =
            itemView.findViewById<ImageView>(R.id.videoOverlayImageView)!!
        private val thumbnailImageView = itemView.findViewById<ImageView>(R.id.thumbnailImageView)!!

        private lateinit var media: Media
        private var isSelected = false

        private val inSelectionModeObserver = Observer { inSelectionMode: Boolean ->
            selectionCheckedImageView.isVisible = inSelectionMode
        }

        val itemDetails = object : ItemDetailsLookup.ItemDetails<Media>() {
            override fun getPosition() = bindingAdapterPosition
            override fun getSelectionKey() = media
        }

        fun onViewAttachedToWindow() {
            inSelectionMode.observeForever(inSelectionModeObserver)
        }

        fun onViewDetachedFromWindow() {
            inSelectionMode.removeObserver(inSelectionModeObserver)
        }

        fun bind(media: Media, isSelected: Boolean = false) {
            this.media = media
            this.isSelected = isSelected

            itemView.setOnClickListener {
                onItemSelected(media)
            }

            thumbnailImageView.load(media.uri)
            videoOverlayImageView.isVisible = media.mediaType == MediaType.VIDEO

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                thumbnailImageView.setRenderEffect(
                    when (isSelected) {
                        true -> blurRenderEffect
                        false -> null
                    }
                )
            } else {
                selectionScrimView.isVisible = isSelected
            }

            selectionCheckedImageView.setImageResource(
                when (isSelected) {
                    true -> R.drawable.ic_check_circle
                    false -> R.drawable.ic_check_circle_outline
                }
            )
        }
    }

    class DateHeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        // Views
        private val textView = view as TextView

        fun bind(date: Date) {
            textView.text = DateUtils.getRelativeTimeSpanString(
                date.time,
                System.currentTimeMillis(),
                DateUtils.DAY_IN_MILLIS
            )
        }
    }

    companion object {
        private const val BLUR_RADIUS = 15f

        @RequiresApi(Build.VERSION_CODES.S)
        private val blurRenderEffect = RenderEffect.createBlurEffect(
            BLUR_RADIUS, BLUR_RADIUS,
            Shader.TileMode.MIRROR
        )
    }
}
