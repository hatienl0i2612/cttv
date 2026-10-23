package dev.cttv.data

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ChannelRepository(
    private val cacheFile: File,
    private val download: () -> String = ::downloadPlaylist,
) {
    suspend fun cached(): List<Channel> = withContext(Dispatchers.IO) {
        try {
            parsePlaylist(cacheFile.readText())
        } catch (_: IOException) {
            emptyList()
        } catch (_: IllegalArgumentException) {
            emptyList()
        }
    }

    suspend fun refresh(): List<Channel> = withContext(Dispatchers.IO) {
        val playlist = download()
        val channels = parsePlaylist(playlist)
        val pending = File(cacheFile.path + ".tmp")
        try {
            cacheFile.parentFile?.mkdirs()
            pending.outputStream().use { output ->
                output.write(playlist.toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            if (!pending.renameTo(cacheFile)) throw IOException("Không lưu được danh sách kênh.")
        } finally {
            pending.delete()
        }
        channels
    }

    private fun parsePlaylist(playlist: String): List<Channel> =
        M3uParser.parse(playlist).map { it.copy(countries = listOf("Vietnam")) }

    companion object {
        const val PLAYLIST_URL = "https://iptv-org.github.io/iptv/countries/vn.m3u"
        private const val MAX_PLAYLIST_BYTES = 16 * 1024 * 1024

        private fun downloadPlaylist(): String {
            val connection = URL(PLAYLIST_URL).openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("User-Agent", "CTTV/0.1.0")
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    throw IOException("Máy chủ danh sách kênh đang không phản hồi.")
                }
                val bytes = connection.inputStream.use { it.readBytesLimited(MAX_PLAYLIST_BYTES) }
                return bytes.toString(Charsets.UTF_8)
            } finally {
                connection.disconnect()
            }
        }

        private fun java.io.InputStream.readBytesLimited(limit: Int): ByteArray {
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val read = read(buffer)
                if (read < 0) break
                if (output.size() + read > limit) throw IOException("Danh sách kênh vượt giới hạn dung lượng.")
                output.write(buffer, 0, read)
            }
            return output.toByteArray()
        }
    }
}
