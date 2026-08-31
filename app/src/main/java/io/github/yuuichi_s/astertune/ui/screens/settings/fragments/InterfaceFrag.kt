package io.github.yuuichi_s.astertune.ui.screens.settings.fragments

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Reorder
import androidx.compose.material.icons.rounded.Tab
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.yuuichi_s.astertune.R
import io.github.yuuichi_s.astertune.constants.ContentCountryKey
import io.github.yuuichi_s.astertune.constants.ContentLanguageKey
import io.github.yuuichi_s.astertune.constants.CountryCodeToName
import io.github.yuuichi_s.astertune.constants.DEFAULT_ENABLED_FILTERS
import io.github.yuuichi_s.astertune.constants.DEFAULT_ENABLED_TABS
import io.github.yuuichi_s.astertune.constants.DefaultOpenTabKey
import io.github.yuuichi_s.astertune.constants.EnabledFiltersKey
import io.github.yuuichi_s.astertune.constants.EnabledTabsKey
import io.github.yuuichi_s.astertune.constants.LanguageCodeToName
import io.github.yuuichi_s.astertune.constants.ListItemHeight
import io.github.yuuichi_s.astertune.constants.SYSTEM_DEFAULT
import io.github.yuuichi_s.astertune.constants.SwipeToQueueKey
import io.github.yuuichi_s.astertune.constants.ThumbnailCornerRadius
import io.github.yuuichi_s.astertune.extensions.move
import io.github.yuuichi_s.astertune.ui.component.ListPreference
import io.github.yuuichi_s.astertune.ui.component.PreferenceEntry
import io.github.yuuichi_s.astertune.ui.component.SwitchPreference
import io.github.yuuichi_s.astertune.ui.dialog.ActionPromptDialog
import io.github.yuuichi_s.astertune.ui.dialog.InfoLabel
import io.github.yuuichi_s.astertune.ui.screens.Screens
import io.github.yuuichi_s.astertune.ui.screens.Screens.LibraryFilter
import io.github.yuuichi_s.astertune.utils.rememberPreference
import com.zionhuang.innertube.YouTube
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.util.Locale
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun ColumnScope.TabArrangementFrag() {
    val (enabledTabs, onEnabledTabsChange) = rememberPreference(EnabledTabsKey, defaultValue = DEFAULT_ENABLED_TABS)
    val (enabledFilters, onEnabledFiltersChange) = rememberPreference(
        EnabledFiltersKey,
        defaultValue = DEFAULT_ENABLED_FILTERS
    )
    val (defaultOpenTab, onDefaultOpenTabChange) = rememberPreference(DefaultOpenTabKey, defaultValue = "home")

    var dragInfo by remember {
        mutableStateOf<Pair<Int, Int>?>(null)
    }

    /**
     * ---------------------------
     * Configurable tabs
     * ---------------------------
     */

    var showTabArrangement by rememberSaveable {
        mutableStateOf(false)
    }
    val mutableTabs = remember { mutableStateListOf<Pair<Screens, Boolean>>() }
    val lazyTabsListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(
        lazyListState = lazyTabsListState,
        scrollThresholdPadding = WindowInsets.systemBars.add(
            WindowInsets(
                top = ListItemHeight,
                bottom = ListItemHeight
            )
        ).asPaddingValues()
    ) { from, to ->
        val currentDragInfo = dragInfo
        dragInfo = if (currentDragInfo == null) {
            from.index to to.index
        } else {
            currentDragInfo.first to to.index
        }
        mutableTabs.move(from.index, to.index)
    }

    fun updateTabs() {
        mutableTabs.apply {
            clear()

            val enabled = Screens.getScreens(enabledTabs)
            val temp = ArrayList<Pair<Screens, Boolean>>()
            temp.addAll(enabled.map { it to true })
            temp.addAll(
                Screens.getAllScreens().filterNot { it in enabled }.map { it to false }
            )
            addAll(temp.distinctBy { it.first })
        }
    }

    LaunchedEffect(showTabArrangement, enabledTabs) {
        updateTabs()
    }


    /**
     * ---------------------------
     * Configurable filters
     * ---------------------------
     */


    var showFilterArrangement by rememberSaveable {
        mutableStateOf(false)
    }
    val mutableFilters = remember { mutableStateListOf<Pair<LibraryFilter, Boolean>>() }
    val lazyFiltersListState = rememberLazyListState()
    val filtersReorderableState = rememberReorderableLazyListState(
        lazyListState = lazyFiltersListState,
        scrollThresholdPadding = WindowInsets.systemBars.add(
            WindowInsets(
                top = ListItemHeight,
                bottom = ListItemHeight
            )
        ).asPaddingValues()
    ) { from, to ->
        val currentDragInfo = dragInfo
        dragInfo = if (currentDragInfo == null) {
            from.index to to.index
        } else {
            currentDragInfo.first to to.index
        }
        mutableFilters.move(from.index, to.index)
    }

    fun updateFilters() {
        mutableFilters.apply {
            clear()

            val enabled = Screens.getFilters(enabledFilters)
            addAll(enabled.map { it to true })
            addAll(
                LibraryFilter.entries.filterNot { it in enabled }.map { it to false }
                    .filterNot { it.first == LibraryFilter.ALL })
        }
    }

    LaunchedEffect(showFilterArrangement, enabledFilters) {
        updateFilters()
    }


    PreferenceEntry(
        title = { Text(stringResource(R.string.tab_arrangement)) },
        icon = { Icon(Icons.Rounded.Reorder, null) },
        onClick = {
            showTabArrangement = true
        }
    )
    PreferenceEntry(
        title = { Text(stringResource(R.string.filter_arrangement)) },
        icon = { Icon(Icons.Rounded.Reorder, null) },
        onClick = {
            showFilterArrangement = true
        }
    )


    /**
     * ---------------------------
     * Dialogs
     * ---------------------------
     */


    if (showTabArrangement) {
        ActionPromptDialog(
            title = stringResource(R.string.tab_arrangement),
            onDismiss = { showTabArrangement = false },
            onConfirm = {
                var encoded = Screens.encodeScreens(mutableTabs.filter { it.second }.map { it.first })

                // reset defaultOpenTab if it got disabled
                if (Screens.getScreens(encoded).find { it.route == defaultOpenTab } == null)
                    onDefaultOpenTabChange(Screens.Home.route)

                // home is required
                if (!encoded.contains('H')) {
                    encoded += "H"
                }

                onEnabledTabsChange(encoded)
                showTabArrangement = false
            },
            onReset = {
                onEnabledTabsChange(DEFAULT_ENABLED_TABS)
                updateTabs()
            },
            onCancel = {
                showTabArrangement = false
            }
        ) {
            // tabs list
            LazyColumn(
                state = lazyTabsListState,
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .border(
                        2.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        RoundedCornerShape(ThumbnailCornerRadius)
                    )
            ) {
                itemsIndexed(
                    items = mutableTabs,
                    key = { _, item -> item.hashCode() }
                ) { index, tab ->
                    ReorderableItem(
                        state = reorderableState,
                        key = tab.hashCode()
                    ) {
                        fun onChecked() {
                            mutableTabs[mutableTabs.indexOf(tab)] = tab.copy(second = !tab.second)
                        }

                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                                .fillMaxWidth()
                                .clickable { onChecked() }
                        ) {
                            Row(
                                modifier = Modifier
                                    .background(if (tab.second) MaterialTheme.colorScheme.primary else Color.Transparent)
                            ) {
                                Row(
                                    Modifier
                                        .padding(start = 8.dp)
                                        .background(MaterialTheme.colorScheme.surface)
                                ) {
                                    Text(
                                        text = stringResource(tab.first.titleId),
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Rounded.DragHandle,
                                contentDescription = null,
                                modifier = Modifier.draggableHandle()
                            )
                        }
                    }
                }
            }

            InfoLabel(stringResource(R.string.tab_arrangement_home_required))
        }
    }

    if (showFilterArrangement) {
        ActionPromptDialog(
            title = stringResource(R.string.filter_arrangement),
            onDismiss = { showFilterArrangement = false },
            onConfirm = {
                val encoded = Screens.encodeFilters(mutableFilters.filter { it.second }.map { it.first })

                onEnabledFiltersChange(encoded)
                showFilterArrangement = false
            },
            onReset = {
                onEnabledFiltersChange(DEFAULT_ENABLED_FILTERS)
                updateFilters()
            },
            onCancel = {
                showFilterArrangement = false
            }
        ) {
            // tabs list
            LazyColumn(
                state = lazyFiltersListState,
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .border(
                        2.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        RoundedCornerShape(ThumbnailCornerRadius)
                    )
            ) {
                itemsIndexed(
                    items = mutableFilters,
                    key = { _, item -> item.hashCode() }
                ) { index, filter ->
                    ReorderableItem(
                        state = filtersReorderableState,
                        key = filter.hashCode()
                    ) {
                        fun onChecked() {
                            mutableFilters[mutableFilters.indexOf(filter)] = filter.copy(second = !filter.second)
                        }

                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                                .fillMaxWidth()
                                .clickable { onChecked() }
                        ) {
                            Row(
                                modifier = Modifier
                                    .background(if (filter.second) MaterialTheme.colorScheme.primary else Color.Transparent)
                            ) {
                                Row(
                                    Modifier
                                        .padding(start = 8.dp)
                                        .background(MaterialTheme.colorScheme.surface)
                                ) {
                                    Text(
                                        text = when (filter.first) {
                                            LibraryFilter.ALBUMS -> stringResource(R.string.albums)
                                            LibraryFilter.ARTISTS -> stringResource(R.string.artists)
                                            LibraryFilter.PLAYLISTS -> stringResource(R.string.playlists)
                                            LibraryFilter.SONGS -> stringResource(R.string.songs)
                                            LibraryFilter.FOLDERS -> stringResource(R.string.folders)
                                            else -> {
                                                // TODO: Do we even need this?
                                                stringResource(R.string.tab_arrangement_disable_tip)
                                            }
                                        },
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Rounded.DragHandle,
                                contentDescription = null,
                                modifier = Modifier.draggableHandle()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ColumnScope.TabExtrasFrag() {
    val enabledTabs by rememberPreference(EnabledTabsKey, defaultValue = DEFAULT_ENABLED_TABS)
    val (defaultOpenTab, onDefaultOpenTabChange) = rememberPreference(DefaultOpenTabKey, defaultValue = "home")

    ListPreference(
        title = { Text(stringResource(R.string.default_open_tab)) },
        icon = { Icon(Icons.Rounded.Tab, null) },
        selectedValue = Screens.getAllScreens().find { it.route == defaultOpenTab } ?: Screens.Home,
        onValueSelected = { screen ->
            onDefaultOpenTabChange(screen.route)
        },
        values = Screens.getAllScreens().filter { Screens.getScreens(enabledTabs).contains(it) },
        valueText = { stringResource(it.titleId) }
    )
}

@Composable
fun ColumnScope.GestureSettingsFrag() {
    val (swipe2Queue, onSwipe2QueueChange) = rememberPreference(SwipeToQueueKey, defaultValue = true)

    SwitchPreference(
        title = { Text(stringResource(R.string.swipe2Queue)) },
        description = stringResource(R.string.swipe2Queue_description),
        icon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null) },
        checked = swipe2Queue,
        onCheckedChange = onSwipe2QueueChange
    )
}

@Composable
fun ColumnScope.LocalizationFrag() {
    val (contentLanguage, onContentLanguageChange) = rememberPreference(
        key = ContentLanguageKey,
        defaultValue = "system"
    )
    val (contentCountry, onContentCountryChange) = rememberPreference(key = ContentCountryKey, defaultValue = "system")

    ListPreference(
        title = { Text(stringResource(R.string.content_language)) },
        icon = { Icon(Icons.Rounded.Language, null) },
        selectedValue = contentLanguage,
        values = listOf(SYSTEM_DEFAULT) + LanguageCodeToName.keys.toList(),
        valueText = {
            LanguageCodeToName.getOrElse(it) {
                stringResource(R.string.system_default)
            }
        },
        onValueSelected = { newValue ->
            val locale = Locale.getDefault()
            val languageTag = locale.toLanguageTag().replace("-Hant", "")

            YouTube.locale = YouTube.locale.copy(
                hl = newValue.takeIf { it != SYSTEM_DEFAULT }
                    ?: locale.language.takeIf { it in LanguageCodeToName }
                    ?: languageTag.takeIf { it in LanguageCodeToName }
                    ?: "en"
            )

            onContentLanguageChange(newValue)
        }
    )
    ListPreference(
        title = { Text(stringResource(R.string.content_country)) },
        icon = { Icon(Icons.Rounded.LocationOn, null) },
        selectedValue = contentCountry,
        values = listOf(SYSTEM_DEFAULT) + CountryCodeToName.keys.toList(),
        valueText = {
            CountryCodeToName.getOrElse(it) {
                stringResource(R.string.system_default)
            }
        },
        onValueSelected = { newValue ->
            val locale = Locale.getDefault()

            YouTube.locale = YouTube.locale.copy(
                gl = newValue.takeIf { it != SYSTEM_DEFAULT }
                    ?: locale.country.takeIf { it in CountryCodeToName }
                    ?: "US"
            )

            onContentCountryChange(newValue)
        }
    )
}

@Preview(showBackground = true)
@Composable
private fun TabArrangementFragPreview() {
    Column {
        TabArrangementFrag()
    }
}

@Preview(showBackground = true)
@Composable
private fun TabExtrasFragPreview() {
    Column {
        TabExtrasFrag()
    }
}

@Preview(showBackground = true)
@Composable
private fun GestureSettingsFragPreview() {
    Column {
        GestureSettingsFrag()
    }
}

@Preview(showBackground = true)
@Composable
private fun LocalizationFragPreview() {
    Column {
        LocalizationFrag()
    }
}
