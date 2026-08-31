/*
 * Copyright (C) 2025 OuterTune Project
 * Copyright (C) 2026 AsterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package io.github.yuuichi_s.astertune.utils

import com.zionhuang.innertube.utils.parseCookieString

/**
 * Resolves a stored dataSyncId to the id the account is addressed by.
 *
 * Installations may hold a value containing "||": keep the id before it when the value ends with
 * "||", and the id after it otherwise. Every request authenticating as an account must use the same
 * resolution, or it addresses a different account than the rest of the app.
 */
fun normalizeDataSyncId(dataSyncId: String?): String? = dataSyncId?.let {
    it.takeIf { !it.contains("||") }
        ?: it.takeIf { it.endsWith("||") }?.substringBefore("||")
        ?: it.substringAfter("||")
}

/**
 * Whether a result fetched for [fetchedCookie] / [fetchedDataSyncId] still describes the account
 * that is signed in now.
 *
 * The account can be replaced while a fetch is in flight, so the result is discarded unless the
 * credentials it was fetched with are still the stored ones.
 */
fun isSameAccount(
    fetchedCookie: String?,
    fetchedDataSyncId: String?,
    currentCookie: String?,
    currentDataSyncId: String?,
): Boolean {
    if (currentCookie == null) return false
    // A hand-edited cookie can be unparseable; treat it as not signed in rather than crashing.
    val signedIn = runCatching { "SAPISID" in parseCookieString(currentCookie) }.getOrDefault(false)
    if (!signedIn) return false
    return fetchedCookie == currentCookie && fetchedDataSyncId == currentDataSyncId
}
