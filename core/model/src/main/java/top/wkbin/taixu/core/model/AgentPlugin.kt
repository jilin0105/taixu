package top.wkbin.taixu.core.model

import kotlinx.serialization.Serializable

@Serializable
data class AgentPlugin(
    val id: String,
    val name: String,
    val description: String,
    val version: String = "1.0.0",
    val author: String = "TaiXu Core Team",
    val permissions: List<String> = emptyList(),
    val iconName: String = "Package",
    val isEnabled: Boolean = true,
    val isBuiltin: Boolean = true,
)

object BuiltinPlugins {
    val presets: List<AgentPlugin> = listOf(
        AgentPlugin(
            id = "proot_health_probe",
            name = "PRoot 沙箱诊断探针",
            description = "实时探测 Linux PRoot 虚拟文件系统、进程与内存状态，在异常时自动辅助生成诊断建议",
            version = "1.0.0",
            author = "TaiXu System",
            permissions = listOf("执行系统状态探针", "收集 PRoot 诊断指标"),
            iconName = "Terminal",
            isEnabled = true,
            isBuiltin = true,
        ),
        AgentPlugin(
            id = "destructive_cmd_interceptor",
            name = "高危命令审查拦截器",
            description = "对 rm -rf /、mkfs、dd 等破坏性命令进行前置安全校验与参数修正，防止误伤沙箱或工作区",
            version = "1.1.0",
            author = "Security Lab",
            permissions = listOf("命令安全审查", "拦截高危操作"),
            iconName = "Alert",
            isEnabled = true,
            isBuiltin = true,
        ),
        AgentPlugin(
            id = "web_search_augment",
            name = "Web 检索与文档索引增强",
            description = "为 Agent 补充外部开发者知识库、Debian 软件包文档与常见报错解决库索引",
            version = "0.9.0",
            author = "Community",
            permissions = listOf("网络请求", "知识库索引"),
            iconName = "Globe",
            isEnabled = false,
            isBuiltin = true,
        ),
    )
}
