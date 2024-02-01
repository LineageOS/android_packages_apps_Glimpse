/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.recyclerview

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.recyclerview.selection.ItemDetailsLookup
import androidx.recyclerview.selection.ItemKeyProvider
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import org.lineageos.glimpse.R
import org.lineageos.glimpse.models.MediaStoreMedia
import org.lineageos.glimpse.models.MediaType
import org.lineageos.glimpse.viewmodels.AlbumViewerViewModel
import org.lineageos.glimpse.viewmodels.AlbumViewerViewModel.DataType
import java.util.Date
import kotlin.reflect.safeCast

class ThumbnailAdapter(
    private val model: AlbumViewerViewModel,
    private val onItemSelected: (media: MediaStoreMedia, anchor: View) -> Unit,
) : ListAdapter<DataType, RecyclerView.ViewHolder>(DATA_TYPE_COMPARATOR) {
    // We store a reverse lookup list for performance reasons
    private var mediaToIndex: Map<MediaStoreMedia, Int>? = null

    var selectionTracker: SelectionTracker<MediaStoreMedia>? = null

    val itemKeyProvider = object : ItemKeyProvider<MediaStoreMedia>(SCOPE_CACHED) {
        override fun getKey(position: Int) = getItem(position).let {
            DataType.Thumbnail::class.safeCast(it)?.media
        }

        override fun getPosition(key: MediaStoreMedia) = mediaToIndex?.get(key) ?: -1
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        LayoutInflater.from(parent.context).let { layoutInflater ->
            when (viewType) {
                ViewTypes.THUMBNAIL.ordinal -> ThumbnailViewHolder(
                    layoutInflater.inflate(R.layout.thumbnail_view, parent, false),
                    model, onItemSelected
                )

                ViewTypes.DATE_HEADER.ordinal -> DateHeaderViewHolder(
                    layoutInflater.inflate(R.layout.date_header_view, parent, false)
                )

                else -> throw Exception("Unknown view type $viewType")
            }
        }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder.itemViewType) {
            ViewTypes.THUMBNAIL.ordinal -> {
                val thumbnailViewHolder = holder as ThumbnailViewHolder
                val media = (getItem(position) as DataType.Thumbnail).media
                thumbnailViewHolder.bind(
                    media, selectionTracker?.isSelected(media) == true,
                )
            }

            ViewTypes.DATE_HEADER.ordinal -> {
                val dateHeaderViewHolder = holder as DateHeaderViewHolder
                dateHeaderViewHolder.bind((getItem(position) as DataType.DateHeader).date)
            }
        }
    }

    override fun onCurrentListChanged(
        previousList: MutableList<DataType>,
        currentList: MutableList<DataType>
    ) {
        super.onCurrentListChanged(previousList, currentList)

        // This gets randomly called with null as argument
        if (currentList == null) {
            return
        }

        val dataTypeToIndex = mutableMapOf<MediaStoreMedia, Int>()
        for (i in currentList.indices) {
            DataType.Thumbnail::class.safeCast(currentList[i])?.let {
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
        super.onViewDetachedFromWindow(holder)

        ThumbnailViewHolder::class.safeCast(holder)?.onViewDetachedToWindow()
    }

    override fun getItemViewType(position: Int) = getItem(position).viewType

    companion object {
        val DATA_TYPE_COMPARATOR = object : DiffUtil.ItemCallback<DataType>() {
            override fun areItemsTheSame(oldItem: DataType, newItem: DataType) = when {
                oldItem is DataType.Thumbnail && newItem is DataType.Thumbnail ->
                    oldItem.media.id == newItem.media.id

                oldItem is DataType.DateHeader && newItem is DataType.DateHeader ->
                    oldItem.date == newItem.date

                else -> false
            }

            override fun areContentsTheSame(oldItem: DataType, newItem: DataType) = when {
                oldItem is DataType.Thumbnail && newItem is DataType.Thumbnail ->
                    oldItem.media.id == newItem.media.id &&
                            oldItem.media.dateModified == newItem.media.dateModified

                oldItem is DataType.DateHeader && newItem is DataType.DateHeader ->
                    oldItem.date == newItem.date

                else -> false
            }
        }
    }

    class ThumbnailViewHolder(
        private val view: View,
        private val model: AlbumViewerViewModel,
        private val onItemSelected: (media: MediaStoreMedia, anchor: View) -> Unit,
    ) : RecyclerView.ViewHolder(view) {
        // Views
        private val selectionCheckedImageView =
            itemView.findViewById<ImageView>(R.id.selectionCheckedImageView)
        private val selectionScrimView = itemView.findViewById<View>(R.id.selectionScrimView)
        private val videoOverlayImageView =
            itemView.findViewById<ImageView>(R.id.videoOverlayImageView)!!
        private val thumbnailImageView = itemView.findViewById<ImageView>(R.id.thumbnailImageView)!!

        private lateinit var media: MediaStoreMedia
        private var isSelected = false

        private val inSelectionModeObserver = Observer { inSelectionMode: Boolean ->
            selectionCheckedImageView.isVisible = inSelectionMode
        }

        val itemDetails = object : ItemDetailsLookup.ItemDetails<MediaStoreMedia>() {
            override fun getPosition() = bindingAdapterPosition
            override fun getSelectionKey() = media
        }

        fun onViewAttachedToWindow() {
            view.findViewTreeLifecycleOwner()?.let {
                model.inSelectionMode.observe(it, inSelectionModeObserver)
            }
        }

        fun onViewDetachedToWindow() {
            model.inSelectionMode.removeObserver(inSelectionModeObserver)
        }

        fun bind(media: MediaStoreMedia, isSelected: Boolean = false) {
            this.media = media
            this.isSelected = isSelected

            itemView.setOnClickListener {
                onItemSelected(media, thumbnailImageView)
            }

            thumbnailImageView.load(media.uri) {
                memoryCacheKey("thumbnail_${media.id}")
                size(DisplayAwareGridLayoutManager.MAX_THUMBNAIL_SIZE)
                placeholder(R.drawable.thumbnail_placeholder)
            }
            videoOverlayImageView.isVisible = media.mediaType == MediaType.VIDEO

            if (isSelected) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val blurRenderEffect = RenderEffect.createBlurEffect(
                        BLUR_RADIUS, BLUR_RADIUS,
                        Shader.TileMode.MIRROR
                    )
                    thumbnailImageView.setRenderEffect(blurRenderEffect)
                } else {
                    selectionScrimView.isVisible = true
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    thumbnailImageView.setRenderEffect(null)
                } else {
                    selectionScrimView.isVisible = false
                }
            }
            selectionCheckedImageView.setImageResource(
                when (isSelected) {
                    true -> R.drawable.ic_check_circle
                    false -> R.drawable.ic_check_circle_outline
                }
            )
        }

        companion object {
            private const val BLUR_RADIUS = 15f
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

    enum class ViewTypes {
        THUMBNAIL,
        DATE_HEADER,
    }
}
