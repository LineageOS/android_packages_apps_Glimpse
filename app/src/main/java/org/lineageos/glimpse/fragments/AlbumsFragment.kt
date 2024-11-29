/*
 * SPDX-FileCopyrightText: 2023-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.fragments

import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.shape.MaterialShapeDrawable
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.lineageos.glimpse.R
import org.lineageos.glimpse.ext.getViewProperty
import org.lineageos.glimpse.models.RequestStatus
import org.lineageos.glimpse.ui.recyclerview.AlbumThumbnailAdapter
import org.lineageos.glimpse.ui.recyclerview.AlbumThumbnailLayoutManager
import org.lineageos.glimpse.utils.PermissionsChecker
import org.lineageos.glimpse.utils.PermissionsUtils
import org.lineageos.glimpse.viewmodels.AlbumsViewModel

/**
 * An albums list visualizer.
 */
class AlbumsFragment : Fragment(R.layout.fragment_albums) {
    // View models
    private val albumsViewModel by viewModels<AlbumsViewModel>()

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
        AlbumThumbnailAdapter { album ->
            parentNavController.navigate(
                R.id.action_mainFragment_to_albumViewerFragment,
                AlbumFragment.createBundle(album.uri)
            )
        }
    }

    // Permissions
    private val permissionsChecker = PermissionsChecker(this, PermissionsUtils.mainPermissions)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Insets
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )

            toolbar.updateLayoutParams<MarginLayoutParams> {
                leftMargin = insets.left
                rightMargin = insets.right
            }

            recyclerView.updateLayoutParams<MarginLayoutParams> {
                leftMargin = insets.left
                rightMargin = insets.right
            }

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
                    // TODO logging
                }
            }
        }
    }
}
