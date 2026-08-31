package io.github.yuuichi_s.astertune.ui.menu

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.github.yuuichi_s.astertune.LocalPlayerConnection
import io.github.yuuichi_s.astertune.R
import io.github.yuuichi_s.astertune.models.MultiQueueObject
import io.github.yuuichi_s.astertune.ui.component.items.QueueListItem
import io.github.yuuichi_s.astertune.ui.dialog.AddToPlaylistDialog
import io.github.yuuichi_s.astertune.ui.dialog.AddToQueueDialog
import io.github.yuuichi_s.astertune.ui.dialog.EditQueueDialog

@Composable
fun QueueMenu(
    navController: NavController,
    mq: MultiQueueObject?,
    onDismiss: () -> Unit,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val queueBoard by playerConnection.queueBoard.collectAsState()

    if (mq == null) {
        onDismiss()
        return
    }
    val songs = mq.getCurrentQueueShuffled()

    var showChoosePlaylistDialog by rememberSaveable {
        mutableStateOf(false)
    }
    var showChooseQueueDialog by rememberSaveable {
        mutableStateOf(false)
    }
    var showEditDialog by rememberSaveable {
        mutableStateOf(false)
    }

    // queue item
    QueueListItem(queue = mq)

    HorizontalDivider()

    // menu options
    GridMenu(
        contentPadding = PaddingValues(
            start = 8.dp,
            top = 8.dp,
            end = 8.dp,
            bottom = 8.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()
        )
    ) {
        GridMenuItem(
            icon = Icons.AutoMirrored.Rounded.QueueMusic,
            title = R.string.add_to_queue
        ) {
            showChooseQueueDialog = true
        }
        GridMenuItem(
            icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
            title = R.string.add_to_playlist
        ) {
            showChoosePlaylistDialog = true
        }
        GridMenuItem(
            icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
            title = R.string.edit
        ) {
            showEditDialog = true
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
            songIds = songs.map { it.id },
            onDismiss = {
                showChoosePlaylistDialog = false
            }
        )
    }

    if (showChooseQueueDialog) {
        AddToQueueDialog(
            onAdd = { queueName ->
                val q = queueBoard.addQueue(
                    queueName,
                    songs,
                    forceInsert = true,
                    delta = false
                )
                q?.let {
                    queueBoard.setCurrQueue(it)
                }
            },
            onDismiss = {
                showChooseQueueDialog = false
                onDismiss() // here we dismiss since we switch to the queue anyways
            }
        )
    }

    if (showEditDialog) {
        EditQueueDialog(
            queue = mq,
            onDismiss = {
                showEditDialog = false
                onDismiss()
            }
        )
    }
}