/*
 * SPDX-FileCopyrightText: 2023-2024 The LineageOS Project
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
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.lineageos.glimpse.ext.asArray
import org.lineageos.glimpse.ext.buildEditIntent
import org.lineageos.glimpse.ext.buildShareIntent
import org.lineageos.glimpse.ext.buildUseAsIntent
import org.lineageos.glimpse.ext.createDeleteRequest
import org.lineageos.glimpse.ext.createFavoriteRequest
import org.lineageos.glimpse.ext.createTrashRequest
import org.lineageos.glimpse.ext.fade
import org.lineageos.glimpse.ext.getParcelable
import org.lineageos.glimpse.ext.setBarsVisibility
import org.lineageos.glimpse.models.Media
import org.lineageos.glimpse.models.FileType
import org.lineageos.glimpse.models.RequestStatus
import org.lineageos.glimpse.ui.recyclerview.MediaViewerAdapter
import org.lineageos.glimpse.ui.dialogs.MediaInfoBottomSheetDialog
import org.lineageos.glimpse.utils.MediaDialogsUtils
import org.lineageos.glimpse.utils.PermissionsGatedCallback
import org.lineageos.glimpse.viewmodels.MediaViewerUIViewModel
import org.lineageos.glimpse.viewmodels.MediaViewerViewModel
import java.text.SimpleDateFormat

/**
 * An activity used to view one or mode medias.
 */
class ViewActivity : AppCompatActivity(R.layout.activity_view) {
    // View models
    private val viewModel by viewModels<MediaViewerViewModel>()
    private val uiViewModel by viewModels<MediaViewerUIViewModel>()

    // Views
    private val adjustButton by lazy { findViewById<MaterialButton>(R.id.adjustButton) }
    private val appBarLayout by lazy { findViewById<AppBarLayout>(R.id.appBarLayout) }
    private val bottomSheetLinearLayout by lazy { findViewById<LinearLayout>(R.id.bottomSheetLinearLayout) }
    private val contentView by lazy { findViewById<View>(android.R.id.content) }
    private val deleteButton by lazy { findViewById<MaterialButton>(R.id.deleteButton) }
    private val favoriteButton by lazy { findViewById<MaterialButton>(R.id.favoriteButton) }
    private val infoButton by lazy { toolbar.menu.findItem(R.id.info) }
    private val shareButton by lazy { findViewById<MaterialButton>(R.id.shareButton) }
    private val toolbar by lazy { findViewById<MaterialToolbar>(R.id.toolbar) }
    private val useAsButton by lazy { toolbar.menu.findItem(R.id.useAs) }
    private val viewPager by lazy { findViewById<ViewPager2>(R.id.viewPager) }

    // System services
    private val keyguardManager by lazy { getSystemService(KeyguardManager::class.java) }

    // Player
    private val exoPlayerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            super.onIsPlayingChanged(isPlaying)

            contentView.keepScreenOn = isPlaying
        }
    }

    private val exoPlayerLazy = lazy {
        ExoPlayer.Builder(this).build().apply {
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

    private var lastVideoUriPlayed: Uri? = null

    // Adapter
    private val mediaViewerAdapter by lazy {
        MediaViewerAdapter(exoPlayerLazy, viewModel, uiViewModel)
    }

    // okhttp
    private val httpClient = OkHttpClient()

    // Intent values
    private var media: Media? = null
    private var albumUri: Uri? = null
    private var additionalMedias: Array<Media>? = null
    private var secure = false

    private var lastProcessedMedia: Media? = null

    /**
     * Check if we're showing a static set of medias.
     */
    private val readOnly
        get() = additionalMedias != null || albumUri == null || secure

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

            if (mediaViewerAdapter.itemCount <= 0) {
                // No medias, bail out
                finish()
                return
            }

            this@ViewActivity.viewModel.mediaPosition = position
        }
    }

    private val mediaInfoBottomSheetDialogCallbacks = MediaInfoBottomSheetDialog.Callbacks(this)

    // Permissions
    private val permissionsGatedCallback = PermissionsGatedCallback(this) {
        lifecycleScope.launch lifecycleCoroutine@{
            val intentHandled = withContext(Dispatchers.IO) {
                handleIntent(intent)
            }

            if (!intentHandled) {
                finish()
                return@lifecycleCoroutine
            }

            additionalMedias?.also { additionalMedias ->
                val medias = media?.let {
                    arrayOf(it) + additionalMedias
                } ?: additionalMedias

                initData(medias.distinct().sortedByDescending { it.dateModified })
            } ?: albumUri?.also {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.media.collectLatest { data ->
                        when (data) {
                            is RequestStatus.Loading -> {
                                // Do nothing
                            }

                            is RequestStatus.Success -> initData(data.data.second)

                            is RequestStatus.Error -> Unit
                        }
                    }
                }
            } ?: media?.also {
                initData(listOf(it))
            } ?: initData(listOf())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Setup edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // We only want to show this activity on top of the keyguard if we're being launched with
        // the ACTION_REVIEW_SECURE intent and the system is currently locked.
        if (keyguardManager.isKeyguardLocked && intent.action == MediaStore.ACTION_REVIEW_SECURE) {
            setShowWhenLocked(true)
        }

        // Observe fullscreen mode
        uiViewModel.fullscreenModeLiveData.observe(this@ViewActivity) { fullscreenMode ->
            appBarLayout.fade(!fullscreenMode)
            bottomSheetLinearLayout.fade(!fullscreenMode)

            window.setBarsVisibility(systemBars = !fullscreenMode)

            // If the sheets are being made visible again, update the values
            if (!fullscreenMode) {
                updateSheetsHeight()
            }
        }

        // Observe displayed media
        uiViewModel.displayedMedia.observe(this@ViewActivity) { displayedMedia ->
            // Update date and time text
            displayedMedia?.let {
                toolbar.title = dateFormatter.format(it.dateModified)
                toolbar.subtitle = timeFormatter.format(it.dateModified)
            } ?: run {
                toolbar.title = ""
                toolbar.subtitle = ""
            }

            // Update favorite button
            favoriteButton.isVisible = !readOnly
            displayedMedia?.let {
                favoriteButton.isSelected = it.isFavorite
                favoriteButton.setText(
                    when (it.isFavorite) {
                        true -> R.string.file_action_remove_from_favorites
                        false -> R.string.file_action_add_to_favorites
                    }
                )
            }

            // Update share button
            shareButton.isVisible = !secure

            // Update use as button
            useAsButton.isVisible = !secure

            // Update info button
            infoButton.isVisible = true

            // Update adjust button
            adjustButton.isVisible = !readOnly

            // Update delete button
            deleteButton.isVisible = !readOnly
            displayedMedia?.let {
                deleteButton.setCompoundDrawablesWithIntrinsicBounds(
                    0,
                    when (it.isTrashed) {
                        true -> R.drawable.ic_restore_from_trash
                        false -> R.drawable.ic_delete
                    },
                    0,
                    0
                )
            }

            // Update ExoPlayer
            displayedMedia?.let {
                updateExoPlayer(it)
            }

            // Trigger a sheets height update
            updateSheetsHeight()
        }

        ViewCompat.setOnApplyWindowInsetsListener(contentView) { _, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )

            // Avoid updating the sheets height when they're hidden.
            // Once the system bars will be made visible again, this function
            // will be called again.
            if (uiViewModel.fullscreenModeLiveData.value != true) {
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
                    uiViewModel.displayedMedia.value?.let {
                        MediaInfoBottomSheetDialog(
                            this@ViewActivity, it, mediaInfoBottomSheetDialogCallbacks, secure
                        ).show()
                    }
                    true
                }

                R.id.useAs -> {
                    uiViewModel.displayedMedia.value?.let {
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
            uiViewModel.displayedMedia.value?.let {
                favoriteContract.launch(
                    contentResolver.createFavoriteRequest(
                        !it.isFavorite, it.uri
                    )
                )
            }
        }

        shareButton.setOnClickListener {
            uiViewModel.displayedMedia.value?.let {
                startActivity(
                    Intent.createChooser(
                        buildShareIntent(it),
                        null
                    )
                )
            }
        }

        adjustButton.setOnClickListener {
            uiViewModel.displayedMedia.value?.let {
                startActivity(
                    Intent.createChooser(
                        buildEditIntent(it),
                        null
                    )
                )
            }
        }

        deleteButton.setOnClickListener {
            uiViewModel.displayedMedia.value?.let {
                trashMedia(it)
            }
        }

        deleteButton.setOnLongClickListener {
            uiViewModel.displayedMedia.value?.let {
                MediaDialogsUtils.openDeleteForeverDialog(this, it.uri) { uris ->
                    deleteUriContract.launch(contentResolver.createDeleteRequest(*uris))
                }

                true
            }

            false
        }

        viewPager.offscreenPageLimit = 2
        viewPager.registerOnPageChangeCallback(onPageChangeCallback)

        permissionsGatedCallback.runAfterPermissionsCheck()
    }

    override fun onResume() {
        super.onResume()

        exoPlayer?.play()
    }

    override fun onPause() {
        super.onPause()

        exoPlayer?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()

        exoPlayer?.release()

        viewPager.unregisterOnPageChangeCallback(onPageChangeCallback)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        updateSheetsHeight()
    }

    private fun initData(data: List<Media>) {
        mediaViewerAdapter.submitList(data)

        // If we already have a position, keep that, else get one from
        // the passed media, else go to the first one
        val mediaPosition = viewModel.mediaPosition ?: media?.let { media ->
            data.indexOfFirst {
                it.uri == media.uri
            }.takeUnless {
                it == -1
            }
        } ?: 0

        viewModel.mediaPosition = mediaPosition

        viewPager.setCurrentItem(mediaPosition, false)
        onPageChangeCallback.onPageSelected(mediaPosition)
    }

    /**
     * Update [exoPlayer]'s status.
     * @param media The currently displayed [Media]
     */
    private fun updateExoPlayer(media: Media) {
        if (media.fileType == FileType.VIDEO) {
            with(exoPlayerLazy.value) {
                if (media.uri != lastVideoUriPlayed) {
                    lastVideoUriPlayed = media.uri
                    setMediaItem(MediaItem.fromUri(media.uri))
                    seekTo(C.TIME_UNSET)
                    prepare()
                    playWhenReady = true
                }
            }
        } else {
            exoPlayer?.stop()

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

        uiViewModel.sheetsHeightLiveData.value = Pair(
            appBarLayout.measuredHeight,
            bottomSheetLinearLayout.measuredHeight,
        )
    }

    /**
     * Handle the received [Intent], parse it and set variables accordingly.
     * Must not be executed on main thread.
     * @param intent The received intent
     * @return true if the intent has been handled, false otherwise
     */
    private fun handleIntent(intent: Intent) = when (intent.action) {
        Intent.ACTION_VIEW -> handleView(intent)
        MediaStore.ACTION_REVIEW -> handleReview(intent)
        MediaStore.ACTION_REVIEW_SECURE -> handleReview(intent, true)
        else -> run {
            runOnUiThread {
                Toast.makeText(
                    this,
                    R.string.intent_action_not_supported,
                    Toast.LENGTH_SHORT
                ).show()
            }

            false
        }
    }

    /**
     * Handle a [Intent.ACTION_VIEW] intent (view a single media, controls also read-only).
     * Must not be executed on main thread.
     * @param intent The received intent
     * @param secure Whether we should show this media in a secure manner
     * @return true if the intent has been handled, false otherwise
     */
    private fun handleView(intent: Intent, secure: Boolean = false): Boolean {
        val uri = intent.data ?: run {
            runOnUiThread {
                Toast.makeText(
                    this,
                    R.string.intent_media_not_found,
                    Toast.LENGTH_SHORT
                ).show()
            }

            return false
        }

        val dataType = intent.type ?: getContentType(uri) ?: run {
            runOnUiThread {
                Toast.makeText(
                    this,
                    R.string.intent_media_type_not_found,
                    Toast.LENGTH_SHORT
                ).show()
            }

            return false
        }

        val uriType = FileType.fromMimeType(dataType) ?: run {
            runOnUiThread {
                Toast.makeText(
                    this,
                    R.string.intent_media_type_not_supported,
                    Toast.LENGTH_SHORT
                ).show()
            }

            return false
        }

        /*
        updateArguments(
            media = Media(uri, dataType, uriType),
            secure = secure,
        )
         */

        return true
    }

    /**
     * Handle a [MediaStore.ACTION_REVIEW] / [MediaStore.ACTION_REVIEW_SECURE] intent
     * (view a media together with medias from the same bucket ID).
     * If uri parsing from [MediaStore] fails, fallback to [handleView].
     * Must not be executed on main thread.
     * @param intent The received intent
     * @param secure Whether we should review this media in a secure manner
     * @return true if the intent has been handled, false otherwise
     */
    private fun handleReview(intent: Intent, secure: Boolean = false) = intent.data?.let {
        getMediaStoreMedia(it)
    }?.let { media ->
        val additionalMedias = intent.clipData?.asArray()?.mapNotNull {
            getMediaStoreMedia(it.uri)
        }

        updateArguments(
            media = media,
            albumUri = intent.extras?.getParcelable(ARG_ALBUM_URI, Uri::class)
                /*?: media?.albumUri.takeUnless { secure }*/,
            /*additionalMedias = additionalMedias?.toTypedArray()?.takeIf { it.isNotEmpty() },*/
            secure = secure,
        )

        true
    } ?: handleView(intent, secure)

    /**
     * Update all media arguments and make sure stuff is set properly.
     * @param media The first media to show
     * @param albumId Album ID, if null [additionalMedias] will be used
     * @param additionalMedias The additional medias to show alongside [media]
     * @param secure Whether this should be considered a secure session
     */
    private fun updateArguments(
        media: Media? = null,
        albumUri: Uri? = null,
        additionalMedias: Array<Media>? = null,
        secure: Boolean = false,
    ) {
        this.media = media
        this.albumUri = albumUri
        this.additionalMedias = additionalMedias
        this.secure = secure
    }

    /**
     * Given a [MediaStore] [Uri], parse its information and get a [Media] object.
     * Must not be executed on main thread.
     * @param uri The [MediaStore] [Uri]
     */
    private fun getMediaStoreMedia(uri: Uri) = runCatching {
        contentResolver.query(
            uri,
            arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.BUCKET_ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.IS_FAVORITE,
                MediaStore.MediaColumns.IS_TRASHED,
                MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.MediaColumns.DATE_ADDED,
                MediaStore.MediaColumns.DATE_MODIFIED,
                MediaStore.MediaColumns.WIDTH,
                MediaStore.MediaColumns.HEIGHT,
                MediaStore.MediaColumns.ORIENTATION,
            ),
            bundleOf(),
            null,
        )?.use {
            val idIndex = it.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val bucketIdIndex = it.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_ID)
            val displayNameIndex = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val isFavoriteIndex = it.getColumnIndexOrThrow(MediaStore.MediaColumns.IS_FAVORITE)
            val isTrashedIndex = it.getColumnIndexOrThrow(MediaStore.MediaColumns.IS_TRASHED)
            val mimeTypeIndex = it.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val dateAddedIndex = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val dateModifiedIndex =
                it.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            val widthIndex = it.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
            val heightIndex = it.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
            val orientationIndex =
                it.getColumnIndexOrThrow(MediaStore.MediaColumns.ORIENTATION)

            if (it.count != 1) {
                return@use null
            }

            it.moveToFirst()

            val id = it.getLong(idIndex)
            val bucketId = it.getInt(bucketIdIndex)
            val displayName = it.getString(displayNameIndex)
            val isFavorite = it.getInt(isFavoriteIndex)
            val isTrashed = it.getInt(isTrashedIndex)
            val fileType = contentResolver.getType(uri)?.let { type ->
                FileType.fromMimeType(type)
            } ?: return@use null
            val mimeType = it.getString(mimeTypeIndex)
            val dateAdded = it.getLong(dateAddedIndex)
            val dateModified = it.getLong(dateModifiedIndex)
            val width = it.getInt(widthIndex)
            val height = it.getInt(heightIndex)
            val orientation = it.getInt(orientationIndex)

            /*
            Media(
                id,
                bucketId,
                displayName,
                isFavorite,
                isTrashed,
                fileType.mediaStoreValue,
                mimeType,
                dateAdded,
                dateModified,
                width,
                height,
                orientation,
            )
             */
            null
        }
    }.getOrNull()

    /**
     * Given a [Uri], figure out it's MIME type.
     * Must not be executed on main thread.
     * @param uri The [Uri] to parse
     */
    private fun getContentType(uri: Uri) = when (uri.scheme) {
        "content" -> contentResolver.getType(uri)

        "file" -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(
            MimeTypeMap.getFileExtensionFromUrl(uri.toString())
        )

        "http", "https" -> {
            val request = Request.Builder()
                .head()
                .url(uri.toString())
                .build()

            runCatching {
                httpClient.newCall(request).execute().use { response ->
                    response.header("content-type")
                }
            }.getOrNull()
        }

        "rtsp" -> "video/rtsp" // Made up MIME type, just to get video type

        else -> null
    }

    companion object {
        /**
         * The album to show, defaults to [media]'s bucket ID.
         */
        const val ARG_ALBUM_URI = "album_uri"

        private val dateFormatter = SimpleDateFormat.getDateInstance()
        private val timeFormatter = SimpleDateFormat.getTimeInstance()
    }
}
