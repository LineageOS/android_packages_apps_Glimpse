/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.viewmodels

sealed class QueryResult<T> {
    class Empty<T> : QueryResult<T>()
    class Data<T>(val values: List<T>) : QueryResult<T>()
}
