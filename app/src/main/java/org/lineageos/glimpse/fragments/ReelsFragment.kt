/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.fragments

import android.content.res.Configuration
import android.database.Cursor
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.loader.app.LoaderManager
import androidx.loader.content.CursorLoader
import androidx.loader.content.Loader
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import org.lineageos.glimpse.R
import org.lineageos.glimpse.ext.getViewProperty
import org.lineageos.glimpse.query.*
import org.lineageos.glimpse.thumbnail.ThumbnailAdapter
import org.lineageos.glimpse.thumbnail.ThumbnailLayoutManager
import org.lineageos.glimpse.utils.MediaStoreRequests
import org.lineageos.glimpse.utils.PermissionsUtils

/**
 * A fragment showing a list of media with thumbnails.
 * Use the [ReelsFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class ReelsFragment : Fragment(R.layout.fragment_reels), LoaderManager.LoaderCallbacks<Cursor> {
    // Views
    private val reelsRecyclerView by getViewProperty<RecyclerView>(R.id.reelsRecyclerView)

    // Fragments
    private val parentNavController by lazy {
        requireParentFragment().requireParentFragment().findNavController()
    }

    // Permissions
    private val permissionsUtils by lazy { PermissionsUtils(requireContext()) }
    private val mainPermissionsRequestLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (it.isNotEmpty()) {
            if (!permissionsUtils.mainPermissionsGranted()) {
                Toast.makeText(
                    requireContext(), R.string.app_permissions_toast, Toast.LENGTH_SHORT
                ).show()
                requireActivity().finish()
            } else {
                initCursorLoader()
                permissionsUtils.showManageMediaPermissionDialogIfNeeded()
            }
        }
    }

    // MediaStore
    private val loaderManagerInstance by lazy { LoaderManager.getInstance(this) }
    private val thumbnailAdapter by lazy {
        ThumbnailAdapter { media, position ->
            parentNavController.navigate(
                R.id.action_mainFragment_to_mediaViewerFragment,
                MediaViewerFragment.createBundle(
                    null, media, position
                )
            )
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        reelsRecyclerView.layoutManager = ThumbnailLayoutManager(
            requireContext(), thumbnailAdapter
        )
        reelsRecyclerView.adapter = thumbnailAdapter

        ViewCompat.setOnApplyWindowInsetsListener(view) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())

            reelsRecyclerView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = insets.left
                rightMargin = insets.right
            }
            reelsRecyclerView.updatePadding(bottom = insets.bottom)

            windowInsets
        }

        if (!permissionsUtils.mainPermissionsGranted()) {
            mainPermissionsRequestLauncher.launch(PermissionsUtils.mainPermissions)
        } else {
            initCursorLoader()
            permissionsUtils.showManageMediaPermissionDialogIfNeeded()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        reelsRecyclerView.layoutManager = ThumbnailLayoutManager(
            requireContext(), thumbnailAdapter
        )
    }

    override fun onCreateLoader(id: Int, args: Bundle?) = when (id) {
        MediaStoreRequests.MEDIA_STORE_MEDIA_LOADER_ID.ordinal -> {
            val projection = MediaQuery.MediaProjection
            val imageOrVideo =
                (MediaStore.Files.FileColumns.MEDIA_TYPE eq MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE) or
                        (MediaStore.Files.FileColumns.MEDIA_TYPE eq MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO)
            val isNotTrashed = MediaStore.Files.FileColumns.IS_TRASHED eq 0
            val selection = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                // Exclude trashed medias
                imageOrVideo and isNotTrashed
            } else {
                imageOrVideo
            }
            val sortOrder = MediaStore.Files.FileColumns.DATE_ADDED + " DESC"
            CursorLoader(
                requireContext(),
                MediaStore.Files.getContentUri("external"),
                projection,
                selection.build(),
                null,
                sortOrder
            )
        }

        else -> throw Exception("Unknown ID $id")
    }

    override fun onLoaderReset(loader: Loader<Cursor>) {
        thumbnailAdapter.changeCursor(null)
    }

    override fun onLoadFinished(loader: Loader<Cursor>, data: Cursor?) {
        thumbnailAdapter.changeCursor(data)
    }

    private fun initCursorLoader() {
        loaderManagerInstance.initLoader(
            MediaStoreRequests.MEDIA_STORE_MEDIA_LOADER_ID.ordinal,
            bundleOf().apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // Exclude trashed media
                    putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_EXCLUDE)
                }
            },
            this
        )
    }

    companion object {
        private fun createBundle() = bundleOf()

        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @return A new instance of fragment ReelsFragment.
         */
        fun newInstance() = ReelsFragment().apply {
            arguments = createBundle()
        }
    }
}
