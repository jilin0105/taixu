package top.wkbin.taixu.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ToolEntity::class, InstallLogEntity::class, InstallTaskEntity::class, RuntimeEntity::class, RuntimeDependencyRefEntity::class, HarnessMessageEntity::class, HarnessSessionEntity::class, AiModelEntity::class, WorkspaceEntity::class, TerminalSessionEntity::class],
    version = 15,
    exportSchema = false,
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
}
