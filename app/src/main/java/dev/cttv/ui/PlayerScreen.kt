package dev.cttv.ui

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import dev.cttv.data.Channel
import kotlinx.coroutines.delay

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(channel: Channel, subtitle: String = channel.countries.joinToString(" · "), onClose: () -> Unit) {
    val context = LocalContext.current
    val streams = remember(channel) { channel.streams.filter { it.isHttp } }
    BackHandler(onBack = onClose)

    // shortcut: list RTMP/SRT/MMSH channels without decoding; add a compatible player when needed.
    if (streams.isEmpty()) {
        val closeFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) { withFrameNanos { }; closeFocus.requestFocus() }
        Column(
            Modifier.fillMaxSize().background(Background).padding(48.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(channel.name, fontSize = 25.sp)
            Spacer(Modifier.height(16.dp))
            Text("Kênh này dùng giao thức chưa hỗ trợ. Bản hiện tại phát các nguồn HTTP/HTTPS.", color = Muted, fontSize = 14.sp)
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onClose,
                modifier = Modifier.focusRequester(closeFocus).pointerInput(onClose) { detectTapGestures { onClose() } },
            ) { Text("Về danh sách") }
        }
        return
    }

    var sourceIndex by remember(channel) { mutableIntStateOf(0) }
    var attempt by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showInitialInfo by remember(channel.id) { mutableStateOf(true) }
    var controlsVisible by remember { mutableStateOf(false) }
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    val focus = remember { FocusRequester() }
    val retryFocus = remember { FocusRequester() }
    val view = remember {
        PlayerView(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            keepScreenOn = true
            controllerShowTimeoutMs = 3_000
            setControllerAutoShow(false)
            setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
            setControllerVisibilityListener(PlayerView.ControllerVisibilityListener {
                controlsVisible = isControllerFullyVisible
            })
        }
    }

    LaunchedEffect(channel.id, sourceIndex, attempt) {
        showInitialInfo = true
        delay(3_000)
        showInitialInfo = false
    }

    LifecycleStartEffect(channel.id, sourceIndex, attempt) {
        loading = true
        error = null
        val stream = streams[sourceIndex]
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent("CTTV/0.1.0")
            .setDefaultRequestProperties(stream.headers)
            .setConnectTimeoutMs(12_000)
            .setReadTimeoutMs(15_000)
            .setAllowCrossProtocolRedirects(true)
        val instance = ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(context, http)))
            .build()
        instance.setAudioAttributes(AudioAttributes.DEFAULT, true)
        instance.setHandleAudioBecomingNoisy(true)
        instance.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                loading = state == Player.STATE_BUFFERING || state == Player.STATE_IDLE
                if (state == Player.STATE_ENDED) error = "Nguồn phát đã kết thúc. Bạn có thể thử kết nối lại."
            }

            override fun onPlayerError(failure: PlaybackException) {
                if (failure.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    instance.seekToDefaultPosition()
                    instance.prepare()
                } else if (sourceIndex + 1 < streams.size) {
                    sourceIndex += 1
                } else {
                    loading = false
                    error = when (failure.errorCode) {
                        PlaybackException.ERROR_CODE_DECODING_FAILED,
                        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ->
                            "TV chưa giải mã được định dạng của nguồn phát này."
                        else -> "Chưa xem được kênh này. Nguồn có thể đang ngừng phát, giới hạn khu vực hoặc mạng bị gián đoạn."
                    }
                }
            }
        })
        val item = MediaItem.Builder().setUri(stream.url)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(channel.name).build())
        when {
            stream.url.contains(".m3u8", ignoreCase = true) -> item.setMimeType(MimeTypes.APPLICATION_M3U8)
            stream.url.contains(".mpd", ignoreCase = true) -> item.setMimeType(MimeTypes.APPLICATION_MPD)
        }
        player = instance
        instance.setMediaItem(item.build())
        instance.prepare()
        instance.playWhenReady = true
        onStopOrDispose {
            view.player = null
            instance.release()
            player = null
        }
    }

    DisposableEffect(view) {
        onDispose {
            view.player = null
            view.keepScreenOn = false
        }
    }

    LaunchedEffect(error) {
        withFrameNanos { }
        if (error == null) focus.requestFocus() else retryFocus.requestFocus()
    }

    Box(Modifier.fillMaxSize().background(Color.Black).testTag("player-screen")) {
        if (error == null) {
            AndroidView(
                factory = { view },
                update = { it.player = player },
                modifier = Modifier.fillMaxSize().focusRequester(focus)
                    .onKeyEvent { event -> event.key != Key.Back && view.dispatchKeyEvent(event.nativeKeyEvent) }
                    .focusable()
                    .testTag(if (loading) "player-loading" else "player-ready"),
            )
            if (showInitialInfo || controlsVisible) {
                Column(
                    Modifier.fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)))
                        .padding(30.dp),
                ) {
                    Text("TRỰC TIẾP  /  $subtitle", color = Accent, fontSize = 10.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(channel.name, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text("BACK · Quay lại", color = Muted, fontSize = 11.sp)
                }
            }
            if (loading) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Accent, modifier = Modifier.size(36.dp), strokeWidth = 3.dp)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        if (sourceIndex == 0) "Đang kết nối kênh…" else "Đang thử nguồn ${sourceIndex + 1}/${streams.size}…",
                        color = Color.White, fontSize = 13.sp,
                    )
                }
            }
        } else {
            Column(
                Modifier.fillMaxSize().padding(60.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(channel.name, color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(14.dp))
                Text(error.orEmpty(), color = Muted, fontSize = 14.sp)
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    val retry = { sourceIndex = 0; attempt += 1 }
                    Button(
                        onClick = retry,
                        modifier = Modifier.focusRequester(retryFocus).pointerInput(retry) { detectTapGestures { retry() } },
                    ) {
                        Text("Thử lại")
                    }
                    Button(
                        onClick = onClose,
                        modifier = Modifier.pointerInput(onClose) { detectTapGestures { onClose() } },
                    ) { Text("Về danh sách") }
                }
            }
        }
    }
}
