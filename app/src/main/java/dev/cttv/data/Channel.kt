package dev.cttv.data

data class Stream(val url: String, val headers: Map<String, String> = emptyMap()) {
    val isHttp: Boolean get() = url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true)
}

data class Channel(
    val id: String,
    val name: String,
    val logoUrl: String?,
    val countries: List<String>,
    val streams: List<Stream>,
)

fun List<Channel>.matching(query: String): List<Channel> {
    val term = query.trim()
    val channels = filter { channel ->
        term.isEmpty() || channel.name.contains(term, ignoreCase = true)
    }
    return channels.sortedBy { !it.name.startsWith("VTV", ignoreCase = true) }
}
