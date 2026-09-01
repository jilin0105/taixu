package top.wkbin.taixu.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** 保留已有模型档案，为多 Key 轮询追加非敏感配置列。 */
val MIGRATION_27_28 = object : Migration(27, 28) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE harness_models ADD COLUMN apiKeyCount INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE harness_models ADD COLUMN requestsPerMinutePerKey INTEGER NOT NULL DEFAULT 0")
    }
}

/** 新建快捷短语与常用指令表 quick_phrases */
val MIGRATION_28_29 = object : Migration(28, 29) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS quick_phrases (
                id TEXT NOT NULL PRIMARY KEY,
                title TEXT NOT NULL,
                content TEXT NOT NULL,
                description TEXT NOT NULL DEFAULT '',
                iconName TEXT NOT NULL DEFAULT 'Play',
                targetProjectType TEXT,
                isEnabled INTEGER NOT NULL DEFAULT 1,
                sortOrder INTEGER NOT NULL DEFAULT 0,
                isBuiltin INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
    }
}

/**
 * 审批请求绑定 harness operation 与参数摘要，并引入过期时间：
 * - operationId：审批所属运行，恢复执行前校验归属，防跨运行重放；
 * - argsHash：argumentsJson 的 SHA-256，防"批准旧参数、执行新参数"；
 * - expiresAt：审批有效期（存量行填 Long.MAX_VALUE 表示永不过期）。
 */
val MIGRATION_30_31 = object : Migration(30, 31) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE agent_approval_requests ADD COLUMN operationId TEXT")
        db.execSQL("ALTER TABLE agent_approval_requests ADD COLUMN argsHash TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE agent_approval_requests ADD COLUMN expiresAt INTEGER NOT NULL DEFAULT 9223372036854775807")
    }
}

/** 为模型档案追加 Responses API 开关列。 */
val MIGRATION_31_32 = object : Migration(31, 32) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE harness_models ADD COLUMN responseApiEnabled INTEGER NOT NULL DEFAULT 0")
    }
}

/** Privileged Android application inventory used by Settings and the Agent. */
val MIGRATION_33_34 = object : Migration(33, 34) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS android_apps (packageName TEXT NOT NULL PRIMARY KEY, label TEXT NOT NULL, uid INTEGER NOT NULL, apkPath TEXT NOT NULL, isSystemApp INTEGER NOT NULL, isEnabled INTEGER NOT NULL, isSuspended INTEGER NOT NULL, isNetworkRestricted INTEGER NOT NULL, lastSyncedAt INTEGER NOT NULL)""")
    }
}

/** Reusable workshop scripts and explicit per-project script selection. */
val MIGRATION_34_35 = object : Migration(34, 35) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS build_scripts (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, description TEXT NOT NULL DEFAULT '', projectType TEXT NOT NULL, content TEXT NOT NULL, isBuiltin INTEGER NOT NULL DEFAULT 0, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS project_build_script_bindings (projectName TEXT NOT NULL PRIMARY KEY, scriptId TEXT NOT NULL, updatedAt INTEGER NOT NULL)""")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_project_build_script_bindings_scriptId ON project_build_script_bindings(scriptId)")
    }
}

/**
 * 修正 mcp_codegraph 内置预设的错误默认启用状态。
 * 上一次提交以 isEnabled=1 写入，但设备上尚无 /opt/taixu/scripts/codegraph_mcp_server.py，
 * 导致 discoverTools() 120s 超时，阻塞 Agent 首次启动。
 */
val MIGRATION_35_36 = object : Migration(35, 36) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("UPDATE mcp_servers SET isEnabled = 0 WHERE id = 'mcp_codegraph' AND isBuiltin = 1")
    }
}

val MIGRATION_36_37 = object : Migration(36, 37) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS agent_tasks (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, description TEXT NOT NULL, status TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, errorMessage TEXT, progress REAL NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_agent_tasks_status ON agent_tasks(status)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_agent_tasks_updatedAt ON agent_tasks(updatedAt)")
    }
}

/** Give project/session memories an explicit owner so they cannot leak across contexts. */
val MIGRATION_37_38 = object : Migration(37, 38) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE agent_memories ADD COLUMN ownerId TEXT NOT NULL DEFAULT ''")
        // Existing non-global rows have no trustworthy owner. Keep them for manual recovery,
        // but exclude them from every live project/session context.
        db.execSQL("UPDATE agent_memories SET ownerId = 'legacy-unscoped' WHERE scope != 'global'")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_agent_memories_scope_ownerId_key ON agent_memories(scope, ownerId, `key`)")
    }
}

/** Bind a concrete provider model variant to each chat session. */
val MIGRATION_38_39 = object : Migration(38, 39) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE harness_sessions ADD COLUMN modelVariant TEXT")
    }
}

/** Replace the legacy flat built-in roles with a versioned, department-aware catalog. */
val MIGRATION_39_40 = object : Migration(39, 40) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE agent_subagents ADD COLUMN departmentId TEXT NOT NULL DEFAULT 'custom'")
        db.execSQL("ALTER TABLE agent_subagent_settings ADD COLUMN catalogRevision TEXT NOT NULL DEFAULT ''")
    }
}
