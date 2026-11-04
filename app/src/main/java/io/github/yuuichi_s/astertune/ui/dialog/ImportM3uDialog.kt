/*
 * Copyright (C) 2025 O​u​t​er​Tu​ne Project
 * Copyright (C) 2026 AsterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */
package io.github.yuuichi_s.astertune.ui.dialog


import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Input
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import io.github.yuuichi_s.astertune.LocalDatabase
import io.github.yuuichi_s.astertune.LocalSnackbarHostState
import io.github.yuuichi_s.astertune.R
import io.github.yuuichi_s.astertune.constants.ScannerM3uMatchCriteria
import io.github.yuuichi_s.astertune.db.MusicDatabase
import io.github.yuuichi_s.astertune.db.entities.ArtistEntity
import io.github.yuuichi_s.astertune.db.entities.Song
import io.github.yuuichi_s.astertune.db.entities.SongEntity
import io.github.yuuichi_s.astertune.models.toMediaMetadata
import io.github.yuuichi_s.astertune.ui.component.EnumListPreference
import io.github.yuuichi_s.astertune.ui.component.LazyColumnScrollbar
import io.github.yuuichi_s.astertune.utils.lmScannerCoroutine
import io.github.yuuichi_s.astertune.utils.reportException
import io.github.yuuichi_s.astertune.utils.scanners.LocalMediaScanner
import io.github.yuuichi_s.astertune.utils.scanners.LocalMediaScanner.Companion.compareM3uSong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.InputStream

@Composable
fun ImportM3uDialog(
    navController: NavController,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val snackbarHostState = LocalSnackbarHostState.current

    var scannerSensitivity by remember {
        mutableStateOf(ScannerM3uMatchCriteria.LEVEL_1)
    }

    var remoteLookup by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(false) }
    var showChoosePlaylistDialog by rememberSaveable {
        mutableStateOf(false)
    }
    var importedTitle by remember { mutableStateOf("") }
    val importedSongs = remember { mutableStateListOf<Song>() }
    val rejectedSongs = remember { mutableStateListOf<String>() }

    val importM3uLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        CoroutineScope(lmScannerCoroutine).launch {
            try {
                isLoading = true
                if (uri != null) {
                    val result = loadM3u(
                        context = context,
                        database = database,
                        snackbarHostState = snackbarHostState,
                        uri = uri,
                        matchStrength = scannerSensitivity,
                        searchOnline = remoteLookup
                    )
                    importedSongs.clear()
                    importedSongs.addAll(result.first)
                    rejectedSongs.clear()
                    rejectedSongs.addAll(result.second)
                    importedTitle = result.third
                }
            } catch (e: Exception) {
                reportException(e)
            } finally {
                isLoading = false
            }
        }

    }


    DefaultDialog(
        onDismiss = onDismiss,
        icon = { Icon(Icons.AutoMirrored.Rounded.Input, null) },
        title = { Text(stringResource(R.string.import_playlist)) },
    ) {
        EnumListPreference(
            title = { Text(stringResource(R.string.scanner_sensitivity_title)) },
            icon = { Icon(Icons.Rounded.GraphicEq, null) },
            selectedValue = scannerSensitivity,
            onValueSelected = { scannerSensitivity = it },
            valueText = {
                when (it) {
                    ScannerM3uMatchCriteria.LEVEL_0 -> stringResource(R.string.scanner_sensitivity_L0)
                    ScannerM3uMatchCriteria.LEVEL_1 -> stringResource(R.string.scanner_sensitivity_L1)
                    ScannerM3uMatchCriteria.LEVEL_2 -> stringResource(R.string.scanner_sensitivity_L2)
                }
            }
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = remoteLookup,
                onCheckedChange = { remoteLookup = it }
            )
            Text(
                stringResource(R.string.m3u_ytm_lookup), color = MaterialTheme.colorScheme.secondary,
                fontSize = 14.sp
            )
        }

        Row(
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
            }

            Button(
                onClick = {
                    importedSongs.clear()
                    rejectedSongs.clear()
                    importM3uLauncher.launch(arrayOf("audio/*"))
                },
                enabled = !isLoading
            ) {
                Text(stringResource(R.string.m3u_import_playlist))
            }
        }

        if (importedSongs.isNotEmpty()) {
            val lazyListState = rememberLazyListState()
            Text(
                text = stringResource(R.string.import_success_songs),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(modifier = Modifier.heightIn(max = 150.dp)) {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .heightIn(max = 150.dp)
                        .padding(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 20.dp)
                ) {
                    itemsIndexed(
                        items = importedSongs.map { it.title },
                        key = { index, _ -> index }
                    ) { index, item ->
                        Text(
                            text = item,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        )
                    }
                }
                LazyColumnScrollbar(
                    state = lazyListState,
                )
            }
        }

        if (rejectedSongs.isNotEmpty()) {
            val lazyListState = rememberLazyListState()
            Text(
                text = stringResource(R.string.import_failed_songs),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(modifier = Modifier.heightIn(max = 150.dp)) {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .heightIn(max = 150.dp)
                        .padding(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 20.dp)
                ) {
                    itemsIndexed(
                        items = rejectedSongs,
                        key = { index, _ -> index }
                    ) { index, item ->
                        Text(
                            text = item,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        )
                    }
                }
                LazyColumnScrollbar(
                    state = lazyListState,
                )
            }
        }



        Row(
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            TextButton(
                onClick = {
                    onDismiss()
                }
            ) {
                Text(stringResource(android.R.string.cancel))
            }

            TextButton(
                onClick = {
                    showChoosePlaylistDialog = true
                },
                enabled = importedSongs.isNotEmpty()
            ) {
                Text(stringResource(R.string.add_to_playlist))
            }
        }
    }


    /**
     * ---------------------------
     * Dialogs
     * ---------------------------
     */

    if (showChoosePlaylistDialog) {
        AddToPlaylistDialog(
            navController = navController,
            allowSyncing = false,
            initialTextFieldValue = importedTitle,
            songIds = importedSongs.map { it.id },
            onPreAdd = {
                importedSongs.forEach {
                    database.insert(it.toMediaMetadata())
                }
                emptyList()
            },
            onDismiss = { showChoosePlaylistDialog = false }
        )

    }


}

/**
 * Parse m3u file and scans the database for matching songs
 *
 * @param uri Uri for m3u file
 * @param matchStrength How lax should the scanner be
 * @param searchOnline Whether to enable fallback for trying to find the song on YTM
 */
suspend fun loadM3u(
    context: Context,
    database: MusicDatabase,
    snackbarHostState: SnackbarHostState,
    uri: Uri,
    matchStrength: ScannerM3uMatchCriteria = ScannerM3uMatchCriteria.LEVEL_1,
    searchOnline: Boolean = false
): Triple<ArrayList<Song>, ArrayList<String>, String> {
    val songs = ArrayList<Song>()
    val rejectedSongs = ArrayList<String>()

    runCatching {
        context.applicationContext.contentResolver.openInputStream(uri)?.use { stream ->
            val lines = stream.readLines()
            if (lines.firstOrNull()?.startsWith("#EXTM3U") != true) return@use

            lines.forEachIndexed { index, rawLine ->
                if (!rawLine.startsWith("#EXTINF:")) return@forEachIndexed

                val metadata = rawLine.removePrefix("#EXTINF:").substringAfter(',')
                val artistText = metadata.substringBefore(" - ", missingDelimiterValue = "")
                val song = findM3uSong(
                    database = database,
                    source = lines.getOrNull(index + 1),
                    title = metadata.substringAfter(" - "),
                    artists = if (artistText.isBlank()) emptyList() else artistText.split(';'),
                    matchStrength = matchStrength,
                    searchOnline = searchOnline,
                )
                if (song == null) {
                    rejectedSongs.add(rawLine)
                } else {
                    songs.add(song)
                }
            }
        }
    }.onFailure {
        reportException(it)
        Toast.makeText(context, R.string.m3u_import_playlist_failed, Toast.LENGTH_SHORT).show()
    }

    if (songs.isEmpty()) {
        CoroutineScope(Dispatchers.Main).launch {
            snackbarHostState.showSnackbar(
                message = context.getString(R.string.m3u_import_failed),
                withDismissAction = true,
                duration = SnackbarDuration.Long
            )
        }
    }
    return Triple(songs, rejectedSongs, uri.path?.substringAfterLast('/')?.substringBeforeLast('.') ?: "")
}

private suspend fun findM3uSong(
    database: MusicDatabase,
    source: String?,
    title: String,
    artists: List<String>,
    matchStrength: ScannerM3uMatchCriteria,
    searchOnline: Boolean,
): Song? {
    val sourceUrl = source?.let { Uri.parse(it.trim()) }?.takeIf {
        it.scheme.equals("http", ignoreCase = true) || it.scheme.equals("https", ignoreCase = true)
    }
    val videoId = sourceUrl?.let(::youtubeVideoId)

    // Exported local entries use "<id>, <path>".
    val localParts = source?.takeIf { sourceUrl == null }?.split(',', limit = 2)
    val id = videoId ?: localParts?.first()
    val idMatch = id?.let { database.song(it).first() }
    if (videoId != null && idMatch != null) {
        return idMatch
    }

    val matches = mutableListOf<Song>()
    idMatch?.let(matches::add)
    matches.addAll(database.searchSongsInDb(title).first())
    if (videoId != null) {
        matches.firstOrNull { it.id == videoId }?.let { return it }
    }

    val sourceAllowsOnlineLookup = source?.contains(',') == false
    if (searchOnline && sourceAllowsOnlineLookup) {
        val onlineMatches = LocalMediaScanner.youtubeSongLookup("$title ${artists.joinToString(" ")}", videoId)
        for (result in onlineMatches) {
            val song = Song(
                song = result.toSongEntity(),
                artists = result.artists.map {
                    ArtistEntity(id = it.id ?: ArtistEntity.generateArtistId(), name = it.name)
                },
            )
            if (song.id == videoId) {
                return song
            }
            matches.add(song)
        }
    }

    if (matchStrength == ScannerM3uMatchCriteria.LEVEL_0 && searchOnline) {
        return matches.firstOrNull()
    }
    val entry = Song(
        song = SongEntity(
            id = "",
            title = title,
            isLocal = true,
            localPath = localParts?.getOrNull(1)?.removePrefix(" ") ?: localParts?.first(),
        ),
        artists = artists.map { ArtistEntity("", it) },
    )
    return matches.firstOrNull { compareM3uSong(entry, it, matchStrength = matchStrength) }
}

private fun youtubeVideoId(uri: Uri): String? {
    val path = uri.pathSegments.singleOrNull()
    val videoId = when (uri.host?.lowercase()) {
        "youtu.be" -> path
        "youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com" ->
            if (path == "watch") uri.getQueryParameter("v") else null
        else -> null
    }
    return videoId?.takeIf { it.isNotBlank() }
}

/**
 * Read a file to a string
 */
fun InputStream.readLines(): List<String> {
    return this.bufferedReader().useLines { it.toList() }
}
