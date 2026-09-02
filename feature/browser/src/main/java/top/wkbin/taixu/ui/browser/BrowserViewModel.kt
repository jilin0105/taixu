package top.wkbin.taixu.ui.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.webkit.WebView
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import top.wkbin.taixu.core.browser.BrowserDescriptor
import top.wkbin.taixu.core.browser.PageSnapshot
import top.wkbin.taixu.runtime.browser.BrowserEventBus
import top.wkbin.taixu.runtime.browser.BrowserRegistry
import top.wkbin.taixu.runtime.browser.BrowserSessionToken
import top.wkbin.taixu.runtime.browser.InAppBrowserViewProvider
import top.wkbin.taixu.ui.browser.snapshot.SnapshotSheetState

@HiltViewModel
class BrowserViewModel @Inject constructor(
    val registry: BrowserRegistry,
    val eventBus: BrowserEventBus,
) : ViewModel() {

    private val _urlInput = MutableStateFlow("")
    private val _coBrowsingEnabled = MutableStateFlow(true)
    private val _toolMessage = MutableStateFlow<String?>(null)
    private val _snapshotSheet = MutableStateFlow<SnapshotSheetState?>(null)

    private val coreState = combine(
        eventBus.snapshot,
        eventBus.url,
        eventBus.title,
        registry.descriptors,
        eventBus.activeTab,
    ) { snapEvent, url, title, descriptors, activeTab ->
        BrowserCoreState(snapEvent?.snapshot, url, title, descriptors, activeTab)
    }

    val uiState: StateFlow<BrowserUiState> = combine(
        coreState,
        _urlInput,
        _coBrowsingEnabled,
        _toolMessage,
        _snapshotSheet,
    ) { core, urlInput, coBrowsing, toolMessage, snapshotSheet ->
        BrowserUiState(
            url = core.url,
            urlInput = urlInput,
            title = core.title,
            descriptors = core.descriptors,
            lastSnapshot = core.snapshot,
            coBrowsingEnabled = coBrowsing,
            toolMessage = toolMessage,
            snapshotSheet = snapshotSheet,
            activeTab = core.activeTab,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, BrowserUiState.Empty)

    init {
        viewModelScope.launch {
            eventBus.url.collect { loadedUrl ->
                if (loadedUrl.isNotBlank()) _urlInput.value = loadedUrl
            }
        }
    }

    fun onUrlInputChanged(value: String) { _urlInput.value = value }

    fun onNavigate() {
        val text = normalizeUrl(_urlInput.value)
        if (text.isBlank()) return
        viewModelScope.launch {
            runCatching {
                val engine = registry.getDefault()
                val active = engine.activeTab()
                if (active == null) engine.openTab(text, activate = true)
                else engine.navigate(active, text)
                _urlInput.value = text
                _toolMessage.value = "已打开 $text"
            }.onFailure { _toolMessage.value = it.message ?: "导航失败" }
        }
    }

    fun onBack() = viewModelScope.launch {
        val engine = runCatching { registry.getDefault() }.getOrNull() ?: return@launch
        engine.activeTab()?.let { engine.back(it) }
    }
    fun onForward() = viewModelScope.launch {
        val engine = runCatching { registry.getDefault() }.getOrNull() ?: return@launch
        engine.activeTab()?.let { engine.forward(it) }
    }
    fun onRefresh() = viewModelScope.launch {
        val engine = runCatching { registry.getDefault() }.getOrNull() ?: return@launch
        engine.activeTab()?.let { engine.refresh(it) }
    }
    fun onCoBrowsingToggle(enabled: Boolean) { _coBrowsingEnabled.value = enabled }

    fun onOpenSnapshot() {
        val s = eventBus.snapshot.value?.snapshot
        if (s != null) _snapshotSheet.value = SnapshotSheetState(url = s.url, title = s.title, snapshot = s)
    }
    fun dismissSnapshot() { _snapshotSheet.value = null }
    fun dismissToolMessage() { _toolMessage.value = null }

    fun ensureActiveTab() = viewModelScope.launch {
        runCatching {
            val engine = registry.getDefault()
            if (engine.activeTab() == null) engine.openTab("about:blank", activate = true)
        }.onFailure { _toolMessage.value = it.message ?: "浏览器引擎尚未就绪" }
    }

    fun activeWebView(): WebView? =
        (runCatching { registry.getDefault() }.getOrNull() as? InAppBrowserViewProvider)?.activeWebView()

    private fun normalizeUrl(raw: String): String {
        val value = raw.trim()
        if (value.isBlank() || value.startsWith("about:") || "://" in value) return value
        return "https://$value"
    }
}

private data class BrowserCoreState(
    val snapshot: PageSnapshot?,
    val url: String,
    val title: String,
    val descriptors: List<BrowserDescriptor>,
    val activeTab: BrowserSessionToken?,
)

data class BrowserUiState(
    val url: String,
    val urlInput: String,
    val title: String,
    val descriptors: List<BrowserDescriptor>,
    val lastSnapshot: PageSnapshot?,
    val coBrowsingEnabled: Boolean,
    val toolMessage: String?,
    val snapshotSheet: SnapshotSheetState?,
    val activeTab: BrowserSessionToken?,
) {
    companion object {
        val Empty = BrowserUiState(
            url = "",
            urlInput = "",
            title = "",
            descriptors = emptyList(),
            lastSnapshot = null,
            coBrowsingEnabled = true,
            toolMessage = null,
            snapshotSheet = null,
            activeTab = null,
        )
    }
}
