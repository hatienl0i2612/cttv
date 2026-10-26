package dev.cttv.football

import dev.cttv.data.Stream

interface FootballSource {
    val name: String
    suspend fun matches(): List<FootballMatch>
    suspend fun commentators(match: FootballMatch): List<FootballCommentator>
}

enum class MatchStatus { Live, Upcoming, Finished }

data class FootballMatch(
    val id: String,
    val homeTeam: String,
    val awayTeam: String,
    val league: String,
    val kickoff: Long,
    val status: MatchStatus,
    val score: String? = null,
    val homeLogo: String? = null,
    val awayLogo: String? = null,
    val commentators: List<FootballCommentator> = emptyList(),
) {
    val title: String get() = "$homeTeam – $awayTeam"
}

data class FootballCommentator(
    val id: String,
    val name: String,
    val avatar: String? = null,
    val streams: List<Stream> = emptyList(),
)
