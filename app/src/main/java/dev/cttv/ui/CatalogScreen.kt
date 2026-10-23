package dev.cttv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import dev.cttv.CatalogState
import dev.cttv.data.Channel
import dev.cttv.data.matching
import kotlinx.coroutines.launch

@Composable
fun CttvApp(state: CatalogState, onRefresh: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var playingId by rememberSaveable { mutableStateOf<String?>(null) }
    var lastPlayedId by rememberSaveable { mutableStateOf<String?>(null) }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val playing = state.channels.firstOrNull { it.id == playingId }

    if (playing != null) {
        PlayerScreen(playing) { playingId = null }
    } else {
        CatalogScreen(
            state = state,
            query = query,
            onQueryChange = {
                query = it
                lastPlayedId = null
                scope.launch { gridState.scrollToItem(0) }
            },
            onRefresh = onRefresh,
            onSelect = { lastPlayedId = it.id; playingId = it.id },
            gridState = gridState,
            restoreChannelId = lastPlayedId,
        )
    }
}

@Composable
fun CatalogScreen(
    state: CatalogState,
    query: String,
    onQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onSelect: (Channel) -> Unit,
    gridState: LazyGridState = rememberLazyGridState(),
    restoreChannelId: String? = null,
) {
    val channels = remember(state.channels, query) { state.channels.matching(query) }
    val initialFocus = remember { FocusRequester() }
    val gridFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        withFrameNanos { }
        if (restoreChannelId == null) initialFocus.requestFocus()
    }

    Row(Modifier.fillMaxSize().background(Background)) {
        Column(
            Modifier.width(204.dp).fillMaxHeight().background(Color(0xFF141923))
                .padding(start = 24.dp, end = 18.dp, top = 28.dp, bottom = 18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("▶", color = Accent, fontSize = 25.sp)
                Spacer(Modifier.width(10.dp))
                Text("CTTV", fontSize = 27.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            }
            Text("TRUYỀN HÌNH VIỆT NAM", color = Muted, fontSize = 8.sp, letterSpacing = 1.sp)
            Spacer(Modifier.height(30.dp))
            Text("QUỐC GIA", color = Muted, fontSize = 9.sp, letterSpacing = 1.5.sp)
            Spacer(Modifier.height(10.dp))
            CountryButton(
                "Vietnam", state.channels.size, selected = true,
                modifier = Modifier.testTag("country-Vietnam").focusRequester(initialFocus)
                    .focusProperties { right = if (channels.isEmpty()) FocusRequester.Default else gridFocus },
            ) {
                if (channels.isNotEmpty()) gridFocus.requestFocus()
            }
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(12.dp))
            Text("Nguồn kênh · iptv-org", color = Muted, fontSize = 9.sp)
        }

        Column(Modifier.weight(1f).padding(start = 28.dp, end = 28.dp, top = 28.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("KHÁM PHÁ / TRUYỀN HÌNH", color = Muted, fontSize = 9.sp, letterSpacing = 1.5.sp)
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(5.dp).background(Accent, CircleShape))
                Spacer(Modifier.width(6.dp))
                Text("LIVE", color = Accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            Text("Vietnam", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Text(
                if (state.channels.isEmpty()) "Chọn một kênh. Khám phá một góc thế giới."
                else "${channels.size} kênh để khám phá  ·  Chọn và xem ngay",
                color = Muted, fontSize = 11.sp,
            )
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query, onValueChange = onQueryChange,
                    modifier = Modifier.weight(1f).testTag("channel-search"),
                    placeholder = { androidx.compose.material3.Text("Tìm tên kênh…", fontSize = 12.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = Color.White),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                )
                Spacer(Modifier.width(12.dp))
                Button(
                    onClick = onRefresh, enabled = !state.refreshing,
                    modifier = Modifier.pointerInput(state.refreshing, onRefresh) {
                        detectTapGestures { if (!state.refreshing) onRefresh() }
                    },
                    colors = ButtonDefaults.colors(containerColor = Panel, contentColor = Color.White),
                ) { Text(if (state.refreshing) "Đang tải…" else "Cập nhật", fontSize = 11.sp) }
            }
            if (state.message != null) {
                Text(state.message, color = Accent, fontSize = 10.sp, modifier = Modifier.padding(top = 10.dp))
            }
            Spacer(Modifier.height(16.dp))
            when {
                state.channels.isEmpty() && state.refreshing -> EmptyCatalog(
                    "Đang tải kênh Việt Nam…", "Đang tải danh sách kênh từ iptv-org.", loading = true,
                )
                state.channels.isEmpty() -> EmptyCatalog("Chưa tải được kênh", "Chọn Cập nhật để thử lại.")
                channels.isEmpty() -> EmptyCatalog("Không tìm thấy kênh", "Thử tìm bằng tên khác.")
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(155.dp), state = gridState,
                    modifier = Modifier.fillMaxSize().focusRequester(gridFocus).focusRestorer().testTag("channel-grid"),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(start = 5.dp, end = 5.dp, top = 5.dp, bottom = 24.dp),
                ) {
                    items(channels, key = { it.id }) { channel ->
                        val requester = remember { FocusRequester() }
                        LaunchedEffect(channel.id) {
                            if (channel.id == restoreChannelId) {
                                withFrameNanos { }
                                requester.requestFocus()
                            }
                        }
                        ChannelCard(channel, Modifier.focusRequester(requester)) { onSelect(channel) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CountryButton(
    name: String, count: Int, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit,
) {
    val pointerFocus = remember { FocusRequester() }
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(38.dp).focusRequester(pointerFocus)
            .pointerInput(onClick) { detectTapGestures { pointerFocus.requestFocus(); onClick() } },
        contentPadding = PaddingValues(horizontal = 10.dp),
        colors = ButtonDefaults.colors(
            containerColor = if (selected) Color(0xFF34302C) else Color.Transparent,
            contentColor = if (selected) Accent else Muted,
            focusedContainerColor = Accent, focusedContentColor = Background,
        ),
        shape = ButtonDefaults.shape(shape = RoundedCornerShape(8.dp)),
        scale = ButtonDefaults.scale(focusedScale = 1.02f),
    ) {
        Text(name, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(count.toString(), fontSize = 9.sp)
    }
}

@Composable
private fun ChannelCard(channel: Channel, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val pointerFocus = remember { FocusRequester() }
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(140.dp).testTag("channel-${channel.id}")
            .focusRequester(pointerFocus)
            .pointerInput(onClick) { detectTapGestures { pointerFocus.requestFocus(); onClick() } }
            .semantics { contentDescription = "Xem ${channel.name}" },
        colors = ClickableSurfaceDefaults.colors(containerColor = Panel, focusedContainerColor = Color(0xFF41404A)),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.035f),
    ) {
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            Box(
                Modifier.fillMaxWidth().height(72.dp)
                    .background(Brush.verticalGradient(listOf(Color(0xFF263040), Panel)), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                val fallback: @Composable () -> Unit = {
                    Text(channel.name.take(2).uppercase(), color = Accent, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                }
                if (channel.logoUrl == null) fallback() else {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(channel.logoUrl).size(240, 120).build(),
                        contentDescription = null, contentScale = ContentScale.Fit,
                        modifier = Modifier.padding(10.dp).fillMaxSize(),
                        loading = { fallback() }, error = { fallback() },
                    )
                }
            }
            Spacer(Modifier.height(9.dp))
            Text(channel.name, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Text(
                if (channel.streams.none { it.isHttp }) "Giao thức chưa hỗ trợ" else channel.countries.joinToString(" · "),
                color = Muted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun EmptyCatalog(title: String, subtitle: String, loading: Boolean = false) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(color = Accent, modifier = Modifier.size(30.dp), strokeWidth = 2.dp)
            Spacer(Modifier.height(18.dp))
        }
        Text(title, fontSize = 18.sp)
        Spacer(Modifier.height(8.dp))
        Text(subtitle, fontSize = 12.sp, color = Muted)
    }
}
