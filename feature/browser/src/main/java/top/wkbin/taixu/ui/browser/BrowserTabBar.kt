package top.wkbin.taixu.ui.browser

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.ui.components.RuntimeCard

@Composable
fun BrowserTabBar(
    activeTabId: String,
    onSelect: (String) -> Unit,
    onClose: (String) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        RuntimeCard(modifier = Modifier.fillMaxWidth()) {
            if (activeTabId.isBlank()) {
                Text("尚无 tab — 输入 URL 打开", maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else {
                Text("Tab: $activeTabId", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
