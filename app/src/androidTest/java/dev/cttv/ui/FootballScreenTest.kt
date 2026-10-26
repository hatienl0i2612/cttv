package dev.cttv.ui

import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.test.platform.app.InstrumentationRegistry
import dev.cttv.data.Stream
import dev.cttv.football.FootballCommentator
import dev.cttv.football.FootballMatch
import dev.cttv.football.FootballState
import dev.cttv.football.MatchStatus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class FootballScreenTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val video = TestVideoServer()
    private val match = FootballMatch("one", "Home", "Away", "Cup", 1793008800, MatchStatus.Live, "1 : 0")

    @Test fun remoteSelectsMatchAndSecondBlvAndBackRestoresBothSelections() {
        val blvs = listOf(
            FootballCommentator("a", "BLV A", streams = listOf(Stream("http://127.0.0.1:1/offline.m3u8"))),
            FootballCommentator("b", "BLV B", streams = listOf(Stream(video.url))),
        )
        showFootball(FootballState(matches = listOf(match, match.copy(id = "two"), match.copy(id = "three"))), blvs)
        compose.onNodeWithTag("match-one").assertIsFocused()
        compose.onNodeWithTag("match-two").assertIsDisplayed()
        compose.onNodeWithTag("match-three").assertIsDisplayed()
        press(Key.DirectionCenter)
        compose.onNodeWithTag("blv-a").assertIsFocused()
        compose.onNodeWithText("BLV B").assertIsDisplayed()
        press(Key.DirectionRight)
        compose.onNodeWithTag("blv-b").assertIsFocused()
        press(Key.DirectionCenter)
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("player-ready").fetchSemanticsNodes().isNotEmpty() }
        back()
        compose.onNodeWithTag("blv-b").assertIsFocused()
        back()
        compose.onNodeWithTag("match-one").assertIsFocused()
    }

    @Test fun blvWithoutHlsExplainsAvailabilityWithoutStartingPlayer() {
        showFootball(FootballState(matches = listOf(match), selectedMatch = match, commentators = listOf(FootballCommentator("a", "BLV A"))))
        compose.onNodeWithTag("blv-a").assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNodeWithText("BLV này chưa có nguồn HLS. Hãy thử cập nhật khi gần giờ đấu.").assertIsDisplayed()
        compose.onAllNodesWithTag("player-screen").assertCountEquals(0)
    }

    @Test fun failedCatalogKeepsRetryAccessibleByRemote() {
        var requests = 0
        compose.setContent {
            CttvTheme {
                FootballScreen(FootballState(message = "Nguồn đang lỗi"), "Test", { requests++ }, {}, {}, {})
            }
        }
        compose.onNodeWithText("Nguồn đang lỗi").assertIsDisplayed()
        compose.onNodeWithTag("football-refresh").assertIsFocused()
        press(Key.DirectionCenter)
        compose.runOnIdle { assertEquals(1, requests) }
    }

    @Test fun matchWithoutBlvsCanReturnToFocusedMatch() {
        showFootball(FootballState(matches = listOf(match), selectedMatch = match))
        compose.onNodeWithText("Trận này chưa có BLV. Hãy thử lại khi gần giờ đấu.").assertIsDisplayed()
        compose.onNodeWithTag("football-refresh").assertIsFocused()
        back()
        compose.onNodeWithTag("match-one").assertIsFocused()
    }

    private fun showFootball(initial: FootballState, blvs: List<FootballCommentator> = emptyList()) {
        compose.setContent {
            var state by remember { mutableStateOf(initial) }
            CttvTheme {
                FootballScreen(state, "Test", {},
                    { state = state.copy(selectedMatch = it, commentators = blvs) },
                    { state = state.copy(selectedMatch = null, commentators = emptyList()) }, {},
                )
            }
        }
    }

    private fun press(key: Key) { compose.onRoot().performKeyInput { keyDown(key); keyUp(key) } }
    private fun back() { InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK) }
}
