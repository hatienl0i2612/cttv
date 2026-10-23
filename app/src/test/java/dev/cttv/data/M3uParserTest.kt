package dev.cttv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uParserTest {
    @Test
    fun `merges duplicate channels and preserves alternative streams and countries`() {
        val playlist = """
            #EXTM3U
            #EXTINF:-1 tvg-id="news" tvg-logo="https://example.com/logo.png" group-title="Vietnam",News, World
            https://example.com/live.m3u8
            #EXTINF:-1 tvg-id="news" group-title="France;Vietnam",News, World
            https://example.com/live.m3u8
            #EXTINF:-1 tvg-id="news" group-title="France",News, World
            https://backup.example.com/live.m3u8
        """.trimIndent()
        val channel = M3uParser.parse(playlist).single()
        assertEquals("News, World", channel.name)
        assertEquals(listOf("Vietnam", "France"), channel.countries)
        assertEquals(2, channel.streams.size)
        assertEquals("https://example.com/logo.png", channel.logoUrl)
    }

    @Test
    fun `keeps quoted commas and stream headers without leaking to the next item`() {
        val playlist = """
            #EXTM3U
            #EXTINF:-1 tvg-id="one" group-title="Bonaire, Sint Eustatius and Saba",One
            #EXTVLCOPT:http-referrer=https://example.com/
            #EXTVLCOPT:http-user-agent=Test Player
            https://example.com/one.m3u8
            #EXTINF:-1 tvg-id="two" group-title="Vietnam",Two
            https://example.com/two.m3u8
        """.trimIndent()
        val channels = M3uParser.parse("\uFEFF$playlist")
        assertEquals(listOf("Bonaire, Sint Eustatius and Saba"), channels.first().countries)
        assertEquals(mapOf("Referer" to "https://example.com/", "User-Agent" to "Test Player"), channels.first().streams.single().headers)
        assertTrue(channels.last().streams.single().headers.isEmpty())
    }

    @Test
    fun `rejects local URLs and incomplete entries while keeping valid HTTP streams`() {
        val playlist = """
            #EXTM3U
            #EXTINF:-1 tvg-id="file",Local file
            file:///etc/passwd
            #EXTINF:-1 tvg-id="script",Script
            javascript:alert(1)
            #EXTINF:-1 tvg-id="invalid",Invalid host
            https:///no-host
            #EXTINF:-1 tvg-id="valid" tvg-logo="file:///tmp/image.png",Valid
            http://example.com/live.ts
            https://example.com/orphan.m3u8
        """.trimIndent()
        val channel = M3uParser.parse(playlist).single()
        assertEquals("valid", channel.id)
        assertEquals(listOf("Khác"), channel.countries)
        assertNull(channel.logoUrl)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects a server error page`() {
        M3uParser.parse("<html>Service unavailable</html>")
    }

    @Test
    fun `keeps uncommon network streams visible without treating them as HTTP`() {
        val playlist = """
            #EXTM3U
            #EXTINF:-1 tvg-id="rtmp",RTMP channel
            rtmp://example.com/live
            #EXTINF:-1 tvg-id="srt",SRT channel
            srt://example.com:9000/live
            #EXTINF:-1 tvg-id="mmsh",MMSH channel
            mmsh://example.com/live
        """.trimIndent()
        val channels = M3uParser.parse(playlist)
        assertEquals(3, channels.size)
        assertTrue(channels.none { it.streams.single().isHttp })
    }

    @Test
    fun `search ignores query case and whitespace`() {
        val channels = listOf(
            Channel("one", "World News", null, listOf("Vietnam"), listOf(Stream("https://example.com/1"))),
            Channel("two", "World Sport", null, listOf("Vietnam"), listOf(Stream("https://example.com/2"))),
        )
        assertEquals(listOf(channels[0]), channels.matching("  NEWS  "))
        assertEquals(channels, channels.matching(""))
        assertTrue(channels.matching("missing").isEmpty())
    }

    @Test
    fun `VTV is first case insensitively and order within each group is preserved`() {
        val channels = listOf("BTV", "MTV Vietnam", "VTV1", "vtv3").map { name ->
            Channel(name, name, null, listOf("Vietnam"), listOf(Stream("https://example.com/live.m3u8")))
        }
        assertEquals(listOf("VTV1", "vtv3", "BTV", "MTV Vietnam"), channels.matching("").map { it.name })
        assertEquals(listOf(channels[3]), channels.matching("  VTV3  "))
    }
}
