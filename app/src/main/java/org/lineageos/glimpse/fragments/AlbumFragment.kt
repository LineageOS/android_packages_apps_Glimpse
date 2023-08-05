/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.fragments

import android.database.Cursor
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.loader.app.LoaderManager
import androidx.loader.content.CursorLoader
import androidx.loader.content.Loader
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.shape.MaterialShapeDrawable
import org.lineageos.glimpse.R
import org.lineageos.glimpse.ext.getParcelable
import org.lineageos.glimpse.ext.getViewProperty
import org.lineageos.glimpse.models.Album
import org.lineageos.glimpse.thumbnail.ThumbnailAdapter
import org.lineageos.glimpse.thumbnail.ThumbnailLayoutManager
import org.lineageos.glimpse.utils.CommonNavigationArguments
import org.lineageos.glimpse.utils.MediaStoreRequests
import org.lineageos.glimpse.utils.PermissionsUtils

/**
 * A fragment showing a list of media from a specific album with thumbnails.
 * Use the [AlbumFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class AlbumFragment : Fragment(R.layout.fragment_album), LoaderManager.LoaderCallbacks<Cursor> {
    // Views
    private val albumRecyclerView by getViewProperty<RecyclerView>(R.id.albumRecyclerView)
    private val appBarLayout by getViewProperty<AppBarLayout>(R.id.appBarLayout)
    private val toolbar by getViewProperty<MaterialToolbar>(R.id.toolbar)

    // Permissions
    private val permissionsUtils by lazy { PermissionsUtils(requireContext()) }
    private val mainPermissionsRequestLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (it.isNotEmpty()) {
            if (!permissionsUtils.mainPermissionsGranted()) {
                Toast.makeText(
                    requireContext(), "No main permissions", Toast.LENGTH_SHORT
                ).show()
                requireActivity().finish()
            } else {
                initCursorLoader()
            }
        }
    }

    // MediaStore
    private val loaderManagerInstance by lazy { LoaderManager.getInstance(this) }
    private val thumbnailAdapter by lazy {
        ThumbnailAdapter { media, position ->
            findNavController().navigate(
                R.id.action_albumFragment_to_mediaViewerFragment,
                MediaViewerFragment.createBundle(
                    album, media, position
                )
            )
        }
    }

    // Arguments
    private val album by lazy { arguments?.getParcelable(KEY_ALBUM_ID, Album::class)!! }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val navController = findNavController()

        appBarLayout.statusBarForeground =
            MaterialShapeDrawable.createWithElevationOverlay(requireContext())

        toolbar.title = album.name

        val appBarConfiguration = AppBarConfiguration(navController.graph)
        toolbar.setupWithNavController(navController, appBarConfiguration)

        albumRecyclerView.layoutManager = ThumbnailLayoutManager(
            requireContext(), thumbnailAdapter
        )
        albumRecyclerView.adapter = thumbnailAdapter

        ViewCompat.setOnApplyWindowInsetsListener(view) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())

            albumRecyclerView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = insets.left
                rightMargin = insets.right
            }
            albumRecyclerView.updatePadding(bottom = insets.bottom)

            windowInsets
        }

        if (!permissionsUtils.mainPermissionsGranted()) {
            mainPermissionsRequestLauncher.launch(PermissionsUtils.mainPermissions)
        } else {
            initCursorLoader()
        }
    }

    override fun onCreateLoader(id: Int, args: Bundle?) = when (id) {
        MediaStoreRequests.MEDIA_STORE_REELS_LOADER_ID.ordinal -> {
            val projection = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DATE_ADDED,
                MediaStore.Files.FileColumns.MEDIA_TYPE,
            )
            val selection = buildString {
                append("(")
                append(buildString {
                    append(MediaStore.Files.FileColumns.MEDIA_TYPE)
                    append("=")
                    append(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE)
                    append(" OR ")
                    append(MediaStore.Files.FileColumns.MEDIA_TYPE)
                    append("=")
                    append(MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO)
                })
                append(")")
                append(" AND ")
                append(MediaStore.Files.FileColumns.BUCKET_ID)
                append(" = ")
                append(album.id)
            }
            CursorLoader(
                requireContext(),
                MediaStore.Files.getContentUri("external"),
                projection,
                selection,
                null,
                MediaStore.Files.FileColumns.DATE_ADDED + " DESC"
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
            MediaStoreRequests.MEDIA_STORE_REELS_LOADER_ID.ordinal, null, this
        )
    }

    companion object {
        private const val KEY_ALBUM_ID = "album_id"

        fun createBundle(
            album: Album,
        ) = CommonNavigationArguments().toBundle().apply {
            putParcelable(KEY_ALBUM_ID, album)
        }

        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param album Album.
         * @return A new instance of fragment ReelsFragment.
         */
        fun newInstance(
            album: Album,
        ) = AlbumFragment().apply {
            arguments = createBundle(album)
        }
    }
}
