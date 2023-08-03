/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.fragments

import android.content.ContentUris
import android.database.Cursor
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.loader.app.LoaderManager
import androidx.loader.content.CursorLoader
import androidx.loader.content.Loader
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.lineageos.glimpse.R
import org.lineageos.glimpse.ext.getViewProperty
import org.lineageos.glimpse.models.Album
import org.lineageos.glimpse.thumbnail.AlbumThumbnailAdapter
import org.lineageos.glimpse.utils.CommonNavigationArguments
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
            MediaStoreRequests.MEDIA_STORE_ALBUMS_LOADER_ID.ordinal, null, this
        )
    }

    override fun onCreateLoader(id: Int, args: Bundle?) = when (id) {
        MediaStoreRequests.MEDIA_STORE_ALBUMS_LOADER_ID.ordinal -> {
            val projection = arrayOf(
                MediaStore.Files.FileColumns.BUCKET_ID,
                MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME,
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DATE_ADDED,
                MediaStore.Files.FileColumns.MEDIA_TYPE,
            )
            val selection = (MediaStore.Files.FileColumns.MEDIA_TYPE + "="
                    + MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE
                    + " OR "
                    + MediaStore.Files.FileColumns.MEDIA_TYPE + "="
                    + MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO)
            val sortOrder = MediaStore.Files.FileColumns.DATE_ADDED + " DESC"
            CursorLoader(
                requireContext(),
                MediaStore.Files.getContentUri("external"),
                projection,
                selection,
                null,
                sortOrder
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

                val bucketId = cursor.getInt(bucketIdIndex)

                albums[bucketId]?.also {
                    it.size += 1
                } ?: run {
                    albums[bucketId] = Album(
                        bucketId,
                        cursor.getString(bucketDisplayNameIndex),
                        ContentUris.withAppendedId(contentUri, cursor.getLong(idIndex)),
                    ).apply { size += 1 }
                }

                cursor.moveToNext()
            }
        }

        albumThumbnailAdapter.changeArray(albums.values.toTypedArray())
    }

    companion object {
        fun createBundle() = CommonNavigationArguments().toBundle()

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
