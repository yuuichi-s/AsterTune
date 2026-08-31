/*
 * Copyright (C) 2025 OuterTune Project
 * Copyright (C) 2026 AsterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package io.github.yuuichi_s.astertune.models

import io.github.yuuichi_s.astertune.db.entities.FormatEntity
import io.github.yuuichi_s.astertune.db.entities.Song

/**
 * For passing along song metadata
 */
data class SongTempData(val song: Song, val format: FormatEntity?)