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

/** 为可导入 Skill 记录原始资源包路径。 */
val MIGRATION_32_33 = object : Migration(32, 33) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE agent_skills ADD COLUMN resourcePath TEXT")
    }
}
