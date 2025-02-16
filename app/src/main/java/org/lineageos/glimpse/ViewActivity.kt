/*
 * SPDX-FileCopyrightText: 2023-2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse

import android.app.Activity
import android.app.KeyguardManager
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.core.util.Consumer
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.lineageos.glimpse.datasources.MediaError
import org.lineageos.glimpse.ext.buildEditIntent
import org.lineageos.glimpse.ext.buildShareIntent
import org.lineageos.glimpse.ext.buildUseAsIntent
import org.lineageos.glimpse.ext.createDeleteRequest
import org.lineageos.glimpse.ext.createFavoriteRequest
import org.lineageos.glimpse.ext.createTrashRequest
import org.lineageos.glimpse.ext.fade
import org.lineageos.glimpse.ext.setBarsVisibility
import org.lineageos.glimpse.models.Album
import org.lineageos.glimpse.models.AlbumType
import org.lineageos.glimpse.models.Media
import org.lineageos.glimpse.models.MediaType
import org.lineageos.glimpse.models.RequestStatus
import org.lineageos.glimpse.ui.dialogs.MediaInfoBottomSheetDialog
import org.lineageos.glimpse.ui.recyclerview.MediaViewerAdapter
import org.lineageos.glimpse.utils.MediaDialogsUtils
import org.lineageos.glimpse.utils.PermissionsChecker
import org.lineageos.glimpse.utils.PermissionsUtils
import org.lineageos.glimpse.viewmodels.IntentsViewModel
import org.lineageos.glimpse.viewmodels.IntentsViewModel.ParsedIntent
import org.lineageos.glimpse.viewmodels.LocalPlayerViewModel
import java.text.SimpleDateFormat

/**
 * An activity used to view one or mode medias.
 */
class ViewActivity : AppCompatActivity(R.layout.activity_view) {
    // View models
    private val viewModel by viewModels<LocalPlayerViewModel>()
    private val intentsViewModel by viewModels<IntentsViewModel>()

    // Views
    private val adjustButton by lazy { findViewById<MaterialButton>(R.id.adjustButton) }
    private val appBarLayout by lazy { findViewById<AppBarLayout>(R.id.appBarLayout) }
    private val bottomSheetLinearLayout by lazy { findViewById<LinearLayout>(R.id.bottomSheetLinearLayout) }
    private val deleteButton by lazy { findViewById<MaterialButton>(R.id.deleteButton) }
    private val favoriteButton by lazy { findViewById<MaterialButton>(R.id.favoriteButton) }
    private val infoButton by lazy { toolbar.menu.findItem(R.id.info) }
    private val shareButton by lazy { findViewById<MaterialButton>(R.id.shareButton) }
    private val toolbar by lazy { findViewById<MaterialToolbar>(R.id.toolbar) }
    private val useAsButton by lazy { toolbar.menu.findItem(R.id.useAs) }
    private val viewPager by lazy { findViewById<ViewPager2>(R.id.viewPager) }

    // System services
    private val keyguardManager by lazy { getSystemService(KeyguardManager::class.java) }

    private var lastVideoUriPlayed: Uri? = null

    // Adapter
    private val mediaViewerAdapter by lazy {
        MediaViewerAdapter(viewModel)
    }

    private var lastProcessedMedia: Media? = null

    // Contracts
    private val deleteUriContract =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            val succeeded = it.resultCode != Activity.RESULT_CANCELED

            MediaDialogsUtils.showDeleteForeverResultSnackbar(
                this,
                bottomSheetLinearLayout,
                succeeded, 1,
                bottomSheetLinearLayout,
            )
        }

    private val trashUriContract =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            val succeeded = it.resultCode != Activity.RESULT_CANCELED

            MediaDialogsUtils.showMoveToTrashResultSnackbar(
                this,
                bottomSheetLinearLayout,
                succeeded, 1,
                bottomSheetLinearLayout,
                lastProcessedMedia?.let { trashedMedia ->
                    { trashMedia(trashedMedia, false) }
                },
            )

            lastProcessedMedia = null
        }

    private val restoreUriFromTrashContract =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            val succeeded = it.resultCode != Activity.RESULT_CANCELED

            MediaDialogsUtils.showRestoreFromTrashResultSnackbar(
                this,
                bottomSheetLinearLayout,
                succeeded, 1,
                bottomSheetLinearLayout,
                lastProcessedMedia?.let { trashedMedia ->
                    { trashMedia(trashedMedia, true) }
                },
            )
        }

    private val favoriteContract =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            // Do nothing
        }

    private val onPageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            super.onPageSelected(position)

            this@ViewActivity.viewModel.setMediaPosition(position)
        }
    }

    private val mediaInfoBottomSheetDialogCallbacks = MediaInfoBottomSheetDialog.Callbacks(this)

    // Intents
    private val intentListener = Consumer<Intent> { intentsViewModel.onIntent(it) }

    // Permissions
    private val permissionsChecker = PermissionsChecker(this, PermissionsUtils.mainPermissions)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge
        enableEdgeToEdge()

        // We only want to show this activity on top of the keyguard if we're being launched with
        // the ACTION_REVIEW_SECURE intent and the system is currently locked.
        if (keyguardManager.isKeyguardLocked && intent.action == MediaStore.ACTION_REVIEW_SECURE) {
            setShowWhenLocked(true)
        }

        ViewCompat.setOnApplyWindowInsetsListener(bottomSheetLinearLayout) { _, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )

            // Avoid updating the sheets height when they're hidden.
            // Once the system bars will be made visible again, this function
            // will be called again.
            if (!viewModel.fullscreenMode.value) {
                bottomSheetLinearLayout.updatePadding(
                    left = insets.left,
                    right = insets.right,
                    bottom = insets.bottom
                )

                updateSheetsHeight()
            }

            windowInsets
        }

        // Attach the adapter to the view pager
        viewPager.adapter = mediaViewerAdapter

        toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.info -> {
                    viewModel.displayedMedia.value?.let {
                        MediaInfoBottomSheetDialog(
                            this@ViewActivity,
                            it,
                            mediaInfoBottomSheetDialogCallbacks,
                            viewModel.secure.value,
                        ).show()
                    }
                    true
                }

                R.id.useAs -> {
                    viewModel.displayedMedia.value?.let {
                        startActivity(Intent.createChooser(buildUseAsIntent(it), null))
                    }
                    true
                }

                else -> false
            }
        }

        toolbar.setNavigationOnClickListener {
            finish()
        }

        favoriteButton.setOnClickListener {
            viewModel.displayedMedia.value?.let {
                favoriteContract.launch(
                    contentResolver.createFavoriteRequest(
                        !it.isFavorite, it.uri
                    )
                )
            }
        }

        shareButton.setOnClickListener {
            viewModel.displayedMedia.value?.let {
                startActivity(
                    Intent.createChooser(
                        buildShareIntent(it),
                        null
                    )
                )
            }
        }

        adjustButton.setOnClickListener {
            viewModel.displayedMedia.value?.let {
                startActivity(
                    Intent.createChooser(
                        buildEditIntent(it),
                        null
                    )
                )
            }
        }

        deleteButton.setOnClickListener {
            viewModel.displayedMedia.value?.let {
                trashMedia(it)
            }
        }

        deleteButton.setOnLongClickListener {
            viewModel.displayedMedia.value?.let {
                MediaDialogsUtils.openDeleteForeverDialog(this, it.uri) { uris ->
                    deleteUriContract.launch(contentResolver.createDeleteRequest(*uris))
                }

                true
            }

            false
        }

        viewPager.offscreenPageLimit = 2
        viewPager.registerOnPageChangeCallback(onPageChangeCallback)

        intentListener.accept(intent)
        addOnNewIntentListener(intentListener)

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                permissionsChecker.withPermissionsGranted {
                    loadData()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        viewModel.play()
    }

    override fun onPause() {
        viewModel.pause()

        super.onPause()
    }

    override fun onDestroy() {
        removeOnNewIntentListener(intentListener)

        viewPager.unregisterOnPageChangeCallback(onPageChangeCallback)

        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        updateSheetsHeight()
    }

    private suspend fun loadData() {
        coroutineScope {
            launch {
                intentsViewModel.parsedIntent.collectLatest { parsedIntent ->
                    parsedIntent?.handle {
                        when (it) {
                            is ParsedIntent.ViewIntent,
                            is ParsedIntent.ReviewIntent,
                            is ParsedIntent.SecureReviewIntent -> {
                                viewModel.setParsedIntent(it)
                            }

                            else -> run {
                                Toast.makeText(
                                    this@ViewActivity,
                                    R.string.intent_action_not_supported,
                                    Toast.LENGTH_SHORT
                                ).show()
                                finish()
                            }
                        }

                    }
                }
            }

            launch {
                viewModel.mediasWithInitialPosition.collect {
                    when (it) {
                        is RequestStatus.Loading -> {
                            // Do nothing
                        }

                        is RequestStatus.Success -> {
                            val (medias, initialPosition) = it.data

                            mediaViewerAdapter.submitList(medias)

                            initialPosition?.let { position ->
                                viewPager.setCurrentItem(position, false)
                                onPageChangeCallback.onPageSelected(position)

                                viewModel.setMediaPosition(position)
                            }

                            if (medias.isEmpty()) {
                                // Get out of here
                                finish()
                            }
                        }

                        is RequestStatus.Error -> {
                            Log.e(LOG_TAG, "Failed to load medias, error: ${it.error}")

                            mediaViewerAdapter.submitList(listOf())

                            if (it.error == MediaError.NOT_FOUND) {
                                // Get out of here
                                finish()
                            }
                        }
                    }
                }
            }

            launch {
                viewModel.isPlaying.collectLatest { isPlaying ->
                    viewPager.keepScreenOn = isPlaying
                }
            }

            launch {
                viewModel.fullscreenMode.collectLatest { fullscreenMode ->
                    appBarLayout.fade(!fullscreenMode)
                    bottomSheetLinearLayout.fade(!fullscreenMode)

                    window.setBarsVisibility(systemBars = !fullscreenMode)

                    // If the sheets are being made visible again, update the values
                    if (!fullscreenMode) {
                        updateSheetsHeight()
                    }
                }
            }

            launch {
                viewModel.displayedMedia.collectLatest { displayedMedia ->
                    // Update date and time text
                    displayedMedia?.also {
                        toolbar.title = dateFormatter.format(it.dateModified)
                        toolbar.subtitle = timeFormatter.format(it.dateModified)
                    } ?: run {
                        toolbar.title = ""
                        toolbar.subtitle = ""
                    }

                    // Update favorite button
                    val isFavorite = displayedMedia?.isFavorite ?: false
                    favoriteButton.isSelected = isFavorite
                    favoriteButton.setText(
                        when (isFavorite) {
                            true -> R.string.file_action_remove_from_favorites
                            false -> R.string.file_action_add_to_favorites
                        }
                    )

                    // Update info button
                    infoButton.isVisible = displayedMedia != null

                    // Update delete button
                    val isTrashed = displayedMedia?.isTrashed ?: false
                    deleteButton.text = when (isTrashed) {
                        true -> getString(R.string.file_action_restore_from_trash)
                        false -> getString(R.string.file_action_move_to_trash)
                    }
                    deleteButton.setCompoundDrawablesWithIntrinsicBounds(
                        0,
                        when (isTrashed) {
                            true -> R.drawable.ic_restore_from_trash
                            false -> R.drawable.ic_delete
                        },
                        0,
                        0
                    )

                    // Update ExoPlayer
                    displayedMedia?.let {
                        updateExoPlayer(it)
                    }

                    // Trigger a sheets height update
                    updateSheetsHeight()
                }
            }

            launch {
                viewModel.secure.collectLatest { secure ->
                    // Update share button
                    shareButton.isVisible = !secure

                    // Update use as button
                    useAsButton.isVisible = !secure
                }
            }

            launch {
                viewModel.readOnly.collectLatest { readOnly ->
                    // Update favorite button
                    favoriteButton.isVisible = !readOnly

                    // Update adjust button
                    adjustButton.isVisible = !readOnly

                    // Update delete button
                    deleteButton.isVisible = !readOnly
                }
            }
        }
    }

    /**
     * Update exoPlayer's status.
     * @param media The currently displayed [Media]
     */
    private fun updateExoPlayer(media: Media) {
        if (media.mediaType == MediaType.VIDEO) {
            if (media.uri != lastVideoUriPlayed) {
                lastVideoUriPlayed = media.uri
                viewModel.setCurrentVideoUri(media.uri)
            }
        } else {
            viewModel.stop()

            // Make sure we will forcefully reload and restart the video
            lastVideoUriPlayed = null
        }
    }

    private fun trashMedia(media: Media, trash: Boolean = !media.isTrashed) {
        if (trash) {
            lastProcessedMedia = media
        }

        val contract = when (trash) {
            true -> trashUriContract
            false -> restoreUriFromTrashContract
        }

        contract.launch(
            contentResolver.createTrashRequest(
                trash, media.uri
            )
        )
    }

    private fun updateSheetsHeight() {
        appBarLayout.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        bottomSheetLinearLayout.measure(
            View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED
        )

        viewModel.setSheetsHeight(
            appBarLayout.measuredHeight,
            bottomSheetLinearLayout.measuredHeight,
        )
    }

    companion object {
        private val LOG_TAG = ViewActivity::class.simpleName!!

        private val dateFormatter = SimpleDateFormat.getDateInstance()
        private val timeFormatter = SimpleDateFormat.getTimeInstance()

        val EXTRA_ALBUM_TYPE = "${ViewActivity::class.qualifiedName}.album_type"
        val EXTRA_ALBUM_URI = "${ViewActivity::class.qualifiedName}.album_uri"
        val EXTRA_MEDIA_TYPE = "${ViewActivity::class.qualifiedName}.media_type"
        val EXTRA_MIME_TYPE = "${ViewActivity::class.qualifiedName}.mime_type"

        /**
         * Create a [Bundle] to use as the extras for this activity.
         * @param albumType The [AlbumType] to display, null to use [albumUri]
         * @param albumUri The [Album] to display's bucket ID, if null, reels will be shown
         * @param fileType The [MediaType] to filter for
         * @param mimeType The MIME type to filter for
         */
        fun createBundle(
            albumType: AlbumType? = null,
            albumUri: Uri? = null,
            fileType: MediaType? = null,
            mimeType: String? = null,
        ) = bundleOf(
            EXTRA_ALBUM_TYPE to albumType,
            EXTRA_ALBUM_URI to albumUri,
            EXTRA_MEDIA_TYPE to fileType,
            EXTRA_MIME_TYPE to mimeType,
        )
    }
}
