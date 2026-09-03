package top.wkbin.taixu.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import top.wkbin.taixu.core.model.AgentSkill
import top.wkbin.taixu.core.model.BuiltinSkills
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Entity(tableName = "agent_skills")
data class AgentSkillEntity(
    @androidx.room.PrimaryKey val id: String,
    val name: String,
    val description: String,
    val systemPrompt: String,
    val triggerCommand: String?,
    val iconName: String,
    val isEnabled: Boolean,
    val isBuiltin: Boolean,
    val isImmutable: Boolean,
    val category: String,
    val resourcePath: String?,
)

@Dao
interface AgentSkillDao {
    @Query("SELECT * FROM agent_skills ORDER BY isBuiltin DESC, name ASC")
    fun observeAll(): Flow<List<AgentSkillEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(skills: List<AgentSkillEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(skill: AgentSkillEntity)

    @Query("UPDATE agent_skills SET isEnabled = :enabled WHERE id = :id AND isImmutable = 0")
    suspend fun setEnabled(id: String, enabled: Boolean)

    @Query("DELETE FROM agent_skills WHERE id = :id AND isBuiltin = 0")
    suspend fun deleteCustom(id: String)
}

/** 可自动发现 Skill 的宿主目录与其在 PRoot 沙箱内的挂载前缀（如 /attachments/skills）。 */
data class SkillScanRoot(
    val hostDir: File,
    val guestPrefix: String,
)

@Singleton
class AgentSkillRepository @Inject constructor(
    private val dao: AgentSkillDao,
) {
    val allSkills: Flow<List<AgentSkill>> = dao.observeAll().map { rows -> rows.map(AgentSkillEntity::toModel) }
    val activeSkills: Flow<List<AgentSkill>> = allSkills.map { skills -> skills.filter { it.isEnabled } }

    private val directorySyncMutex = Mutex()

    suspend fun ensureInitialized() {
        dao.insertAll(BuiltinSkills.presets.map(AgentSkill::toEntity))
    }

    suspend fun setEnabled(id: String, enabled: Boolean) {
        ensureInitialized()
        dao.setEnabled(id, enabled)
    }

    suspend fun addCustom(skill: AgentSkill) {
        ensureInitialized()
        dao.upsert(skill.toEntity())
    }

    suspend fun deleteCustom(id: String) {
        ensureInitialized()
        dao.deleteCustom(id)
    }

    /**
     * 扫描各根目录下的一级子目录，把包含 SKILL.md / prompt.md 且尚未入库的目录
     * 自动注册为自定义 Skill。用户手动复制 Skill 文件夹（或把其他工具的整个
     * skills 目录整体复制）到 attachments/skills 或工作区 skills 目录即可被发现，
     * 无需通过 ZIP 逐个导入。按 resourcePath 去重，重复调用安全。
     *
     * 兼容两种目录形态：
     * - 单个 Skill 目录：目录直属 SKILL.md；
     * - 集合目录：本身不含 SKILL.md，但一级子目录各自是一个 Skill（整包复制场景）。
     *
     * @return 本次新注册的 Skill 列表
     */
    suspend fun syncFromDirectories(roots: List<SkillScanRoot>): List<AgentSkill> = directorySyncMutex.withLock {
        ensureInitialized()
        val knownPaths = dao.observeAll().first()
            .mapNotNull { it.resourcePath }
            .mapTo(mutableSetOf()) { stored ->
                runCatching { File(stored).canonicalPath }.getOrDefault(stored)
            }
        val imported = mutableListOf<AgentSkill>()

        suspend fun register(dir: File, promptFile: File, guestPath: String) {
            val markdown = runCatching { promptFile.readText().trim() }.getOrNull()
            if (markdown.isNullOrBlank()) return
            val skill = AgentSkill(
                id = "custom_" + UUID.randomUUID().toString().take(8),
                name = extractSkillMetadata(markdown, "name")
                    ?: markdown.lineSequence().firstOrNull { it.startsWith("# ") }?.removePrefix("# ")?.trim()
                    ?: dir.name,
                description = extractSkillMetadata(markdown, "description") ?: "从目录自动发现的 Skill",
                systemPrompt = markdown + "\n\n【Skill 资源目录】$guestPath\n如需执行该 Skill 附带的脚本，请先检查脚本内容与参数，再从此目录调用。",
                isBuiltin = false,
                category = "自定义",
                resourcePath = dir.absolutePath,
            )
            dao.upsert(skill.toEntity())
            knownPaths += runCatching { dir.canonicalPath }.getOrDefault(dir.absolutePath)
            imported += skill
        }

        roots.forEach { root ->
            root.hostDir.listFiles().orEmpty()
                .filter { it.isDirectory }
                .forEach { dir ->
                    val canonical = runCatching { dir.canonicalPath }.getOrNull() ?: return@forEach
                    if (canonical in knownPaths) return@forEach
                    val directPrompt = directPromptFile(dir)
                    if (directPrompt != null) {
                        register(dir, directPrompt, root.guestPrefix.trimEnd('/') + "/" + dir.name)
                    } else {
                        // 集合目录：一级子目录各自是一个 Skill（如 rikkahub / aicode 等工具的整个 skills 目录整体复制）
                        dir.listFiles().orEmpty()
                            .filter { it.isDirectory }
                            .forEach { child ->
                                val childCanonical = runCatching { child.canonicalPath }.getOrNull() ?: return@forEach
                                if (childCanonical in knownPaths) return@forEach
                                val childPrompt = directPromptFile(child) ?: return@forEach
                                register(child, childPrompt, root.guestPrefix.trimEnd('/') + "/" + dir.name + "/" + child.name)
                            }
                    }
                }
        }
        imported
    }

    /** 目录直属的提示词文件（不含嵌套子目录），优先 SKILL.md。 */
    private fun directPromptFile(dir: File): File? =
        dir.listFiles().orEmpty()
            .filter { it.isFile && it.name.lowercase() in SKILL_PROMPT_FILE_NAMES }
            .let { files -> files.firstOrNull { it.name.lowercase() == "skill.md" } ?: files.firstOrNull() }

    companion object {
        private val SKILL_PROMPT_FILE_NAMES = setOf("skill.md", "prompt.md")

        /** 从 SKILL.md 的 YAML frontmatter 中提取 name / description 等元数据。 */
        fun extractSkillMetadata(markdown: String, key: String): String? {
            if (!markdown.startsWith("---")) return null
            return markdown.lineSequence().drop(1).takeWhile { it.trim() != "---" }
                .firstOrNull { it.substringBefore(':').trim().equals(key, ignoreCase = true) }
                ?.substringAfter(':')?.trim()?.trim('"', '\'')?.takeIf { it.isNotBlank() }
        }
    }
}

private fun AgentSkillEntity.toModel() = AgentSkill(
    id = id,
    name = name,
    description = description,
    systemPrompt = systemPrompt,
    triggerCommand = triggerCommand,
    iconName = iconName,
    isEnabled = isEnabled,
    isBuiltin = isBuiltin,
    isImmutable = isImmutable,
    category = category,
    resourcePath = resourcePath,
)

private fun AgentSkill.toEntity() = AgentSkillEntity(
    id = id,
    name = name,
    description = description,
    systemPrompt = systemPrompt,
    triggerCommand = triggerCommand,
    iconName = iconName,
    isEnabled = isEnabled,
    isBuiltin = isBuiltin,
    isImmutable = isImmutable,
    category = category,
    resourcePath = resourcePath,
)
