package dev.cttv.data

import java.io.File
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ChannelRepositoryTest {
    @get:Rule val folder = TemporaryFolder()
    private val validPlaylist = "#EXTM3U\n#EXTINF:-1 tvg-id=\"news\" group-title=\"General\",News\nhttps://example.com/live.m3u8"

    @Test
    fun `refresh persists a valid playlist that works without a network connection`() = runTest {
        val file = File(folder.root, "channels-vn.m3u")
        val channels = ChannelRepository(file) { validPlaylist }.refresh()
        assertEquals(listOf("Vietnam"), channels.single().countries)
        val offline = ChannelRepository(file) { throw IOException("offline") }
        assertEquals(channels, offline.cached())
        assertFalse(File(file.path + ".tmp").exists())
    }

    @Test
    fun `raw Vietnam playlist preserves all channels and alternative streams in cache`() = runTest {
        val playlist = """
            #EXTM3U
            #EXTINF:-1 tvg-id="VTV1.vn@HD",VTV1 HD (1080p)
            https://example.com/vtv1/index.m3u8
            #EXTINF:-1 tvg-id="HTV9.vn@HD",HTV9 HD (1080p)
            https://example.com/htv9/index.m3u8
            #EXTINF:-1 tvg-id="VTV1.vn@HD",VTV1 HD (720p)
            https://backup.example.com/vtv1/index.m3u8
            #EXTINF:-1 tvg-id="VTV1.vn@HD",VTV1 HD (1080p) [Geo-blocked]
            https://example.com/vtv1/manifest.mpd
        """.trimIndent()
        val file = File(folder.root, "channels-vn.m3u")
        val channels = ChannelRepository(file) { playlist }.refresh()

        assertEquals(listOf("HTV9.vn@HD", "VTV1.vn@HD"), channels.map { it.id })
        assertTrue(channels.all { it.countries == listOf("Vietnam") })
        val vtv1 = channels.single { it.id == "VTV1.vn@HD" }
        assertEquals(
            listOf(
                Stream("https://example.com/vtv1/index.m3u8"),
                Stream("https://backup.example.com/vtv1/index.m3u8"),
                Stream("https://example.com/vtv1/manifest.mpd"),
            ),
            vtv1.streams,
        )
        assertEquals(listOf(Stream("https://example.com/htv9/index.m3u8")), channels.first().streams)
        assertEquals(channels, ChannelRepository(file) { throw IOException("offline") }.cached())
    }

    @Test
    fun `invalid server response cannot overwrite a previously working cache`() = runTest {
        val file = folder.newFile("channels-vn.m3u").apply { writeText(validPlaylist) }
        val repository = ChannelRepository(file) { "<html>Error</html>" }
        try {
            repository.refresh()
            throw AssertionError("Expected invalid playlist to fail")
        } catch (_: IllegalArgumentException) {
            assertEquals(validPlaylist, file.readText())
            assertEquals("news", repository.cached().single().id)
        }
    }

    @Test
    fun `missing or corrupt cache starts empty`() = runTest {
        val file = File(folder.root, "channels-vn.m3u")
        val repository = ChannelRepository(file) { validPlaylist }
        assertTrue(repository.cached().isEmpty())
        file.writeText("interrupted content")
        assertTrue(repository.cached().isEmpty())
    }

    @Test
    fun `Vietnam cache does not read or overwrite an old global cache`() = runTest {
        val legacy = folder.newFile("channels.m3u").apply {
            writeText("#EXTM3U\n#EXTINF:-1 tvg-id=\"us\" group-title=\"United States\",US News\nhttps://example.com/us.m3u8")
        }
        val previousContents = legacy.readText()
        val file = File(folder.root, "channels-vn.m3u")
        val repository = ChannelRepository(file) { validPlaylist }
        assertTrue(repository.cached().isEmpty())
        repository.refresh()
        assertEquals(listOf("news"), repository.cached().map { it.id })
        assertEquals(listOf("Vietnam"), repository.cached().single().countries)
        assertEquals(previousContents, legacy.readText())
    }
}
