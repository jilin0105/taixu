package top.wkbin.taixu.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.AttachFile
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
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

enum class RuntimeIconName {
    Home, Workspace, Terminal, Settings, Back, ChevronRight, ChevronDown, Package,
    Refresh, Shield, Storage, Globe, Trash, Close, Check, Alert, Logs,
    Download, Play, Stop, More, Plus, Chat, List, Copy,
    Folder, File, Code, Edit, Save, ArrowUp, Cpu, Search, Info,
    Image, Attach,
}

/** Official Material vector icons, shared by every screen for consistent optical weight. */
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
}
