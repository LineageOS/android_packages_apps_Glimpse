/*
 * SPDX-FileCopyrightText: 2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.ui.dialogs

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isEmpty
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.lineageos.glimpse.R
import org.lineageos.glimpse.ext.loadThumbnail
import org.lineageos.glimpse.models.Album
import org.lineageos.glimpse.models.Media
import org.lineageos.glimpse.models.RequestStatus
import org.lineageos.glimpse.models.Thumbnail
import org.lineageos.glimpse.models.UniqueItem
import org.lineageos.glimpse.ui.recyclerview.SimpleListAdapter
import org.lineageos.glimpse.ui.recyclerview.UniqueItemDiffCallback
import org.lineageos.glimpse.viewmodels.AlbumsViewModel

class AddToAlbumBottomSheetDialog(
    context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val viewModel: AlbumsViewModel,
    private val medias: List<Media>,
) : BottomSheetDialog(context) {

    private sealed interface AddToAlbumItem : UniqueItem<AddToAlbumItem> {
        data object NewAlbum : AddToAlbumItem {
            override fun areItemsTheSame(other: AddToAlbumItem) = other is NewAlbum
            override fun areContentsTheSame(other: AddToAlbumItem) = true
        }

        data class AlbumItem(val album: Album) : AddToAlbumItem {
            override fun areItemsTheSame(other: AddToAlbumItem) =
                (other as? AlbumItem)?.album?.uri == album.uri

            override fun areContentsTheSame(other: AddToAlbumItem) =
                (other as? AlbumItem)?.album?.areContentsTheSame(album) ?: false
        }
    }

    // Views
    private val contentView by lazy { findViewById<View>(android.R.id.content)!! }
    private val recyclerView by lazy { findViewById<RecyclerView>(R.id.recyclerView)!! }

    // Adapter
    private val adapter = object : SimpleListAdapter<AddToAlbumItem, View>(
        UniqueItemDiffCallback(), { parent ->
            LayoutInflater.from(parent.context).inflate(
                R.layout.list_item, parent, false
            )
        }) {
        override fun getItemViewType(position: Int) = position

        override fun ViewHolder.onPrepareView() {
            view.setOnClickListener {
                when (val item = item) {
                    is AddToAlbumItem.NewAlbum -> showCreateAlbumDialog()
                    is AddToAlbumItem.AlbumItem -> addMediasToAlbum(item.album)
                    null -> {}
                }
            }
        }

        override fun ViewHolder.onBindView(item: AddToAlbumItem) {
            val headlineTextView = view.findViewById<TextView>(R.id.headlineTextView)
            val supportingTextView = view.findViewById<TextView>(R.id.supportingTextView)
            val leadingIconImageView = view.findViewById<ImageView>(R.id.leadingIconImageView)
            val leadingViewContainerFrameLayout =
                view.findViewById<ViewGroup>(R.id.leadingViewContainerFrameLayout)

            when (item) {
                is AddToAlbumItem.NewAlbum -> {
                    headlineTextView.text = context.getString(R.string.add_to_album_new_album)
                    supportingTextView.visibility = View.GONE
                    leadingIconImageView.apply {
                        visibility = View.VISIBLE
                        setImageResource(R.drawable.ic_albums)
                    }
                    leadingViewContainerFrameLayout.visibility = View.GONE
                }

                is AddToAlbumItem.AlbumItem -> {
                    val album = item.album
                    headlineTextView.text = album.name
                    supportingTextView.apply {
                        visibility = View.VISIBLE
                        text = context.resources.getQuantityString(
                            R.plurals.album_thumbnail_items,
                            album.mediaCount ?: 0,
                            album.mediaCount ?: 0
                        )
                    }

                    leadingIconImageView.visibility = View.GONE
                    leadingViewContainerFrameLayout.apply {
                        visibility = View.VISIBLE
                        if (isEmpty()) {
                            LayoutInflater.from(context).inflate(
                                R.layout.album_thumbnail_view, this, true
                            ).apply {
                                updateLayoutParams {
                                    width = context.resources.getDimensionPixelSize(
                                        R.dimen.album_thumbnail_size_small
                                    )
                                    height = width
                                }
                            }
                        }

                        findViewById<ImageView>(R.id.thumbnailImageView)?.loadThumbnail(
                            album.thumbnail, options = RequestOptions().override(
                                    Thumbnail.MAX_THUMBNAIL_SIZE, Thumbnail.MAX_THUMBNAIL_SIZE
                                ).centerCrop()
                        )
                    }
                }
            }
        }
    }

    init {
        setContentView(R.layout.add_to_album_bottom_sheet_dialog)

        ViewCompat.setOnApplyWindowInsetsListener(contentView) { _, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )

            contentView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = insets.left
                rightMargin = insets.right
            }

            windowInsets
        }

        recyclerView.adapter = adapter

        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.virtualAlbums.collectLatest {
                    when (it) {
                        is RequestStatus.Success -> {
                            adapter.submitList(
                                listOf(AddToAlbumItem.NewAlbum) + it.data.map { album ->
                                    AddToAlbumItem.AlbumItem(album)
                                })
                        }

                        else -> {
                            // Do nothing
                        }
                    }
                }
            }
        }

        viewModel.loadAlbums(AlbumsViewModel.AlbumsRequest())
    }

    private fun showCreateAlbumDialog() {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_edit_text, null)
        val editText = view.findViewById<TextInputEditText>(R.id.editText)

        MaterialAlertDialogBuilder(context).setTitle(R.string.add_to_album_new_album).setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = editText.text.toString()
                if (name.isNotBlank()) {
                    viewModel.createAlbum(name) {
                        viewModel.addMediaToAlbum(it, medias.map { media -> media.uri })
                        dismiss()
                    }
                }
            }.setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun addMediasToAlbum(album: Album) {
        viewModel.addMediaToAlbum(album.uri, medias.map { it.uri })
        dismiss()
    }
}
