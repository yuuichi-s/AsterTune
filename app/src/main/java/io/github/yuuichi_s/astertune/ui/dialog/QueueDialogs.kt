package io.github.yuuichi_s.astertune.ui.dialog

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import io.github.yuuichi_s.astertune.LocalPlayerConnection
import io.github.yuuichi_s.astertune.R
import io.github.yuuichi_s.astertune.constants.ListThumbnailSize
import io.github.yuuichi_s.astertune.models.MultiQueueObject
import io.github.yuuichi_s.astertune.ui.component.items.ListItem
import io.github.yuuichi_s.astertune.ui.component.items.QueueListItem


@Composable
fun AddToQueueDialog(
    initialTextFieldValue: String? = null,
    onAdd: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val queueBoard by playerConnection.queueBoard.collectAsState()
    var queues by remember {
        mutableStateOf(emptyList<MultiQueueObject>())
    }
    var showCreateQueueDialog by rememberSaveable {
        mutableStateOf(false)
    }


    LaunchedEffect(Unit) {
        queues = queueBoard.getAllQueues().reversed()
    }

    ListDialog(
        onDismiss = onDismiss
    ) {
        item {
            ListItem(
                title = stringResource(R.string.create_queue),
                thumbnailContent = {
                    Image(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground),
                        modifier = Modifier.size(ListThumbnailSize)
                    )
                },
                modifier = Modifier.clickable {
                    showCreateQueueDialog = true
                }
            )
        }

        var index = queues.size
        // add queue
        items(queues) { queue ->
            QueueListItem(
                queue = queue,
                number = index--,
                modifier = Modifier.clickable {
                    onAdd(queue.title)
                    onDismiss()
                }
            )
        }
    }

    if (showCreateQueueDialog) {
        TextFieldDialog(
            icon = { Icon(imageVector = Icons.Rounded.Add, contentDescription = null) },
            title = { Text(text = stringResource(R.string.create_queue)) },
            initialTextFieldValue = TextFieldValue(initialTextFieldValue ?: ""),
            onDismiss = { showCreateQueueDialog = false },
            onDone = { queuetName ->
                onAdd(queuetName)
                onDismiss()
            },
        )
    }
}

@Composable
fun EditQueueDialog(
    queue: MultiQueueObject,
    onDismiss: () -> Unit,
) {
    val playerConnection = LocalPlayerConnection.current?: return
    val queueBoard by playerConnection.queueBoard.collectAsState()
    TextFieldDialog(
        icon = { Icon(imageVector = Icons.Rounded.Edit, contentDescription = null) },
        title = { Text(text = stringResource(R.string.edit_playlist)) },
        onDismiss = onDismiss,
        initialTextFieldValue = TextFieldValue(
            queue.title,
            TextRange(queue.title.length)
        ),
        onDone = { name ->
            onDismiss()
            queueBoard.renameQueue(queue, name)
        }
    )
}