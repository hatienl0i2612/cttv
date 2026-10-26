package dev.cttv.football.sources

import dev.cttv.data.Stream
import dev.cttv.football.FootballCommentator
import dev.cttv.football.FootballMatch
import dev.cttv.football.FootballSource
import dev.cttv.football.MatchStatus
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class GavangSource(private val get: (String) -> String = ::download) : FootballSource {
    override val name = "Gavang"

    override suspend fun matches(): List<FootballMatch> = withContext(Dispatchers.IO) {
        val data = api("/api/v1/matches")
        data.keys().asSequence().mapNotNull { slug ->
            val match = data.optJSONObject(slug) ?: return@mapNotNull null
            val home = match.text("homeTeamName").ifEmpty { match.optJSONObject("homeTeam")?.text("name").orEmpty() }
            val away = match.text("awayTeamName").ifEmpty { match.optJSONObject("awayTeam")?.text("name").orEmpty() }
            if (slug.isBlank() || home.isBlank() || away.isBlank()) return@mapNotNull null
            val status = when (match.text("matchStatus").lowercase()) {
                "2", "3", "4", "5", "6", "live", "halftime", "second_half", "extra_time", "penalty" -> MatchStatus.Live
                "7", "finished" -> MatchStatus.Finished
                else -> MatchStatus.Upcoming
            }
            FootballMatch(
                id = slug, homeTeam = home, awayTeam = away,
                league = match.text("competitionName").ifEmpty { match.optJSONObject("competition")?.text("name").orEmpty() },
                kickoff = match.optLong("matchTime", 0), status = status,
                score = if (status == MatchStatus.Upcoming) null else "${match.score("home")} : ${match.score("away")}",
                homeLogo = image(match.text("homeTeamLogo").ifEmpty { match.optJSONObject("homeTeam")?.text("logo").orEmpty() }),
                awayLogo = image(match.text("awayTeamLogo").ifEmpty { match.optJSONObject("awayTeam")?.text("logo").orEmpty() }),
                commentators = parseCommentators(match, slug),
            )
        }.toList().sortedWith(compareBy<FootballMatch> { it.status.ordinal }.thenBy { if (it.kickoff > 0) it.kickoff else Long.MAX_VALUE })
    }

    override suspend fun commentators(match: FootballMatch): List<FootballCommentator> = withContext(Dispatchers.IO) {
        val detail = api("/api/v1/matches/detail/${encode(match.id)}")
        // The website also merges custom BLVs from the match list, absent from the detail response.
        (parseCommentators(detail, match.id) + match.commentators).distinctBy { it.id }
    }

    private fun api(path: String): JSONObject {
        val base = try {
            apiBase(JSONObject(get(CONFIG_URL)))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            apiBase(JSONObject(get("$SITE/config.json")))
        }
        val response = JSONObject(get("$base$path?webType=gavang"))
        if (response.optBoolean("success", true).not() || response.text("code").let { it.isNotEmpty() && it != "0000" }) {
            throw IOException("Gavang đang không trả được dữ liệu.")
        }
        return response.optJSONObject("data") ?: throw IOException("Định dạng dữ liệu Gavang đã thay đổi.")
    }

    private fun apiBase(config: JSONObject): String {
        val base = config.text("api_url", "apiUrl").trimEnd('/')
        require(httpUrl(base) && URI(base).scheme.equals("https", true)) { "Địa chỉ API Gavang không hợp lệ." }
        return base
    }

    private fun parseCommentators(match: JSONObject, slug: String): List<FootballCommentator> {
        val entries = match.optJSONArray("anchorAppointmentVoList")?.takeIf { it.length() > 0 } ?: match.optJSONArray("commentators")
        return entries.objects().mapNotNull { entry ->
            val id = entry.text("house_id", "id")
            if (id.isBlank()) return@mapNotNull null
            val raw = entry.optJSONObject("raw_data")
            val candidates = streamUrls(entry) + (raw?.let(::streamUrls) ?: emptyList())
            val headers = mapOf("Referer" to "$SITE/truc-tiep/${encode(slug)}", "Origin" to SITE, "User-Agent" to USER_AGENT)
            FootballCommentator(
                id = id, name = entry.text("nickName", "nick_name", "name").ifEmpty { "BLV" },
                avatar = image(entry.text("userImage", "user_image", "avatar")),
                streams = candidates.distinct().filter { httpUrl(it) && URI(it).path.orEmpty().endsWith(".m3u8", true) }
                    .map { Stream(it, headers) },
            )
        }.distinctBy { it.id }
    }

    private fun streamUrls(entry: JSONObject): List<String> {
        val urls = mutableListOf<String>()
        for (key in listOf("streamUrls", "stream_urls", "servers")) {
            val array = entry.optJSONArray(key) ?: continue
            for (i in 0 until array.length()) {
                when (val value = array.opt(i)) {
                    is String -> urls += value.trim()
                    is JSONArray -> for (j in 0 until value.length()) {
                        if (value.opt(j) is String) urls += value.getString(j).trim()
                    }
                }
            }
        }
        for (key in listOf("playStreamAddress", "playStreamAddress2", "play_stream_address", "play_stream_address2", "video_url")) {
            entry.text(key).takeIf { it.isNotEmpty() }?.let(urls::add)
        }
        return urls
    }

    private fun JSONObject.score(team: String): Int =
        (optJSONArray("${team}Scores") ?: optJSONArray("${team}Score"))?.optInt(0) ?: optInt("${team}Score", 0)

    private fun JSONObject.text(vararg keys: String): String = keys.firstNotNullOfOrNull { key ->
        if (isNull(key)) null else optString(key).trim().takeIf { it.isNotEmpty() }
    }.orEmpty()

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

    private fun image(url: String): String? = url.takeIf(::httpUrl)

    companion object {
        private const val SITE = "https://gavang33.uk"
        private const val CONFIG_URL = "https://gvabc.cc/websocket"
        private const val USER_AGENT = "Mozilla/5.0"

        private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

        private fun httpUrl(value: String): Boolean = try {
            val uri = URI(value)
            uri.scheme?.lowercase() in listOf("https", "http") && !uri.host.isNullOrBlank() && uri.userInfo == null
        } catch (_: IllegalArgumentException) { false } catch (_: java.net.URISyntaxException) { false }

        private fun download(url: String): String {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 12_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Referer", "$SITE/")
            connection.setRequestProperty("Origin", SITE)
            connection.setRequestProperty("Accept", "application/json")
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("Gavang không phản hồi (${connection.responseCode}).")
                return connection.inputStream.use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (output.size() + count > 8 * 1024 * 1024) throw IOException("Dữ liệu Gavang vượt giới hạn dung lượng.")
                        output.write(buffer, 0, count)
                    }
                    output.toString("UTF-8")
                }
            } finally { connection.disconnect() }
        }
    }
}
