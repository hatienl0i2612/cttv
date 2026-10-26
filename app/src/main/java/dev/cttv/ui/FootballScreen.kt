package dev.cttv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.cttv.data.Channel
import dev.cttv.football.FootballMatch
import dev.cttv.football.FootballState
import dev.cttv.football.FootballViewModel
import dev.cttv.football.MatchStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun FootballRoute(onExit: () -> Unit) {
    val football: FootballViewModel = viewModel()
    val state = football.state.collectAsStateWithLifecycle().value
    FootballScreen(state, football.sourceName, football::refresh, football::selectMatch, football::closeMatch, onExit)
}

@Composable
fun FootballScreen(
    state: FootballState,
    sourceName: String,
    onRefresh: () -> Unit,
    onSelectMatch: (FootballMatch) -> Unit,
    onCloseMatch: () -> Unit,
    onExit: () -> Unit,
) {
    val match = state.selectedMatch
    var lastMatchId by rememberSaveable { mutableStateOf<String?>(null) }
    var playingId by rememberSaveable(match?.id) { mutableStateOf<String?>(null) }
    var lastCommentatorId by rememberSaveable(match?.id) { mutableStateOf<String?>(null) }
    var notice by remember(match?.id) { mutableStateOf<String?>(null) }
    val matchGrid = rememberLazyGridState()
    val blvGrid = rememberLazyGridState()
    val playing = state.commentators.firstOrNull { it.id == playingId }
    if (match != null && playing != null) {
        val channel = remember(match, playing) { Channel("${match.id}/${playing.id}", match.title, null, emptyList(), playing.streams) }
        PlayerScreen(channel, subtitle = "${match.league} · ${playing.name}") { playingId = null }
        return
    }

    BackHandler(enabled = match != null, onBack = onCloseMatch)
    val refreshFocus = remember { FocusRequester() }
    val loading = if (match == null) state.refreshing else state.loadingCommentators
    val empty = if (match == null) state.matches.isEmpty() else state.commentators.isEmpty()
    LaunchedEffect(match?.id) {
        if (empty) { withFrameNanos { }; refreshFocus.requestFocus() }
    }

    Column(Modifier.fillMaxSize().background(Background).padding(28.dp).testTag("football-screen")) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (match == null) "Bóng đá" else match.title, fontSize = 25.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Text(
                    if (match == null) "${state.matches.size} trận đấu · Nguồn $sourceName" else "${match.league} · Chọn bình luận viên",
                    color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            FootballButton("Quay lại", Modifier.testTag("football-back"), if (match == null) onExit else onCloseMatch)
            FootballButton(
                if (match == null) "Cập nhật" else "Thử lại",
                Modifier.focusRequester(refreshFocus).testTag("football-refresh"),
            ) { notice = null; if (match == null) onRefresh() else onSelectMatch(match) }
        }
        Spacer(Modifier.height(20.dp))
        val message = notice ?: if (match == null) state.message else state.commentatorError
        if (message != null) {
            Text(message, color = Accent, fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))
        }
        if (loading) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), color = Accent, strokeWidth = 2.dp)
                Text(if (match == null) "Đang tải trận đấu…" else "Đang tải BLV và nguồn phát…", color = Muted, fontSize = 12.sp)
            }
            Spacer(Modifier.height(12.dp))
        }
        when {
            empty && !loading -> Text(
                if (match == null) "Nguồn hiện chưa có trận đấu. Chọn Cập nhật để thử lại." else "Trận này chưa có BLV. Hãy thử lại khi gần giờ đấu.",
                color = Muted, fontSize = 16.sp,
            )
            match == null -> {
                val focusId = state.matches.firstOrNull { it.id == lastMatchId }?.id ?: state.matches.firstOrNull()?.id
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(240.dp), state = matchGrid,
                    modifier = Modifier.fillMaxSize().testTag("football-matches"),
                    horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(state.matches, key = { it.id }) { item ->
                        val focus = remember { FocusRequester() }
                        if (item.id == focusId) LaunchedEffect(Unit) { withFrameNanos { }; focus.requestFocus() }
                        FootballCard(Modifier.height(180.dp).testTag("match-${item.id}"), focus, {
                            lastMatchId = item.id; onSelectMatch(item)
                        }) {
                            Text(item.league, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Team(item.homeTeam, item.homeLogo, Modifier.weight(1f))
                                Text(item.score ?: "VS", color = Accent, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                Team(item.awayTeam, item.awayLogo, Modifier.weight(1f))
                            }
                            Spacer(Modifier.weight(1f))
                            Text("${item.status.label()} · ${kickoff(item.kickoff)}", color = Accent, fontSize = 11.sp)
                            Spacer(Modifier.height(6.dp))
                            Text(item.commentators.joinToString(" · ") { it.name }.ifEmpty { "Chưa phân công BLV" }, color = Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            else -> {
                val focusId = state.commentators.firstOrNull { it.id == lastCommentatorId }?.id ?: state.commentators.firstOrNull()?.id
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(210.dp), state = blvGrid,
                    modifier = Modifier.fillMaxSize().testTag("football-commentators"),
                    horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(state.commentators, key = { it.id }) { blv ->
                        val focus = remember { FocusRequester() }
                        if (blv.id == focusId) LaunchedEffect(Unit) { withFrameNanos { }; focus.requestFocus() }
                        FootballCard(Modifier.height(150.dp).testTag("blv-${blv.id}"), focus, {
                            lastCommentatorId = blv.id
                            if (blv.streams.isEmpty()) notice = "BLV này chưa có nguồn HLS. Hãy thử cập nhật khi gần giờ đấu."
                            else { notice = null; playingId = blv.id }
                        }) {
                            AsyncImage(blv.avatar, contentDescription = null, modifier = Modifier.size(52.dp))
                            Spacer(Modifier.height(10.dp))
                            Text(blv.name, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(6.dp))
                            Text(if (blv.streams.isEmpty()) "Chưa có nguồn HLS" else "OK · Xem trực tiếp", color = Muted, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FootballButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val focus = remember { FocusRequester() }
    Button(onClick, modifier.focusRequester(focus).pointerInput(onClick) { detectTapGestures { focus.requestFocus(); onClick() } }) {
        Text(text, fontSize = 12.sp)
    }
}

@Composable
private fun FootballCard(modifier: Modifier, focus: FocusRequester, onClick: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().focusRequester(focus)
            .pointerInput(onClick) { detectTapGestures { focus.requestFocus(); onClick() } },
        colors = ClickableSurfaceDefaults.colors(containerColor = Panel, focusedContainerColor = androidx.compose.ui.graphics.Color(0xFF41404A)),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.025f),
    ) { Column(Modifier.fillMaxSize().padding(16.dp), content = content) }
}

@Composable
private fun Team(name: String, logo: String?, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        AsyncImage(logo, contentDescription = null, modifier = Modifier.size(38.dp))
        Spacer(Modifier.height(6.dp))
        Text(name, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

private fun MatchStatus.label(): String = when (this) {
    MatchStatus.Live -> "TRỰC TIẾP"
    MatchStatus.Upcoming -> "Sắp diễn ra"
    MatchStatus.Finished -> "Đã kết thúc"
}

private fun kickoff(seconds: Long): String = if (seconds <= 0) "Chưa có giờ đấu" else
    SimpleDateFormat("HH:mm dd/MM", Locale.forLanguageTag("vi-VN")).apply {
        timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
    }.format(Date(seconds * 1000))
