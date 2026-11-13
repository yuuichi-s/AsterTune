/*
 * Copyright (C) 2026 AsterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package io.github.yuuichi_s.lastfm

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Pending scrobbles kept in [file] across restarts, in insertion order.
 *
 * Non-empty updates are written to a temporary file before replacing [file]. Unreadable or
 * malformed queue files are treated as empty.
 */
class ScrobbleQueue(private val file: File) {
    private val lock = Any()
    private var items: MutableList<Scrobble>? = null

    fun add(scrobble: Scrobble) = synchronized(lock) {
        val list = load()
        list.add(scrobble)
        save(list)
    }

    /** The oldest [count] scrobbles. */
    fun peek(count: Int): List<Scrobble> = synchronized(lock) {
        load().take(count).toList()
    }

    /** Removes the oldest [count] scrobbles. */
    fun removeOldest(count: Int) = synchronized(lock) {
        val list = load()
        repeat(minOf(count, list.size)) { list.removeAt(0) }
        save(list)
    }

    fun clear() = synchronized(lock) {
        items = mutableListOf()
        file.delete()
        tempFile().delete()
    }

    val size: Int
        get() = synchronized(lock) { load().size }

    private fun load(): MutableList<Scrobble> {
        items?.let { return it }
        val loaded = runCatching {
            if (file.exists()) json.decodeFromString(serializer, file.readText()).toMutableList() else null
        }.getOrNull() ?: mutableListOf()
        items = loaded
        return loaded
    }

    private fun save(list: List<Scrobble>) {
        if (list.isEmpty()) {
            file.delete()
            return
        }
        file.parentFile?.mkdirs()
        val temp = tempFile()
        temp.writeText(json.encodeToString(serializer, list))
        // rename(2) atomically replaces the target on Android. Fall back for platforms that refuse
        // to rename over an existing file.
        if (!temp.renameTo(file)) {
            file.delete()
            temp.renameTo(file)
        }
    }

    private fun tempFile() = File(file.path + ".tmp")

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
        val serializer = ListSerializer(Scrobble.serializer())
    }
}
