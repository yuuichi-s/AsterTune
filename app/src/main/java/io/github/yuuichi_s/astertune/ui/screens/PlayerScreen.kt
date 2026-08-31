package io.github.yuuichi_s.astertune.ui.screens

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.util.Log
import io.github.yuuichi_s.astertune.constants.UI_DEBUG
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.github.yuuichi_s.astertune.LocalPlayerConnection
import io.github.yuuichi_s.astertune.constants.DEFAULT_PLAYER_BACKGROUND
import io.github.yuuichi_s.astertune.constants.DarkMode
import io.github.yuuichi_s.astertune.constants.DarkModeKey
import io.github.yuuichi_s.astertune.constants.MiniPlayerHeight
import io.github.yuuichi_s.astertune.constants.PlayerBackgroundStyleKey
import io.github.yuuichi_s.astertune.constants.ShowLyricsKey
import io.github.yuuichi_s.astertune.extensions.supportsWideScreen
import io.github.yuuichi_s.astertune.extensions.tabMode
import io.github.yuuichi_s.astertune.ui.component.expandedAnchor
import io.github.yuuichi_s.astertune.ui.component.rememberBottomSheetState
import io.github.yuuichi_s.astertune.ui.player.LandscapePlayer
import io.github.yuuichi_s.astertune.ui.player.PlayerBackground
import io.github.yuuichi_s.astertune.ui.player.PortraitPlayer
import io.github.yuuichi_s.astertune.utils.rememberEnumPreference
import io.github.yuuichi_s.astertune.utils.rememberPreference

@SuppressLint("UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp,
) {
    val TAG = "PlayerScreen"

    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val queueBoard by playerConnection.queueBoard.collectAsState()

    val playerBackground by rememberEnumPreference(
        key = PlayerBackgroundStyleKey,
        defaultValue = DEFAULT_PLAYER_BACKGROUND
    )

    val darkTheme by rememberEnumPreference(DarkModeKey, defaultValue = DarkMode.AUTO)
    val isSystemInDarkTheme = isSystemInDarkTheme()
    val useDarkTheme = remember(darkTheme, isSystemInDarkTheme) {
        if (darkTheme == DarkMode.AUTO) isSystemInDarkTheme else darkTheme == DarkMode.ON
    }

    val showLyrics by rememberPreference(ShowLyricsKey, defaultValue = false)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = bottomPadding)
//            .background(MaterialTheme.colorScheme.surface)
    ) {
        PlayerBackground(
            playerConnection = playerConnection,
            playerBackground = playerBackground,
            showLyrics = showLyrics,
            useDarkTheme = useDarkTheme,
        )
        if (UI_DEBUG) Log.v(TAG, "PLR-3.0")

        val state = rememberBottomSheetState(
            dismissedBound = 0.dp,
            expandedBound = maxHeight,
            collapsedBound = MiniPlayerHeight,
            initialAnchor = expandedAnchor,
        )

        val tabMode = context.tabMode()
        if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE && !tabMode && context.supportsWideScreen()) {
            LandscapePlayer(state, navController, queueBoard)
        } else {
            PortraitPlayer(state, navController, queueBoard)
        }
    }
}
