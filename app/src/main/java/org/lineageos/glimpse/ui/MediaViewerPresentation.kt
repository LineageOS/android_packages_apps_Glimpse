/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.ui

import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.view.Display

class MediaViewerPresentation(
    context: Context, display: Display
) : Presentation(context, display) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // TODO: setContentView(), handle playback, etc.
    }
}
