/*
 * Copyright (C) 2026 AsterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package io.github.yuuichi_s.astertune.ui.screens.settings

import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import io.github.yuuichi_s.astertune.LocalPlayerAwareWindowInsets
import io.github.yuuichi_s.astertune.R
import io.github.yuuichi_s.astertune.constants.HomeAccountPlaylistsKey
import io.github.yuuichi_s.astertune.constants.HomeChipsKey
import io.github.yuuichi_s.astertune.constants.HomeForgottenFavKey
import io.github.yuuichi_s.astertune.constants.HomeKeepListeningKey
import io.github.yuuichi_s.astertune.constants.HomeMoodKey
import io.github.yuuichi_s.astertune.constants.HomeQuickPicksKey
import io.github.yuuichi_s.astertune.constants.HomeSimilarToKey
import io.github.yuuichi_s.astertune.constants.HomeYtmSectionsKey
import io.github.yuuichi_s.astertune.constants.TopBarInsets
import io.github.yuuichi_s.astertune.ui.component.ColumnWithContentPadding
import io.github.yuuichi_s.astertune.ui.component.SwitchPreference
import io.github.yuuichi_s.astertune.ui.component.button.IconButton
import io.github.yuuichi_s.astertune.ui.utils.backToMain
import io.github.yuuichi_s.astertune.utils.dataStore
import io.github.yuuichi_s.astertune.utils.rememberPreference
import kotlinx.coroutines.launch

private class HomeSectionEntry(
    val key: Preferences.Key<Boolean>,
    @StringRes val title: Int,
    @StringRes val description: Int? = null,
)

private val homeSectionEntries = listOf(
    HomeSectionEntry(HomeQuickPicksKey, R.string.quick_picks),
    HomeSectionEntry(HomeForgottenFavKey, R.string.forgotten_favorites),
    HomeSectionEntry(HomeKeepListeningKey, R.string.keep_listening),
    HomeSectionEntry(HomeAccountPlaylistsKey, R.string.your_youtube_playlists),
    HomeSectionEntry(HomeSimilarToKey, R.string.similar_to),
    HomeSectionEntry(
        HomeChipsKey,
        R.string.home_sections_chips,
        R.string.home_sections_chips_description
    ),
    HomeSectionEntry(
        HomeYtmSectionsKey,
        R.string.home_sections_ytm,
        R.string.home_sections_ytm_description
    ),
    HomeSectionEntry(
        HomeMoodKey,
        R.string.home_sections_mood,
        R.string.home_sections_mood_description
    ),
)

/**
 * Saves [visible] for [key], refusing to turn off the last visible Home section.
 * The check runs inside the same edit so that quick consecutive taps cannot turn off every section.
 *
 * @return `false` if the change was refused
 */
private suspend fun setHomeSectionVisible(
    context: Context,
    key: Preferences.Key<Boolean>,
    visible: Boolean,
): Boolean {
    var accepted = true
    context.dataStore.edit { settings ->
        accepted = visible || homeSectionEntries.any { entry ->
            entry.key != key && (settings[entry.key] ?: true)
        }
        if (accepted) {
            settings[key] = visible
        }
    }
    return accepted
}

/** Displays Home section visibility settings, keeping at least one option enabled. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeSectionsSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val keepOneMessage = stringResource(R.string.home_sections_keep_one)

    ColumnWithContentPadding(
        modifier = Modifier.fillMaxHeight(),
        columnModifier = Modifier
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            homeSectionEntries.forEach { entry ->
                // Follows the saved value only, so a refused change never shows as off.
                val checked by rememberPreference(entry.key, defaultValue = true)
                SwitchPreference(
                    title = { Text(stringResource(entry.title)) },
                    description = entry.description?.let { stringResource(it) },
                    checked = checked,
                    onCheckedChange = { visible ->
                        coroutineScope.launch {
                            if (!setHomeSectionVisible(context, entry.key, visible)) {
                                Toast.makeText(context, keepOneMessage, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }

    TopAppBar(
        title = { Text(stringResource(R.string.home_sections_title)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = null
                )
            }
        },
        windowInsets = TopBarInsets,
        scrollBehavior = scrollBehavior
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
private fun HomeSectionsSettingsPreview() {
    CompositionLocalProvider(
        LocalPlayerAwareWindowInsets provides WindowInsets(0, 0, 0, 0),
    ) {
        HomeSectionsSettings(
            navController = rememberNavController(),
            scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(),
        )
    }
}
