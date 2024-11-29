/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.fragments.picker

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.lineageos.glimpse.R
import org.lineageos.glimpse.ext.getViewProperty
import org.lineageos.glimpse.models.RequestStatus
import org.lineageos.glimpse.ui.recyclerview.AlbumThumbnailAdapter
import org.lineageos.glimpse.ui.recyclerview.AlbumThumbnailLayoutManager
import org.lineageos.glimpse.utils.PermissionsChecker
import org.lineageos.glimpse.utils.PermissionsUtils
import org.lineageos.glimpse.utils.PickerUtils
import org.lineageos.glimpse.viewmodels.AlbumsViewModel

class AlbumSelectorFragment : Fragment(R.layout.fragment_picker_album_selector) {
    // View models
    private val model by viewModels<AlbumsViewModel>()

    // Views
    private val noMediaLinearLayout by getViewProperty<LinearLayout>(R.id.noMediaLinearLayout)
    private val recyclerView by getViewProperty<RecyclerView>(R.id.recyclerView)

    // Intent data
    private val mimeType by lazy { PickerUtils.translateMimeType(activity?.intent) }

    // Recyclerview
    private val albumThumbnailAdapter by lazy {
        AlbumThumbnailAdapter { album ->
            findNavController().navigate(
                R.id.action_pickerAlbumSelectorFragment_to_pickerMediaSelectorFragment,
                MediaSelectorFragment.createBundle(album.uri)
            )
        }
    }

    // Permissions
    private val permissionsChecker = PermissionsChecker(this, PermissionsUtils.mainPermissions)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val context = requireContext()

        recyclerView.layoutManager = AlbumThumbnailLayoutManager(context)
        recyclerView.adapter = albumThumbnailAdapter

        ViewCompat.setOnApplyWindowInsetsListener(view) { _, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )

            recyclerView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = insets.left
                rightMargin = insets.right
            }
            recyclerView.updatePadding(bottom = insets.bottom)

            windowInsets
        }

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
        model.albums.collectLatest {
            when (it) {
                is RequestStatus.Loading -> {
                    // Do nothing
                }

                is RequestStatus.Success -> {
                    albumThumbnailAdapter.submitList(it.data)

                    val isEmpty = it.data.isEmpty()
                    recyclerView.isVisible = !isEmpty
                    noMediaLinearLayout.isVisible = isEmpty
                }

                is RequestStatus.Error -> {
                    recyclerView.isVisible = false
                    noMediaLinearLayout.isVisible = true
                }
            }
        }
    }
}
