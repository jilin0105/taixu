package top.wkbin.taixu.harness

import top.wkbin.taixu.core.database.AgentContextRepository
import top.wkbin.taixu.core.database.AgentMemoryEntity
import top.wkbin.taixu.core.database.AgentPlanEntity
import top.wkbin.taixu.core.database.AgentScratchpadEntity
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 负责执行 agent-context 核心系统能力工具：
 * - memory: 长期语义与事实记忆
 * - plan: 多步骤任务执行计划管理
 * - scratchpad: 任务局部工作草稿与排查便签
 */
@Singleton
class AgentContextExecutor @Inject constructor(
    private val agentContextDao: AgentContextRepository,
    private val json: Json,
) {
    suspend fun executeMemory(args: JsonObject, sessionId: String, workspace: String): Pair<Boolean, String> {
        val action = args["action"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: "list"
        return when (action) {
            "save" -> {
                val key = args["key"]?.jsonPrimitive?.contentOrNull?.trim()
                    ?: return false to "memory save 必须提供 key 参数"
                val value = args["value"]?.jsonPrimitive?.contentOrNull?.trim()
                    ?: return false to "memory save 必须提供 value 参数"
                val kind = args["kind"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: "fact"
                val scope = args["scope"]?.jsonPrimitive?.contentOrNull?.lowercase()
                    ?: if (workspace.isNotBlank()) "project" else "global"
                val existing = agentContextDao.getMemoryByKey(key, scope)
                val id = existing?.id ?: UUID.randomUUID().toString()
                agentContextDao.saveMemory(
                    AgentMemoryEntity(
                        id = id,
                        scope = scope,
                        kind = kind,
                        key = key,
                        value = value,
                        updatedAt = System.currentTimeMillis(),
                    )
                )
                val savedMessage = if (existing != null) {
                    "已更新既有长期记忆（原值被覆盖）[$scope/$kind] $key = $value"
                } else {
                    "已成功存储长期记忆 [$scope/$kind] $key = $value"
                }
                true to savedMessage
            }
            "query", "search" -> {
                val query = args["query"]?.jsonPrimitive?.contentOrNull?.trim()
                    ?: args["key"]?.jsonPrimitive?.contentOrNull?.trim()
                    ?: return false to "memory query 必须提供 query 或 key"
                val results = agentContextDao.searchMemories(query)
                if (results.isEmpty()) {
                    true to "未查询到与 '$query' 相关的记忆"
                } else {
                    val formatted = results.joinToString("\n") { "- [${it.scope}/${it.kind}] ${it.key}: ${it.value}" }
                    true to "查询到以下记忆：\n$formatted"
                }
            }
            "list" -> {
                val scopes = listOf("global", "project", "session")
                val results = agentContextDao.getMemoriesByScopes(scopes)
                if (results.isEmpty()) {
                    true to "当前暂无长期记忆"
                } else {
                    val formatted = results.joinToString("\n") { "- [${it.scope}/${it.kind}] ${it.key}: ${it.value}" }
                    true to "已保存的记忆列表：\n$formatted"
                }
            }
            "delete" -> {
                val key = args["key"]?.jsonPrimitive?.contentOrNull?.trim()
                val id = args["id"]?.jsonPrimitive?.contentOrNull?.trim()
                if (id != null) {
                    agentContextDao.deleteMemoryById(id)
                    true to "已删除记忆 id=$id"
                } else if (key != null) {
                    val scope = args["scope"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: "global"
                    agentContextDao.deleteMemoryByKey(key, scope)
                    true to "已删除记忆 [$scope] key=$key"
                } else {
                    false to "memory delete 需提供 id 或 key"
                }
            }
            else -> false to "未知的 memory 动作: $action"
        }
    }

    suspend fun executePlan(args: JsonObject, sessionId: String): Pair<Boolean, String> {
        val action = args["action"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: "get"
        return when (action) {
            "replace_active", "set_active", "create" -> {
                val goal = args["goal"]?.jsonPrimitive?.contentOrNull?.trim()
                    ?: return false to "plan replace_active 必须提供 goal 目标描述"
                val stepsElement = args["steps"]
                val stepsJson = stepsElement?.toString() ?: "[]"
                agentContextDao.savePlan(
                    AgentPlanEntity(
                        sessionId = sessionId,
                        goal = goal,
                        stepsJson = stepsJson,
                        status = "active",
                        updatedAt = System.currentTimeMillis(),
                    )
                )
                true to "已成功创建/更新任务执行规划：\n目标：$goal\n步骤：$stepsJson"
            }
            "get_active", "get" -> {
                val plan = agentContextDao.getActivePlan(sessionId)
                if (plan == null) {
                    true to "当前会话暂无活跃任务计划"
                } else {
                    true to "当前活跃任务计划：\n目标：${plan.goal}\n步骤：${plan.stepsJson}\n状态：${plan.status}"
                }
            }
            "advance", "update_steps" -> {
                val plan = agentContextDao.getActivePlan(sessionId)
                    ?: return false to "当前会话没有可推进的活跃计划，请先用 replace_active 创建"
                val stepsElement = args["steps"]
                val newStepsJson = stepsElement?.toString() ?: plan.stepsJson
                val newStatus = args["status"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: plan.status
                agentContextDao.savePlan(
                    plan.copy(
                        stepsJson = newStepsJson,
                        status = newStatus,
                        updatedAt = System.currentTimeMillis(),
                    )
                )
                true to "计划已更新：状态=$newStatus, 步骤=$newStepsJson"
            }
            "clear_active", "clear" -> {
                agentContextDao.deletePlanBySession(sessionId)
                true to "已清空当前会话的任务规划"
            }
            else -> false to "未知的 plan 动作: $action"
        }
    }

    suspend fun executeScratchpad(args: JsonObject, sessionId: String): Pair<Boolean, String> {
        val action = args["action"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: "list"
        return when (action) {
            "save", "set" -> {
                val key = args["key"]?.jsonPrimitive?.contentOrNull?.trim()
                    ?: return false to "scratchpad save 必须提供 key"
                val value = args["value"]?.jsonPrimitive?.contentOrNull?.trim()
                    ?: return false to "scratchpad save 必须提供 value"
                agentContextDao.saveScratchpad(
                    AgentScratchpadEntity(
                        sessionId = sessionId,
                        key = key,
                        value = value,
                        updatedAt = System.currentTimeMillis(),
                    )
                )
                true to "已记录工作草稿 [$key] = $value"
            }
            "get" -> {
                val key = args["key"]?.jsonPrimitive?.contentOrNull?.trim()
                    ?: return false to "scratchpad get 必须提供 key"
                val item = agentContextDao.getScratchpad(sessionId, key)
                if (item == null) {
                    true to "草稿 [$key] 不存在"
                } else {
                    true to "草稿 [$key]：${item.value}"
                }
            }
            "list" -> {
                val list = agentContextDao.listScratchpads(sessionId)
                if (list.isEmpty()) {
                    true to "当前无工作草稿记录"
                } else {
                    val formatted = list.joinToString("\n") { "- [${it.key}]: ${it.value}" }
                    true to "当前工作草稿：\n$formatted"
                }
            }
            "delete" -> {
                val key = args["key"]?.jsonPrimitive?.contentOrNull?.trim()
                    ?: return false to "scratchpad delete 必须提供 key"
                agentContextDao.deleteScratchpad(sessionId, key)
                true to "已删除草稿 [$key]"
            }
            "clear" -> {
                agentContextDao.clearScratchpads(sessionId)
                true to "已清空当前任务的所有工作草稿"
            }
            else -> false to "未知的 scratchpad 动作: $action"
        }
    }
}
