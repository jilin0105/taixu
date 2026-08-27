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

    init { refresh() }

    fun refresh() {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            runCatching { storageManager.inspect() }
                .onSuccess { _usage.value = it }
                .onFailure { _message.value = "读取存储占用失败：${it.message}" }
            _refreshing.value = false
        }
    }

    fun clearCache() {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            val result = storageManager.clearCache()
            _message.value = if (result.isSuccess) "下载缓存已清理。" else "清理缓存失败：${result.errorOrNull()?.message}"
            _usage.value = runCatching { storageManager.inspect() }.getOrNull() ?: _usage.value
            _refreshing.value = false
        }
    }

    fun dismissMessage() { _message.value = null }
}
