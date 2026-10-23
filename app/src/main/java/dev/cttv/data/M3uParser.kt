package dev.cttv.data

import java.net.URI

object M3uParser {
    private val attributes = Regex("""([\w-]+)\s*=\s*"([^"]*)"""")

    fun parse(playlist: String): List<Channel> {
        val lines = playlist.removePrefix("\uFEFF").lineSequence().map(String::trim)
        require(lines.firstOrNull { it.isNotEmpty() }?.startsWith("#EXTM3U") == true) {
            "Nguồn trả về không phải playlist M3U."
        }
        val channels = linkedMapOf<String, Channel>()
        var info: Map<String, String>? = null
        var name = ""
        var headers = linkedMapOf<String, String>()
        for (line in lines) {
            when {
                line.startsWith("#EXTINF:") -> {
                    var quoted = false
                    val comma = line.indexOfFirst { char ->
                        if (char == '"') quoted = !quoted
                        char == ',' && !quoted
                    }
                    info = if (comma >= 0) attributes.findAll(line.substring(0, comma))
                        .associate { it.groupValues[1] to it.groupValues[2] } else null
                    name = if (comma >= 0) line.substring(comma + 1).trim() else ""
                    headers = linkedMapOf()
                }
                line.startsWith("#EXTVLCOPT:") -> {
                    val option = line.substringAfter(':')
                    val key = when (option.substringBefore('=').lowercase()) {
                        "http-user-agent" -> "User-Agent"
                        "http-referrer", "http-referer" -> "Referer"
                        "http-origin" -> "Origin"
                        else -> null
                    }
                    val value = option.substringAfter('=', "")
                    if (key != null && value.isNotBlank() && '\r' !in value && '\n' !in value) {
                        headers[key] = value
                    }
                }
                line.isNotEmpty() && !line.startsWith('#') -> {
                    val metadata = info
                    if (metadata != null && name.isNotBlank() && isNetworkUrl(line)) {
                        val id = metadata["tvg-id"].orEmpty().ifBlank { line }
                        val countries = metadata["group-title"].orEmpty().split(';')
                            .map(String::trim).filter(String::isNotEmpty).ifEmpty { listOf("Khác") }
                        val stream = Stream(line, headers.toMap())
                        val previous = channels[id]
                        channels[id] = if (previous == null) {
                            Channel(id, name, metadata["tvg-logo"]?.takeIf {
                                isNetworkUrl(it) && URI(it).scheme.lowercase() in listOf("http", "https")
                            }, countries, listOf(stream))
                        } else {
                            previous.copy(
                                countries = (previous.countries + countries).distinct(),
                                streams = (previous.streams + stream).distinct(),
                            )
                        }
                    }
                    info = null
                    headers = linkedMapOf()
                }
            }
        }
        require(channels.isNotEmpty()) { "Playlist chưa có kênh hợp lệ." }
        return channels.values.sortedBy { it.name.lowercase() }
    }

    private fun isNetworkUrl(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme?.lowercase() in listOf("https", "http", "rtmp", "srt", "mmsh") &&
            !uri.host.isNullOrBlank() && uri.userInfo == null
    }.getOrDefault(false)
}
