/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.ext

import android.view.View
import androidx.media3.ui.PlayerControlView
import androidx.media3.ui.requestPlayPauseFocus
import androidx.media3.ui.updateAll

@androidx.media3.common.util.UnstableApi
fun PlayerControlView.fade(visible: Boolean) {
    (this as View).fade(visible)

    // This is needed to resume progress updating
    if (visible) {
        updateAll()
        requestPlayPauseFocus()
        show()
    }
}
