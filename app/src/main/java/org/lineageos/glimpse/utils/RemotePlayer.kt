/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.utils

import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.mediarouter.media.MediaControlIntent
import androidx.mediarouter.media.MediaItemStatus
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import androidx.mediarouter.media.MediaSessionStatus
import androidx.mediarouter.media.RemotePlaybackClient
import org.lineageos.glimpse.models.Media
import org.lineageos.glimpse.ui.MediaViewerPresentation
import org.lineageos.glimpse.viewmodels.MediaViewerUIViewModel

/**
 * Remote playback manager
 * @param context The context
 * @param lifecycle The lifecycle to observe for callback registration,
 *        for fragment use the view lifecycle
 * @param uiModelGetter A unit that returns a [MediaViewerUIViewModel]
 */
class RemotePlayer(
    private val context: Context,
    lifecycle: Lifecycle,
    private val uiModelGetter: () -> MediaViewerUIViewModel,
) {
    // UI model shortcut
    private val uiModel
        get() = uiModelGetter()

    // Media route
    private val mediaRouter by lazy { MediaRouter.getInstance(context) }

    // Media route selector
    val mediaRouteSelector by lazy {
        MediaRouteSelector.Builder()
            .addControlCategory(MediaControlIntent.CATEGORY_LIVE_VIDEO)
            .addControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK)
            .build()
    }

    private val mediaRouterCallback = object : MediaRouter.Callback() {
        override fun onRouteSelected(
            router: MediaRouter,
            route: MediaRouter.RouteInfo,
            reason: Int
        ) {
            Log.i(LOG_TAG, "onRouteSelected: route: $route, reason: $reason")

            val remoteRoute = route.takeUnless { it.isDefault }

            Log.i(LOG_TAG, "${remoteRoute?.let { "Remote" } ?: "Local"} route selected")

            updateRoute(remoteRoute)
        }

        override fun onRouteUnselected(
            router: MediaRouter,
            route: MediaRouter.RouteInfo,
            reason: Int
        ) {
            Log.i(LOG_TAG, "onRouteUnselected: route: $route, reason: $reason")

            // TODO: Might not be needed
            updateRoute(null)
        }

        override fun onRoutePresentationDisplayChanged(
            router: MediaRouter,
            route: MediaRouter.RouteInfo
        ) {
            Log.i(LOG_TAG, "onRoutePresentationDisplayChanged: route: $route")
            // TODO
        }
    }

    // Current route
    private var currentRoute: MediaRouter.RouteInfo? = null
    private var remotePlaybackClient: RemotePlaybackClient? = null
    private var presentation: MediaViewerPresentation? = null

    // Displayed media observer
    private val displayedMediaObserver by lazy {
        Observer<Media?> {
            updateMedia(it)
        }
    }

    // Lifecycle observer
    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onCreate(owner: LifecycleOwner) {
            super.onCreate(owner)

            // Register MediaRouter callbacks without passive discovery
            mediaRouter.addCallback(mediaRouteSelector, mediaRouterCallback, 0)
        }

        override fun onStart(owner: LifecycleOwner) {
            super.onStart(owner)

            // Re-register MediaRouter callbacks with passive discovery
            mediaRouter.addCallback(
                mediaRouteSelector, mediaRouterCallback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY
            )

            // Observe displayed media
            uiModel.displayedMedia.observeForever(displayedMediaObserver)
        }

        override fun onStop(owner: LifecycleOwner) {
            // Stop observing displayed media
            uiModel.displayedMedia.removeObserver(displayedMediaObserver)

            // Re-register the MediaRouter callbacks but without passive discovery
            mediaRouter.addCallback(mediaRouteSelector, mediaRouterCallback, 0)

            super.onStop(owner)
        }

        override fun onDestroy(owner: LifecycleOwner) {
            // Unregister MediaRouter callbacks
            mediaRouter.removeCallback(mediaRouterCallback)

            super.onDestroy(owner)
        }
    }

    init {
        // Start observing the owner lifecycle
        lifecycle.addObserver(lifecycleObserver)
    }

    /**
     * Update the currently used route.
     * @param route The new route, null if local playback is wanted
     */
    private fun updateRoute(route: MediaRouter.RouteInfo?) {
        if (route == currentRoute) {
            Log.d(LOG_TAG, "Received same route, ignoring")
            return
        }

        // Check if we support this route
        if (route?.matchesSelector(mediaRouteSelector) == false) {
            Log.e(LOG_TAG, "Received unsupported route")
            return
        }

        Log.i(LOG_TAG, "Stopping current route sessions")

        // Stop any remote playback client if present
        remotePlaybackClient?.release()
        remotePlaybackClient = null

        // Dismiss any presentation if present
        presentation?.dismiss()
        presentation = null

        // Update the current route
        currentRoute = route

        route?.also handleRoute@{ route ->
            Log.i(LOG_TAG, "Received valid route: $route")

            route.takeIf {
                it.supportsControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK)
            }?.let handleRemotePlaybackRoute@{
                Log.i(LOG_TAG, "Route supports remote playback")

                remotePlaybackClient = RemotePlaybackClient(context, it)

                return@handleRoute
            }

            Log.i(LOG_TAG, "Route doesn't support remote playback")

            route.takeIf {
                route.supportsControlCategory(MediaControlIntent.CATEGORY_LIVE_VIDEO)
            }?.let handleLiveVideoRoute@{
                Log.i(LOG_TAG, "Route supports live video")

                val display = it.presentationDisplay ?: run {
                    Log.i(LOG_TAG, "Route doesn't have a valid display")
                    return@handleLiveVideoRoute
                }

                Log.i(LOG_TAG, "Found valid display")

                presentation = MediaViewerPresentation(context, display).apply {
                    show()
                }

                return@handleRoute
            }

            Log.wtf(LOG_TAG, "Nothing is supported, ignoring?")
            currentRoute = null
        } ?: run {
            Log.i(LOG_TAG, "Received no route, using local playback")
        }

        uiModel.isCasting.value = currentRoute != null

        updateMedia(uiModel.displayedMedia.value)
    }

    /**
     * Update the currently playing media.
     * @param media The [Media] to playback, null to stop
     */
    private fun updateMedia(media: Media?) {
        remotePlaybackClient?.let { remotePlaybackClient ->
            media?.also {
                /**
                 * TODO: Check if media is supported
                 * @see MediaRouter.getSelectedRoute
                 */

                remotePlaybackClient.play(
                    it.uri,
                    it.mimeType,
                    null,
                    0L,
                    null,
                    object : RemotePlaybackClient.ItemActionCallback() {
                        override fun onResult(
                            data: Bundle,
                            sessionId: String,
                            sessionStatus: MediaSessionStatus?,
                            itemId: String,
                            itemStatus: MediaItemStatus
                        ) {
                            Log.i(
                                LOG_TAG,
                                "RemotePlaybackClient: play: onResult: " +
                                        "data: $data" +
                                        ", sessionId: $sessionId" +
                                        ", sessionStatus: $sessionStatus" +
                                        ", itemId: $itemId" +
                                        ", itemStatus: $itemStatus"
                            )
                        }

                        override fun onError(error: String?, code: Int, data: Bundle?) {
                            Log.i(
                                LOG_TAG,
                                "RemotePlaybackClient: play: onError: " +
                                    "error: $error" +
                                    ", code: $code" +
                                    ", data: $data"
                            )
                        }
                    }
                )
            } ?: run {
                remotePlaybackClient.stop(
                    null,
                    object : RemotePlaybackClient.SessionActionCallback() {
                        override fun onResult(
                            data: Bundle,
                            sessionId: String,
                            sessionStatus: MediaSessionStatus?
                        ) {
                            Log.i(
                                LOG_TAG,
                                "RemotePlaybackClient: stop: onResult: " +
                                        "data: $data" +
                                        ", sessionId: $sessionId" +
                                        ", sessionStatus: $sessionStatus"
                            )
                        }

                        override fun onError(error: String?, code: Int, data: Bundle?) {
                            Log.i(
                                LOG_TAG,
                                "RemotePlaybackClient: stop: onError: " +
                                        "error: $error" +
                                        ", code: $code" +
                                        ", data: $data"
                            )
                        }
                    }
                )
            }
        }
    }

    companion object {
        const val LOG_TAG = "RemotePlayer"
    }
}
