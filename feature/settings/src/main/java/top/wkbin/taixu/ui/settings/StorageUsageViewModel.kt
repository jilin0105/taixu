package top.wkbin.taixu.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.wkbin.taixu.runtime.StorageManager
import top.wkbin.taixu.runtime.StorageUsage

@HiltViewModel
class StorageUsageViewModel @Inject constructor(
    private val storageManager: StorageManager,
) : ViewModel() {
    private val _usage = MutableStateFlow<StorageUsage?>(null)
    val usage: StateFlow<StorageUsage?> = _usage.asStateFlow()
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** 当前 message 是否为失败结果（类型化标记，避免 UI 用字符串匹配判断样式）。 */
    private val _messageIsError = MutableStateFlow(false)
    val messageIsError: StateFlow<Boolean> = _messageIsError.asStateFlow()

    init { refresh() }

    fun refresh() {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            runCatching { storageManager.inspect() }
                .onSuccess { _usage.value = it }
                .onFailure {
                    android.util.Log.e("StorageUsage", "Inspect storage failed: ${it.message}", it)
                    _messageIsError.value = true
                    _message.value = "读取存储占用失败，请点击刷新重试"
                }
            _refreshing.value = false
        }
    }

    fun clearCache() {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            val result = storageManager.clearCache()
            _messageIsError.value = result.isFailure
            _message.value = if (result.isSuccess) {
                "下载缓存已清理。"
            } else {
                android.util.Log.e("StorageUsage", "Clear cache failed: ${result.errorOrNull()?.message}")
                "清理缓存失败，请稍后重试"
            }
            _usage.value = runCatching { storageManager.inspect() }.getOrNull() ?: _usage.value
            _refreshing.value = false
        }
    }

    fun dismissMessage() {
        _message.value = null
        _messageIsError.value = false
    }
}
