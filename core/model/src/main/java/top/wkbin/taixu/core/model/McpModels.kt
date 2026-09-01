package top.wkbin.taixu.core.model

import kotlinx.serialization.Serializable

/**
 * MCP (Model Context Protocol) 传输协议类型
 */
@Serializable
enum class McpTransportType {
    STDIO,  // 在 Linux 沙箱内通过标准输入输出与进程交互
    SSE,    // 远程 HTTP 服务：连接时自动协商 Streamable HTTP 与 legacy HTTP+SSE 两种协议
}

/**
 * MCP 服务配置
 */
@Serializable
data class McpServerConfig(
    val id: String,
    val name: String,
    val description: String = "",
    val transportType: McpTransportType = McpTransportType.STDIO,
    /** 沙箱内执行命令（例如 "npx" 或 "python3"） */
    val command: String = "",
    /** 命令行参数列表（例如 ["-y", "@modelcontextprotocol/server-sqlite", "--db-path", "/workspace/app.db"]） */
    val args: List<String> = emptyList(),
    /** 环境变量 */
    val env: Map<String, String> = emptyMap(),
    /** 远程 MCP 服务的 HTTP 端点 URL（例如 http://127.0.0.1:8000/mcp 或 .../sse） */
    val serverUrl: String = "",
    /** 是否启用 */
    val isEnabled: Boolean = true,
    /** 是否为内置预设服务 */
    val isBuiltin: Boolean = false,
) {
    /** 导出为 mcpServers JSON 配置片段（键为服务 id）；配置格式约定归模型层，View 只读展示 */
    fun toExportJsonConfig(): String = buildString {
        appendLine("{")
        appendLine("  \"$id\": {")
        if (transportType == McpTransportType.STDIO) {
            appendLine("    \"command\": \"$command\",")
            appendLine("    \"args\": [${args.joinToString(", ") { "\"$it\"" }}]")
            if (env.isNotEmpty()) {
                appendLine("    \"env\": {")
                env.entries.forEachIndexed { i, (k, v) ->
                    val comma = if (i == env.size - 1) "" else ","
                    appendLine("      \"$k\": \"$v\"$comma")
                }
                appendLine("    }")
            }
        } else {
            appendLine("    \"url\": \"$serverUrl\"")
        }
        appendLine("  }")
        append("}")
    }
}

/**
 * MCP 服务连通性检测状态（运行时状态，不持久化）
 */
enum class McpConnectionState {
    /** 尚未检测（默认态 / 服务未启用） */
    UNKNOWN,
    /** 正在检测连通性 */
    CHECKING,
    /** 已连通，服务在线可用 */
    ONLINE,
    /** 检测失败 / 服务离线不可达 */
    OFFLINE,
}

/**
 * 动态从 MCP Server 发现并注册的工具定义
 */
@Serializable
data class McpToolInfo(
    val serverId: String,
    val serverName: String,
    val name: String,
    val description: String,
    val parametersJson: String = "{}",
)

/**
 * 子智能体任务规格参数
 */
@Serializable
data class SubagentTaskSpec(
    val taskName: String,
    /** Optional exact profile id/name override retained for persisted and legacy calls. */
    val role: String = "",
    val prompt: String,
    /** Built-in department id used to constrain local catalog matching. */
    val department: String = "",
    /** Short English professional keywords matched against id/name/description locally. */
    val agentQuery: String = "",
)

/**
 * 内置 MCP 预设服务
 */
object BuiltinMcpPresets {
    val presets: List<McpServerConfig> = listOf(
        McpServerConfig(
            id = "mcp_sqlite",
            name = "SQLite 数据库探索器",
            description = "查询、分析与操作沙箱或工作区内的 SQLite 数据库文件",
            transportType = McpTransportType.STDIO,
            command = "python3",
            args = listOf("-u", "/opt/taixu/scripts/sqlite_mcp_server.py", "--db-path", "/root/taixu.db"),
            isEnabled = false,
            isBuiltin = true,
        ),
        McpServerConfig(
            id = "mcp_git",
            name = "Git 仓库协同中心",
            description = "深入分析 Git 历史提交、分支拓扑、Diff 差异与工作区状态",
            transportType = McpTransportType.STDIO,
            command = "python3",
            args = listOf("-u", "/opt/taixu/scripts/git_mcp_server.py", "--repository", "/workspace"),
            isEnabled = false,
            isBuiltin = true,
        ),
        McpServerConfig(
            id = "mcp_apktool",
            name = "Android 逆向与 APK 审计",
            description = "自动化反编译 APK、解析清单权限、提取硬编码凭据、Smali 敏感代码检索与重打包签名（太墟内置 MCP 服务，依赖 android-suite 的 apktool/jadx/aapt 工具链）",
            transportType = McpTransportType.STDIO,
            command = "python3",
            args = listOf("-u", "/opt/taixu/scripts/apktool_mcp_server.py"),
            isEnabled = false,
            isBuiltin = true,
        ),
        McpServerConfig(
            id = "mcp_codegraph",
            name = "CodeGraph 代码知识图谱",
            description = "毫秒级索引工作区符号定义、调用链与影响面（支持 Python/Java/Kotlin/C/C++/JS/TS/Smali），大幅减少代码探索的 Token 与工具调用轮次",
            transportType = McpTransportType.STDIO,
            command = "python3",
            args = listOf("-u", "/opt/taixu/scripts/codegraph_mcp_server.py", "--repository", "/workspace"),
            isEnabled = false,
            isBuiltin = true,
        ),
        McpServerConfig(
            id = "mcp_websearch",
            name = "Web 搜索（Open-WebSearch）",
            description = "免 API Key 的多引擎网络搜索与网页正文抓取（太墟内置零依赖 MCP 服务）：支持 Baidu、Bing、DuckDuckGo 等多引擎直连搜索与正文清洗提取，毫秒级响应，无需 Node.js/npx，开箱即用",
            transportType = McpTransportType.STDIO,
            command = "python3",
            args = listOf("-u", "/opt/taixu/scripts/websearch_mcp_server.py"),
            env = mapOf(
                "DEFAULT_SEARCH_ENGINE" to "baidu",
            ),
            isEnabled = false,
            isBuiltin = true,
        ),
    )
}
