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
