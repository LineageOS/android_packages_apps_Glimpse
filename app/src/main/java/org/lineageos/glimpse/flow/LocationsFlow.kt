/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.flow

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import org.lineageos.glimpse.models.Media
import org.lineageos.glimpse.utils.MediaStoreBuckets
import java.util.Collections

class LocationsFlow(context: Context) {
    // Flows
    private val mediaFlow = MediaFlow(context, MediaStoreBuckets.MEDIA_STORE_BUCKET_REELS.id)

    // Content resolver
    private val contentResolver = context.contentResolver

    // Geocoder
    private val geocoder = Geocoder(context)

    // Coroutines
    @OptIn(ExperimentalCoroutinesApi::class)
    private val ioScope = CoroutineScope(Job() + Dispatchers.IO.limitedParallelism(5))

    /**
     * Get a flow of location to media, to use as a thumbnail.
     */
    fun get() = channelFlow {
        mediaFlow.flowData().collect {
            val locations = Collections.synchronizedMap(mutableMapOf<String, Media>())

            val parseLocation = { addresses: List<Address>, media: Media ->
                addresses.getOrNull(0)?.let { address ->
                    val location = address.locality

                    if (!locations.containsKey(location)) {
                        locations[location] = media

                        trySend(Pair(location, media))
                    }
                }
            }

            for (media in it) {
                ioScope.launch {
                    runCatching {
                        contentResolver.openInputStream(media.uri)?.use { inputStream ->
                            val exifInterface = ExifInterface(inputStream)

                            val (lat, long) = exifInterface.latLong ?: return@runCatching

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                geocoder.getFromLocation(lat, long, 1) { addresses ->
                                    parseLocation(addresses, media)
                                }
                            } else {
                                @Suppress("DEPRECATION")
                                val addresses =
                                    geocoder.getFromLocation(lat, long, 1) ?: listOf()
                                parseLocation(addresses, media)
                            }
                        }
                    }
                }
            }
        }
    }
}
