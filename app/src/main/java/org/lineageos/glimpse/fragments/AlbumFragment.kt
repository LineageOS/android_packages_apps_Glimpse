/*
 * SPDX-FileCopyrightText: 2023-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.fragments

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.AppBarConfiguration
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
import org.lineageos.glimpse.ext.getViewProperty
import org.lineageos.glimpse.models.Album
import org.lineageos.glimpse.models.Media
import org.lineageos.glimpse.models.RequestStatus
import org.lineageos.glimpse.ui.recyclerview.ThumbnailAdapter
import org.lineageos.glimpse.ui.recyclerview.ThumbnailItemDetailsLookup
import org.lineageos.glimpse.ui.recyclerview.ThumbnailLayoutManager
import org.lineageos.glimpse.utils.MediaDialogsUtils
import org.lineageos.glimpse.utils.MediaStoreBuckets
import org.lineageos.glimpse.utils.PermissionsChecker
import org.lineageos.glimpse.utils.PermissionsUtils
import org.lineageos.glimpse.viewmodels.AlbumViewModel
import kotlin.reflect.safeCast

/**
 * A fragment showing a list of media from a specific album with thumbnails.
 */
class AlbumFragment : Fragment(R.layout.fragment_album_viewer) {
    // View models
    private val viewModel by viewModels<AlbumViewModel>()

    // Views
    private val appBarLayout by getViewProperty<AppBarLayout>(R.id.appBarLayout)
    private val noMediaLinearLayout by getViewProperty<LinearLayout>(R.id.noMediaLinearLayout)
    private val recyclerView by getViewProperty<RecyclerView>(R.id.recyclerView)
    private val toolbar by getViewProperty<MaterialToolbar>(R.id.toolbar)

    // MediaStore
    private val thumbnailAdapter by lazy {
        ThumbnailAdapter(viewModel) { media ->
            startActivity(
                Intent(requireContext(), ViewActivity::class.java).apply {
                    action = MediaStore.ACTION_REVIEW
                    data = media.uri
                    putExtra(ViewActivity.ARG_ALBUM_URI, albumUri)
                }
            )
        }
    }

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
                when (0) {
                    MediaStoreBuckets.MEDIA_STORE_BUCKET_TRASH.id -> R.menu.album_action_bar_trash
                    else -> R.menu.album_action_bar
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

                    else -> false
                }
            } ?: false

        override fun onDestroyActionMode(mode: ActionMode?) {
            selectionTracker?.clearSelection()
        }
    }

    private val inSelectionModeObserver = Observer { inSelectionMode: Boolean ->
        if (inSelectionMode) {
            startSelectionMode()
        } else {
            endSelectionMode()
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
    private val albumUri: Uri
        get() = requireArguments().getParcelable(ARG_ALBUM_URI, Uri::class)!!

    // Permissions
    private val permissionsChecker = PermissionsChecker(this, PermissionsUtils.mainPermissions)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val navController = findNavController()

        appBarLayout.statusBarForeground =
            MaterialShapeDrawable.createWithElevationOverlay(requireContext())

        val appBarConfiguration = AppBarConfiguration(navController.graph)
        toolbar.setupWithNavController(navController, appBarConfiguration)

        when (0) {
            MediaStoreBuckets.MEDIA_STORE_BUCKET_TRASH.id ->
                R.menu.fragment_album_viewer_toolbar_trash

            else -> null
        }?.let {
            toolbar.inflateMenu(it)
        }

        toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.emptyTrash -> {
                    val selection = thumbnailAdapter.currentList.mapNotNull {
                        AlbumViewModel.DataType.Thumbnail::class.safeCast(it)?.media
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

        selectionTracker = SelectionTracker.Builder(
            "thumbnail-${albumUri}",
            recyclerView,
            thumbnailAdapter.itemKeyProvider,
            ThumbnailItemDetailsLookup(recyclerView),
            StorageStrategy.createParcelableStorage(Media::class.java),
        ).withSelectionPredicate(
            SelectionPredicates.createSelectAnything()
        ).build().also {
            thumbnailAdapter.selectionTracker = it
            it.addObserver(selectionTrackerObserver)
        }

        viewModel.inSelectionMode.observe(viewLifecycleOwner, inSelectionModeObserver)

        viewModel.loadAlbum(albumUri)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                permissionsChecker.withPermissionsGranted {
                    loadData()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()

        // Clear action mode if still active
        endSelectionMode()
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
                viewModel.album.collectLatest {
                    when (it) {
                        is RequestStatus.Loading -> {
                            // Do nothing
                        }

                        is RequestStatus.Success -> {
                            val (album, _) = it.data

                            toolbar.title = album.name
                        }

                        is RequestStatus.Error -> {
                            toolbar.title = ""
                        }
                    }
                }
            }

            launch {
                viewModel.albumWithHeaders.collectLatest {
                    thumbnailAdapter.submitList(it)

                    val noMedia = it.isEmpty()
                    recyclerView.isVisible = !noMedia
                    toolbar.menu.findItem(R.id.emptyTrash)?.isVisible = !noMedia
                    noMediaLinearLayout.isVisible = noMedia
                }
            }
        }
    }

    private fun updateSelection() {
        viewModel.inSelectionMode.value = selectionTracker?.hasSelection() == true

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

    companion object {
        private const val ARG_ALBUM_URI = "album_uri"

        /**
         * Create a [Bundle] to use as the arguments for this fragment.
         * @param albumUri The [Album] to display's bucket ID, if null, reels will be shown
         */
        fun createBundle(
            albumUri: Uri,
        ) = bundleOf(
            ARG_ALBUM_URI to albumUri,
        )
    }
}
