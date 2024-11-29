/*
 * SPDX-FileCopyrightText: 2023-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.fragments

import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import coil3.request.placeholder
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.shape.MaterialShapeDrawable
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.lineageos.glimpse.R
import org.lineageos.glimpse.ext.getViewProperty
import org.lineageos.glimpse.ext.updatePadding
import org.lineageos.glimpse.fragments.picker.MediaSelectorFragment
import org.lineageos.glimpse.models.Album
import org.lineageos.glimpse.models.RequestStatus
import org.lineageos.glimpse.ui.recyclerview.AlbumThumbnailLayoutManager
import org.lineageos.glimpse.ui.recyclerview.DisplayAwareGridLayoutManager
import org.lineageos.glimpse.ui.recyclerview.SimpleListAdapter
import org.lineageos.glimpse.ui.recyclerview.UniqueItemDiffCallback
import org.lineageos.glimpse.utils.PermissionsChecker
import org.lineageos.glimpse.utils.PermissionsUtils
import org.lineageos.glimpse.viewmodels.AlbumsViewModel
import org.lineageos.glimpse.viewmodels.IntentsViewModel

/**
 * An albums list visualizer.
 */
class AlbumsFragment : Fragment(R.layout.fragment_albums) {
    // View models
    private val albumsViewModel by viewModels<AlbumsViewModel>()
    private val intentsViewModel by viewModels<IntentsViewModel>()

    // Views
    private val appBarLayout by getViewProperty<AppBarLayout>(R.id.appBarLayout)
    private val noMediaLinearLayout by getViewProperty<LinearLayout>(R.id.noMediaLinearLayout)
    private val recyclerView by getViewProperty<RecyclerView>(R.id.recyclerView)
    private val toolbar by getViewProperty<MaterialToolbar>(R.id.toolbar)

    // Fragments
    private val parentNavController by lazy {
        requireParentFragment().requireParentFragment().findNavController()
    }

    // RecyclerView
    private val adapter by lazy {
        object : SimpleListAdapter<Album, View>(
            UniqueItemDiffCallback(),
            { context ->
                LayoutInflater.from(context).inflate(R.layout.album_thumbnail_view, null)
            }
        ) {
            // Views
            private val ViewHolder.descriptionTextView
                get() = view.findViewById<TextView>(R.id.descriptionTextView)!!
            private val ViewHolder.itemsCountTextView
                get() = view.findViewById<TextView>(R.id.itemsCountTextView)!!
            private val ViewHolder.thumbnailImageView
                get() = view.findViewById<ImageView>(R.id.thumbnailImageView)!!

            override fun ViewHolder.onPrepareView() {
                view.setOnClickListener {
                    item?.let {
                        when (isPicking) {
                            true -> parentNavController.navigate(
                                R.id.action_pickerAlbumSelectorFragment_to_pickerMediaSelectorFragment,
                                MediaSelectorFragment.createBundle(it.uri)
                            )
                            false -> parentNavController.navigate(
                                R.id.action_mainFragment_to_albumViewerFragment,
                                AlbumFragment.createBundle(it.uri)
                            )
                        }
                    }
                }
            }

            override fun ViewHolder.onBindView(item: Album) {
                descriptionTextView.text = item.name
                itemsCountTextView.text = view.resources.getQuantityString(
                    R.plurals.album_thumbnail_items, 0, 0
                )

                thumbnailImageView.load(item.thumbnail) {
                    item.thumbnail?.let {
                        memoryCacheKey("thumbnail_${it.uri}")
                    }
                    size(DisplayAwareGridLayoutManager.MAX_THUMBNAIL_SIZE)
                    placeholder(R.drawable.thumbnail_placeholder)
                }
            }
        }
    }

    // Arguments
    private val isPicking: Boolean
        get() = requireArguments().getBoolean(ARG_IS_PICKING)

    // Permissions
    private val permissionsChecker = PermissionsChecker(this, PermissionsUtils.mainPermissions)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Insets
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { _, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )

            toolbar.updatePadding(
                insets,
                start = true,
                end = true,
            )

            windowInsets
        }

        ViewCompat.setOnApplyWindowInsetsListener(recyclerView) { _, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )

            recyclerView.updatePadding(
                insets,
                start = true,
                end = true,
            )

            windowInsets
        }

        val context = requireContext()

        appBarLayout.statusBarForeground = MaterialShapeDrawable.createWithElevationOverlay(context)

        recyclerView.layoutManager = AlbumThumbnailLayoutManager(context)
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

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        recyclerView.layoutManager = AlbumThumbnailLayoutManager(requireContext())
    }

    private suspend fun loadData() {
        albumsViewModel.albums.collectLatest {
            when (it) {
                is RequestStatus.Loading -> {
                    // Do nothing
                }

                is RequestStatus.Success -> {
                    adapter.submitList(it.data)

                    val isEmpty = it.data.isEmpty()
                    recyclerView.isVisible = !isEmpty
                    noMediaLinearLayout.isVisible = isEmpty
                }

                is RequestStatus.Error -> {
                    Log.e(LOG_TAG, "Failed to load albums, error: ${it.error}")

                    recyclerView.isVisible = false
                    noMediaLinearLayout.isVisible = true
                }
            }
        }
    }

    companion object {
        private val LOG_TAG = AlbumsFragment::class.simpleName!!

        private const val ARG_IS_PICKING = "is_picking"
    }
}
