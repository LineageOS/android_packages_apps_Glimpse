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
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.loader.app.LoaderManager
import androidx.loader.content.CursorLoader
import androidx.loader.content.Loader
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.fragment.findNavController
import org.lineageos.glimpse.R
import org.lineageos.glimpse.ext.*
import org.lineageos.glimpse.models.Album
import org.lineageos.glimpse.models.Media
import org.lineageos.glimpse.models.MediaType
import org.lineageos.glimpse.utils.CommonNavigationArguments
import org.lineageos.glimpse.utils.MediaStoreRequests
import org.lineageos.glimpse.utils.PermissionsUtils
import java.text.SimpleDateFormat
import java.util.Date

/**
 * A fragment showing a media that supports scrolling before and after it.
 * Use the [MediaViewerFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class MediaViewerFragment : Fragment(
    R.layout.fragment_media_viewer
), LoaderManager.LoaderCallbacks<Cursor> {
    // Views
    private val adjustButton by getViewProperty<ImageButton>(R.id.adjustButton)
    private val backButton by getViewProperty<ImageButton>(R.id.backButton)
    private val bottomSheetLinearLayout by getViewProperty<LinearLayout>(R.id.bottomSheetLinearLayout)
    private val dateTextView by getViewProperty<TextView>(R.id.dateTextView)
    private val deleteButton by getViewProperty<ImageButton>(R.id.deleteButton)
    private val favoriteButton by getViewProperty<ImageButton>(R.id.favoriteButton)
    private val imageView by getViewProperty<ImageView>(R.id.imageView)
    private val playerView by getViewProperty<PlayerView>(R.id.playerView)
    private val shareButton by getViewProperty<ImageButton>(R.id.shareButton)
    private val timeTextView by getViewProperty<TextView>(R.id.timeTextView)
    private val topSheetConstraintLayout by getViewProperty<ConstraintLayout>(R.id.topSheetConstraintLayout)

    // ExoPlayer
    private var exoPlayer: ExoPlayer? = null

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

    private var album: Album? = null
    private lateinit var media: Media

    private var cursor: Cursor? = null
    private var position: Int = -1

    private var idIndex = -1
    private var mediaTypeIndex = -1
    private var dateAddedIndex = -1

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        arguments?.let {
            position = it.getInt(KEY_POSITION, -1)
            album = it.getParcelable(KEY_ALBUM, Album::class)
            media = it.getParcelable(KEY_MEDIA, Media::class)!!
        }

        backButton.setOnClickListener {
            findNavController().popBackStack()
        }

        ViewCompat.setOnApplyWindowInsetsListener(view) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())

            topSheetConstraintLayout.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = insets.left
                rightMargin = insets.right
                topMargin = insets.top
            }
            bottomSheetLinearLayout.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = insets.bottom
                leftMargin = insets.left
                rightMargin = insets.right
            }

            windowInsets
        }

        if (!permissionsUtils.mainPermissionsGranted()) {
            mainPermissionsRequestLauncher.launch(PermissionsUtils.mainPermissions)
        } else {
            initCursorLoader()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()

        exoPlayer?.release()
        exoPlayer = null
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
                album?.let {
                    append(
                        buildString {
                            append(" AND ")
                            append(MediaStore.Files.FileColumns.BUCKET_ID)
                            append(" = ?")
                        }
                    )
                }
            }
            CursorLoader(
                requireContext(),
                MediaStore.Files.getContentUri("external"),
                projection,
                selection,
                album?.let { arrayOf(it.id.toString()) },
                MediaStore.Files.FileColumns.DATE_ADDED + " DESC"
            )
        }

        else -> throw Exception("Unknown ID $id")
    }

    override fun onLoaderReset(loader: Loader<Cursor>) {
        cursor = null

        idIndex = -1
        mediaTypeIndex = -1
        dateAddedIndex = -1

        loadMedia()
    }

    override fun onLoadFinished(loader: Loader<Cursor>, data: Cursor?) {
        cursor = data

        cursor?.let {
            idIndex = it.getColumnIndex(MediaStore.Files.FileColumns._ID)
            mediaTypeIndex = it.getColumnIndex(MediaStore.Files.FileColumns.MEDIA_TYPE)
            dateAddedIndex = it.getColumnIndex(MediaStore.Files.FileColumns.DATE_ADDED)
        }

        loadMedia()
    }

    private fun initCursorLoader() {
        loaderManagerInstance.initLoader(
            MediaStoreRequests.MEDIA_STORE_REELS_LOADER_ID.ordinal, null, this
        )
    }

    private fun loadMedia() {
        cursor?.also { cursor ->
            cursor.moveToPosition(position)

            media = Media(
                cursor.getLong(idIndex),
                MediaType.fromMediaStoreValue(cursor.getInt(mediaTypeIndex)),
                Date(cursor.getLong(dateAddedIndex) * 1000),
            )

            when (media.mediaType) {
                MediaType.IMAGE -> {
                    imageView.setImageURI(media.externalContentUri)
                }

                MediaType.VIDEO -> {
                    exoPlayer = ExoPlayer.Builder(requireContext())
                        .build()
                        .also {
                            playerView.player = it

                            it.setMediaItem(MediaItem.fromUri(media.externalContentUri))

                            it.playWhenReady = true
                            it.seekTo(0)
                            it.prepare()
                        }
                }
            }

            dateTextView.text = dateFormatter.format(media.dateAdded)
            timeTextView.text = timeFormatter.format(media.dateAdded)

            imageView.isVisible = media.mediaType == MediaType.IMAGE
            playerView.isVisible = media.mediaType == MediaType.VIDEO
        }
    }

    companion object {
        private const val KEY_ALBUM = "album"
        private const val KEY_MEDIA = "media"
        private const val KEY_POSITION = "position"

        private val dateFormatter = SimpleDateFormat.getDateInstance()
        private val timeFormatter = SimpleDateFormat.getTimeInstance()

        fun createBundle(
            album: Album?,
            media: Media,
            position: Int,
        ) = CommonNavigationArguments().toBundle().apply {
            putParcelable(KEY_ALBUM, album)
            putParcelable(KEY_MEDIA, media)
            putInt(KEY_POSITION, position)
        }

        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param album Album.
         * @return A new instance of fragment ReelsFragment.
         */
        fun newInstance(
            album: Album?,
            media: Media,
            position: Int,
        ) = MediaViewerFragment().apply {
            arguments = createBundle(
                album,
                media,
                position,
            )
        }
    }
}
