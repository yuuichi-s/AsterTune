package com.zionhuang.innertube

import com.zionhuang.innertube.models.Album
import com.zionhuang.innertube.models.Artist
import com.zionhuang.innertube.models.MusicCarouselShelfRenderer
import com.zionhuang.innertube.models.SongItem
import com.zionhuang.innertube.pages.HomePage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests that home shelves are converted from both list items and cards. */
class HomeListShelfTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    private val shelves: JsonObject = javaClass.classLoader!!.getResourceAsStream("home_list_shelves.json")!!
        .bufferedReader().use { it.readText() }
        .let { json.parseToJsonElement(it).jsonObject }

    // numItemsPerColumn is the string "4" in the response
    private fun section(name: String): HomePage.Section =
        json.decodeFromJsonElement(MusicCarouselShelfRenderer.serializer(), shelves[name]!!)
            .let { HomePage.Section.fromMusicCarouselShelfRenderer(it)!! }

    private fun HomePage.Section.song(index: Int) = items[index] as SongItem

    @Test
    fun quickPicksShelfIsListStyleWithPlayEndpoint() {
        val section = section("quickPicks")

        assertEquals("Quick picks", section.title)
        assertTrue(section.isListStyle)
        assertEquals("RDAMVMv1", section.playEndpoint?.playlistId)
        assertNull(section.endpoint)
        assertEquals(listOf("v1", "v2"), section.items.map { it.id })
    }

    @Test
    fun playCountIsNotTakenAsArtist() {
        val song = section("quickPicks").song(0)

        assertEquals("Song One", song.title)
        assertEquals(listOf(Artist("Artist A", "UCa")), song.artists)
        assertEquals(Album("Album X", "MPREx"), song.album)
        assertNull(song.duration)
        assertTrue(song.explicit)
    }

    @Test
    fun multipleArtistsAreKept() {
        val song = section("quickPicks").song(1)

        assertEquals(listOf(Artist("Artist A", "UCa"), Artist("Artist B", "UCb")), song.artists)
        assertFalse(song.explicit)
    }

    @Test
    fun unlinkedUploaderAndLongDurationAreRead() {
        val section = section("longListens")
        val song = section.song(0)

        assertTrue(section.isListStyle)
        assertNull(section.playEndpoint)
        assertEquals(listOf(Artist("louisette", null)), song.artists)
        assertNull(song.album)
        assertEquals(2 * 3600 + 3 * 60 + 4, song.duration)
    }

    @Test
    fun cardShelfKeepsCardsAndBrowseEndpoint() {
        val section = section("cardShelf")

        assertFalse(section.isListStyle)
        assertEquals(listOf("c1", "c2"), section.items.map { it.id })
        assertEquals("FEmusic_new_releases", section.endpoint?.browseId)
        assertNull(section.playEndpoint)
    }

    @Test
    fun mixedShelfKeepsBothKindsInOrder() {
        val section = section("mixedShelf")

        assertFalse(section.isListStyle)
        assertEquals(listOf("c1", "v4", "c2"), section.items.map { it.id })
    }
}
