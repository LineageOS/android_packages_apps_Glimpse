/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.ext

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.round
import kotlin.math.roundToInt

fun Double.toFraction(tolerance: Double = 1.0E-1): String {
    if (this < 0) {
        return "-" + (-this).toFraction()
    }
    var h1 = 1.0
    var h2 = 0.0
    var k1 = 0.0
    var k2 = 1.0
    var b = this
    do {
        val a = floor(b)
        var aux = h1
        h1 = a * h1 + h2
        h2 = aux
        aux = k1
        k1 = a * k1 + k2
        k2 = aux
        b = 1 / (b - a)
    } while (abs(this - h1 / k1) > this * tolerance)

    return "${h1.roundToInt()}/${k1.roundToInt()}"
}

fun Double.round(decimals: Int): Double {
    var multiplier = 1.0
    repeat(decimals) { multiplier *= 10 }
    return round(this * multiplier) / multiplier
}
