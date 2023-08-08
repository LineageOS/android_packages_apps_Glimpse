/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.fragments

import android.app.Activity
import android.content.Intent
import android.database.Cursor
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData
import androidx.loader.app.LoaderManager
import androidx.loader.content.GlimpseCursorLoader
import androidx.loader.content.Loader
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.widget.ViewPager2
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import org.lineageos.glimpse.R
import org.lineageos.glimpse.ext.*
import org.lineageos.glimpse.models.Album
import org.lineageos.glimpse.models.Media
import org.lineageos.glimpse.models.MediaType
import org.lineageos.glimpse.query.*
import org.lineageos.glimpse.thumbnail.MediaViewerAdapter
import org.lineageos.glimpse.utils.MediaStoreBuckets
import org.lineageos.glimpse.utils.MediaStoreRequests
import org.lineageos.glimpse.utils.PermissionsUtils
import java.text.SimpleDateFormat

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
    private val shareButton by getViewProperty<ImageButton>(R.id.shareButton)
    private val timeTextView by getViewProperty<TextView>(R.id.timeTextView)
    private val topSheetConstraintLayout by getViewProperty<ConstraintLayout>(R.id.topSheetConstraintLayout)
    private val viewPager by getViewProperty<ViewPager2>(R.id.viewPager)

    private var restoreLastTrashedMediaFromTrash: (() -> Unit)? = null

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

    // Player
    private val exoPlayer by lazy {
        ExoPlayer.Builder(requireContext()).build().apply {
            repeatMode = ExoPlayer.REPEAT_MODE_ONE
        }
    }

    // Adapter
    private val mediaViewerAdapter by lazy {
        MediaViewerAdapter(exoPlayer, currentPositionLiveData)
    }

    // MediaStore
    private val loaderManagerInstance by lazy { LoaderManager.getInstance(this) }

    // Arguments
    private val currentPositionLiveData = MutableLiveData(-1)
    private var position: Int
        get() = currentPositionLiveData.value!!
        set(value) {
            currentPositionLiveData.value = value
        }
    private val album by lazy { arguments?.getParcelable(KEY_ALBUM, Album::class) }

    // Contracts
    private val deleteUriContract =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            Snackbar.make(
                requireView(),
                resources.getQuantityString(
                    if (it.resultCode == Activity.RESULT_CANCELED) {
                        R.plurals.file_deletion_unsuccessful
                    } else {
                        R.plurals.file_deletion_successful
                    },
                    1, 1
                ),
                Snackbar.LENGTH_LONG,
            ).setAnchorView(bottomSheetLinearLayout).show()
        }
    private val trashUriContract =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            val succeeded = it.resultCode != Activity.RESULT_CANCELED

            Snackbar.make(
                requireView(),
                resources.getQuantityString(
                    if (succeeded) {
                        R.plurals.file_trashing_successful
                    } else {
                        R.plurals.file_trashing_unsuccessful
                    },
                    1, 1
                ),
                Snackbar.LENGTH_LONG,
            ).setAnchorView(bottomSheetLinearLayout).also {
                restoreLastTrashedMediaFromTrash?.takeIf { succeeded }?.let { unit ->
                    it.setAction(R.string.file_trashing_undo) { unit() }
                }
            }.show()

            restoreLastTrashedMediaFromTrash = null
        }
    private val restoreUriFromTrashContract =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            Snackbar.make(
                requireView(),
                resources.getQuantityString(
                    if (it.resultCode == Activity.RESULT_CANCELED) {
                        R.plurals.file_restoring_from_trash_unsuccessful
                    } else {
                        R.plurals.file_restoring_from_trash_successful
                    },
                    1, 1
                ),
                Snackbar.LENGTH_LONG,
            ).setAnchorView(bottomSheetLinearLayout).show()
        }
    private val favoriteContract =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            mediaViewerAdapter.getMediaFromMediaStore(viewPager.currentItem)?.let {
                favoriteButton.isSelected = it.isFavorite
            }
        }

    private val onPageChangeCallback = object : OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            super.onPageSelected(position)

            if (mediaViewerAdapter.itemCount <= 0) {
                // No medias, bail out
                // TODO: Do better once we support showing a specific album
                //       from intents (dialog and such)
                findNavController().popBackStack()
                return
            }

            this@MediaViewerFragment.position = position

            val media = mediaViewerAdapter.getMediaFromMediaStore(position) ?: return

            dateTextView.text = dateFormatter.format(media.dateAdded)
            timeTextView.text = timeFormatter.format(media.dateAdded)
            favoriteButton.isSelected = media.isFavorite
            deleteButton.setImageResource(
                when (media.isTrashed) {
                    true -> R.drawable.ic_restore_from_trash
                    false -> R.drawable.ic_delete
                }
            )

            if (media.mediaType == MediaType.VIDEO) {
                exoPlayer.setMediaItem(MediaItem.fromUri(media.externalContentUri))
                exoPlayer.seekTo(C.TIME_UNSET)
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
            } else {
                exoPlayer.stop()
            }
        }
    }

    override fun onResume() {
        super.onResume()

        exoPlayer.play()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        position = arguments?.getInt(KEY_POSITION, -1)!!

        backButton.setOnClickListener {
            findNavController().popBackStack()
        }

        deleteButton.setOnClickListener {
            mediaViewerAdapter.getMediaFromMediaStore(viewPager.currentItem)?.let {
                trashMedia(it)
            }
        }

        deleteButton.setOnLongClickListener {
            mediaViewerAdapter.getMediaFromMediaStore(viewPager.currentItem)?.let {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.file_deletion_confirm_title)
                    .setMessage(
                        resources.getQuantityString(
                            R.plurals.file_deletion_confirm_message, 1, 1
                        )
                    )
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            deleteUriContract.launch(
                                requireContext().contentResolver.createDeleteRequest(
                                    it.externalContentUri
                                )
                            )
                        } else {
                            it.delete(requireContext().contentResolver)
                        }
                    }
                    .setNegativeButton(android.R.string.cancel) { _, _ ->
                        // Do nothing
                    }
                    .show()

                true
            }

            false
        }

        favoriteButton.setOnClickListener {
            mediaViewerAdapter.getMediaFromMediaStore(viewPager.currentItem)?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    favoriteContract.launch(
                        requireContext().contentResolver.createFavoriteRequest(
                            !it.isFavorite, it.externalContentUri
                        )
                    )
                } else {
                    it.favorite(requireContext().contentResolver, !it.isFavorite)
                    favoriteButton.isSelected = !it.isFavorite
                }
            }
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

        viewPager.adapter = mediaViewerAdapter
        viewPager.offscreenPageLimit = 2
        viewPager.registerOnPageChangeCallback(onPageChangeCallback)

        shareButton.setOnClickListener {
            mediaViewerAdapter.getMediaFromMediaStore(viewPager.currentItem)?.let {
                val intent = Intent().shareIntent(it)
                startActivity(Intent.createChooser(intent, null))
            }
        }

        adjustButton.setOnClickListener {
            mediaViewerAdapter.getMediaFromMediaStore(viewPager.currentItem)?.let {
                val intent = Intent().editIntent(it)
                startActivity(Intent.createChooser(intent, null))
            }
        }

        if (!permissionsUtils.mainPermissionsGranted()) {
            mainPermissionsRequestLauncher.launch(PermissionsUtils.mainPermissions)
        } else {
            initCursorLoader()
        }
    }

    override fun onDestroyView() {
        viewPager.unregisterOnPageChangeCallback(onPageChangeCallback)

        exoPlayer.stop()

        super.onDestroyView()
    }

    override fun onPause() {
        super.onPause()

        exoPlayer.pause()
    }

    override fun onDestroy() {
        exoPlayer.release()

        super.onDestroy()
    }

    override fun onCreateLoader(id: Int, args: Bundle?) = when (id) {
        MediaStoreRequests.MEDIA_STORE_REELS_LOADER_ID.ordinal -> {
            val projection = MediaQuery.MediaProjection
            val imageOrVideo =
                (MediaStore.Files.FileColumns.MEDIA_TYPE eq MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE) or
                        (MediaStore.Files.FileColumns.MEDIA_TYPE eq MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO)
            val albumFilter = album?.let {
                when (it.id) {
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
            }
            val selection = albumFilter?.let { imageOrVideo and it } ?: imageOrVideo
            val sortOrder = MediaStore.Files.FileColumns.DATE_ADDED + " DESC"
            val queryArgs = args ?: Bundle()
            GlimpseCursorLoader(
                requireContext(),
                MediaStore.Files.getContentUri("external"),
                projection,
                selection.build(),
                album?.takeIf {
                    MediaStoreBuckets.values().none { bucket -> it.id == bucket.id }
                }?.let { arrayOf(it.id.toString()) },
                sortOrder,
                queryArgs
            )
        }

        else -> throw Exception("Unknown ID $id")
    }

    override fun onLoaderReset(loader: Loader<Cursor>) {
        mediaViewerAdapter.changeCursor(null)
    }

    override fun onLoadFinished(loader: Loader<Cursor>, data: Cursor?) {
        mediaViewerAdapter.changeCursor(data)
        viewPager.setCurrentItem(position, false)
        onPageChangeCallback.onPageSelected(position)
    }

    private fun initCursorLoader() {
        loaderManagerInstance.initLoader(
            MediaStoreRequests.MEDIA_STORE_REELS_LOADER_ID.ordinal,
            bundleOf().apply {
                album?.let {
                    when (it.id) {
                        MediaStoreBuckets.MEDIA_STORE_BUCKET_TRASH.id -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
                            }
                        }
                    }
                }
            }, this
        )
    }

    private fun trashMedia(media: Media, trash: Boolean = !media.isTrashed) {
        if (trash) {
            restoreLastTrashedMediaFromTrash = { trashMedia(media, false) }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val contract = when (trash) {
                true -> trashUriContract
                false -> restoreUriFromTrashContract
            }
            contract.launch(
                requireContext().contentResolver.createTrashRequest(
                    trash, media.externalContentUri
                )
            )
        } else {
            media.trash(requireContext().contentResolver, trash)
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
        ) = bundleOf(
            KEY_ALBUM to album,
            KEY_MEDIA to media,
            KEY_POSITION to position,
        )

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
