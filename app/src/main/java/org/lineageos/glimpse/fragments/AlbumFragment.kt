/*
 * SPDX-FileCopyrightText: 2023-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.fragments

import android.app.Activity
import android.app.WallpaperManager
import android.content.ClipData
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import androidx.recyclerview.selection.SelectionPredicates
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.selection.StorageStrategy
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.shape.MaterialShapeDrawable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.lineageos.glimpse.R
import org.lineageos.glimpse.ViewActivity
import org.lineageos.glimpse.ext.buildShareIntent
import org.lineageos.glimpse.ext.createDeleteRequest
import org.lineageos.glimpse.ext.createTrashRequest
import org.lineageos.glimpse.ext.getParcelable
import org.lineageos.glimpse.ext.getSerializable
import org.lineageos.glimpse.ext.getViewProperty
import org.lineageos.glimpse.ext.kill
import org.lineageos.glimpse.models.Album
import org.lineageos.glimpse.models.AlbumType
import org.lineageos.glimpse.models.Media
import org.lineageos.glimpse.models.MediaType
import org.lineageos.glimpse.models.RequestStatus
import org.lineageos.glimpse.ui.recyclerview.ThumbnailAdapter
import org.lineageos.glimpse.ui.recyclerview.ThumbnailItemDetailsLookup
import org.lineageos.glimpse.ui.recyclerview.ThumbnailLayoutManager
import org.lineageos.glimpse.utils.MediaDialogsUtils
import org.lineageos.glimpse.utils.PermissionsChecker
import org.lineageos.glimpse.utils.PermissionsUtils
import org.lineageos.glimpse.viewmodels.AlbumViewModel
import org.lineageos.glimpse.viewmodels.IntentsViewModel
import kotlin.reflect.safeCast

/**
 * A fragment showing a list of media from a specific album with thumbnails.
 */
class AlbumFragment : Fragment(R.layout.fragment_album) {
    // View models
    private val viewModel by viewModels<AlbumViewModel>()
    private val intentsViewModel by activityViewModels<IntentsViewModel>()

    // Views
    private val appBarLayout by getViewProperty<AppBarLayout>(R.id.appBarLayout)
    private val noMediaLinearLayout by getViewProperty<LinearLayout>(R.id.noMediaLinearLayout)
    private val recyclerView by getViewProperty<RecyclerView>(R.id.recyclerView)
    private val toolbar by getViewProperty<MaterialToolbar>(R.id.toolbar)

    // System services
    private val wallpaperManager by lazy {
        requireContext().getSystemService(WallpaperManager::class.java)
    }

    // RecyclerView
    private val thumbnailAdapter by lazy { ThumbnailAdapter() }

    // Selection
    private var selectionTracker: SelectionTracker<Media>? = null

    private val selectionTrackerObserver =
        object : SelectionTracker.SelectionObserver<Media>() {
            override fun onSelectionChanged() {
                super.onSelectionChanged()

                updateSelection()
            }

            override fun onSelectionRefresh() {
                super.onSelectionRefresh()

                updateSelection()
            }

            override fun onSelectionRestored() {
                super.onSelectionRestored()

                updateSelection()
            }
        }

    private var actionMode: ActionMode? = null

    private val actionModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            requireActivity().menuInflater.inflate(
                when (viewModel.albumRequest.value?.albumType) {
                    AlbumType.TRASH -> R.menu.album_action_bar_trash
                    else -> when (intentsViewModel.isPicking.value) {
                        true -> R.menu.fragment_album_pick_action_bar
                        false -> R.menu.fragment_album_action_bar
                    }
                },
                menu
            )
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?) = false

        override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?) =
            selectionTracker?.selection?.toList()?.toTypedArray()?.takeUnless {
                it.isEmpty()
            }?.let { selection ->
                when (item?.itemId) {
                    R.id.deleteForever -> {
                        MediaDialogsUtils.openDeleteForeverDialog(requireContext(), *selection) {
                            deleteForeverContract.launch(
                                requireContext().contentResolver.createDeleteRequest(
                                    *it.map { media ->
                                        media.uri
                                    }.toTypedArray()
                                )
                            )
                        }

                        true
                    }

                    R.id.restoreFromTrash -> {
                        MediaDialogsUtils.openRestoreFromTrashDialog(requireContext(), *selection) {
                            trashMedias(false, *selection)
                        }

                        true
                    }

                    R.id.share -> {
                        requireActivity().startActivity(buildShareIntent(*selection))

                        true
                    }

                    R.id.moveToTrash -> {
                        MediaDialogsUtils.openMoveToTrashDialog(requireContext(), *selection) {
                            trashMedias(true, *selection)
                        }

                        true
                    }

                    R.id.done -> {
                        sendResult(*selection)
                        true
                    }

                    else -> false
                }
            } ?: false

        override fun onDestroyActionMode(mode: ActionMode?) {
            selectionTracker?.clearSelection()
        }
    }

    // Contracts
    private var lastProcessedSelection: Array<out Media>? = null

    private val deleteForeverContract =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            val succeeded = it.resultCode != Activity.RESULT_CANCELED
            val count = lastProcessedSelection?.count() ?: 1

            MediaDialogsUtils.showDeleteForeverResultSnackbar(
                requireContext(),
                requireView(),
                succeeded, count,
            )

            lastProcessedSelection = null
            selectionTracker?.clearSelection()
        }

    private val trashContract =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            val succeeded = it.resultCode != Activity.RESULT_CANCELED
            val count = lastProcessedSelection?.count() ?: 1

            MediaDialogsUtils.showMoveToTrashResultSnackbar(
                requireContext(),
                requireView(),
                succeeded, count,
                actionCallback = lastProcessedSelection?.let { trashedMedias ->
                    {
                        trashMedias(false, *trashedMedias)
                    }
                }
            )

            lastProcessedSelection = null
            selectionTracker?.clearSelection()
        }

    private val restoreFromTrashContract =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            val succeeded = it.resultCode != Activity.RESULT_CANCELED
            val count = lastProcessedSelection?.count() ?: 1

            MediaDialogsUtils.showRestoreFromTrashResultSnackbar(
                requireContext(),
                requireView(),
                succeeded, count,
                actionCallback = lastProcessedSelection?.let { trashedMedias ->
                    {
                        trashMedias(true, *trashedMedias)
                    }
                }
            )

            lastProcessedSelection = null
            selectionTracker?.clearSelection()
        }

    // Arguments
    private val albumType: AlbumType?
        get() = requireArguments().getSerializable(ARG_ALBUM_TYPE, AlbumType::class)
    private val albumUri: Uri?
        get() = requireArguments().getParcelable(ARG_ALBUM_URI, Uri::class)
    private val mediaType: MediaType?
        get() = requireArguments().getSerializable(ARG_MEDIA_TYPE, MediaType::class)
    private val mimeType: String?
        get() = requireArguments().getString(ARG_MIME_TYPE, null)

    // Permissions
    private val permissionsChecker = PermissionsChecker(this, PermissionsUtils.mainPermissions)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Insets
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )

            toolbar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = insets.left
                rightMargin = insets.right
            }

            recyclerView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = insets.left
                rightMargin = insets.right
            }
            recyclerView.updatePadding(bottom = insets.bottom)

            windowInsets
        }

        toolbar.setupWithNavController(findNavController())

        val context = requireContext()

        appBarLayout.statusBarForeground = MaterialShapeDrawable.createWithElevationOverlay(context)

        when (albumType) {
            AlbumType.TRASH -> R.menu.fragment_album_toolbar_trash

            else -> null
        }?.also {
            toolbar.inflateMenu(it)
        } ?: toolbar.menu.clear()

        toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.emptyTrash -> {
                    val selection = thumbnailAdapter.currentList.mapNotNull {
                        AlbumViewModel.AlbumContent.MediaItem::class.safeCast(it)?.media
                    }.toTypedArray()
                    val count = selection.size

                    if (count > 0) {
                        MediaDialogsUtils.openDeleteForeverDialog(requireContext(), *selection) {
                            deleteForeverContract.launch(
                                requireContext().contentResolver.createDeleteRequest(
                                    *it.map { media -> media.uri }.toTypedArray()
                                )
                            )
                        }
                    }

                    true
                }

                else -> false
            }
        }

        recyclerView.layoutManager = ThumbnailLayoutManager(
            requireContext(), thumbnailAdapter
        )
        recyclerView.adapter = thumbnailAdapter

        thumbnailAdapter.setOnItemSelected { media ->
            if (intentsViewModel.isPicking.value) {
                selectionTracker?.select(media)
            } else {
                startActivity(
                    Intent(requireContext(), ViewActivity::class.java).apply {
                        action = MediaStore.ACTION_REVIEW
                        setDataAndTypeAndNormalize(media.uri, media.mimeType)
                        putExtras(
                            ViewActivity.createBundle(
                                albumType,
                                albumUri,
                                mediaType,
                                mimeType,
                            )
                        )
                    }
                )
            }
        }

        selectionTracker = SelectionTracker.Builder(
            "thumbnail-${albumUri}",
            recyclerView,
            thumbnailAdapter.itemKeyProvider,
            ThumbnailItemDetailsLookup(recyclerView),
            StorageStrategy.createParcelableStorage(Media::class.java),
        ).withSelectionPredicate(
            when (intentsViewModel.allowMultipleSelection.value) {
                true -> SelectionPredicates.createSelectAnything()
                false -> SelectionPredicates.createSelectSingleAnything()
            }
        ).build().also {
            thumbnailAdapter.selectionTracker = it
            it.addObserver(selectionTrackerObserver)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                permissionsChecker.withPermissionsGranted {
                    loadData()
                }
            }
        }
    }

    override fun onDestroyView() {
        // Clear action mode if still active
        endSelectionMode()

        thumbnailAdapter.selectionTracker = null
        selectionTracker?.kill(thumbnailAdapter)
        selectionTracker = null

        recyclerView.layoutManager = null
        recyclerView.adapter = null

        thumbnailAdapter.setOnItemSelected(null)

        super.onDestroyView()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        recyclerView.layoutManager = ThumbnailLayoutManager(
            requireContext(), thumbnailAdapter
        )
    }

    private suspend fun loadData() {
        coroutineScope {
            launch {
                intentsViewModel.parsedIntent.collectLatest {
                    when (it) {
                        is IntentsViewModel.ParsedIntent.PickIntent -> {
                            viewModel.loadAlbum(
                                AlbumViewModel.AlbumRequest(
                                    albumType = albumType,
                                    albumUri = albumUri,
                                    mediaType = mediaType,
                                    mimeType = it.mimeType ?: mimeType,
                                )
                            )
                        }

                        else -> viewModel.loadAlbum(
                            AlbumViewModel.AlbumRequest(
                                albumType = albumType,
                                albumUri = albumUri,
                                mediaType = mediaType,
                                mimeType = mimeType,
                            )
                        )
                    }
                }
            }

            launch {
                viewModel.album.collectLatest {
                    when (it) {
                        is RequestStatus.Loading -> {
                            // Do nothing
                        }

                        is RequestStatus.Success -> {
                            val (album, medias) = it.data

                            val albumRequest = viewModel.albumRequest.value
                            when (albumRequest?.albumType) {
                                AlbumType.REELS -> when (albumRequest.mediaType) {
                                    MediaType.IMAGE -> R.string.album_photos
                                    MediaType.VIDEO -> R.string.album_videos
                                    else -> R.string.album_reels
                                }

                                AlbumType.FAVORITES -> R.string.album_favorites

                                AlbumType.TRASH -> R.string.album_trash

                                null -> null
                            }?.also { stringResId ->
                                toolbar.setTitle(stringResId)
                            } ?: run {
                                toolbar.title = album.name
                            }

                            thumbnailAdapter.submitList(medias)

                            val isEmpty = medias.isEmpty()
                            recyclerView.isVisible = !isEmpty
                            toolbar.menu.findItem(R.id.emptyTrash)?.isVisible = !isEmpty
                            noMediaLinearLayout.isVisible = isEmpty
                        }

                        is RequestStatus.Error -> {
                            Log.e(LOG_TAG, "Failed to load album, error: ${it.error}")

                            toolbar.title = ""

                            thumbnailAdapter.submitList(listOf())

                            recyclerView.isVisible = false
                            toolbar.menu.findItem(R.id.emptyTrash)?.isVisible = false
                            noMediaLinearLayout.isVisible = true
                        }
                    }
                }
            }

            launch {
                viewModel.inSelectionMode.collectLatest {
                    thumbnailAdapter.setInSelectionMode(it)

                    if (it) {
                        startSelectionMode()
                    } else {
                        endSelectionMode()
                    }
                }
            }
        }
    }

    private fun updateSelection() {
        viewModel.setInSelectionMode(selectionTracker?.hasSelection() == true)

        selectionTracker?.selection?.count()?.takeIf { it > 0 }?.let {
            startSelectionMode().apply {
                title = resources.getQuantityString(
                    R.plurals.thumbnail_selection_count, it, it
                )
            }
        }
    }

    private fun startSelectionMode() = actionMode ?: toolbar.startActionMode(
        actionModeCallback
    ).also {
        actionMode = it
    }

    private fun endSelectionMode() {
        actionMode?.finish()
        actionMode = null
    }

    private fun trashMedias(trash: Boolean, vararg medias: Media) {
        lastProcessedSelection = medias

        val contract = when (trash) {
            true -> trashContract
            false -> restoreFromTrashContract
        }

        contract.launch(
            requireContext().contentResolver.createTrashRequest(
                trash, *medias.map { it.uri }.toTypedArray()
            )
        )
    }

    /**
     * Set the activity result and close the activity.
     * @param medias The selected medias
     */
    private fun sendResult(vararg medias: Media) {
        val activity = activity ?: return
        val intent = activity.intent ?: return

        when (intent.action) {
            Intent.ACTION_GET_CONTENT,
            Intent.ACTION_PICK -> activity.setResult(
                Activity.RESULT_OK,
                Intent().apply {
                    if (intentsViewModel.allowMultipleSelection.value) {
                        clipData = ClipData.newUri(
                            activity.contentResolver, "", medias.first().uri
                        ).also { clipData ->
                            for (media in 1 until medias.size) {
                                clipData.addItem(
                                    ClipData.Item(medias[media].uri)
                                )
                            }
                        }
                    } else {
                        require(medias.size == 1) {
                            "More than one media provided when only one was requested"
                        }

                        data = medias.first().uri
                    }

                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
            )

            Intent.ACTION_SET_WALLPAPER -> {
                require(medias.size == 1) {
                    "More than one media provided when only one was requested"
                }

                runCatching {
                    wallpaperManager.getCropAndSetWallpaperIntent(
                        medias.first().uri
                    )
                }.getOrNull()?.also {
                    activity.startActivity(it)
                } ?: Toast.makeText(
                    activity,
                    R.string.intent_no_system_wallpaper_cropper_available,
                    Toast.LENGTH_LONG,
                ).show()
            }

            else -> throw Exception("Unknown action")
        }

        activity.finish()
    }

    companion object {
        private val LOG_TAG = AlbumFragment::class.simpleName!!

        private const val ARG_ALBUM_TYPE = "album_type"
        private const val ARG_ALBUM_URI = "album_uri"
        private const val ARG_MEDIA_TYPE = "media_type"
        private const val ARG_MIME_TYPE = "mime_type"

        /**
         * Create a [Bundle] to use as the arguments for this fragment.
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
            ARG_ALBUM_TYPE to albumType,
            ARG_ALBUM_URI to albumUri,
            ARG_MEDIA_TYPE to fileType,
            ARG_MIME_TYPE to mimeType,
        )
    }
}
