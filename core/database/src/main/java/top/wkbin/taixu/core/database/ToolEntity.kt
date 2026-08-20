package top.wkbin.taixu.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tools")
data class ToolEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val dependencies: String,
    val launchType: String,
    val state: String,
    val manifestVersion: String = "0.1.0",
    val installedVersion: String? = null,
    val publisher: String = "",
    val category: String = "AI_AGENT",
    val permissions: String = "",
    val homepage: String? = null,
    val updateStrategy: String = "REINSTALL",
    val latestVersion: String? = null,
)
