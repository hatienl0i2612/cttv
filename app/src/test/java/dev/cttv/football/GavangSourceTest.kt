package dev.cttv.football

import dev.cttv.football.sources.GavangSource
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GavangSourceTest {
    private val config = """{"api_url":"https://api.example.com"}"""
    private val matches = """{"code":"0000","data":{
        "later":{"homeTeam":{"name":"Home"},"awayTeam":{"name":"Away"},"competition":{"name":"Cup"},"matchTime":200,"matchStatus":"scheduled"},
        "live":{"homeTeamName":"Live Home","awayTeamName":"Live Away","matchTime":100,"matchStatus":3,"homeScores":[2,1],"awayScore":[1,0]},
        "earlier":{"homeTeamName":"Early Home","awayTeamName":"Early Away","matchTime":150,"matchStatus":"scheduled"},
        "ended":{"homeTeamName":"End Home","awayTeamName":"End Away","matchTime":50,"matchStatus":"finished"},
        "broken":null
    }}"""

    @Test fun `loads every match and sorts live then upcoming by kickoff then finished`() = runTest {
        val source = GavangSource { url -> if (url.endsWith("/websocket")) config else matches }
        val result = source.matches()
        assertEquals(listOf("live", "earlier", "later", "ended"), result.map { it.id })
        assertEquals("2 : 1", result.first().score)
        assertEquals(MatchStatus.Live, result.first().status)
        assertEquals("Cup", result[2].league)
        assertEquals("Home – Away", result[2].title)
    }

    @Test fun `uses dynamic API configuration and webType without loading match details`() = runTest {
        val requests = mutableListOf<String>()
        val source = GavangSource { url ->
            requests += url
            if (url.endsWith("/websocket")) config else matches
        }
        source.matches()
        assertEquals(listOf("https://gvabc.cc/websocket", "https://api.example.com/api/v1/matches?webType=gavang"), requests)
    }

    @Test fun `falls back to site config when primary configuration is unavailable or invalid`() = runTest {
        for (primary in listOf<String?>(null, """{"api_url":"file:///tmp/local"}""")) {
            val requests = mutableListOf<String>()
            val source = GavangSource { url ->
                requests += url
                when {
                    url.endsWith("/websocket") -> primary ?: throw IOException("offline")
                    url.endsWith("/config.json") -> """{"apiUrl":"https://fallback.example.com/"}"""
                    else -> matches
                }
            }
            assertEquals(4, source.matches().size)
            assertTrue(requests.contains("https://gavang33.uk/config.json"))
            assertEquals("https://fallback.example.com/api/v1/matches?webType=gavang", requests.last())
        }
    }

    @Test fun `keeps all BLVs and only HLS URLs with required playback headers`() = runTest {
        val detail = """{"success":true,"data":{"anchorAppointmentVoList":[
            {"id":"a","nickName":"BLV A","streamUrls":["https://live.example.com/a.flv","https://live.example.com/a.m3u8?token=x"],"servers":[["https://backup.example.com/a.m3u8"]]},
            {"id":"b","nickName":"BLV B","playStreamAddress":"https://live.example.com/b.m3u8"}
        ]}}"""
        val source = GavangSource { url -> if (url.endsWith("/websocket")) config else detail }
        val result = source.commentators(match())
        assertEquals(listOf("BLV A", "BLV B"), result.map { it.name })
        assertEquals(listOf("https://live.example.com/a.m3u8?token=x", "https://backup.example.com/a.m3u8"), result.first().streams.map { it.url })
        assertEquals("https://gavang33.uk", result.first().streams.first().headers["Origin"])
        assertEquals("https://gavang33.uk/truc-tiep/match-one", result.first().streams.first().headers["Referer"])
        assertEquals("https://live.example.com/b.m3u8", result[1].streams.single().url)
    }

    @Test fun `merges custom list BLVs while preferring fresh detail streams for duplicates`() = runTest {
        val list = """{"code":"0000","data":{"match-one":{"homeTeamName":"H","awayTeamName":"A","anchorAppointmentVoList":[
            {"id":"a","nickName":"Old A","streamUrls":["https://old.example.com/a.m3u8"]},
            {"id":"custom","nickName":"Custom","streamUrls":["https://live.example.com/custom.m3u8"]}
        ]}}}"""
        val source = GavangSource { url ->
            when {
                url.endsWith("/websocket") -> config
                "/detail/" in url -> """{"success":true,"data":{"anchorAppointmentVoList":[{"id":"a","nickName":"New A","streamUrls":["https://new.example.com/a.m3u8"]}]}}"""
                else -> list
            }
        }
        val result = source.commentators(source.matches().single())
        assertEquals(listOf("a", "custom"), result.map { it.id })
        assertEquals("https://new.example.com/a.m3u8", result.first().streams.single().url)
    }

    @Test fun `accepts website alternate BLV schema and ignores unsafe URLs and placeholder video`() = runTest {
        val source = GavangSource { url ->
            if (url.endsWith("/websocket")) config else """{"success":true,"data":{
                "defaultLink":"https://placeholder.example.com/fake.m3u8",
                "commentators":[
                    {"house_id":"a","nick_name":"BLV A","stream_urls":["file:///secret.m3u8","javascript:alert(1)","https://user:pass@example.com/a.m3u8","https://example.com/a.flv"]},
                    {"house_id":"b","name":"BLV B","raw_data":{"streamUrls":["https://example.com/b.m3u8"]}}
                ]}}"""
        }
        val result = source.commentators(match())
        assertEquals(2, result.size)
        assertTrue(result.first().streams.isEmpty())
        assertEquals("https://example.com/b.m3u8", result[1].streams.single().url)
        assertFalse(result.flatMap { it.streams }.any { "placeholder" in it.url })
    }

    @Test fun `encodes match IDs in the detail URL`() = runTest {
        var detailUrl = ""
        val source = GavangSource { url ->
            if (url.endsWith("/websocket")) config else {
                detailUrl = url
                """{"success":true,"data":{"anchorAppointmentVoList":[]}}"""
            }
        }
        assertTrue(source.commentators(match().copy(id = "a/b ?")).isEmpty())
        assertEquals("https://api.example.com/api/v1/matches/detail/a%2Fb%20%3F?webType=gavang", detailUrl)
    }

    @Test fun `uses alternate commentators when the primary BLV array is empty`() = runTest {
        val source = GavangSource { url ->
            if (url.endsWith("/websocket")) config else """{"success":true,"data":{"anchorAppointmentVoList":[],"commentators":[
                {"house_id":"house","id":"record","nick_name":"BLV A","stream_urls":["https://example.com/live.m3u8"]}
            ]}}"""
        }
        assertEquals("house", source.commentators(match()).single().id)
    }

    @Test(expected = IOException::class) fun `server errors cannot become an empty successful catalog`() = runTest {
        GavangSource { url -> if (url.endsWith("/websocket")) config else """{"success":false,"code":"5000","data":{}}""" }.matches()
        Unit
    }

    private fun match() = FootballMatch("match-one", "Home", "Away", "Cup", 100, MatchStatus.Live)
}
