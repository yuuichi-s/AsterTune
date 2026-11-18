package io.github.yuuichi_s.astertune.ui.component

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.yuuichi_s.astertune.LocalPlayerAwareWindowInsets
import io.github.yuuichi_s.astertune.constants.ShowScrollbarKey
import io.github.yuuichi_s.astertune.utils.rememberPreference
import my.nanihadesuka.compose.InternalLazyColumnScrollbar
import my.nanihadesuka.compose.InternalLazyVerticalGridScrollbar
import my.nanihadesuka.compose.ScrollbarSettings

// Thumb doesn't reach bottom / thumb size randomly changes
// https://github.com/nanihadesuka/LazyColumnScrollbar/issues/40
val DefaultScrollbar: ScrollbarSettings
    @Composable
    get() =
        ScrollbarSettings.Default.copy(
            thumbThickness = 8.dp,
            thumbUnselectedColor = MaterialTheme.colorScheme.primary,
            thumbSelectedColor = MaterialTheme.colorScheme.secondary, // TODO: re-eval colour
            hideDelayMillis = 2000,
        )

/**
 * Draws nothing when the scrollbar is turned off in settings.
 */
@Composable
fun LazyColumnScrollbar(
    state: LazyListState,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier.windowInsetsPadding(
        LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top + WindowInsetsSides.Bottom)
    ),
    settings: ScrollbarSettings = DefaultScrollbar,
) {
    val showScrollbar by rememberPreference(ShowScrollbarKey, defaultValue = true)
    if (!showScrollbar) return

    InternalLazyColumnScrollbar(
        state = state,
        settings = settings,
        modifier = modifier
    )
}

/**
 * Draws nothing when the scrollbar is turned off in settings, as [LazyColumnScrollbar] does.
 */
@Composable
fun LazyVerticalGridScrollbar(
    state: LazyGridState,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier.windowInsetsPadding(
        LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top + WindowInsetsSides.Bottom)
    ),
    settings: ScrollbarSettings = DefaultScrollbar,
) {
    val showScrollbar by rememberPreference(ShowScrollbarKey, defaultValue = true)
    if (!showScrollbar) return

    InternalLazyVerticalGridScrollbar(
        state = state,
        settings = settings,
        modifier = modifier
    )
}
