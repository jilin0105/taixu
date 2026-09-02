package top.wkbin.taixu.ui.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp

@Composable
fun BrowserTopBar(
    urlInput: String,
    onUrlChange: (String) -> Unit,
    onNavigate: () -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onRefresh: () -> Unit,
    onShowSnapshot: () -> Unit,
    coBrowsingEnabled: Boolean,
    onCoBrowsingChange: (Boolean) -> Unit,
) {
    var input by remember(urlInput) { mutableStateOf(urlInput) }
    Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it; onUrlChange(it) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onNavigate() }),
                placeholder = { Text("about:blank 或 https://...", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onNavigate) { Icon(Icons.Default.OpenInBrowser, contentDescription = "打开") }
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "后退") }
                IconButton(onClick = onForward) { Icon(Icons.Default.ArrowForward, contentDescription = "前进") }
                IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, contentDescription = "刷新") }
                IconButton(onClick = onShowSnapshot) { Icon(Icons.Default.Visibility, contentDescription = "snapshot") }
            }
        }
    }
}
