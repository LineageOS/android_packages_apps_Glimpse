/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.ui.recyclerview

import android.content.Context
import org.lineageos.glimpse.ext.px

class AlbumThumbnailLayoutManager(
    context: Context,
) : DisplayAwareGridLayoutManager(context, 2, 24.px)
