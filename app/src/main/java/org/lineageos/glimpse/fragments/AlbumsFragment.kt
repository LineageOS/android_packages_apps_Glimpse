/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.fragments

import android.content.ContentUris
import android.database.Cursor
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
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
import androidx.loader.app.LoaderManager
import androidx.loader.content.GlimpseCursorLoader
import androidx.loader.content.Loader
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.lineageos.glimpse.R
import org.lineageos.glimpse.ext.getViewProperty
import org.lineageos.glimpse.models.Album
import org.lineageos.glimpse.query.*
import org.lineageos.glimpse.thumbnail.AlbumThumbnailAdapter
import org.lineageos.glimpse.utils.MediaStoreBuckets
import org.lineageos.glimpse.utils.MediaStoreRequests

/**
 * An albums list visualizer.
 * Use the [AlbumsFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class AlbumsFragment : Fragment(), LoaderManager.LoaderCallbacks<Cursor> {
    // Views
    private val albumsRecyclerView by getViewProperty<RecyclerView>(R.id.albumsRecyclerView)

    // Fragments
    private val parentNavController by lazy {
        requireParentFragment().requireParentFragment().findNavController()
    }

    // MediaStore
    private val loaderManagerInstance by lazy { LoaderManager.getInstance(this) }
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

        loaderManagerInstance.initLoader(
            MediaStoreRequests.MEDIA_STORE_ALBUMS_LOADER_ID.ordinal,
            bundleOf().apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE)
                }
            },
            this
        )
    }

    override fun onCreateLoader(id: Int, args: Bundle?) = when (id) {
        MediaStoreRequests.MEDIA_STORE_ALBUMS_LOADER_ID.ordinal -> {
            val projection = MediaQuery.AlbumsProjection
            val imageOrVideo =
                (MediaStore.Files.FileColumns.MEDIA_TYPE eq MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE) or
                        (MediaStore.Files.FileColumns.MEDIA_TYPE eq MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO)
            val sortOrder = MediaStore.Files.FileColumns.DATE_ADDED + " DESC"
            val queryArgs = args ?: Bundle()
            GlimpseCursorLoader(
                requireContext(),
                MediaStore.Files.getContentUri("external"),
                projection,
                imageOrVideo.build(),
                null,
                sortOrder,
                queryArgs
            )
        }

        else -> throw Exception("Unknown ID $id")
    }

    override fun onLoaderReset(loader: Loader<Cursor>) {
        albumThumbnailAdapter.changeArray(null)
    }

    override fun onLoadFinished(loader: Loader<Cursor>, data: Cursor?) {
        // Google killed GROUP BY in Android 10, forget about it

        val albums = mutableMapOf<Int, Album>()

        data?.let { cursor ->
            if (cursor.count <= 0) {
                return@let
            }

            val idIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns._ID)
            val isFavoriteIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns.IS_FAVORITE)
            val isTrashedIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns.IS_TRASHED)
            val mediaTypeIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns.MEDIA_TYPE)
            val bucketIdIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns.BUCKET_ID)
            val bucketDisplayNameIndex =
                cursor.getColumnIndex(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)

            cursor.moveToFirst()

            while (!cursor.isAfterLast) {
                val contentUri = when (cursor.getInt(mediaTypeIndex)) {
                    MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE ->
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI

                    MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO ->
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI

                    else -> return@let
                }

                val bucketIds = listOfNotNull(
                    when (cursor.getInt(isTrashedIndex)) {
                        1 -> MediaStoreBuckets.MEDIA_STORE_BUCKET_TRASH.id
                        else -> cursor.getInt(bucketIdIndex)
                    },
                    MediaStoreBuckets.MEDIA_STORE_BUCKET_FAVORITES.id.takeIf {
                        cursor.getInt(isFavoriteIndex) == 1
                    },
                )

                for (bucketId in bucketIds) {
                    albums[bucketId]?.also {
                        it.size += 1
                    } ?: run {
                        albums[bucketId] = Album(
                            bucketId,
                            when (bucketId) {
                                MediaStoreBuckets.MEDIA_STORE_BUCKET_FAVORITES.id ->
                                    getString(R.string.album_favorites)
                                MediaStoreBuckets.MEDIA_STORE_BUCKET_TRASH.id ->
                                    getString(R.string.album_trash)
                                else -> cursor.getString(bucketDisplayNameIndex) ?: Build.MODEL
                            },
                            ContentUris.withAppendedId(contentUri, cursor.getLong(idIndex)),
                        ).apply { size += 1 }
                    }
                }

                cursor.moveToNext()
            }
        }

        albumThumbnailAdapter.changeArray(albums.values.toTypedArray())
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
