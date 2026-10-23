package dev.cttv

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.cttv.data.Channel
import dev.cttv.data.ChannelRepository
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CatalogState(
    val channels: List<Channel> = emptyList(),
    val refreshing: Boolean = true,
    val message: String? = null,
)

class CatalogViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ChannelRepository(File(application.filesDir, "channels-vn.m3u"))
    private val mutableState = MutableStateFlow(CatalogState())
    val state = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            val cached = repository.cached()
            mutableState.value = CatalogState(channels = cached, refreshing = false)
            refresh()
        }
    }

    fun refresh() {
        if (mutableState.value.refreshing) return
        mutableState.value = mutableState.value.copy(refreshing = true, message = null)
        viewModelScope.launch {
            try {
                mutableState.value = CatalogState(repository.refresh(), refreshing = false)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                val hasCache = mutableState.value.channels.isNotEmpty()
                mutableState.value = mutableState.value.copy(
                    refreshing = false,
                    message = if (hasCache) "Chưa cập nhật được. Đang dùng danh sách đã lưu."
                        else "Không tải được danh sách kênh. Kiểm tra kết nối mạng rồi thử lại.",
                )
            }
        }
    }
}
