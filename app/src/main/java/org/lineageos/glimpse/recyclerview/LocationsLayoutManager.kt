/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.recyclerview

import android.content.Context
import org.lineageos.glimpse.ext.px

class LocationsLayoutManager(
    context: Context,
) : DisplayAwareGridLayoutManager(context, 4, 4.px)
