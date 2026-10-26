package dev.cttv.football

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cttv.football.sources.GavangSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FootballState(
    val matches: List<FootballMatch> = emptyList(),
    val refreshing: Boolean = false,
    val message: String? = null,
    val selectedMatch: FootballMatch? = null,
    val commentators: List<FootballCommentator> = emptyList(),
    val loadingCommentators: Boolean = false,
    val commentatorError: String? = null,
)

class FootballViewModel(private val source: FootballSource = GavangSource()) : ViewModel() {
    val sourceName: String get() = source.name
    private val mutableState = MutableStateFlow(FootballState())
    val state = mutableState.asStateFlow()
    private var detailJob: Job? = null

    init { refresh() }

    fun refresh() {
        if (mutableState.value.refreshing) return
        mutableState.value = mutableState.value.copy(refreshing = true, message = null)
        viewModelScope.launch {
            try {
                mutableState.value = mutableState.value.copy(matches = source.matches(), refreshing = false)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                mutableState.value = mutableState.value.copy(refreshing = false, message = "Không tải được trận đấu từ ${source.name}. Chọn Cập nhật để thử lại.")
            }
        }
    }

    fun selectMatch(match: FootballMatch) {
        detailJob?.cancel()
        mutableState.value = mutableState.value.copy(
            selectedMatch = match, commentators = emptyList(), loadingCommentators = true, commentatorError = null,
        )
        detailJob = viewModelScope.launch {
            try {
                val commentators = source.commentators(match)
                mutableState.value = mutableState.value.copy(commentators = commentators, loadingCommentators = false)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                mutableState.value = mutableState.value.copy(loadingCommentators = false, commentatorError = "Không tải được danh sách BLV. Chọn Thử lại để cập nhật nguồn phát.")
            }
        }
    }

    fun closeMatch() {
        detailJob?.cancel()
        mutableState.value = mutableState.value.copy(selectedMatch = null, commentators = emptyList(), loadingCommentators = false, commentatorError = null)
    }
}
