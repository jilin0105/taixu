package top.wkbin.taixu.core.model

import kotlinx.serialization.Serializable

/**
 * MCP (Model Context Protocol) 传输协议类型
 */
@Serializable
enum class McpTransportType {
    STDIO,  // 在 Linux 沙箱内通过标准输入输出与进程交互
    SSE,    // 通过 HTTP / SSE 连接本地或远程 MCP 服务器
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
    /** SSE 服务的 HTTP 端点 URL（例如 http://127.0.0.1:8000/sse） */
    val serverUrl: String = "",
    /** 是否启用 */
    val isEnabled: Boolean = true,
    /** 是否为内置预设服务 */
    val isBuiltin: Boolean = false,
)

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
    val role: String,
    val prompt: String,
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
            command = "npx",
            args = listOf("-y", "@modelcontextprotocol/server-sqlite", "--db-path", "/root/taixu.db"),
            isEnabled = false,
            isBuiltin = true,
        ),
        McpServerConfig(
            id = "mcp_fetch",
            name = "Web 内容抓取与解析",
            description = "安全抓取外部网页、Markdown 文档并提取核心正文数据",
            transportType = McpTransportType.STDIO,
            command = "npx",
            args = listOf("-y", "@modelcontextprotocol/server-fetch"),
            isEnabled = false,
            isBuiltin = true,
        ),
        McpServerConfig(
            id = "mcp_git",
            name = "Git 仓库协同中心",
            description = "深入分析 Git 历史提交、分支拓扑、Diff 差异与工作区状态",
            transportType = McpTransportType.STDIO,
            command = "npx",
            args = listOf("-y", "@modelcontextprotocol/server-git", "--repository", "/workspace"),
            isEnabled = false,
            isBuiltin = true,
        ),
        McpServerConfig(
            id = "mcp_apktool",
            name = "Android 逆向与 APK 审计",
            description = "自动化反编译 APK、解析清单权限、提取硬编码凭据、Smali 敏感代码检索与重打包签名",
            transportType = McpTransportType.STDIO,
            command = "npx",
            args = listOf("-y", "apktool-mcp-server"),
            isEnabled = false,
            isBuiltin = true,
        ),
    )
}
