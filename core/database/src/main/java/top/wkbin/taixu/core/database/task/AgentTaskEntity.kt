package top.wkbin.taixu.core.database.task

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(tableName = "agent_tasks", indices = [Index(value = ["status"]), Index(value = ["updatedAt"])])
data class AgentTaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val status: String, // IDLE, RUNNING, SUSPENDED, ERROR, COMPLETED
    val createdAt: Long,
    val updatedAt: Long,
    val errorMessage: String? = null,
    val progress: Float = 0f, // 0.0 to 1.0
)
