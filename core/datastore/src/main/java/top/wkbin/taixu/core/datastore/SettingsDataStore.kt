package top.wkbin.taixu.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import top.wkbin.taixu.core.security.SecretManager
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val secretManager: SecretManager,
) {
    private val developerModeKey = booleanPreferencesKey("developer_mode")
    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val onboardingCompletedKey = booleanPreferencesKey("onboarding_completed")
    private val selectedDistributionKey = stringPreferencesKey("selected_distribution")
    private val mirrorPolicyKey = stringPreferencesKey("mirror_policy")
    private val providerKey = stringPreferencesKey("provider")
    private val providerBaseUrlKey = stringPreferencesKey("provider_base_url")
    private val providerModelKey = stringPreferencesKey("provider_model")
    private val apiKeyCiphertextKey = stringPreferencesKey("api_key_ciphertext")
    private val registryManifestUrlKey = stringPreferencesKey("registry_manifest_url")
    private val registrySignatureUrlKey = stringPreferencesKey("registry_signature_url")
    private val registryPublicKeyKey = stringPreferencesKey("registry_public_key")
    private val thinkingExpandedKey = booleanPreferencesKey("thinking_blocks_expanded")
    private val agentLoggingEnabledKey = booleanPreferencesKey("agent_local_logging_enabled")
    private val executionModeKey = stringPreferencesKey("execution_mode")

    val executionMode: Flow<top.wkbin.taixu.core.model.ExecutionMode> = context.settingsDataStore.data.map { preferences ->
        top.wkbin.taixu.core.model.ExecutionMode.fromId(preferences[executionModeKey] ?: top.wkbin.taixu.core.model.ExecutionMode.PROOT.id)
    }

    suspend fun setExecutionMode(mode: top.wkbin.taixu.core.model.ExecutionMode) {
        context.settingsDataStore.edit { preferences ->
            preferences[executionModeKey] = mode.id
        }
    }

    val agentLoggingEnabled: Flow<Boolean> = context.settingsDataStore.data.map { preferences ->
        preferences[agentLoggingEnabledKey] ?: false
    }

    suspend fun setAgentLoggingEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[agentLoggingEnabledKey] = enabled
        }
    }

    val themeMode: Flow<String> = context.settingsDataStore.data.map { preferences ->
        preferences[themeModeKey] ?: "system"
    }

    suspend fun setThemeMode(mode: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[themeModeKey] = mode
        }
    }

    val developerMode: Flow<Boolean> = context.settingsDataStore.data.map { preferences ->
        preferences[developerModeKey] ?: false
    }

    val onboardingCompleted: Flow<Boolean> = context.settingsDataStore.data.map { it[onboardingCompletedKey] ?: false }
    suspend fun setOnboardingCompleted(value: Boolean) { context.settingsDataStore.edit { it[onboardingCompletedKey] = value } }
    val selectedDistribution: Flow<String> = context.settingsDataStore.data.map { it[selectedDistributionKey] ?: "ubuntu" }
    suspend fun setSelectedDistribution(value: String) { context.settingsDataStore.edit { it[selectedDistributionKey] = value } }
    val mirrorPolicy: Flow<String> = context.settingsDataStore.data.map { it[mirrorPolicyKey] ?: "auto" }
    suspend fun setMirrorPolicy(value: String) { context.settingsDataStore.edit { it[mirrorPolicyKey] = value } }

    suspend fun setDeveloperMode(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[developerModeKey] = enabled
        }
    }

    val provider: Flow<String> = context.settingsDataStore.data.map { it[providerKey] ?: "OpenAI" }
    val apiKeyConfigured: Flow<Boolean> = context.settingsDataStore.data.map {
        !it[apiKeyCiphertextKey].isNullOrBlank()
    }

    suspend fun setProvider(value: String) {
        context.settingsDataStore.edit { it[providerKey] = value }
    }

    val providerBaseUrl: Flow<String> = context.settingsDataStore.data.map {
        it[providerBaseUrlKey].orEmpty()
    }

    suspend fun setProviderBaseUrl(value: String) {
        context.settingsDataStore.edit {
            if (value.isBlank()) it.remove(providerBaseUrlKey) else it[providerBaseUrlKey] = value.trim()
        }
    }

    val providerModel: Flow<String> = context.settingsDataStore.data.map {
        it[providerModelKey].orEmpty()
    }

    suspend fun setProviderModel(value: String) {
        context.settingsDataStore.edit {
            if (value.isBlank()) it.remove(providerModelKey) else it[providerModelKey] = value.trim()
        }
    }

    suspend fun setApiKey(value: String) {
        context.settingsDataStore.edit {
            if (value.isBlank()) it.remove(apiKeyCiphertextKey)
            else it[apiKeyCiphertextKey] = secretManager.encrypt(value.trim())
        }
    }

    suspend fun readApiKey(): String? = context.settingsDataStore.data
        .map { it[apiKeyCiphertextKey] }
        .first()
        ?.let(secretManager::decrypt)

    suspend fun setModelApiKey(secretRef: String, value: String) {
        require(secretRef.matches(Regex("[a-zA-Z0-9_-]{8,80}"))) { "Invalid secret reference" }
        val key = stringPreferencesKey("model_api_key_$secretRef")
        context.settingsDataStore.edit {
            if (value.isBlank()) it.remove(key) else it[key] = secretManager.encrypt(value.trim())
        }
    }

    suspend fun readModelApiKey(secretRef: String): String? {
        if (secretRef.isBlank()) return null
        val key = stringPreferencesKey("model_api_key_$secretRef")
        return context.settingsDataStore.data.map { it[key] }.first()?.let(secretManager::decrypt)
    }

    suspend fun removeModelApiKey(secretRef: String) {
        if (secretRef.isBlank()) return
        context.settingsDataStore.edit { it.remove(stringPreferencesKey("model_api_key_$secretRef")) }
    }

    val registryManifestUrl: Flow<String> = context.settingsDataStore.data.map {
        it[registryManifestUrlKey].orEmpty()
    }
    val registrySignatureUrl: Flow<String> = context.settingsDataStore.data.map {
        it[registrySignatureUrlKey].orEmpty()
    }
    val registryPublicKey: Flow<String> = context.settingsDataStore.data.map {
        it[registryPublicKeyKey].orEmpty()
    }

    suspend fun setRegistryConfig(manifestUrl: String, signatureUrl: String, publicKey: String) {
        context.settingsDataStore.edit {
            it[registryManifestUrlKey] = manifestUrl.trim()
            it[registrySignatureUrlKey] = signatureUrl.trim()
            it[registryPublicKeyKey] = publicKey.trim()
        }
    }

    /** 聊天里“思考过程”块是否默认展开（记忆用户上次的选择）。 */
    val thinkingExpanded: Flow<Boolean> = context.settingsDataStore.data.map { it[thinkingExpandedKey] ?: false }

    suspend fun setThinkingExpanded(value: Boolean) {
        context.settingsDataStore.edit { it[thinkingExpandedKey] = value }
    }

    // ==================== Agent 智能体核心配置 ====================

    private val contextCompactionEnabledKey = booleanPreferencesKey("agent_context_compaction_enabled")
    private val contextCompactionThresholdKey = androidx.datastore.preferences.core.intPreferencesKey("agent_context_compaction_threshold")
    private val maxToolRoundsKey = androidx.datastore.preferences.core.intPreferencesKey("agent_max_tool_rounds")
    private val autoWorkspaceCwdKey = booleanPreferencesKey("agent_auto_workspace_cwd")
    private val enabledSkillsKey = stringPreferencesKey("agent_enabled_skills_ids")
    private val customSkillsKey = stringPreferencesKey("agent_custom_skills_json")
    private val enabledPluginsKey = stringPreferencesKey("agent_enabled_plugins_ids")

    val contextCompactionEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[contextCompactionEnabledKey] ?: true }
    suspend fun setContextCompactionEnabled(value: Boolean) { context.settingsDataStore.edit { it[contextCompactionEnabledKey] = value } }

    /** 触发上下文压缩的历史轮数阈值（默认 15 轮） */
    val contextCompactionThreshold: Flow<Int> = context.settingsDataStore.data.map { it[contextCompactionThresholdKey] ?: 15 }
    suspend fun setContextCompactionThreshold(value: Int) { context.settingsDataStore.edit { it[contextCompactionThresholdKey] = value.coerceIn(5, 50) } }

    /** 最大工具执行轮次（默认 100 轮） */
    val maxToolRounds: Flow<Int> = context.settingsDataStore.data.map { it[maxToolRoundsKey] ?: 100 }
    suspend fun setMaxToolRounds(value: Int) { context.settingsDataStore.edit { it[maxToolRoundsKey] = value.coerceIn(10, 300) } }

    /** 执行命令时是否自动注入工作区路径为 cwd */
    val autoWorkspaceCwd: Flow<Boolean> = context.settingsDataStore.data.map { it[autoWorkspaceCwdKey] ?: true }
    suspend fun setAutoWorkspaceCwd(value: Boolean) { context.settingsDataStore.edit { it[autoWorkspaceCwdKey] = value } }

    private val jsonHelper = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** 获取所有 Skill（预置 + 用户自定义），根据用户启用状态计算 isEnabled */
    val allSkills: Flow<List<top.wkbin.taixu.core.model.AgentSkill>> = context.settingsDataStore.data.map { prefs ->
        val enabledIdsJson = prefs[enabledSkillsKey]
        val enabledIds: Set<String> = if (enabledIdsJson != null) {
            runCatching { jsonHelper.decodeFromString<Set<String>>(enabledIdsJson) }.getOrElse { top.wkbin.taixu.core.model.BuiltinSkills.presets.map { s -> s.id }.toSet() }
        } else {
            top.wkbin.taixu.core.model.BuiltinSkills.presets.filter { it.isEnabled }.map { it.id }.toSet()
        }

        val customJson = prefs[customSkillsKey]
        val customSkills: List<top.wkbin.taixu.core.model.AgentSkill> = if (!customJson.isNullOrBlank()) {
            runCatching { jsonHelper.decodeFromString<List<top.wkbin.taixu.core.model.AgentSkill>>(customJson) }.getOrDefault(emptyList())
        } else emptyList()

        val all = top.wkbin.taixu.core.model.BuiltinSkills.presets + customSkills
        all.map { skill -> skill.copy(isEnabled = skill.id in enabledIds) }
    }

    /** 获取当前已启用的 Skill 列表 */
    val activeSkills: Flow<List<top.wkbin.taixu.core.model.AgentSkill>> = allSkills.map { list -> list.filter { it.isEnabled } }

    suspend fun setSkillEnabled(skillId: String, enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            val enabledIdsJson = prefs[enabledSkillsKey]
            val current: MutableSet<String> = if (enabledIdsJson != null) {
                runCatching { jsonHelper.decodeFromString<Set<String>>(enabledIdsJson).toMutableSet() }
                    .getOrElse { top.wkbin.taixu.core.model.BuiltinSkills.presets.map { it.id }.toMutableSet() }
            } else {
                top.wkbin.taixu.core.model.BuiltinSkills.presets.filter { it.isEnabled }.map { it.id }.toMutableSet()
            }
            if (enabled) current.add(skillId) else current.remove(skillId)
            prefs[enabledSkillsKey] = jsonHelper.encodeToString(current)
        }
    }

    suspend fun addCustomSkill(skill: top.wkbin.taixu.core.model.AgentSkill) {
        context.settingsDataStore.edit { prefs ->
            val customJson = prefs[customSkillsKey]
            val current: MutableList<top.wkbin.taixu.core.model.AgentSkill> = if (!customJson.isNullOrBlank()) {
                runCatching { jsonHelper.decodeFromString<List<top.wkbin.taixu.core.model.AgentSkill>>(customJson).toMutableList() }.getOrDefault(mutableListOf())
            } else mutableListOf()
            current.removeAll { it.id == skill.id }
            current.add(skill)
            prefs[customSkillsKey] = jsonHelper.encodeToString(current)

            // 默认启用新添加的自定义技能
            val enabledIdsJson = prefs[enabledSkillsKey]
            val enabledSet: MutableSet<String> = if (enabledIdsJson != null) {
                runCatching { jsonHelper.decodeFromString<Set<String>>(enabledIdsJson).toMutableSet() }
                    .getOrElse { top.wkbin.taixu.core.model.BuiltinSkills.presets.map { it.id }.toMutableSet() }
            } else {
                top.wkbin.taixu.core.model.BuiltinSkills.presets.filter { it.isEnabled }.map { it.id }.toMutableSet()
            }
            enabledSet.add(skill.id)
            prefs[enabledSkillsKey] = jsonHelper.encodeToString(enabledSet)
        }
    }

    suspend fun deleteCustomSkill(skillId: String) {
        context.settingsDataStore.edit { prefs ->
            val customJson = prefs[customSkillsKey]
            if (!customJson.isNullOrBlank()) {
                val current = runCatching { jsonHelper.decodeFromString<List<top.wkbin.taixu.core.model.AgentSkill>>(customJson).toMutableList() }.getOrNull()
                if (current != null) {
                    current.removeAll { it.id == skillId }
                    prefs[customSkillsKey] = jsonHelper.encodeToString(current)
                }
            }
        }
    }

    /** 获取所有 Plugin（预置），根据用户启用状态计算 isEnabled */
    val allPlugins: Flow<List<top.wkbin.taixu.core.model.AgentPlugin>> = context.settingsDataStore.data.map { prefs ->
        val enabledIdsJson = prefs[enabledPluginsKey]
        val enabledIds: Set<String> = if (enabledIdsJson != null) {
            runCatching { jsonHelper.decodeFromString<Set<String>>(enabledIdsJson) }
                .getOrElse { top.wkbin.taixu.core.model.BuiltinPlugins.presets.filter { it.isEnabled }.map { it.id }.toSet() }
        } else {
            top.wkbin.taixu.core.model.BuiltinPlugins.presets.filter { it.isEnabled }.map { it.id }.toSet()
        }
        top.wkbin.taixu.core.model.BuiltinPlugins.presets.map { plugin -> plugin.copy(isEnabled = plugin.id in enabledIds) }
    }

    suspend fun setPluginEnabled(pluginId: String, enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            val enabledIdsJson = prefs[enabledPluginsKey]
            val current: MutableSet<String> = if (enabledIdsJson != null) {
                runCatching { jsonHelper.decodeFromString<Set<String>>(enabledIdsJson).toMutableSet() }
                    .getOrElse { top.wkbin.taixu.core.model.BuiltinPlugins.presets.filter { it.isEnabled }.map { it.id }.toMutableSet() }
            } else {
                top.wkbin.taixu.core.model.BuiltinPlugins.presets.filter { it.isEnabled }.map { it.id }.toMutableSet()
            }
            if (enabled) current.add(pluginId) else current.remove(pluginId)
            prefs[enabledPluginsKey] = jsonHelper.encodeToString(current)
        }
    }

    // 存储挂载配置 (PRoot -b 挂载点)
    private val mountDownloadKey = booleanPreferencesKey("mount_download_enabled")
    private val mountDocumentsKey = booleanPreferencesKey("mount_documents_enabled")
    private val mountSharedStorageKey = booleanPreferencesKey("mount_shared_storage_enabled")
    private val customMountsKey = stringPreferencesKey("custom_mount_bindings")

    val mountDownloadEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[mountDownloadKey] ?: true }
    suspend fun setMountDownloadEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[mountDownloadKey] = enabled }
    }

    val mountDocumentsEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[mountDocumentsKey] ?: true }
    suspend fun setMountDocumentsEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[mountDocumentsKey] = enabled }
    }

    val mountSharedStorageEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[mountSharedStorageKey] ?: false }
    suspend fun setMountSharedStorageEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[mountSharedStorageKey] = enabled }
    }

    val customMountBindings: Flow<List<top.wkbin.taixu.core.model.StorageMountBinding>> = context.settingsDataStore.data.map { prefs ->
        val json = prefs[customMountsKey]
        if (!json.isNullOrBlank()) {
            runCatching { jsonHelper.decodeFromString<List<top.wkbin.taixu.core.model.StorageMountBinding>>(json) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
    }

    suspend fun addCustomMountBinding(binding: top.wkbin.taixu.core.model.StorageMountBinding) {
        context.settingsDataStore.edit { prefs ->
            val json = prefs[customMountsKey]
            val current = if (!json.isNullOrBlank()) {
                runCatching { jsonHelper.decodeFromString<List<top.wkbin.taixu.core.model.StorageMountBinding>>(json).toMutableList() }.getOrDefault(mutableListOf())
            } else mutableListOf()
            current.removeAll { it.id == binding.id }
            current.add(binding)
            prefs[customMountsKey] = jsonHelper.encodeToString(current)
        }
    }

    suspend fun removeCustomMountBinding(bindingId: String) {
        context.settingsDataStore.edit { prefs ->
            val json = prefs[customMountsKey]
            if (!json.isNullOrBlank()) {
                val current = runCatching { jsonHelper.decodeFromString<List<top.wkbin.taixu.core.model.StorageMountBinding>>(json).toMutableList() }.getOrNull()
                if (current != null) {
                    current.removeAll { it.id == bindingId }
                    prefs[customMountsKey] = jsonHelper.encodeToString(current)
                }
            }
        }
    }

    suspend fun setCustomMountEnabled(bindingId: String, enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            val json = prefs[customMountsKey]
            if (!json.isNullOrBlank()) {
                val current = runCatching { jsonHelper.decodeFromString<List<top.wkbin.taixu.core.model.StorageMountBinding>>(json).toMutableList() }.getOrNull()
                if (current != null) {
                    val index = current.indexOfFirst { it.id == bindingId }
                    if (index >= 0) {
                        current[index] = current[index].copy(enabled = enabled)
                        prefs[customMountsKey] = jsonHelper.encodeToString(current)
                    }
                }
            }
        }
    }
}

