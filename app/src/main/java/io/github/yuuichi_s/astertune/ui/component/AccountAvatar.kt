/*
 * Copyright (C) 2025 OuterTune Project
 * Copyright (C) 2026 AsterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package io.github.yuuichi_s.astertune.ui.component

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import io.github.yuuichi_s.astertune.constants.AccountImageUrlKey
import io.github.yuuichi_s.astertune.constants.InnerTubeCookieKey
import io.github.yuuichi_s.astertune.utils.rememberPreference
import com.zionhuang.innertube.utils.parseCookieString

/**
 * The signed-in account's profile image, falling back to [placeholder] when signed out, when no
 * image has been stored, or when the image cannot be loaded.
 */
@Composable
fun AccountAvatar(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    contentDescription: String? = null,
    placeholder: @Composable () -> Unit,
) {
    val innerTubeCookie by rememberPreference(InnerTubeCookieKey, "")
    val imageUrl by rememberPreference(AccountImageUrlKey, "")
    val isLoggedIn = remember(innerTubeCookie) {
        runCatching { "SAPISID" in parseCookieString(innerTubeCookie) }.getOrDefault(false)
    }

    if (!isLoggedIn || imageUrl.isEmpty()) {
        placeholder()
        return
    }

    SubcomposeAsyncImage(
        model = imageUrl,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(size)
            .clip(CircleShape),
        loading = { placeholder() },
        error = { placeholder() },
    )
}
