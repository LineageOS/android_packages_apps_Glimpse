/*
 * SPDX-FileCopyrightText: 2023-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.fragments

import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.lineageos.glimpse.R
import org.lineageos.glimpse.ext.getViewProperty
import org.lineageos.glimpse.models.FileType
import org.lineageos.glimpse.ui.recyclerview.SimpleListAdapter
import org.lineageos.glimpse.ui.recyclerview.ThumbnailLayoutManager
import org.lineageos.glimpse.ui.recyclerview.UniqueItemDiffCallback
import org.lineageos.glimpse.utils.PermissionsChecker
import org.lineageos.glimpse.utils.PermissionsUtils
import org.lineageos.glimpse.viewmodels.AlbumContent
import org.lineageos.glimpse.viewmodels.ReelsViewModel

class ReelsFragment : Fragment(R.layout.fragment_reels) {
    // View models
    private val viewModel by viewModels<ReelsViewModel>()

    // Views
    private val noMediaLinearLayout by getViewProperty<LinearLayout>(R.id.noMediaLinearLayout)
    private val recyclerView by getViewProperty<RecyclerView>(R.id.recyclerView)
    private val toolbar by getViewProperty<MaterialToolbar>(R.id.toolbar)

    // RecyclerView
    private val adapter by lazy {
        object : SimpleListAdapter<AlbumContent, View>(
            UniqueItemDiffCallback(),
            { context ->
                LayoutInflater.from(context).inflate(R.layout.item_album_content, null)
            },
        ) {
            // Views
            private val ViewHolder.dateTextView
                get() = view.findViewById<TextView>(R.id.dateTextView)
            private val ViewHolder.mediaConstraintLayout
                get() = view.findViewById<ConstraintLayout>(R.id.mediaConstraintLayout)
            private val ViewHolder.selectionCheckedImageView
                get() = view.findViewById<ImageView>(R.id.selectionCheckedImageView)
            private val ViewHolder.selectionScrimView
                get() = view.findViewById<View>(R.id.selectionScrimView)
            private val ViewHolder.thumbnailImageView
                get() = view.findViewById<ImageView>(R.id.thumbnailImageView)
            private val ViewHolder.videoOverlayImageView
                get() = view.findViewById<ImageView>(R.id.videoOverlayImageView)

            override fun getItemViewType(position: Int) = getItem(position).viewType

            override fun ViewHolder.onBindView(item: AlbumContent) {
                when (item) {
                    is AlbumContent.DateHeader -> {
                        mediaConstraintLayout.isVisible = false
                        dateTextView.isVisible = true

                        dateTextView.text = DateUtils.getRelativeTimeSpanString(
                            item.date.time,
                            System.currentTimeMillis(),
                            DateUtils.DAY_IN_MILLIS
                        )
                    }

                    is AlbumContent.MediaItem -> {
                        dateTextView.isVisible = false
                        mediaConstraintLayout.isVisible = true

                        thumbnailImageView.load(item.media.uri)
                        videoOverlayImageView.isVisible = item.media.fileType == FileType.VIDEO
                    }
                }
            }
        }
    }

    // Permissions
    private val permissionsChecker = PermissionsChecker(
        this, PermissionsUtils.mainPermissions
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        toolbar.setupWithNavController(findNavController())

        recyclerView.layoutManager = ThumbnailLayoutManager(
            requireContext(), adapter
        )
        recyclerView.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                permissionsChecker.withPermissionsGranted {
                    loadData()
                }
            }
        }
    }

    override fun onDestroyView() {
        recyclerView.adapter = null

        super.onDestroyView()
    }

    private suspend fun loadData() {
        viewModel.reelsWithHeaders.collectLatest {
            adapter.submitList(it)

            val isEmpty = it.isEmpty()
            noMediaLinearLayout.isVisible = isEmpty
            recyclerView.isVisible = !isEmpty
        }
    }
}
