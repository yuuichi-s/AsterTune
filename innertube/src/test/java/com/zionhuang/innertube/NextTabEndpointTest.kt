package com.zionhuang.innertube

import com.zionhuang.innertube.models.BrowseEndpoint.BrowseEndpointContextSupportedConfigs.BrowseEndpointContextMusicConfig.Companion.MUSIC_PAGE_TYPE_TRACK_LYRICS
import com.zionhuang.innertube.models.BrowseEndpoint.BrowseEndpointContextSupportedConfigs.BrowseEndpointContextMusicConfig.Companion.MUSIC_PAGE_TYPE_TRACK_RELATED
import com.zionhuang.innertube.models.Tabs
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Tests that the lyrics and related tabs of a "next" response are found by page type. */
@OptIn(ExperimentalSerializationApi::class)
class NextTabEndpointTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    // Up next, Lyrics, Comments, Related: the Comments tab has no browse endpoint
    private val tabs = javaClass.classLoader!!.getResourceAsStream("next_tabs_with_comments.json")!!
        .bufferedReader().use { it.readText() }
        .let { json.decodeFromString<List<Tabs.Tab>>(it) }

    @Test
    fun relatedTabAfterCommentsIsFound() {
        assertEquals("MPTRt_related", YouTube.tabEndpoint(tabs, MUSIC_PAGE_TYPE_TRACK_RELATED)?.browseId)
    }

    @Test
    fun lyricsTabIsFound() {
        assertEquals("MPLYt_lyrics", YouTube.tabEndpoint(tabs, MUSIC_PAGE_TYPE_TRACK_LYRICS)?.browseId)
    }

    @Test
    fun tabsAreFoundInAnyOrder() {
        val reversed = tabs.reversed()

        assertEquals("MPTRt_related", YouTube.tabEndpoint(reversed, MUSIC_PAGE_TYPE_TRACK_RELATED)?.browseId)
        assertEquals("MPLYt_lyrics", YouTube.tabEndpoint(reversed, MUSIC_PAGE_TYPE_TRACK_LYRICS)?.browseId)
    }

    @Test
    fun missingTabGivesNull() {
        val withoutRelated = tabs.filter { it.tabRenderer.title != "Related" }

        assertNull(YouTube.tabEndpoint(withoutRelated, MUSIC_PAGE_TYPE_TRACK_RELATED))
    }
}
