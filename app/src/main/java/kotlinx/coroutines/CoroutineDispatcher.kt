/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package kotlinx.coroutines

// Shim for com.github.panpf.zoomimage.subsampling.internal.TileManager
object CoroutineDispatcherShim {
    @JvmStatic
    @OptIn(ExperimentalCoroutinesApi::class)
    fun CoroutineDispatcher.limitedParallelism(
        parallelism: Int,
        name: String? = null
    ) = limitedParallelism(parallelism)
}
