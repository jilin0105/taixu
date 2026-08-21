package top.wkbin.taixu.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderShared
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

enum class RuntimeIconName {
    Home, Workspace, Terminal, Settings, Back, ChevronRight, ChevronDown, Package,
    Refresh, Shield, Storage, Globe, Trash, Close, Check, Alert, Logs,
    Download, Play, Stop, More, Plus, Chat, List, Copy,
    Folder, File, Code, Edit, Save, ArrowUp, Cpu, Search, Info,
    Image, Attach,
    // 语义增强新图标
    Linux, Debian, Ubuntu, Arch, Kali,
    Github, Qq,
    Bot, Palette, FontSize, Battery, Bug, Update, Extension, Hub, Mount, OpenInNew, Key, Tune,
}

/** Official Material & Customized Brand vector icons, shared by every screen for consistent optical weight. */
@Composable
fun RuntimeIcon(
    name: RuntimeIconName,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
) {
    Icon(
        imageVector = name.materialVector(),
        contentDescription = null,
        modifier = modifier,
        tint = if (tint == Color.Unspecified) LocalContentColor.current else tint,
    )
}

/**
 * 根据发行版标识返回专有 Linux 发行版 Logo
 */
fun distroIconFor(distroId: String): RuntimeIconName = when (distroId.lowercase()) {
    "debian" -> RuntimeIconName.Debian
    "ubuntu" -> RuntimeIconName.Ubuntu
    "arch", "archlinux" -> RuntimeIconName.Arch
    "kali" -> RuntimeIconName.Kali
    else -> RuntimeIconName.Linux
}

private fun RuntimeIconName.materialVector(): ImageVector = when (this) {
    RuntimeIconName.Home -> Icons.Outlined.Home
    RuntimeIconName.Workspace -> Icons.Outlined.Dashboard
    RuntimeIconName.Terminal -> Icons.Outlined.Terminal
    RuntimeIconName.Settings -> Icons.Outlined.Settings
    RuntimeIconName.Back -> Icons.AutoMirrored.Outlined.ArrowBack
    RuntimeIconName.ChevronRight -> Icons.Outlined.ChevronRight
    RuntimeIconName.ChevronDown -> Icons.Outlined.ExpandMore
    RuntimeIconName.Package -> Icons.Outlined.Inventory2
    RuntimeIconName.Refresh -> Icons.Outlined.Refresh
    RuntimeIconName.Shield -> Icons.Outlined.Security
    RuntimeIconName.Storage -> Icons.Outlined.Storage
    RuntimeIconName.Globe -> Icons.Outlined.Language
    RuntimeIconName.Trash -> Icons.Outlined.DeleteOutline
    RuntimeIconName.Close -> Icons.Outlined.Close
    RuntimeIconName.Check -> Icons.Outlined.Check
    RuntimeIconName.Alert -> Icons.Outlined.WarningAmber
    RuntimeIconName.Logs -> Icons.AutoMirrored.Outlined.ReceiptLong
    RuntimeIconName.Download -> Icons.Outlined.Download
    RuntimeIconName.Play -> Icons.Outlined.PlayArrow
    RuntimeIconName.Stop -> Icons.Outlined.Stop
    RuntimeIconName.More -> Icons.Outlined.MoreHoriz
    RuntimeIconName.Plus -> Icons.Outlined.Add
    RuntimeIconName.Chat -> Icons.Outlined.ChatBubbleOutline
    RuntimeIconName.List -> Icons.AutoMirrored.Outlined.List
    RuntimeIconName.Copy -> Icons.Outlined.ContentCopy
    RuntimeIconName.Folder -> Icons.Outlined.Folder
    RuntimeIconName.File -> Icons.AutoMirrored.Outlined.InsertDriveFile
    RuntimeIconName.Code -> Icons.Outlined.Code
    RuntimeIconName.Edit -> Icons.Outlined.Edit
    RuntimeIconName.Save -> Icons.Outlined.Save
    RuntimeIconName.ArrowUp -> Icons.Outlined.ArrowUpward
    RuntimeIconName.Cpu -> Icons.Outlined.Memory
    RuntimeIconName.Search -> Icons.Outlined.Search
    RuntimeIconName.Info -> Icons.Outlined.Info
    RuntimeIconName.Image -> Icons.Outlined.Image
    RuntimeIconName.Attach -> Icons.Outlined.AttachFile
    // 语义增强新图标
    RuntimeIconName.Bot -> Icons.Outlined.AutoAwesome
    RuntimeIconName.Palette -> Icons.Outlined.Palette
    RuntimeIconName.FontSize -> Icons.Outlined.FormatSize
    RuntimeIconName.Battery -> Icons.Outlined.BatteryChargingFull
    RuntimeIconName.Bug -> Icons.Outlined.BugReport
    RuntimeIconName.Update -> Icons.Outlined.SystemUpdate
    RuntimeIconName.Extension -> Icons.Outlined.Extension
    RuntimeIconName.Hub -> Icons.Outlined.Hub
    RuntimeIconName.Mount -> Icons.Outlined.FolderShared
    RuntimeIconName.OpenInNew -> Icons.AutoMirrored.Outlined.OpenInNew
    RuntimeIconName.Key -> Icons.Outlined.Key
    RuntimeIconName.Tune -> Icons.Outlined.Tune
    // 专有品牌矢量
    RuntimeIconName.Linux -> LinuxVector
    RuntimeIconName.Debian -> DebianVector
    RuntimeIconName.Ubuntu -> UbuntuVector
    RuntimeIconName.Arch -> ArchVector
    RuntimeIconName.Kali -> KaliVector
    RuntimeIconName.Github -> GithubVector
    RuntimeIconName.Qq -> QqVector
}

/**
 * 🐧 Linux Tux 企鹅官方矢量图标 (24x24)
 */
private val LinuxVector: ImageVector by lazy {
    ImageVector.Builder(
        name = "LinuxTux",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        // Tux 身体与头部轮廓
        path(fill = SolidColor(Color.White)) {
            addPath(PathParser().parsePathString("M12 2C9.5 2 8 3.8 8 6.5c0 1.2.4 2.3 1 3.1-.6 1.4-2 3.8-2 6.4 0 3.2 2.2 5 5 5s5-1.8 5-5c0-2.6-1.4-5-2-6.4.6-.8 1-1.9 1-3.1C16 3.8 14.5 2 12 2z").toNodes())
        }
        // 眼睛与肚皮剪影
        path(fill = SolidColor(Color.White)) {
            addPath(PathParser().parsePathString("M12 11c-2.2 0-3.5 1.8-3.5 4.5 0 2.5 1.5 4 3.5 4s3.5-1.5 3.5-4c0-2.7-1.3-4.5-3.5-4.5zm-1.8-4c-.4 0-.8-.4-.8-1s.4-1 .8-1 .8.4.8 1-.4 1-.8 1zm3.6 0c-.4 0-.8-.4-.8-1s.4-1 .8-1 .8.4.8 1-.4 1-.8 1z").toNodes())
        }
        // 喙部与脚蹼
        path(fill = SolidColor(Color(0xFFFFA000))) {
            addPath(PathParser().parsePathString("M10.8 7.8c0-.2.5-.5 1.2-.5s1.2.3 1.2.5c0 .6-.8 1.2-1.2 1.2s-1.2-.6-1.2-1.2zm-4.3 12.7c-.8.3-1.5.8-1.5 1.3 0 .7 1.3 1.2 3 1.2 1.2 0 2.2-.3 2.7-.7l-1.2-.8c-.8-.2-2-.6-3-1zm11 0c-1 .4-2.2.8-3 1l-1.2.8c.5.4 1.5.7 2.7.7 1.7 0 3-.5 3-1.2 0-.5-.7-1-1.5-1.3z").toNodes())
        }
    }.build()
}

/**
 * 🍥 Debian 经典红色漩涡矢量图标 (24x24)
 */
private val DebianVector: ImageVector by lazy {
    ImageVector.Builder(
        name = "DebianSwirl",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color(0xFFD70A53))) {
            addPath(PathParser().parsePathString("M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1.2 4.2c2.8.2 4.6 2 4.6 4.3 0 2.6-2.2 4.7-4.8 4.7-2.3 0-4.2-1.7-4.2-3.8 0-1.8 1.5-3.2 3.3-3.2 1.4 0 2.4 1 2.4 2.2 0 .8-.7 1.5-1.5 1.5-.5 0-.9-.3-.9-.8 0-.3.2-.5.5-.5.1 0 .2.1.2.2 0 .1-.1.2-.2.2-.1 0-.1 0-.1-.1 0-.2.3-.4.6-.4.5 0 .8.4.8.9 0 .8-.7 1.4-1.5 1.4-1.1 0-1.9-.8-1.9-1.8 0-1.3 1.2-2.4 2.6-2.4 1.7 0 3.1 1.3 3.1 2.9 0 2-1.8 3.7-3.9 3.7-2.4 0-4.4-1.8-4.4-4.2 0-2.6 2.3-4.8 5.2-4.8 3.2 0 5.8 2.4 5.8 5.4 0 3.4-3 6.2-6.6 6.2-3.8 0-7-2.9-7-6.5 0-4.1 3.5-7.4 7.8-7.4.5 0 1 .1 1.5.2l-.4.9z").toNodes())
        }
    }.build()
}

/**
 * ⭕ Ubuntu 经典友谊之圈矢量图标 (24x24)
 */
private val UbuntuVector: ImageVector by lazy {
    ImageVector.Builder(
        name = "UbuntuLogo",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color(0xFFE95420))) {
            addPath(PathParser().parsePathString("M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-5 5.5c.83 0 1.5.67 1.5 1.5s-.67 1.5-1.5 1.5S5.5 9.83 5.5 9s.67-1.5 1.5-1.5zm10 0c.83 0 1.5.67 1.5 1.5s-.67 1.5-1.5 1.5-1.5-.67-1.5-1.5.67-1.5 1.5-1.5zM12 17.5c.83 0 1.5.67 1.5 1.5s-.67 1.5-1.5 1.5-1.5-.67-1.5-1.5.67-1.5 1.5-1.5zm-4.3-3.2c.4.7.9 1.3 1.6 1.7l-.8 1.4c-1-.6-1.8-1.5-2.3-2.5l1.5-.6zm8.6 0l1.5.6c-.5 1-1.3 1.9-2.3 2.5l-.8-1.4c.7-.4 1.2-1 1.6-1.7zm-7.6-4.6l-1.5-.6c.5-1 1.3-1.9 2.3-2.5l.8 1.4c-.7.4-1.2 1-1.6 1.7zm6.6 0c-.4-.7-.9-1.3-1.6-1.7l.8-1.4c1 .6 1.8 1.5 2.3 2.5l-1.5.6z").toNodes())
        }
    }.build()
}

/**
 * ⛰️ Arch Linux 拱门矢量图标 (24x24)
 */
private val ArchVector: ImageVector by lazy {
    ImageVector.Builder(
        name = "ArchLogo",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color(0xFF1793D1))) {
            addPath(PathParser().parsePathString("M12 2.5L2 21.5h4.2l2.3-4.5c.8.3 1.7.5 2.5.5h2c.8 0 1.7-.2 2.5-.5l2.3 4.5H22L12 2.5zm0 5.8l2.6 5.2c-.8.3-1.7.5-2.6.5s-1.8-.2-2.6-.5L12 8.3z").toNodes())
        }
    }.build()
}

/**
 * 🐉 Kali Linux 飞龙/盾牌矢量图标 (24x24)
 */
private val KaliVector: ImageVector by lazy {
    ImageVector.Builder(
        name = "KaliLogo",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color(0xFF557C94))) {
            addPath(PathParser().parsePathString("M12 2L3 5v6c0 5.55 3.84 10.74 9 12 5.16-1.26 9-6.45 9-12V5l-9-3zm0 4c1.8 0 3.2 1.4 3.2 3.2 0 1.2-.7 2.3-1.7 2.8v2.5h-3v-2.5c-1-.5-1.7-1.6-1.7-2.8 0-1.8 1.4-3.2 3.2-3.2z").toNodes())
        }
    }.build()
}

/**
 * 🐙 GitHub 官方 Octocat 矢量剪影 (24x24)
 */
private val GithubVector: ImageVector by lazy {
    ImageVector.Builder(
        name = "GithubLogo",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.White)) {
            addPath(PathParser().parsePathString("M12 2C6.477 2 2 6.484 2 12.017c0 4.425 2.865 8.18 6.839 9.504.5.092.682-.217.682-.483 0-.237-.008-.868-.013-1.703-2.782.605-3.369-1.343-3.369-1.343-.454-1.158-1.11-1.466-1.11-1.466-.908-.62.069-.608.069-.608 1.003.07 1.53 1.032 1.53 1.032.892 1.53 2.341 1.088 2.91.832.092-.647.35-1.088.636-1.338-2.22-.253-4.555-1.113-4.555-4.951 0-1.093.39-1.988 1.029-2.688-.103-.253-.446-1.272.098-2.65 0 0 .84-.27 2.75 1.026A9.564 9.564 0 0112 6.844c.85.004 1.705.115 2.504.337 1.909-1.296 2.747-1.027 2.747-1.027.546 1.379.202 2.398.1 2.651.64.7 1.028 1.595 1.028 2.688 0 3.848-2.339 4.695-4.566 4.943.359.309.678.92.678 1.855 0 1.338-.012 2.419-.012 2.747 0 .268.18.58.688.482A10.019 10.019 0 0022 12.017C22 6.484 17.522 2 12 2z").toNodes())
        }
    }.build()
}

/**
 * 🐧 QQ 企鹅官方剪影矢量图标 (24x24)
 */
private val QqVector: ImageVector by lazy {
    ImageVector.Builder(
        name = "QqLogo",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color(0xFF12B7F5))) {
            addPath(PathParser().parsePathString("M12 2c-4.4 0-8 3.4-8 7.6 0 2.2 1 4.1 2.5 5.5-.3 1.2-1.2 2.8-2.3 3.6 1.5.2 3.2-.3 4.4-1.2 1.1.4 2.2.6 3.4.6 4.4 0 8-3.4 8-7.6S16.4 2 12 2zm-3.2 8.5c-.7 0-1.2-.7-1.2-1.5s.5-1.5 1.2-1.5 1.2.7 1.2 1.5-.5 1.5-1.2 1.5zm6.4 0c-.7 0-1.2-.7-1.2-1.5s.5-1.5 1.2-1.5 1.2.7 1.2 1.5-.5 1.5-1.2 1.5z").toNodes())
        }
    }.build()
}
