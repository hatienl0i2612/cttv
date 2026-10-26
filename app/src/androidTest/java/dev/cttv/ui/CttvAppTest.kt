package dev.cttv.ui

import android.view.KeyEvent
import androidx.compose.runtime.SideEffect
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
import dev.cttv.CatalogState
import dev.cttv.data.Channel
import dev.cttv.data.Stream
import dev.cttv.football.FootballState
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class CttvAppTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val video = TestVideoServer()

    @Test
    fun startsOnMenuWithoutLoadingTelevision() {
        var televisionLoaded = false
        compose.setContent {
            CttvTheme { CttvApp { SideEffect { televisionLoaded = true } } }
        }
        compose.onNodeWithText("1. Truyền hình").assertIsDisplayed()
        compose.onNodeWithText("2. Bóng đá").assertIsDisplayed()
        compose.onNodeWithTag("home-Television").assertIsFocused()
        compose.onAllNodesWithTag("football-screen").assertCountEquals(0)
        compose.runOnIdle { assertFalse(televisionLoaded) }
    }

    @Test
    fun remoteOpensFootballPageAndBackRestoresFootballFocus() {
        var televisionLoaded = false
        compose.setContent {
            CttvTheme {
                CttvApp(football = { onExit -> FootballScreen(FootballState(), "Test", {}, {}, {}, onExit) }) {
                    SideEffect { televisionLoaded = true }
                }
            }
        }
        pressRemoteKey(Key.DirectionRight)
        compose.onNodeWithTag("home-Football").assertIsFocused()
        pressRemoteKey(Key.DirectionCenter)
        compose.onNodeWithTag("football-screen").assertIsDisplayed()
        compose.onAllNodesWithTag("home-screen").assertCountEquals(0)
        compose.runOnIdle { assertFalse(televisionLoaded) }
        pressBack()
        compose.onNodeWithTag("home-Football").assertIsFocused()
        pressRemoteKey(Key.DirectionLeft)
        compose.onNodeWithTag("home-Television").assertIsFocused()
    }

    @Test
    fun televisionOpensVietnamAndBackReturnsHome() {
        showApp()
        pressRemoteKey(Key.DirectionCenter)
        compose.onNodeWithTag("country-Vietnam").assertIsFocused()
        compose.onNodeWithTag("channel-vtv1").assertIsDisplayed()
        compose.onAllNodesWithTag("home-screen").assertCountEquals(0)
        pressBack()
        compose.onNodeWithTag("home-Television").assertIsFocused()
        pressRemoteKey(Key.DirectionCenter)
        compose.onNodeWithTag("country-Vietnam").assertIsFocused()
    }

    @Test
    fun backFromPlayerReturnsToVietnamBeforeHome() {
        showApp()
        pressRemoteKey(Key.DirectionCenter)
        pressRemoteKey(Key.DirectionCenter)
        compose.onNodeWithTag("channel-vtv1").assertIsFocused()
        pressRemoteKey(Key.DirectionCenter)
        compose.waitUntil(20_000) {
            compose.onAllNodesWithTag("player-ready").fetchSemanticsNodes().isNotEmpty()
        }
        pressBack()
        compose.onNodeWithTag("channel-vtv1").assertIsFocused()
        pressBack()
        compose.onNodeWithTag("home-Television").assertIsFocused()
    }

    private fun showApp() {
        val channel = Channel("vtv1", "VTV1", null, listOf("Vietnam"), listOf(Stream(video.url)))
        compose.setContent {
            CttvTheme { CttvApp { TelevisionScreen(CatalogState(listOf(channel), false)) {} } }
        }
    }

    private fun pressRemoteKey(key: Key) {
        compose.onRoot().performKeyInput { keyDown(key); keyUp(key) }
    }

    private fun pressBack() {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
    }
}
