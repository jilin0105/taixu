package top.wkbin.taixu.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 长期语义与事实记忆实体
 * 用于记录用户的长期偏好、项目架构规范、全局事实。
 */
@Entity(tableName = "agent_memories")
data class AgentMemoryEntity(
    @PrimaryKey
    val id: String,
    val scope: String, // global, project, session
    val kind: String,  // preference, rule, fact, project_info
    val key: String,
    val value: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

/**
 * 结构化多步骤任务执行计划实体
 */
@Entity(tableName = "agent_plans")
data class AgentPlanEntity(
    @PrimaryKey
    val sessionId: String,
    val goal: String,
    val stepsJson: String, // List<PlanStep> JSON
    val status: String,    // active, completed, cancelled
    val updatedAt: Long = System.currentTimeMillis(),
)

/**
 * 会话/任务局部工作草稿便签实体
 */
@Entity(tableName = "agent_scratchpads", primaryKeys = ["sessionId", "key"])
data class AgentScratchpadEntity(
    val sessionId: String,
    val key: String,
    val value: String,
    val updatedAt: Long = System.currentTimeMillis(),
)
