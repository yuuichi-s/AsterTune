/*
 * Copyright (C) 2025 O⁠ute⁠rTu⁠ne Project
 * Copyright (C) 2026 AsterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package io.github.yuuichi_s.astertune.ui.utils

import android.Manifest
import android.os.Build
import io.github.yuuichi_s.astertune.models.CulmSongs
import io.github.yuuichi_s.astertune.models.DirectoryTree
import io.github.yuuichi_s.astertune.utils.fixFilePath

const val TAG = "LocalMediaUtils"

const val EXTRACTOR_TAG = "MetadataExtractor"

// stuff to make this work
val MEDIA_PERMISSION_LEVEL =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE
const val STORAGE_ROOT = "/storage/"
val ARTIST_SEPARATORS = Regex("\\s*;\\s*|\\s*ft\\.\\s*|\\s*feat\\.\\s*|\\s*&\\s*|\\s*,\\s*", RegexOption.IGNORE_CASE)
val uninitializedDirectoryTree = DirectoryTree(STORAGE_ROOT, CulmSongs(0))
private var cachedDirectoryTree: ArrayList<DirectoryTree> = ArrayList()

/**
 * ==========================
 * Various misc helpers
 * ==========================
 */


/**
 * Get cached DirectoryTree
 */
fun getDirectoryTree(path: String): DirectoryTree {
    val yes = cachedDirectoryTree.firstOrNull { fixFilePath(it.getFullPath()) == fixFilePath(path) }

    if (yes != null) {
        return yes
    }
    return uninitializedDirectoryTree
}

/**
 * Cache a DirectoryTree
 */
fun cacheDirectoryTree(new: DirectoryTree) {
    // initiate with root's subdirs
    if (cachedDirectoryTree.isEmpty() && fixFilePath(new.getFullPath()) == STORAGE_ROOT) {
        val dirs = new.getFlattenedSubdirs(true)
        dirs.forEach { it.isSkeleton = !it.files.isEmpty() }
        cachedDirectoryTree.addAll(dirs)
        return
    }

    // update structure
    val match = cachedDirectoryTree.firstOrNull { fixFilePath(it.getFullPath()) == fixFilePath(new.getFullPath()) }
    if (match == null) {
        cachedDirectoryTree.add(new)
    } else {
        match.subdirs = new.subdirs
        match.files = new.files
        match.isSkeleton = new.isSkeleton
    }
}

fun clearDtCache() {
    cachedDirectoryTree.clear()
    DirectoryTree.directoryUID = 0
}