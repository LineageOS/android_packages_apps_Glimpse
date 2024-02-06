/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.fragments

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.lineageos.glimpse.R
import org.lineageos.glimpse.ViewActivity
import org.lineageos.glimpse.ext.*
import org.lineageos.glimpse.models.Album
import org.lineageos.glimpse.models.MediaStoreMedia
import org.lineageos.glimpse.recyclerview.ThumbnailAdapter
import org.lineageos.glimpse.recyclerview.ThumbnailItemDetailsLookup
import org.lineageos.glimpse.recyclerview.ThumbnailLayoutManager
import org.lineageos.glimpse.utils.MediaDialogsUtils
import org.lineageos.glimpse.utils.MediaStoreBuckets
import org.lineageos.glimpse.utils.PermissionsGatedCallback
import org.lineageos.glimpse.viewmodels.AlbumViewerViewModel
import org.lineageos.glimpse.viewmodels.QueryResult.Data
import org.lineageos.glimpse.viewmodels.QueryResult.Empty
import kotlin.reflect.safeCast

/**
 * A fragment showing a list of media from a specific album with thumbnails.
 * Use the [AlbumViewerFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class AlbumViewerFragment : Fragment(R.layout.fragment_album_viewer) {
    // View models
    private val model: AlbumViewerViewModel by viewModels {
        bucketId?.let {
            AlbumViewerViewModel.factory(requireActivity().application, it)
        } ?: AlbumViewerViewModel.factory(requireActivity().application)
    }

    // Views
    private val appBarLayout by getViewProperty<AppBarLayout>(R.id.appBarLayout)
    private val noMediaLinearLayout by getViewProperty<LinearLayout>(R.id.noMediaLinearLayout)
    private val recyclerView by getViewProperty<RecyclerView>(R.id.recyclerView)
    private val toolbar by getViewProperty<MaterialToolbar>(R.id.toolbar)

    // Permissions
    private val permissionsGatedCallback = PermissionsGatedCallback(this) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.mediaWithHeaders.collectLatest {
                    when (it) {
                        is Data -> {
                            thumbnailAdapter.submitList(it.values)

                            val noMedia = it.values.isEmpty()
                            recyclerView.isVisible = !noMedia
                            noMediaLinearLayout.isVisible = noMedia
                        }

                        is Empty -> Unit
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.album.collectLatest {
                    activity?.runOnUiThread {
                        toolbar.title = it.name
                    }
                }
            }
        }
    }

    // MediaStore
    private val thumbnailAdapter by lazy {
        ThumbnailAdapter(model) { media ->
            startActivity(
                Intent(requireContext(), ViewActivity::class.java).apply {
                    action = MediaStore.ACTION_REVIEW
                    data = media.uri
                    putExtra(ViewActivity.KEY_ALBUM_ID, model.bucketId)
                }
            )
        }
    }

    // Selection
    private var selectionTracker: SelectionTracker<MediaStoreMedia>? = null

    private val selectionTrackerObserver =
        object : SelectionTracker.SelectionObserver<MediaStoreMedia>() {
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
                when (bucketId) {
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
                        trashMedias(false, *selection)

                        true
                    }

                    R.id.share -> {
                        requireActivity().startActivity(buildShareIntent(*selection))

                        true
                    }

                    R.id.moveToTrash -> {
                        trashMedias(true, *selection)

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
    private var lastProcessedSelection: Array<out MediaStoreMedia>? = null

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
    private val bucketId by lazy { arguments?.getInt(KEY_BUCKET_ID) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val navController = findNavController()

        appBarLayout.statusBarForeground =
            MaterialShapeDrawable.createWithElevationOverlay(requireContext())

        val appBarConfiguration = AppBarConfiguration(navController.graph)
        toolbar.setupWithNavController(navController, appBarConfiguration)

        when (bucketId) {
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
                        AlbumViewerViewModel.DataType.Thumbnail::class.safeCast(it)?.media
                    }.toTypedArray()
                    val count = selection.size

                    if (count > 0) {
                        MediaDialogsUtils.openDeleteForeverDialog(requireContext(), *selection) {
                            deleteForeverContract.launch(
                                requireContext().contentResolver.createDeleteRequest(
                                    *it.mapNotNull { media ->
                                        MediaStoreMedia::class.safeCast(media)?.uri
                                    }.toTypedArray()
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
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())

            recyclerView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = insets.left
                rightMargin = insets.right
            }
            recyclerView.updatePadding(bottom = insets.bottom)

            windowInsets
        }

        selectionTracker = SelectionTracker.Builder(
            "thumbnail-${model.bucketId}",
            recyclerView,
            thumbnailAdapter.itemKeyProvider,
            ThumbnailItemDetailsLookup(recyclerView),
            StorageStrategy.createParcelableStorage(MediaStoreMedia::class.java),
        ).withSelectionPredicate(
            SelectionPredicates.createSelectAnything()
        ).build().also {
            thumbnailAdapter.selectionTracker = it
            it.addObserver(selectionTrackerObserver)
        }

        model.inSelectionMode.observe(viewLifecycleOwner, inSelectionModeObserver)

        permissionsGatedCallback.runAfterPermissionsCheck()
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

    private fun updateSelection() {
        model.inSelectionMode.value = selectionTracker?.hasSelection() == true

        selectionTracker?.selection?.count()?.takeIf { it > 0 }?.let {
            startSelectionMode().apply {
                title = getString(R.string.thumbnail_selection_count, it)
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

    private fun trashMedias(trash: Boolean, vararg medias: MediaStoreMedia) {
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
        private const val KEY_BUCKET_ID = "bucket_id"

        /**
         * Create a [Bundle] to use as the arguments for this fragment.
         * @param bucketId The [Album] to display's bucket ID, if null, reels will be shown
         */
        fun createBundle(
            bucketId: Int? = null,
        ) = bundleOf(
            KEY_BUCKET_ID to bucketId,
        )

        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @see createBundle
         * @return A new instance of fragment [AlbumViewerFragment].
         */
        fun newInstance(
            bucketId: Int? = null,
        ) = AlbumViewerFragment().apply {
            arguments = createBundle(bucketId)
        }
    }
}
