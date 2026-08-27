package top.wkbin.taixu.harness.projection

import top.wkbin.taixu.harness.ToolCall
import top.wkbin.taixu.harness.HarnessTool
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** 会话抽屉 / 状态点所展示的"正在执行什么"动作描述。 */
object ToolStatusDescriber {
    const val MAX_STATUS_ARG_LENGTH = 60

    private fun arg(args: JsonObject, name: String): String? =
        runCatching { args[name]?.jsonPrimitive?.content }.getOrNull()?.takeIf { it.isNotBlank() }

    fun describe(tool: HarnessTool, args: JsonObject, rawToolName: String? = null): String = when (tool) {
        HarnessTool.BASE -> {
            val command = arg(args, "command")?.lineSequence()?.first()?.trim().orEmpty()
            if (command.isEmpty()) "执行命令" else "执行命令：${command.take(MAX_STATUS_ARG_LENGTH)}"
        }
        HarnessTool.PROCESS -> "管理后台进程：${arg(args, "action") ?: "process"}${arg(args, "id")?.let { " · ${it.take(MAX_STATUS_ARG_LENGTH)}" }.orEmpty()}"
        HarnessTool.HOST -> "正在使用宿主权限：${arg(args, "action") ?: "host"}${arg(args, "command")?.let { " · ${it.take(MAX_STATUS_ARG_LENGTH)}" }.orEmpty()}"
        HarnessTool.DOWNLOAD -> arg(args, "destination")?.let { "下载文件：${it.takeLast(MAX_STATUS_ARG_LENGTH)}" } ?: "下载文件"
        HarnessTool.READ -> arg(args, "path")?.let { "读取文件：${it.takeLast(MAX_STATUS_ARG_LENGTH)}" } ?: "读取文件"
        HarnessTool.WRITE -> arg(args, "path")?.let { "写入文件：${it.takeLast(MAX_STATUS_ARG_LENGTH)}" } ?: "写入文件"
        HarnessTool.EDIT -> arg(args, "path")?.let { "编辑文件：${it.takeLast(MAX_STATUS_ARG_LENGTH)}" } ?: "编辑文件"
        HarnessTool.MEMORY -> "正在存取长期记忆：${arg(args, "key") ?: arg(args, "action") ?: "memory"}"
        HarnessTool.PLAN -> "正在更新任务执行规划：${arg(args, "goal") ?: arg(args, "action") ?: "plan"}"
        HarnessTool.SCRATCHPAD -> "正在记录工作草稿便签：${arg(args, "key") ?: arg(args, "action") ?: "scratchpad"}"
        HarnessTool.HISTORY_SEARCH -> "正在检索历史消息：${arg(args, "query")?.take(MAX_STATUS_ARG_LENGTH).orEmpty()}"
        HarnessTool.HISTORY_READ -> "正在读取历史消息：${arg(args, "message_id") ?: arg(args, "index") ?: "history"}"
        HarnessTool.SUBAGENT -> "正在派发并执行子智能体协同任务…"
        HarnessTool.MCP -> "正在调用 MCP 插件工具：${rawToolName ?: "mcp"}…"
    }
}
