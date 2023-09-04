/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.fragments

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.View.MeasureSpec
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.widget.ViewPager2
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.lineageos.glimpse.R
import org.lineageos.glimpse.ext.*
import org.lineageos.glimpse.models.Media
import org.lineageos.glimpse.models.MediaType
import org.lineageos.glimpse.models.MediaUri
import org.lineageos.glimpse.recyclerview.MediaViewerAdapter
import org.lineageos.glimpse.ui.MediaInfoBottomSheetDialog
import org.lineageos.glimpse.utils.PermissionsGatedCallback
import org.lineageos.glimpse.viewmodels.MediaViewerViewModel
import java.text.SimpleDateFormat

/**
 * A fragment showing a media that supports scrolling before and after it.
 * Use the [MediaViewerFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class MediaViewerFragment : Fragment(R.layout.fragment_media_viewer) {
    // View models
    private val mediaViewModel: MediaViewerViewModel by viewModels { MediaViewerViewModel.Factory }

    // Views
    private val adjustButton by getViewProperty<ImageButton>(R.id.adjustButton)
    private val backButton by getViewProperty<ImageButton>(R.id.backButton)
    private val bottomSheetLinearLayout by getViewProperty<LinearLayout>(R.id.bottomSheetLinearLayout)
    private val dateTextView by getViewProperty<TextView>(R.id.dateTextView)
    private val deleteButton by getViewProperty<ImageButton>(R.id.deleteButton)
    private val favoriteButton by getViewProperty<ImageButton>(R.id.favoriteButton)
    private val infoButton by getViewProperty<ImageButton>(R.id.infoButton)
    private val shareButton by getViewProperty<ImageButton>(R.id.shareButton)
    private val timeTextView by getViewProperty<TextView>(R.id.timeTextView)
    private val topSheetConstraintLayout by getViewProperty<ConstraintLayout>(R.id.topSheetConstraintLayout)
    private val viewPager by getViewProperty<ViewPager2>(R.id.viewPager)

    private var restoreLastTrashedMediaFromTrash: (() -> Unit)? = null

    // Permissions
    private val permissionsGatedCallback = PermissionsGatedCallback(this) {
        viewLifecycleOwner.lifecycleScope.launch {
            mediaUri?.also {
                initData(it)
            } ?: viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                mediaViewModel.setBucketId(albumId)
                mediaViewModel.mediaForAlbum.collectLatest(::initData)
            }
        }
    }

    // Player
    private val exoPlayerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            super.onIsPlayingChanged(isPlaying)

            view?.keepScreenOn = isPlaying
        }
    }

    private val exoPlayerLazy = lazy {
        ExoPlayer.Builder(requireContext()).build().apply {
            repeatMode = ExoPlayer.REPEAT_MODE_ONE

            addListener(exoPlayerListener)
        }
    }
    private val exoPlayer
        get() = if (exoPlayerLazy.isInitialized()) {
            exoPlayerLazy.value
        } else {
            null
        }

    // Adapter
    private val mediaViewerAdapter by lazy {
        MediaViewerAdapter(exoPlayerLazy, mediaViewModel)
    }

    // Arguments
    private val media by lazy { arguments?.getParcelable(KEY_MEDIA, Media::class) }
    private val albumId by lazy { arguments?.getInt(KEY_ALBUM_ID, -1).takeUnless { it == -1 } }
    private val mediaUri by lazy { arguments?.getParcelable(KEY_MEDIA_URI, MediaUri::class) }
    private val secure by lazy { arguments?.getBoolean(KEY_SECURE) == true }

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
            mediaViewerAdapter.getItemAtPosition(viewPager.currentItem).let {
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

            this@MediaViewerFragment.mediaViewModel.mediaPosition = position

            mediaUri?.also {
                if (it.mediaType == MediaType.VIDEO) {
                    with(exoPlayerLazy.value) {
                        setMediaItem(MediaItem.fromUri(it.externalContentUri))
                        seekTo(C.TIME_UNSET)
                        prepare()
                        playWhenReady = true
                    }
                } else {
                    exoPlayer?.stop()
                }
            } ?: run {
                val media = mediaViewerAdapter.getItemAtPosition(position)

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
                    with(exoPlayerLazy.value) {
                        setMediaItem(MediaItem.fromUri(media.externalContentUri))
                        seekTo(C.TIME_UNSET)
                        prepare()
                        playWhenReady = true
                    }
                } else {
                    exoPlayer?.stop()
                }
            }
        }
    }

    private val mediaInfoBottomSheetDialogCallbacks =
        MediaInfoBottomSheetDialog.Callbacks(this)

    override fun onResume() {
        super.onResume()

        exoPlayer?.play()

        // Force status bar icons to be light
        requireActivity().window.isAppearanceLightStatusBars = false
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mediaViewModel.mediaPosition = -1

        backButton.setOnClickListener {
            findNavController().popBackStack()
        }

        deleteButton.setOnClickListener {
            trashMedia(mediaViewerAdapter.getItemAtPosition(viewPager.currentItem))
        }

        deleteButton.setOnLongClickListener {
            mediaViewerAdapter.getItemAtPosition(viewPager.currentItem).let {
                MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.file_deletion_confirm_title)
                    .setMessage(
                        resources.getQuantityString(
                            R.plurals.file_deletion_confirm_message, 1, 1
                        )
                    ).setPositiveButton(android.R.string.ok) { _, _ ->
                        deleteUriContract.launch(
                            requireContext().contentResolver.createDeleteRequest(
                                it.externalContentUri
                            )
                        )
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
            mediaViewerAdapter.getItemAtPosition(viewPager.currentItem).let {
                favoriteContract.launch(
                    requireContext().contentResolver.createFavoriteRequest(
                        !it.isFavorite, it.externalContentUri
                    )
                )
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(view) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())

            // Avoid updating the sheets height when they're hidden.
            // Once the system bars will be made visible again, this function
            // will be called again.
            if (mediaViewModel.fullscreenModeLiveData.value != true) {
                topSheetConstraintLayout.updatePadding(
                    left = insets.left,
                    right = insets.right,
                    top = insets.top,
                )
                bottomSheetLinearLayout.updatePadding(
                    bottom = insets.bottom,
                    left = insets.left,
                    right = insets.right,
                )

                updateSheetsHeight()
            }

            windowInsets
        }

        viewPager.adapter = mediaViewerAdapter
        viewPager.offscreenPageLimit = 2
        viewPager.registerOnPageChangeCallback(onPageChangeCallback)

        shareButton.setOnClickListener {
            mediaUri?.also {
                val intent = buildShareIntent(it)
                startActivity(Intent.createChooser(intent, null))
            } ?: mediaViewerAdapter.getItemAtPosition(viewPager.currentItem).let {
                val intent = buildShareIntent(it)
                startActivity(Intent.createChooser(intent, null))
            }
        }

        adjustButton.setOnClickListener {
            mediaViewerAdapter.getItemAtPosition(viewPager.currentItem).let {
                val intent = buildEditIntent(it)
                startActivity(Intent.createChooser(intent, null))
            }
        }

        infoButton.setOnClickListener {
            mediaViewerAdapter.getItemAtPosition(viewPager.currentItem).let {
                MediaInfoBottomSheetDialog(
                    requireContext(), it, mediaInfoBottomSheetDialogCallbacks
                ).show()
            }
        }

        view.findViewTreeLifecycleOwner()?.let {
            mediaViewModel.fullscreenModeLiveData.observe(it) { fullscreenMode ->
                topSheetConstraintLayout.fade(!fullscreenMode)
                bottomSheetLinearLayout.fade(!fullscreenMode)

                requireActivity().window.setBarsVisibility(systemBars = !fullscreenMode)

                // If the sheets are being made visible again, update the values
                if (!fullscreenMode) {
                    updateSheetsHeight()
                }
            }
        }

        // Set UI elements visibility based on initial arguments
        val shouldShowMediaButtons = mediaUri == null && !secure

        dateTextView.isVisible = shouldShowMediaButtons
        timeTextView.isVisible = shouldShowMediaButtons

        favoriteButton.isVisible = shouldShowMediaButtons
        shareButton.isVisible = !secure
        infoButton.isVisible = shouldShowMediaButtons
        adjustButton.isVisible = shouldShowMediaButtons
        deleteButton.isVisible = shouldShowMediaButtons

        updateSheetsHeight()

        permissionsGatedCallback.runAfterPermissionsCheck()
    }

    override fun onDestroyView() {
        viewPager.unregisterOnPageChangeCallback(onPageChangeCallback)

        exoPlayer?.stop()

        super.onDestroyView()
    }

    override fun onPause() {
        super.onPause()

        exoPlayer?.pause()

        // Restore status bar icons appearance
        requireActivity().window.resetStatusBarAppearance()

        // Restore system bars visibility
        requireActivity().window.setBarsVisibility(systemBars = true)
    }

    override fun onDestroy() {
        exoPlayer?.release()

        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        updateSheetsHeight()
    }

    private fun initData(data: List<Media>) {
        mediaViewerAdapter.data = data.toTypedArray()

        // If we already have a position, keep that, else get one from
        // the passed media, else go to the first one
        mediaViewModel.mediaPosition = mediaViewModel.mediaPosition.takeUnless {
            it == -1
        } ?: media?.let { media ->
            mediaViewerAdapter.data.indexOfFirst {
                it.id == media.id
            }.takeUnless {
                it == -1
            }
        } ?: 0

        viewPager.setCurrentItem(mediaViewModel.mediaPosition, false)
        onPageChangeCallback.onPageSelected(mediaViewModel.mediaPosition)
    }

    private fun initData(mediaUri: MediaUri) {
        mediaViewerAdapter.mediaUri = mediaUri

        viewPager.setCurrentItem(0, false)
        onPageChangeCallback.onPageSelected(0)
    }

    private fun trashMedia(media: Media, trash: Boolean = !media.isTrashed) {
        if (trash) {
            restoreLastTrashedMediaFromTrash = { trashMedia(media, false) }
        }

        val contract = when (trash) {
            true -> trashUriContract
            false -> restoreUriFromTrashContract
        }

        contract.launch(
            requireContext().contentResolver.createTrashRequest(
                trash, media.externalContentUri
            )
        )
    }

    private fun updateSheetsHeight() {
        topSheetConstraintLayout.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)
        bottomSheetLinearLayout.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)

        mediaViewModel.sheetsHeightLiveData.value = Pair(
            topSheetConstraintLayout.measuredHeight,
            bottomSheetLinearLayout.measuredHeight,
        )
    }

    companion object {
        private const val KEY_MEDIA = "media"
        private const val KEY_ALBUM_ID = "album_id"
        private const val KEY_MEDIA_URI = "media_uri"
        private const val KEY_SECURE = "secure"

        private val dateFormatter = SimpleDateFormat.getDateInstance()
        private val timeFormatter = SimpleDateFormat.getTimeInstance()

        /**
         * Create a bundle with proper arguments for this fragment.
         *
         * @param media The media to show, if null, the first media found will be shown.
         * @param albumId The album to show, defaults to [media]'s bucket ID. If null, this instance
         *                will show all medias in the device.
         * @param mediaUri The [MediaUri] to display, setting this will disable any kind of
         *                 interaction to [MediaStore] and UI will be stripped down.
         * @param secure Whether this should be considered a secure session (no edit, no share, etc)
         */
        fun createBundle(
            media: Media? = null,
            albumId: Int? = media?.bucketId,
            mediaUri: MediaUri? = null,
            secure: Boolean = false,
        ) = bundleOf(
            KEY_MEDIA to media,
            KEY_ALBUM_ID to albumId,
            KEY_MEDIA_URI to mediaUri,
            KEY_SECURE to secure,
        )

        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @see createBundle
         * @return A new instance of fragment ReelsFragment.
         */
        fun newInstance(
            media: Media? = null,
            albumId: Int? = media?.bucketId,
            mediaUri: MediaUri? = null,
            secure: Boolean = false,
        ) = MediaViewerFragment().apply {
            arguments = createBundle(
                media,
                albumId,
                mediaUri,
                secure,
            )
        }
    }
}
