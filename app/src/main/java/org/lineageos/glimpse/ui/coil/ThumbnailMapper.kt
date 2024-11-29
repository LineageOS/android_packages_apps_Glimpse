/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.ui.coil

import coil3.map.Mapper
import coil3.request.Options
import org.lineageos.glimpse.models.Thumbnail

object ThumbnailMapper : Mapper<Thumbnail, Any> {
    override fun map(data: Thumbnail, options: Options) = data.bitmap ?: data.uri
}
