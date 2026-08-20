package top.wkbin.taixu.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Durable transaction state used to recover after an Android process kill. */
@Entity(tableName = "install_tasks")
data class InstallTaskEntity(
    @PrimaryKey val toolId: String,
    val operation: String,
    val state: String,
    val message: String,
    val startedAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
