package top.wkbin.taixu.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import top.wkbin.taixu.core.model.AgentSkill
import top.wkbin.taixu.core.model.BuiltinSkills
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

@Singleton
class AgentSkillRepository @Inject constructor(
    private val dao: AgentSkillDao,
) {
    val allSkills: Flow<List<AgentSkill>> = dao.observeAll().map { rows -> rows.map(AgentSkillEntity::toModel) }
    val activeSkills: Flow<List<AgentSkill>> = allSkills.map { skills -> skills.filter { it.isEnabled } }

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
