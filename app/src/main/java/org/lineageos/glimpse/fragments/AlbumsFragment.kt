/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.lineageos.glimpse.R
import org.lineageos.glimpse.ext.getViewProperty
import org.lineageos.glimpse.thumbnail.AlbumThumbnailAdapter
import org.lineageos.glimpse.viewmodels.MediaViewModel

/**
 * An albums list visualizer.
 * Use the [AlbumsFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class AlbumsFragment : Fragment() {
    // View models
    private val mediaViewModel: MediaViewModel by viewModels { MediaViewModel.Factory }

    // Views
    private val albumsRecyclerView by getViewProperty<RecyclerView>(R.id.albumsRecyclerView)

    // Fragments
    private val parentNavController by lazy {
        requireParentFragment().requireParentFragment().findNavController()
    }

    // MediaStore
    private val albumThumbnailAdapter by lazy { AlbumThumbnailAdapter(parentNavController) }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_albums, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val context = requireContext()

        albumsRecyclerView.layoutManager = GridLayoutManager(context, 2)
        albumsRecyclerView.adapter = albumThumbnailAdapter

        ViewCompat.setOnApplyWindowInsetsListener(view) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())

            albumsRecyclerView.updateLayoutParams<MarginLayoutParams> {
                leftMargin = insets.left
                rightMargin = insets.right
            }
            albumsRecyclerView.updatePadding(bottom = insets.bottom)

            windowInsets
        }

        viewLifecycleOwner.lifecycleScope.launch {
            mediaViewModel.albums.collectLatest {
                albumThumbnailAdapter.data = it.toTypedArray()
            }
        }
    }

    companion object {
        private fun createBundle() = bundleOf()

        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @return A new instance of fragment ReelsFragment.
         */
        fun newInstance() = AlbumsFragment().apply {
            arguments = createBundle()
        }
    }
}
