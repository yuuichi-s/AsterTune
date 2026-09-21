package com.zionhuang.innertube.pages

import com.zionhuang.innertube.models.BrowseEndpoint.BrowseEndpointContextSupportedConfigs.BrowseEndpointContextMusicConfig.Companion.MUSIC_PAGE_TYPE_ARTIST
import com.zionhuang.innertube.models.BrowseEndpoint.BrowseEndpointContextSupportedConfigs.BrowseEndpointContextMusicConfig.Companion.MUSIC_PAGE_TYPE_USER_CHANNEL
import com.zionhuang.innertube.models.MusicResponsiveListItemRenderer.FlexColumn
import com.zionhuang.innertube.models.Run
import com.zionhuang.innertube.models.oddElements
import com.zionhuang.innertube.models.splitBySeparator

object PageHelper {
    fun extractRuns(columns: List<FlexColumn>, typeLike: String): List<Run> {
        val filteredRuns = mutableListOf<Run>()
        for (column in columns) {
            val runs = column.musicResponsiveListItemFlexColumnRenderer.text?.runs
                ?: continue

            for (run in runs) {
                val typeStr = run.navigationEndpoint?.watchEndpoint?.watchEndpointMusicSupportedConfigs?.watchEndpointMusicConfig?.musicVideoType
                    ?: run.navigationEndpoint?.browseEndpoint?.browseEndpointContextSupportedConfigs?.browseEndpointContextMusicConfig?.pageType
                    ?: continue

                if (typeLike in typeStr) {
                    filteredRuns.add(run)
                }
            }
        }
        return filteredRuns
    }

    /**
     * Extracts artist names from a subtitle by its artist or channel link, retaining unlinked
     * names from the same segment.
     */
    fun extractArtistRuns(runs: List<Run>): List<Run> =
        runs.splitBySeparator().firstOrNull { segment ->
            segment.any { run ->
                val pageType = run.navigationEndpoint?.browseEndpoint
                    ?.browseEndpointContextSupportedConfigs?.browseEndpointContextMusicConfig?.pageType
                pageType == MUSIC_PAGE_TYPE_ARTIST || pageType == MUSIC_PAGE_TYPE_USER_CHANNEL
            }
        }?.oddElements().orEmpty()
}
