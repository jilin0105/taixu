package top.wkbin.taixu.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.wkbin.taixu.core.database.AiModelEntity
import top.wkbin.taixu.core.model.ToolState
import top.wkbin.taixu.ui.components.InfoRow
import top.wkbin.taixu.ui.components.NoticeBanner
import top.wkbin.taixu.ui.components.RuntimeCard
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.RuntimeTopBar
import top.wkbin.taixu.ui.components.StatusBadge

/**
 * 太墟 · 插件配置详情页 (Tool Detail & Configuration Screen)
 *
 * 功能概览：
 * - 工具概览与状态
 * - 网关/服务管理（Web 类工具）
 * - 一键应用模型配置
 * - 自启动开关
 * - 生成带 Token 的访问链接
 * - 工具元信息面板
 * - 操作按钮（自检、终端、卸载）
 */
@Composable
fun ToolDetailScreen(
    toolId: String,
    onBack: () -> Unit,
    onLaunchTerminal: (toolId: String) -> Unit,
    viewModel: ToolDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val tool = state.tool
    val manifest = state.manifest

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            RuntimeTopBar(
                title = tool?.name ?: toolId,
                statusText = "配置详情",
                onBack = onBack,
            )
        },
    ) { innerPadding ->
        if (tool == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text("工具未找到", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ── 错误提示 ──
            state.error?.let { error ->
                NoticeBanner(text = error, isError = true)
            }

            // ── 1. 工具概览卡片 ──
            ToolOverviewCard(tool = tool, manifest = manifest, gatewayRunning = state.gatewayRunning)

            // ── 2. 网关/服务管理（仅 Web 类工具） ──
            if (state.isWebService) {
                GatewayManagementCard(
                    running = state.gatewayRunning,
                    operating = state.gatewayOperating,
                    port = state.servicePort,
                    onStart = viewModel::startGateway,
                    onStop = viewModel::stopGateway,
                )
            }

            // ── 3. 一键应用模型配置 ──
            ModelApplyCard(
                models = state.models,
                appliedModelId = state.appliedModelId,
                applying = state.applyingModel,
                onApply = viewModel::applyModel,
            )

            // ── 4. 自启动配置 ──
            if (state.isWebService) {
                AutoStartCard(
                    enabled = state.autoStartEnabled,
                    toolName = tool.name,
                    onToggle = viewModel::toggleAutoStart,
                )
            }

            // ── 5. 带 Token 访问链接（仅 Web 类工具） ──
            if (state.isWebService) {
                AccessLinkCard(
                    accessUrl = state.accessUrl,
                    hasToken = state.accessToken != null,
                    onGenerate = viewModel::generateToken,
                    onClear = viewModel::clearToken,
                    onCopy = { url ->
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        clipboard?.setPrimaryClip(ClipData.newPlainText("tool_url", url))
                        Toast.makeText(context, "链接已复制", Toast.LENGTH_SHORT).show()
                    },
                )
            }

            // ── 6. 工具元信息面板 ──
            if (manifest != null) {
                ToolMetadataCard(tool = tool, manifest = manifest)
            }

            // ── 7. 操作区 ──
            ToolActionsCard(
                isInstalled = tool.state == ToolState.INSTALLED.name || tool.state == ToolState.UPDATE_AVAILABLE.name,
                onVerify = viewModel::verifyTool,
                onLaunchTerminal = { onLaunchTerminal(toolId) },
                onUninstall = viewModel::uninstallTool,
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ════════════════════════════════════════════════════════════════════════
// 子组件
// ════════════════════════════════════════════════════════════════════════

/**
 * 工具品牌专属视觉 Avatar（复用 ToolCenterScreen 的映射逻辑）
 */
@Composable
private fun DetailToolAvatar(
    toolId: String,
    category: String,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 56.dp,
) {
    val key = toolId.lowercase().trim()
    val (logoRes, emoji, brandColor) = when {
        key.contains("claude") || key.contains("anthropic") ->
            Triple(top.wkbin.taixu.feature.components.R.drawable.ic_provider_anthropic, null, Color(0xFFD97757))
        key.contains("codex") || key.contains("openai") ->
            Triple(top.wkbin.taixu.feature.components.R.drawable.ic_provider_openai, null, Color(0xFF10A37F))
        key.contains("openclaw") ->
            Triple(null, "🦞", Color(0xFFFF4757))
        key.contains("hermes") ->
            Triple(null, "🪽", Color(0xFF8B5CF6))
        key.contains("android") ->
            Triple(null, "🤖", Color(0xFF3DDC84))
        key.contains("devtools") || key.contains("base-devtools") ->
            Triple(null, "⚡", Color(0xFF0284C7))
        key.contains("hello") ->
            Triple(null, "🧪", Color(0xFF10B981))
        else -> when (category) {
            "CODING_AGENT" -> Triple(null, "💻", Color(0xFF6366F1))
            "AI_AGENT" -> Triple(null, "🤖", Color(0xFF0EA5E9))
            else -> Triple(null, "📦", Color(0xFF64748B))
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(15.dp))
            .background(brandColor.copy(alpha = 0.12f))
            .border(1.dp, brandColor.copy(alpha = 0.28f), RoundedCornerShape(15.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (logoRes != null) {
            Box(
                modifier = Modifier
                    .size(size * 0.72f)
                    .clip(CircleShape)
                    .background(brandColor),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(logoRes),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp),
                    contentScale = ContentScale.Fit,
                )
            }
        } else if (emoji != null) {
            Text(text = emoji, fontSize = (size.value * 0.44f).sp)
        } else {
            RuntimeIcon(
                name = RuntimeIconName.Package,
                modifier = Modifier.size(size * 0.48f),
                tint = brandColor,
            )
        }
    }
}

/** 1. 工具概览 */
@Composable
private fun ToolOverviewCard(
    tool: top.wkbin.taixu.core.database.ToolEntity,
    manifest: top.wkbin.taixu.core.model.ToolManifest?,
    gatewayRunning: Boolean,
) {
    val isInstalled = tool.state == ToolState.INSTALLED.name || tool.state == ToolState.UPDATE_AVAILABLE.name
    RuntimeCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f),
            ) {
                DetailToolAvatar(toolId = tool.id, category = tool.category)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = tool.name,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val version = tool.installedVersion ?: tool.manifestVersion
                        if (version.isNotBlank()) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                shape = RoundedCornerShape(6.dp),
                            ) {
                                Text(
                                    text = "v$version",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                    Text(
                        text = "${tool.publisher} · ${
                            when (tool.category) {
                                "CODING_AGENT" -> "编程智能体"
                                "AI_AGENT" -> "AI 智能体"
                                "DEVELOPER_TOOL" -> "开发加速包"
                                else -> tool.category
                            }
                        }",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            StatusBadge(
                text = when {
                    gatewayRunning -> "运行中"
                    tool.state == ToolState.UPDATE_AVAILABLE.name -> "可更新"
                    isInstalled -> "已就绪"
                    tool.state == ToolState.FAILED.name -> "异常"
                    else -> "未安装"
                },
                color = when {
                    gatewayRunning -> Color(0xFF2E7D32)
                    isInstalled -> Color(0xFF2E7D32)
                    tool.state == ToolState.FAILED.name -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.outline
                },
                pulsing = gatewayRunning,
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = tool.description,
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 2. 网关/服务管理 */
@Composable
private fun GatewayManagementCard(
    running: Boolean,
    operating: Boolean,
    port: Int?,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    RuntimeCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RuntimeIcon(
                        name = RuntimeIconName.Globe,
                        modifier = Modifier.size(18.dp),
                        tint = if (running) Color(0xFF2E7D32) else MaterialTheme.colorScheme.outline,
                    )
                    Text(
                        text = "网关服务",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (running) Color(0xFF4CAF50) else MaterialTheme.colorScheme.outline),
                    )
                    Text(
                        text = if (running) "运行中" else "已停止",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (running) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (port != null) {
                        Text(
                            text = "· 端口 $port",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (operating) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
            } else if (running) {
                OutlinedButton(
                    onClick = onStop,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    RuntimeIcon(name = RuntimeIconName.Stop, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("停止", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                }
            } else {
                Button(
                    onClick = onStart,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    RuntimeIcon(name = RuntimeIconName.Play, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("启动", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** 3. 一键应用模型配置 */
@Composable
private fun ModelApplyCard(
    models: List<AiModelEntity>,
    appliedModelId: String?,
    applying: Boolean,
    onApply: (AiModelEntity) -> Unit,
) {
    RuntimeCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RuntimeIcon(
                name = RuntimeIconName.Chat,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "模型配置",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "选择已配置的模型一键应用到插件，工具重启后生效",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (models.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            NoticeBanner(text = "尚未配置任何模型，请前往 设置 → 模型档案管理 添加")
        } else {
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                models.forEach { model ->
                    val isApplied = model.id == appliedModelId
                    val isActive = model.isActive

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = when {
                            isApplied -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            isActive -> MaterialTheme.colorScheme.surfaceContainerHigh
                            else -> MaterialTheme.colorScheme.surfaceContainerLow
                        },
                        border = if (isApplied) {
                            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                        } else null,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        text = model.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (isActive) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                            shape = RoundedCornerShape(4.dp),
                                        ) {
                                            Text(
                                                text = "当前活跃",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                            )
                                        }
                                    }
                                    if (isApplied) {
                                        Surface(
                                            color = Color(0xFF2E7D32).copy(alpha = 0.12f),
                                            shape = RoundedCornerShape(4.dp),
                                        ) {
                                            Text(
                                                text = "已应用",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                                color = Color(0xFF2E7D32),
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = "${model.provider} · ${model.model.ifBlank { "默认模型" }}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            if (applying) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else if (!isApplied) {
                                FilledTonalButton(
                                    onClick = { onApply(model) },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                ) {
                                    Text(
                                        "应用",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    )
                                }
                            } else {
                                RuntimeIcon(
                                    name = RuntimeIconName.Check,
                                    modifier = Modifier.size(20.dp),
                                    tint = Color(0xFF2E7D32),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 4. 自启动配置 */
@Composable
private fun AutoStartCard(
    enabled: Boolean,
    toolName: String,
    onToggle: (Boolean) -> Unit,
) {
    RuntimeCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "随应用自启动",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                )
                Text(
                    text = "Linux 环境就绪后自动启动 $toolName 网关服务",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
            )
        }
    }
}

/** 5. 带 Token 访问链接 */
@Composable
private fun AccessLinkCard(
    accessUrl: String?,
    hasToken: Boolean,
    onGenerate: () -> Unit,
    onClear: () -> Unit,
    onCopy: (String) -> Unit,
) {
    RuntimeCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RuntimeIcon(
                name = RuntimeIconName.Globe,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "访问链接",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "生成带 Token 的安全链接，可用于浏览器访问或外部集成",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(10.dp))

        if (accessUrl != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = accessUrl,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(
                        onClick = { onCopy(accessUrl) },
                        modifier = Modifier.size(32.dp),
                    ) {
                        RuntimeIcon(
                            name = RuntimeIconName.Copy,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                onClick = onGenerate,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = if (hasToken) "重新生成" else "生成链接",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                )
            }
            if (hasToken) {
                TextButton(
                    onClick = onClear,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Text(
                        "清除 Token",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

/** 6. 工具元信息 */
@Composable
private fun ToolMetadataCard(
    tool: top.wkbin.taixu.core.database.ToolEntity,
    manifest: top.wkbin.taixu.core.model.ToolManifest,
) {
    RuntimeCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RuntimeIcon(
                name = RuntimeIconName.Settings,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "工具信息",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            )
        }

        Spacer(Modifier.height(10.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoRow(label = "工具 ID", value = tool.id)
            InfoRow(label = "启动类型", value = when (manifest.launchType) {
                "web" -> "Web 网关服务"
                "pty" -> "交互式终端"
                "one_shot" -> "一次性命令"
                else -> manifest.launchType
            })
            manifest.servicePort?.let { InfoRow(label = "服务端口", value = it.toString()) }
            manifest.launchCommand?.let { InfoRow(label = "启动命令", value = it) }
            manifest.verifyCommand?.let { InfoRow(label = "验证命令", value = it) }
            InfoRow(label = "安装路径", value = "/opt/taixu/tools/${tool.id}")
            InfoRow(label = "数据目录", value = "/opt/taixu/data/${tool.id}")
            InfoRow(label = "安装方式", value = manifest.installMethod)
            if (manifest.dependencies.isNotEmpty()) {
                InfoRow(label = "依赖项", value = manifest.dependencies.joinToString(", "))
            }
            if (manifest.environment.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHighest)
                Text(
                    text = "环境变量",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                manifest.environment.forEach { (key, value) ->
                    InfoRow(label = key, value = value)
                }
            }
            if (tool.permissions.isNotBlank()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHighest)
                Text(
                    text = "权限",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    tool.permissions.split(",").filter { it.isNotBlank() }.forEach { perm ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Text(
                                text = perm.trim(),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 7. 操作区 */
@Composable
private fun ToolActionsCard(
    isInstalled: Boolean,
    onVerify: () -> Unit,
    onLaunchTerminal: () -> Unit,
    onUninstall: () -> Unit,
) {
    RuntimeCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RuntimeIcon(
                name = RuntimeIconName.More,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "操作",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            )
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (isInstalled) {
                FilledTonalButton(
                    onClick = onVerify,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    RuntimeIcon(name = RuntimeIconName.Check, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("自检", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                }

                FilledTonalButton(
                    onClick = onLaunchTerminal,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    RuntimeIcon(name = RuntimeIconName.Terminal, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("终端", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = onUninstall,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    RuntimeIcon(name = RuntimeIconName.Trash, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("卸载", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                }
            } else {
                Text(
                    text = "工具尚未安装，请先在工具中心完成安装",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
