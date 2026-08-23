package top.wkbin.taixu.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ToolEntity::class,
        InstallLogEntity::class,
        InstallTaskEntity::class,
        RuntimeEntity::class,
        RuntimeDependencyRefEntity::class,
        HarnessMessageEntity::class,
        HarnessSessionEntity::class,
        AiModelEntity::class,
        WorkspaceEntity::class,
        TerminalSessionEntity::class,
        AgentMemoryEntity::class,
        AgentPlanEntity::class,
        AgentScratchpadEntity::class,
        AgentSubagentEntity::class,
        AgentSubagentSettingsEntity::class,
        McpServerEntity::class,
        AgentSkillEntity::class,
        StorageMountBindingEntity::class,
        ToolSettingsEntity::class,
        AgentApprovalRequestEntity::class,
        AgentApprovalSettingsEntity::class,
    ],
    version = 27,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun toolDao(): ToolDao
    abstract fun installLogDao(): InstallLogDao
    abstract fun installTaskDao(): InstallTaskDao
    abstract fun runtimeDao(): RuntimeDao
    abstract fun harnessMessageDao(): HarnessMessageDao
    abstract fun harnessSessionDao(): HarnessSessionDao
    abstract fun aiModelDao(): AiModelDao
    abstract fun workspaceDao(): WorkspaceDao
    abstract fun terminalSessionDao(): TerminalSessionDao
    abstract fun agentContextDao(): AgentContextDao
    abstract fun agentSubagentDao(): AgentSubagentDao
    abstract fun mcpServerDao(): McpServerDao
    abstract fun agentSkillDao(): AgentSkillDao
    abstract fun storageMountBindingDao(): StorageMountBindingDao
    abstract fun toolSettingsDao(): ToolSettingsDao
    abstract fun agentApprovalDao(): AgentApprovalDao
}
