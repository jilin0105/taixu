package top.wkbin.taixu.ui.browser

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.ui.components.RuntimeCard

@Composable
fun BrowserCoBrowsingPill(
    enabled: Boolean,
    author: String,
    modifier: Modifier = Modifier,
) {
    val borderTint = if (enabled) Color(0xFF3F8FFF) else Color(0xFFFF8F3F)
    RuntimeCard(
        modifier = modifier.padding(vertical = 4.dp),
        borderColor = borderTint,
    ) {
        Text(text = if (enabled) "🟢 $author" else "🟠 $author")
    }
}
