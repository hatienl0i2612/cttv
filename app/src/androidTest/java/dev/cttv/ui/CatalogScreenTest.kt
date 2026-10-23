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
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import dev.cttv.CatalogState
import dev.cttv.data.Channel
import dev.cttv.data.Stream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class CatalogScreenTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val video = TestVideoServer()
    private val channels = listOf(
        Channel("news", "News Vietnam", null, listOf("Vietnam"), listOf(Stream("https://example.com/news.m3u8"))),
        Channel("music", "Music Live", null, listOf("Vietnam"), listOf(Stream("https://example.com/music.m3u8"))),
        Channel("vtv1", "VTV1", null, listOf("Vietnam"), listOf(Stream("https://example.com/vtv1.m3u8"))),
    )

    @Test
    fun startsWithVietnamFocusedEvenBeforeChannelsLoad() {
        val state = mutableStateOf(CatalogState())
        compose.setContent { CttvTheme { CttvApp(state.value) {} } }
        compose.onNodeWithTag("country-Vietnam").assertIsFocused()
        compose.runOnIdle { state.value = CatalogState(channels, false) }
        compose.onNodeWithTag("country-Vietnam").assertIsFocused()
        compose.onNodeWithTag("channel-vtv1").assertIsDisplayed()
        pressRemoteKey(Key.DirectionRight)
        compose.onNodeWithTag("channel-vtv1").assertIsFocused()
        compose.runOnIdle { state.value = state.value.copy(refreshing = true) }
        compose.onNodeWithTag("channel-vtv1").assertIsFocused()
    }

    @Test
    fun countryMenuOnlyShowsVietnam() {
        compose.setContent {
            CttvTheme { CatalogScreen(CatalogState(channels, false), "", {}, {}, {}) }
        }
        compose.onNodeWithTag("country-Vietnam").assertIsDisplayed()
        for (country in listOf("all", "United States", "China", "South Korea", "Japan")) {
            compose.onAllNodesWithTag("country-$country").assertCountEquals(0)
        }
    }

    @Test
    fun searchFiltersVietnamChannelsAndKeepsVTVFirst() {
        compose.setContent {
            var query by remember { mutableStateOf("") }
            CttvTheme {
                CatalogScreen(CatalogState(channels, false), query, { query = it }, {}, {})
            }
        }
        assertTrue(
            compose.onNodeWithTag("channel-vtv1").fetchSemanticsNode().boundsInRoot.left <
                compose.onNodeWithTag("channel-news").fetchSemanticsNode().boundsInRoot.left,
        )
        compose.onNodeWithTag("channel-search").performTextInput("  NEWS  ")
        compose.onNodeWithTag("channel-news").assertIsDisplayed()
        compose.onAllNodesWithTag("channel-vtv1").assertCountEquals(0)
        compose.onAllNodesWithTag("channel-music").assertCountEquals(0)
    }

    @Test
    fun remoteMovesBetweenChannelsAndEnterSelectsTheFocusedChannel() {
        var selected: Channel? = null
        compose.setContent {
            CttvTheme { CatalogScreen(CatalogState(channels, false), "", {}, {}, { selected = it }) }
        }
        compose.onNodeWithTag("country-Vietnam").assertIsFocused()
        pressRemoteKey(Key.DirectionRight)
        compose.onNodeWithTag("channel-vtv1").assertIsFocused()
        pressRemoteKey(Key.DirectionRight)
        compose.onNodeWithTag("channel-news").assertIsFocused()
        pressRemoteKey(Key.DirectionCenter)
        compose.runOnIdle { assertEquals("news", selected?.id) }
    }

    @Test
    fun returningFromPlaybackRestoresFocusToTheChannel() {
        compose.setContent {
            CttvTheme {
                CttvApp(CatalogState(channels.map { it.copy(streams = listOf(Stream(video.url))) }, false)) {}
            }
        }
        compose.onNodeWithTag("channel-vtv1").performClick()
        compose.waitUntil(20_000) {
            compose.onAllNodesWithTag("player-ready").fetchSemanticsNodes().isNotEmpty()
        }
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.onNodeWithTag("channel-vtv1").assertIsFocused()
    }

    @Test
    fun remoteCanBrowseChannelsAndPlayWithoutTouch() {
        val catalog = (channels +
            (1..8).map { Channel("extra-$it", "Extra $it", null, listOf("Vietnam"), emptyList()) })
            .map { it.copy(streams = listOf(Stream(video.url))) }
        compose.setContent { CttvTheme { CttvApp(CatalogState(catalog, false)) {} } }
        compose.onNodeWithTag("country-Vietnam").assertIsFocused()
        pressRemoteKey(Key.DirectionCenter)
        compose.onNodeWithTag("channel-vtv1").assertIsFocused()
        pressRemoteKey(Key.DirectionRight)
        compose.onNodeWithTag("channel-news").assertIsFocused()
        pressRemoteKey(Key.DirectionLeft)
        compose.onNodeWithTag("channel-vtv1").assertIsFocused()
        pressRemoteKey(Key.DirectionDown)
        compose.onNodeWithTag("channel-extra-2").assertIsFocused()
        pressRemoteKey(Key.DirectionUp)
        compose.onNodeWithTag("channel-vtv1").assertIsFocused()
        pressRemoteKey(Key.DirectionLeft)
        compose.onNodeWithTag("country-Vietnam").assertIsFocused()
        pressRemoteKey(Key.DirectionRight)
        compose.onNodeWithTag("channel-vtv1").assertIsFocused()
        pressRemoteKey(Key.DirectionCenter)
        compose.waitUntil(20_000) {
            compose.onAllNodesWithTag("player-ready").fetchSemanticsNodes().isNotEmpty()
        }
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.onNodeWithTag("channel-vtv1").assertIsFocused()
    }

    private fun pressRemoteKey(key: Key) {
        compose.onRoot().performKeyInput { keyDown(key); keyUp(key) }
    }
}
