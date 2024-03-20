/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.recyclerview

import android.content.Context
import lineagex.core.ext.toPx

class AlbumThumbnailLayoutManager(
    context: Context,
) : DisplayAwareGridLayoutManager(context, 2, 24.toPx)
