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
import androidx.loader.content.GlimpseCursorLoader
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
import org.lineageos.glimpse.query.*
import org.lineageos.glimpse.thumbnail.ThumbnailAdapter
import org.lineageos.glimpse.thumbnail.ThumbnailLayoutManager
import org.lineageos.glimpse.utils.MediaStoreBuckets
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
                    requireContext(), R.string.app_permissions_toast, Toast.LENGTH_SHORT
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

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        albumRecyclerView.layoutManager = ThumbnailLayoutManager(
            requireContext(), thumbnailAdapter
        )
    }

    override fun onCreateLoader(id: Int, args: Bundle?) = when (id) {
        MediaStoreRequests.MEDIA_STORE_MEDIA_LOADER_ID.ordinal -> {
            val projection = MediaQuery.MediaProjection
            val imageOrVideo =
                (MediaStore.Files.FileColumns.MEDIA_TYPE eq MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE) or
                        (MediaStore.Files.FileColumns.MEDIA_TYPE eq MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO)
            val albumFilter = when (album.id) {
                MediaStoreBuckets.MEDIA_STORE_BUCKET_FAVORITES.id -> {
                    MediaStore.Files.FileColumns.IS_FAVORITE eq 1
                }

                MediaStoreBuckets.MEDIA_STORE_BUCKET_TRASH.id -> {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                        MediaStore.Files.FileColumns.IS_TRASHED eq 1
                    } else {
                        null
                    }
                }

                else -> {
                    MediaStore.Files.FileColumns.BUCKET_ID eq Query.ARG
                }
            }
            val selection = albumFilter?.let { imageOrVideo and it } ?: imageOrVideo
            val sortOrder = MediaStore.Files.FileColumns.DATE_ADDED + " DESC"
            val queryArgs = args ?: Bundle()
            GlimpseCursorLoader(
                requireContext(),
                MediaStore.Files.getContentUri("external"),
                projection,
                selection.build(),
                album.takeIf {
                    MediaStoreBuckets.values().none { bucket -> it.id == bucket.id }
                }?.let { arrayOf(it.id.toString()) },
                sortOrder,
                queryArgs
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
                when (album.id) {
                    MediaStoreBuckets.MEDIA_STORE_BUCKET_TRASH.id -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
                        }
                    }
                }
            }, this
        )
    }

    companion object {
        private const val KEY_ALBUM_ID = "album_id"

        fun createBundle(
            album: Album,
        ) = bundleOf(
            KEY_ALBUM_ID to album,
        )

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
