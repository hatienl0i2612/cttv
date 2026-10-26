package dev.cttv.football

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FootballViewModelTest {
    private val match = FootballMatch("one", "Home", "Away", "Cup", 100, MatchStatus.Live)

    @Test fun `loads details only when a match is selected and retries failed details`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var detailRequests = 0
            val blv = FootballCommentator("a", "BLV A")
            val source = object : FootballSource {
                override val name = "Test"
                override suspend fun matches() = listOf(match)
                override suspend fun commentators(match: FootballMatch): List<FootballCommentator> {
                    detailRequests++
                    if (detailRequests == 1) throw IOException("offline")
                    return listOf(blv)
                }
            }
            val model = FootballViewModel(source)
            advanceUntilIdle()
            assertEquals(listOf(match), model.state.value.matches)
            assertEquals(0, detailRequests)
            model.selectMatch(match)
            advanceUntilIdle()
            assertFalse(model.state.value.loadingCommentators)
            assertTrue(model.state.value.commentatorError != null)
            model.selectMatch(match)
            advanceUntilIdle()
            assertEquals(listOf(blv), model.state.value.commentators)
            assertNull(model.state.value.commentatorError)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun `failed refresh preserves the current matches and can recover`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var fail = false
            val source = object : FootballSource {
                override val name = "Test"
                override suspend fun matches(): List<FootballMatch> {
                    if (fail) throw IOException("offline")
                    return listOf(match)
                }
                override suspend fun commentators(match: FootballMatch) = emptyList<FootballCommentator>()
            }
            val model = FootballViewModel(source)
            advanceUntilIdle()
            fail = true
            model.refresh()
            advanceUntilIdle()
            assertEquals(listOf(match), model.state.value.matches)
            assertFalse(model.state.value.refreshing)
            assertTrue(model.state.value.message != null)
            fail = false
            model.refresh()
            advanceUntilIdle()
            assertNull(model.state.value.message)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun `back cancels a pending detail request so its result cannot reopen the match`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val pending = CompletableDeferred<List<FootballCommentator>>()
            val source = object : FootballSource {
                override val name = "Test"
                override suspend fun matches() = listOf(match)
                override suspend fun commentators(match: FootballMatch) = pending.await()
            }
            val model = FootballViewModel(source)
            advanceUntilIdle()
            model.selectMatch(match)
            advanceUntilIdle()
            assertTrue(model.state.value.loadingCommentators)
            model.closeMatch()
            pending.complete(listOf(FootballCommentator("a", "BLV A")))
            advanceUntilIdle()
            assertNull(model.state.value.selectedMatch)
            assertTrue(model.state.value.commentators.isEmpty())
            assertFalse(model.state.value.loadingCommentators)
        } finally { Dispatchers.resetMain() }
    }
}
