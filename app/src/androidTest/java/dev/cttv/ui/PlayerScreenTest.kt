package dev.cttv.ui

import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import androidx.tv.material3.Text
import dev.cttv.data.Channel
import dev.cttv.data.Stream
import java.net.InetAddress
import java.net.ServerSocket
import org.junit.Rule
import org.junit.Test

class PlayerScreenTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val video = TestVideoServer()
    private val fixture get() = video.url

    @Test
    fun playsVideoAndBackReturnsToCatalog() {
        showPlayer(listOf(Stream(fixture)))
        compose.waitUntil(20_000) {
            compose.onAllNodesWithTag("player-ready").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("player-ready").assertIsDisplayed()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.onNodeWithText("Đã về danh sách").assertIsDisplayed()
    }

    @Test
    fun channelInfoHidesAfterThreeSecondsAndReturnsWithRemote() {
        showPlayer(listOf(Stream(fixture)))
        compose.waitUntil(20_000) {
            compose.onAllNodesWithTag("player-ready").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Test TV").assertIsDisplayed()
        compose.onNodeWithText("BACK · Quay lại").assertIsDisplayed()
        compose.waitUntil(4_000) {
            compose.onAllNodesWithText("Test TV").fetchSemanticsNodes().isEmpty()
        }

        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitUntil(2_000) {
            compose.onAllNodesWithText("Test TV").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("BACK · Quay lại").assertIsDisplayed()
        compose.waitUntil(4_000) {
            compose.onAllNodesWithText("Test TV").fetchSemanticsNodes().isEmpty()
        }
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.onNodeWithText("Đã về danh sách").assertIsDisplayed()
    }

    @Test
    fun channelInfoAlsoHidesWhileWaitingForTheStream() {
        // Leaving the request unanswered keeps the player buffering.
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            showPlayer(listOf(Stream("http://127.0.0.1:${server.localPort}/waiting.m3u8")))
            compose.onNodeWithText("Test TV").assertIsDisplayed()
            compose.waitUntil(4_000) {
                compose.onAllNodesWithText("Test TV").fetchSemanticsNodes().isEmpty()
            }
            compose.onNodeWithTag("player-loading").assertIsDisplayed()
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            compose.onNodeWithText("Đã về danh sách").assertIsDisplayed()
        }
    }

    @Test
    fun automaticallyTriesNextStreamAfterConnectionFailure() {
        showPlayer(listOf(Stream("http://127.0.0.1:1/offline.m3u8"), Stream(fixture)))
        compose.waitUntil(25_000) {
            compose.onAllNodesWithTag("player-ready").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("player-ready").assertIsDisplayed()
    }

    @Test
    fun unavailableStreamShowsFocusedRetryAndCanReturn() {
        showPlayer(listOf(Stream("http://127.0.0.1:1/offline.m3u8")))
        compose.waitUntil(25_000) {
            compose.onAllNodesWithText("Thử lại").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Thử lại").assertIsFocused()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.onNodeWithText("Đã về danh sách").assertIsDisplayed()
    }

    @Test
    fun unsupportedProtocolExplainsTheLimitAndAllowsBack() {
        showPlayer(listOf(Stream("srt://example.com:9000/live")))
        compose.onNodeWithText("Về danh sách").assertIsFocused()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.onNodeWithText("Đã về danh sách").assertIsDisplayed()
    }

    private fun showPlayer(streams: List<Stream>) {
        val channel = Channel("test", "Test TV", null, listOf("Vietnam"), streams)
        compose.setContent {
            var playing by remember { mutableStateOf(true) }
            CttvTheme {
                if (playing) PlayerScreen(channel) { playing = false } else Text("Đã về danh sách")
            }
        }
    }
}
