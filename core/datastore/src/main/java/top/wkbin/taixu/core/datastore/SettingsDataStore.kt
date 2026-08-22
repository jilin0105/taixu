package top.wkbin.taixu.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
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
    private val defaultReasoningDepthKey = stringPreferencesKey("agent_default_reasoning_depth")
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

    // 终端外观与显示定制
    private val terminalFontSizeKey = androidx.datastore.preferences.core.intPreferencesKey("terminal_font_size")
    private val terminalColorSchemeKey = stringPreferencesKey("terminal_color_scheme")
    private val terminalHapticsEnabledKey = booleanPreferencesKey("terminal_haptics_enabled")
    private val appFontScaleKey = androidx.datastore.preferences.core.floatPreferencesKey("app_font_scale")

    val terminalFontSize: Flow<Int> = context.settingsDataStore.data.map { prefs ->
        prefs[terminalFontSizeKey] ?: 13
    }

    suspend fun setTerminalFontSize(sizeSp: Int) {
        context.settingsDataStore.edit { prefs ->
            prefs[terminalFontSizeKey] = sizeSp.coerceIn(10, 24)
        }
    }

    val terminalColorScheme: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[terminalColorSchemeKey] ?: "obsidian"
    }

    suspend fun setTerminalColorScheme(scheme: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[terminalColorSchemeKey] = scheme
        }
    }

    val terminalHapticsEnabled: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[terminalHapticsEnabledKey] ?: true
    }

    suspend fun setTerminalHapticsEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[terminalHapticsEnabledKey] = enabled
        }
    }

    val appFontScale: Flow<Float> = context.settingsDataStore.data.map { prefs ->
        prefs[appFontScaleKey] ?: 1.0f
    }

    suspend fun setAppFontScale(scale: Float) {
        context.settingsDataStore.edit { prefs ->
            prefs[appFontScaleKey] = scale
        }
    }

    private val autoCheckUpdatesKey = booleanPreferencesKey("auto_check_updates")

    val autoCheckUpdates: Flow<Boolean> = context.settingsDataStore.data.map { preferences ->
        preferences[autoCheckUpdatesKey] ?: true
    }

    suspend fun setAutoCheckUpdates(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[autoCheckUpdatesKey] = enabled
        }
    }

    val developerMode: Flow<Boolean> = context.settingsDataStore.data.map { preferences ->
        preferences[developerModeKey] ?: false
    }

    private val installedDistributionsKey = stringPreferencesKey("installed_distributions_json")

    val onboardingCompleted: Flow<Boolean> = context.settingsDataStore.data.map { it[onboardingCompletedKey] ?: false }
    suspend fun setOnboardingCompleted(value: Boolean) { context.settingsDataStore.edit { it[onboardingCompletedKey] = value } }
    val selectedDistribution: Flow<String> = context.settingsDataStore.data.map { it[selectedDistributionKey] ?: "ubuntu" }
    suspend fun setSelectedDistribution(value: String) { context.settingsDataStore.edit { it[selectedDistributionKey] = value } }

    /** 已安装的 Linux 发行版 ID 列表 */
    val installedDistributions: Flow<List<String>> = context.settingsDataStore.data.map { prefs ->
        val json = prefs[installedDistributionsKey]
        if (!json.isNullOrBlank()) {
            runCatching { jsonHelper.decodeFromString<List<String>>(json) }.getOrDefault(listOf(prefs[selectedDistributionKey] ?: "ubuntu"))
        } else {
            listOf(prefs[selectedDistributionKey] ?: "ubuntu")
        }
    }

    suspend fun setInstalledDistributions(list: List<String>) {
        context.settingsDataStore.edit {
            it[installedDistributionsKey] = jsonHelper.encodeToString(list.distinct())
        }
    }

    suspend fun addInstalledDistribution(id: String) {
        context.settingsDataStore.edit { prefs ->
            val json = prefs[installedDistributionsKey]
            val current = if (!json.isNullOrBlank()) {
                runCatching { jsonHelper.decodeFromString<List<String>>(json).toMutableList() }.getOrDefault(mutableListOf())
            } else mutableListOf(prefs[selectedDistributionKey] ?: "ubuntu")
            if (id !in current) current.add(id)
            prefs[installedDistributionsKey] = jsonHelper.encodeToString(current.distinct())
        }
    }

    suspend fun removeInstalledDistribution(id: String) {
        context.settingsDataStore.edit { prefs ->
            val json = prefs[installedDistributionsKey]
            val current = if (!json.isNullOrBlank()) {
                runCatching { jsonHelper.decodeFromString<List<String>>(json).toMutableList() }.getOrDefault(mutableListOf())
            } else mutableListOf(prefs[selectedDistributionKey] ?: "ubuntu")
            current.remove(id)
            prefs[installedDistributionsKey] = jsonHelper.encodeToString(current.distinct())
        }
    }

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

    /** 全局推理深度：auto / disabled / low / medium / high（作用于未单独设置强度的模型）。 */
    val defaultReasoningDepth: Flow<String> = context.settingsDataStore.data.map { it[defaultReasoningDepthKey] ?: "auto" }
    suspend fun setDefaultReasoningDepth(value: String) {
        context.settingsDataStore.edit { it[defaultReasoningDepthKey] = value }
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

    /**
     * 上下文 Token 预算（默认 128000）。当模型未单独配置 contextTokens 时作为兜底，
     * apiMessages 据此做滑动窗口裁剪，防止长会话撞上下文窗口上限。
     */
    private val contextBudgetTokensKey = androidx.datastore.preferences.core.intPreferencesKey("agent_context_budget_tokens")
    val contextBudgetTokens: Flow<Int> = context.settingsDataStore.data.map { it[contextBudgetTokensKey] ?: 128_000 }
    suspend fun setContextBudgetTokens(value: Int) { context.settingsDataStore.edit { it[contextBudgetTokensKey] = value.coerceIn(4_000, 2_000_000) } }

    /**
     * 单轮最多允许执行的工具调用数量（默认 12）。超过则本轮回填占位结果并提示模型，
     * 防止一次爆发大量工具调用耗尽上下文或陷入失控循环。
     */
    private val maxToolsPerRoundKey = androidx.datastore.preferences.core.intPreferencesKey("agent_max_tools_per_round")
    val maxToolsPerRound: Flow<Int> = context.settingsDataStore.data.map { it[maxToolsPerRoundKey] ?: 12 }
    suspend fun setMaxToolsPerRound(value: Int) { context.settingsDataStore.edit { it[maxToolsPerRoundKey] = value.coerceIn(1, 50) } }

    /**
     * 连续失败熔断阈值（默认 8）。当连续 N 轮工具调用全部失败时，主动终止循环并提示用户，
     * 避免模型在"调用→失败→再调用"中死循环空转。
     */
    private val maxConsecutiveFailuresKey = androidx.datastore.preferences.core.intPreferencesKey("agent_max_consecutive_failures")
    val maxConsecutiveFailures: Flow<Int> = context.settingsDataStore.data.map { it[maxConsecutiveFailuresKey] ?: 8 }
    suspend fun setMaxConsecutiveFailures(value: Int) { context.settingsDataStore.edit { it[maxConsecutiveFailuresKey] = value.coerceIn(1, 50) } }

    /**
     * 危险命令闸门：对 base 执行的破坏性 shell 命令做保护。
     * true = 拦截（命中危险模式时拒绝执行并提示用户，默认）；
     * false = 放行（遵循既有系统提示词约定，由模型自行判断）。
     */
    private val destructiveGuardEnabledKey = booleanPreferencesKey("agent_destructive_guard_enabled")
    val destructiveGuardEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[destructiveGuardEnabledKey] ?: true }
    suspend fun setDestructiveGuardEnabled(value: Boolean) { context.settingsDataStore.edit { it[destructiveGuardEnabledKey] = value } }

    // ==================== 内置 ADB（宿主桥接插件） ====================

    private val adbWirelessPortKey = androidx.datastore.preferences.core.intPreferencesKey("adb_wireless_debug_port")
    private val adbPairedOnceKey = booleanPreferencesKey("adb_wireless_paired_once")

    /** 无线调试主端口（开发者选项里"无线调试"显示的 IP:PORT），0 表示未配置。 */
    val adbWirelessPort: Flow<Int> = context.settingsDataStore.data.map { it[adbWirelessPortKey] ?: 0 }
    suspend fun setAdbWirelessPort(port: Int) {
        context.settingsDataStore.edit { it[adbWirelessPortKey] = port.coerceIn(0, 65535) }
    }

    /** 是否完成过一次无线调试配对（用于启动引导与状态展示）。 */
    val adbPairedOnce: Flow<Boolean> = context.settingsDataStore.data.map { it[adbPairedOnceKey] ?: false }
    suspend fun setAdbPairedOnce(value: Boolean) {
        context.settingsDataStore.edit { it[adbPairedOnceKey] = value }
    }

    /** 同步读取插件启用状态（供运行时启停判断）。 */
    suspend fun isPluginEnabled(pluginId: String): Boolean = allPlugins.first().any { it.id == pluginId && it.isEnabled }

    /** 插件启用状态的响应式流。 */
    fun isPluginEnabledFlow(pluginId: String): Flow<Boolean> = allPlugins.map { list -> list.any { it.id == pluginId && it.isEnabled } }

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
        all.map { skill -> if (skill.isImmutable) skill.copy(isEnabled = true) else skill.copy(isEnabled = skill.id in enabledIds) }
    }

    /** 获取当前已启用的 Skill 列表 */
    val activeSkills: Flow<List<top.wkbin.taixu.core.model.AgentSkill>> = allSkills.map { list -> list.filter { it.isEnabled } }

    suspend fun setSkillEnabled(skillId: String, enabled: Boolean) {
        val target = top.wkbin.taixu.core.model.BuiltinSkills.presets.find { it.id == skillId }
        if (target?.isImmutable == true && !enabled) return // 系统核心技能不可被关闭

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

    // ── Tool Detail: Auto-start & Access Token ──────────────────────────

    /** Whether the tool's gateway service should auto-start when the Linux Runtime becomes ready. */
    fun toolAutoStart(toolId: String): Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[booleanPreferencesKey("tool_${toolId}_auto_start")] ?: false
    }

    suspend fun setToolAutoStart(toolId: String, enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[booleanPreferencesKey("tool_${toolId}_auto_start")] = enabled
        }
    }

    /** Persisted access token for generating shareable URLs to web-type tool services. */
    fun toolAccessToken(toolId: String): Flow<String?> = context.settingsDataStore.data.map { prefs ->
        prefs[stringPreferencesKey("tool_${toolId}_access_token")]?.let(::decodeProtectedValue)
    }

    suspend fun setToolAccessToken(toolId: String, token: String?) {
        context.settingsDataStore.edit { prefs ->
            if (token == null) {
                prefs.remove(stringPreferencesKey("tool_${toolId}_access_token"))
            } else {
                prefs[stringPreferencesKey("tool_${toolId}_access_token")] = encodeProtectedValue(token)
            }
        }
    }

    // ── MCP (Model Context Protocol) Servers Configuration ─────────────

    private val mcpServersKey = stringPreferencesKey("mcp_servers_config_json")

    val mcpServers: Flow<List<top.wkbin.taixu.core.model.McpServerConfig>> = context.settingsDataStore.data.map { preferences ->
        val raw = preferences[mcpServersKey]
        if (raw.isNullOrBlank()) {
            top.wkbin.taixu.core.model.BuiltinMcpPresets.presets
        } else {
            val decoded = decodeMcpServers(raw)
            val existingIds = decoded.map { it.id }.toSet()
            val missingPresets = top.wkbin.taixu.core.model.BuiltinMcpPresets.presets.filter { it.id !in existingIds }
            if (missingPresets.isNotEmpty()) {
                decoded + missingPresets
            } else {
                decoded
            }
        }
    }

    suspend fun setMcpServers(servers: List<top.wkbin.taixu.core.model.McpServerConfig>) {
        context.settingsDataStore.edit { preferences ->
            preferences[mcpServersKey] = encodeMcpServers(servers)
        }
    }

    suspend fun toggleMcpServer(serverId: String, enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            val raw = preferences[mcpServersKey]
            val current = if (raw.isNullOrBlank()) {
                top.wkbin.taixu.core.model.BuiltinMcpPresets.presets.toMutableList()
            } else {
                decodeMcpServers(raw).toMutableList()
            }
            val index = current.indexOfFirst { it.id == serverId }
            if (index >= 0) {
                current[index] = current[index].copy(isEnabled = enabled)
                preferences[mcpServersKey] = encodeMcpServers(current)
            }
        }
    }

    suspend fun saveMcpServer(server: top.wkbin.taixu.core.model.McpServerConfig) {
        context.settingsDataStore.edit { preferences ->
            val raw = preferences[mcpServersKey]
            val current = if (raw.isNullOrBlank()) {
                top.wkbin.taixu.core.model.BuiltinMcpPresets.presets.toMutableList()
            } else {
                decodeMcpServers(raw).toMutableList()
            }
            val index = current.indexOfFirst { it.id == server.id }
            if (index >= 0) {
                current[index] = server
            } else {
                current.add(server)
            }
            preferences[mcpServersKey] = encodeMcpServers(current)
        }
    }

    suspend fun deleteMcpServer(serverId: String) {
        context.settingsDataStore.edit { preferences ->
            val raw = preferences[mcpServersKey]
            val current = if (raw.isNullOrBlank()) {
                top.wkbin.taixu.core.model.BuiltinMcpPresets.presets.toMutableList()
            } else {
                decodeMcpServers(raw).toMutableList()
            }
            current.removeAll { it.id == serverId }
            preferences[mcpServersKey] = encodeMcpServers(current)
        }
    }

    private fun encodeProtectedValue(value: String): String = PROTECTED_VALUE_PREFIX + secretManager.encrypt(value)

    /** 兼容升级前的明文值；下一次保存时会自动转为 Keystore 密文。 */
    private fun decodeProtectedValue(value: String): String? =
        if (value.startsWith(PROTECTED_VALUE_PREFIX)) {
            secretManager.decrypt(value.removePrefix(PROTECTED_VALUE_PREFIX))
        } else {
            value
        }

    private fun encodeMcpServers(servers: List<top.wkbin.taixu.core.model.McpServerConfig>): String =
        encodeProtectedValue(jsonHelper.encodeToString(servers))

    private fun decodeMcpServers(raw: String): List<top.wkbin.taixu.core.model.McpServerConfig> =
        decodeProtectedValue(raw)?.let { decoded ->
            runCatching {
                jsonHelper.decodeFromString<List<top.wkbin.taixu.core.model.McpServerConfig>>(decoded)
            }.getOrNull()
        } ?: top.wkbin.taixu.core.model.BuiltinMcpPresets.presets

    private companion object {
        const val PROTECTED_VALUE_PREFIX = "enc:v1:"
    }
}

