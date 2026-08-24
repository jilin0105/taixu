package top.wkbin.taixu.core.datastore

import javax.inject.Inject
import javax.inject.Singleton

/** Narrow preference views keep consumers from depending on the complete settings schema. */
@Singleton
class AppearancePreferences @Inject constructor(private val store: SettingsDataStore) {
    val themeMode get() = store.themeMode
    val themeStyle get() = store.themeStyle
    val chengmingBackgroundUri get() = store.chengmingBackgroundUri
    val appFontScale get() = store.appFontScale
    val autoCheckUpdates get() = store.autoCheckUpdates
}

@Singleton
class TerminalPreferences @Inject constructor(private val store: SettingsDataStore) {
    val terminalFontSize get() = store.terminalFontSize
    val terminalColorScheme get() = store.terminalColorScheme
    val terminalHapticsEnabled get() = store.terminalHapticsEnabled
    suspend fun setTerminalFontSize(value: Int) = store.setTerminalFontSize(value)
}

@Singleton
class RuntimePreferences @Inject constructor(private val store: SettingsDataStore) {
    val selectedDistribution get() = store.selectedDistribution
    val mirrorPolicy get() = store.mirrorPolicy
    val mountDownloadEnabled get() = store.mountDownloadEnabled
    val mountDocumentsEnabled get() = store.mountDocumentsEnabled
    val mountSharedStorageEnabled get() = store.mountSharedStorageEnabled
    val executionMode get() = store.executionMode
    val adbWirelessPort get() = store.adbWirelessPort
    suspend fun readLegacyEnvironmentVariables() = store.readLegacyEnvironmentVariables()
    suspend fun clearLegacyEnvironmentVariables() = store.clearLegacyEnvironmentVariables()
    suspend fun setSelectedDistribution(value: String) = store.setSelectedDistribution(value)
    suspend fun setMirrorPolicy(value: String) = store.setMirrorPolicy(value)
    suspend fun setExecutionMode(value: top.wkbin.taixu.core.model.ExecutionMode) = store.setExecutionMode(value)
    suspend fun setAdbPairedOnce(value: Boolean) = store.setAdbPairedOnce(value)
}

data class LegacyEnvironmentVariable(
    val metadata: top.wkbin.taixu.core.model.EnvironmentVariable,
    val value: String,
)

@Singleton
class AgentPreferences @Inject constructor(private val store: SettingsDataStore) {
    val thinkingLanguage get() = store.thinkingLanguage
    val customSystemPromptEnabled get() = store.customSystemPromptEnabled
    val customSystemPrompt get() = store.customSystemPrompt
    val agentLoggingEnabled get() = store.agentLoggingEnabled
    val selectedDistribution get() = store.selectedDistribution
    val thinkingExpanded get() = store.thinkingExpanded
    val defaultReasoningDepth get() = store.defaultReasoningDepth
    val contextCompactionEnabled get() = store.contextCompactionEnabled
    val contextCompactionThreshold get() = store.contextCompactionThreshold
    val maxToolRounds get() = store.maxToolRounds
    val autoWorkspaceCwd get() = store.autoWorkspaceCwd
    val contextBudgetTokens get() = store.contextBudgetTokens
    val maxToolsPerRound get() = store.maxToolsPerRound
    val maxConsecutiveFailures get() = store.maxConsecutiveFailures
    val providerModel get() = store.providerModel
    val environmentPrivacyMode get() = store.environmentPrivacyMode
    suspend fun setThinkingExpanded(value: Boolean) = store.setThinkingExpanded(value)
    suspend fun removeModelApiKey(secretRef: String) = store.removeModelApiKey(secretRef)
}

@Singleton
class OnboardingPreferences @Inject constructor(private val store: SettingsDataStore) {
    val onboardingCompleted get() = store.onboardingCompleted
    val selectedDistribution get() = store.selectedDistribution
    val mirrorPolicy get() = store.mirrorPolicy
    suspend fun setSelectedDistribution(value: String) = store.setSelectedDistribution(value)
    suspend fun setMirrorPolicy(value: String) = store.setMirrorPolicy(value)
    suspend fun setModelApiKey(secretRef: String, value: String) = store.setModelApiKey(secretRef, value)
    suspend fun setOnboardingCompleted(value: Boolean) = store.setOnboardingCompleted(value)
}

@Singleton
class ToolPreferences @Inject constructor(private val store: SettingsDataStore) {
    fun toolAccessToken(distroId: String, toolId: String) = store.toolAccessToken(distroId, toolId)
    suspend fun setToolAccessToken(distroId: String, toolId: String, token: String?) =
        store.setToolAccessToken(distroId, toolId, token)
}
