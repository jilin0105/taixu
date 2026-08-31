package top.wkbin.taixu.core.common.util

import android.content.ClipboardManager
import android.content.Context

/**
 * 健壮读取剪贴板文本：遍历全部 ClipData.Item，用 coerceToText 统一处理
 * 纯文本 / content URI / Intent，避免只取首个 item 的 text 导致内容缺失或被裁切。
 */
fun Context.readClipboardText(): String {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return ""
    val clip = clipboard.primaryClip ?: return ""
    return buildString {
        for (i in 0 until clip.itemCount) {
            val itemText = runCatching {
                clip.getItemAt(i).coerceToText(this@readClipboardText)?.toString()
            }.getOrNull().orEmpty()
            if (itemText.isNotBlank()) {
                if (isNotEmpty()) append('\n')
                append(itemText)
            }
        }
    }.trim()
}
