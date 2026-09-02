package top.wkbin.taixu.ui.browser

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.view.ViewGroup
import top.wkbin.taixu.ui.components.RuntimeCard
import top.wkbin.taixu.ui.components.RuntimeTopBar
import top.wkbin.taixu.ui.browser.snapshot.SnapshotSheet

@Composable
fun BrowserScreen(
    onBack: () -> Unit,
    viewModel: BrowserViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.ensureActiveTab() }
    Column(modifier = Modifier.fillMaxSize()) {
        RuntimeTopBar(
            title = "内置浏览器",
            statusText = state.title.takeIf { it.isNotBlank() } ?: state.url.takeIf { it.isNotBlank() } ?: "—",
            onBack = onBack,
        )
        Box(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            RuntimeCard(modifier = Modifier.fillMaxWidth()) {
                BrowserTopBar(
                    urlInput = state.urlInput,
                    onUrlChange = viewModel::onUrlInputChanged,
                    onNavigate = viewModel::onNavigate,
                    onBack = viewModel::onBack,
                    onForward = viewModel::onForward,
                    onRefresh = viewModel::onRefresh,
                    onShowSnapshot = viewModel::onOpenSnapshot,
                    coBrowsingEnabled = state.coBrowsingEnabled,
                    onCoBrowsingChange = viewModel::onCoBrowsingToggle,
                )
            }
        }
        BrowserTabBar(
            activeTabId = state.activeTab?.tabId ?: "",
            onSelect = { },
            onClose = { },
        )
        BrowserCoBrowsingPill(
            enabled = state.coBrowsingEnabled,
            author = if (state.coBrowsingEnabled) "AI 已接管" else "你正在接管",
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        )
        Box(modifier = Modifier.fillMaxSize()) {
            state.activeTab?.let { tab ->
                key(tab.tabId) {
                    viewModel.activeWebView()?.let { webView ->
                        AndroidView(
                            factory = {
                                (webView.parent as? ViewGroup)?.removeView(webView)
                                webView
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
            state.snapshotSheet?.let { sheet ->
                SnapshotSheet(
                    state = sheet,
                    onDismiss = viewModel::dismissSnapshot,
                )
            }
        }
    }
}
