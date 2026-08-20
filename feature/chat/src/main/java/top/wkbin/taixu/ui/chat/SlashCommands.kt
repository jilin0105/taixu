package top.wkbin.taixu.ui.chat

import top.wkbin.taixu.ui.components.RuntimeIconName

data class SlashCommandItem(
    val command: String,
    val label: String,
    val description: String,
    val template: String,
    val icon: RuntimeIconName,
)

object SlashCommands {
    val presetCommands = listOf(
        SlashCommandItem(
            command = "/run",
            label = "运行代码",
            description = "执行当前工作区的入口代码（如 python main.py / npm start）",
            template = "/run ",
            icon = RuntimeIconName.Play,
        ),
        SlashCommandItem(
            command = "/install",
            label = "安装依赖",
            description = "在 Debian 沙箱中安装系统或语言依赖（apt / pip / npm）",
            template = "/install ",
            icon = RuntimeIconName.Package,
        ),
        SlashCommandItem(
            command = "/init",
            label = "初始化项目",
            description = "创建新的项目骨架模板（Python, Node.js, C/C++, HTML）",
            template = "/init ",
            icon = RuntimeIconName.Plus,
        ),
        SlashCommandItem(
            command = "/git",
            label = "Git 操作",
            description = "查看状态、提交或拉取版本控制仓库",
            template = "/git status",
            icon = RuntimeIconName.Code,
        ),
        SlashCommandItem(
            command = "/test",
            label = "运行测试",
            description = "执行单元测试与代码验证",
            template = "/test ",
            icon = RuntimeIconName.Check,
        ),
        SlashCommandItem(
            command = "/clear",
            label = "清空上下文",
            description = "开启全新会话轮次，避免历史上下文过长",
            template = "/clear",
            icon = RuntimeIconName.Trash,
        ),
        SlashCommandItem(
            command = "/help",
            label = "环境与帮助",
            description = "查看当前 Linux PRoot 沙箱环境与 Agent 工具说明",
            template = "/help",
            icon = RuntimeIconName.Alert,
        ),
    )

    fun filterCommands(query: String, activeSkills: List<top.wkbin.taixu.core.model.AgentSkill> = emptyList()): List<SlashCommandItem> {
        val skillItems = activeSkills.mapNotNull { skill ->
            val cmd = skill.triggerCommand ?: return@mapNotNull null
            SlashCommandItem(
                command = if (cmd.startsWith("/")) cmd else "/$cmd",
                label = skill.name,
                description = skill.description,
                template = if (cmd.startsWith("/")) "$cmd " else "/$cmd ",
                icon = RuntimeIconName.Code,
            )
        }
        val all = (skillItems + presetCommands).distinctBy { it.command }
        val q = query.trim().removePrefix("/").lowercase()
        if (q.isEmpty()) return all
        return all.filter {
            it.command.removePrefix("/").contains(q) ||
                it.label.lowercase().contains(q) ||
                it.description.lowercase().contains(q)
        }
    }
}
