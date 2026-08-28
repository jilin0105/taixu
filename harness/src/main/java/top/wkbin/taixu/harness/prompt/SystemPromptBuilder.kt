package top.wkbin.taixu.harness.prompt

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import top.wkbin.taixu.core.database.AgentContextRepository
import top.wkbin.taixu.core.database.AgentSkillRepository
import top.wkbin.taixu.core.database.AgentSubagentRepository
import top.wkbin.taixu.core.datastore.AgentPreferences
import top.wkbin.taixu.core.model.AgentSkill
import top.wkbin.taixu.core.tools.ToolRepository
import top.wkbin.taixu.harness.R
import top.wkbin.taixu.harness.ToolCallMode
import top.wkbin.taixu.harness.WorkspaceFileAccess

/**
 * Agent 系统提示词的统一构建器。
 *
 * 从原 HarnessLoop.buildSystemPrompt 迁移而来，聚合：基础模板、发行版环境、
 * 专精技能、已安装套件、长期记忆、活动任务规划、子智能体指引、工具调用协议、
 * 工作区上下文、项目说明与权限章节。
 */
@Singleton
class SystemPromptBuilder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsDataStore: AgentPreferences,
    private val skillRepository: AgentSkillRepository,
    private val toolRepository: ToolRepository,
    private val agentContextDao: AgentContextRepository,
    private val subagentRepository: AgentSubagentRepository,
    private val promptAssets: PromptAssetLoader,
    private val fileAccess: WorkspaceFileAccess,
    private val privilegeRenderer: PrivilegeSectionRenderer,
) {
    /** 组装完整系统提示词；各分节缺失时自然留空并由 joinToString 过滤。 */
    suspend fun build(
        workspacePath: String,
        toolCallMode: ToolCallMode = ToolCallMode.NATIVE,
        mentionedNames: Set<String> = emptySet(),
        sessionId: String = "",
        projectTypeOverride: String = "",
    ): String {
        val distroId = runCatching { settingsDataStore.selectedDistribution.first() }.getOrDefault("debian")
        val distroName = DistroCatalog.displayName(distroId)
        val pkgManager = DistroCatalog.packageManagerCommand(distroId)
        val customPromptEnabled = runCatching { settingsDataStore.customSystemPromptEnabled.first() }.getOrDefault(false)
        val customPrompt = runCatching { settingsDataStore.customSystemPrompt.first() }.getOrDefault("")
        val baseRawTemplate = if (customPromptEnabled && customPrompt.isNotBlank()) {
            customPrompt
        } else {
            promptAssets.read("prompts/agent_system.md")
        }

        val providerModelId = runCatching { settingsDataStore.providerModel.first() }.getOrDefault("")
        val resolvedTemplate = PromptVariableResolver.resolve(
            template = baseRawTemplate,
            context = context,
            modelId = providerModelId,
            modelName = providerModelId,
            charName = "太墟智枢",
            userName = "用户",
        )

        val allSkills = runCatching { skillRepository.allSkills.first() }.getOrDefault(emptyList())
        val selectedSkills = selectSkills(allSkills, mentionedNames)

        val skillSection = if (selectedSkills.isNotEmpty()) {
            "## 当前生效的专精技能指导规则 (Active Skills)\n\n" + selectedSkills.joinToString("\n\n") { skill ->
                "### [专精技能] " + skill.name + " (" + skill.category + ")\n" + skill.systemPrompt.trim()
            }
        } else ""

        val installedTools =
            runCatching {
                toolRepository.getForDistro(distroId).filter { it.state == top.wkbin.taixu.core.model.ToolState.INSTALLED.name }
            }.getOrDefault(emptyList())
        val installedToolsSection = if (installedTools.isNotEmpty()) {
            "\n\n## 当前 Linux 沙箱已就绪的开发套件与工具环境（已安装就绪，直接调用即可，切勿重复下载或重新安装）：\n" +
                installedTools.joinToString("\n") { tool ->
                    val ver = tool.installedVersion?.let { " (v$it)" } ?: ""
                    "- ${tool.name}$ver: ${tool.description}"
                }
        } else ""

        val memories = runCatching { agentContextDao.getMemoriesByScopes(listOf("global", "project", "session")) }
            .getOrDefault(emptyList())
        val memorySection = if (memories.isNotEmpty()) {
            "\n\n## 长期事实与偏好记忆 (Long-Term Memory)\n" +
                memories.joinToString("\n") { "- [${it.scope}/${it.kind}] ${it.key}: ${it.value}" }
        } else ""

        val activePlan = runCatching { agentContextDao.getActivePlan(sessionId) }.getOrNull()
        val planSection = if (activePlan != null && activePlan.status == "active") {
            "\n\n## 当前任务多步骤执行规划与进度看板 (Active Plan)\n目标：${activePlan.goal}\n步骤与状态：\n${activePlan.stepsJson}"
        } else ""

        val subagentSection = buildSubagentGuidance(toolCallMode)

        val projectContext = loadProjectContext(workspacePath)
        val workspaceSection = if (workspacePath.isNotBlank()) {
            "\n\n当前工作区：$workspacePath（base 命令默认在此目录执行；read/write/edit 的相对路径以此为根）"
        } else ""
        val workspaceGuidance = buildWorkspaceGuidance(
            workspacePath = workspacePath,
            projectTypeOverride = projectTypeOverride,
            distroName = distroName,
            toolCallMode = toolCallMode,
            installedTools = installedTools,
        )

        val toolCallSection = when (toolCallMode) {
            ToolCallMode.JSON_TEXT -> promptAssets.render("prompts/tool_call_json.md")
            ToolCallMode.DISABLED -> context.getString(R.string.harness_prompt_tool_call_disabled)
            ToolCallMode.NATIVE -> ""
        }

        val thinkingLang = runCatching { settingsDataStore.thinkingLanguage.first() }.getOrDefault("zh")
        val thinkingLanguageSection = when (thinkingLang) {
            "zh" -> context.getString(R.string.harness_prompt_thinking_language_zh)
            "en" -> context.getString(R.string.harness_prompt_thinking_language_en)
            else -> ""
        }

        val privilegeSection = runCatching { privilegeRenderer.render() }.getOrElse {
            // 渲染失败（如资产缺失）不等于能力不可用——旧的兜底文案会让模型
            // 在授权后误以为宿主通道被禁用。改为中性指示，以 host(status) 实测为准。
            context.getString(R.string.harness_prompt_privilege_render_failed)
        }

        val baseVariables = mapOf(
            "DISTRO_NAME" to distroName,
            "PKG_MANAGER" to pkgManager,
            "ACTIVE_SKILLS" to skillSection,
        )
        val basePrompt = if (customPromptEnabled && customPrompt.isNotBlank()) {
            baseVariables.entries.fold(resolvedTemplate) { prompt, (name, value) ->
                prompt.replace("{{$name}}", value)
            }.trim()
        } else {
            promptAssets.renderTemplate(
                path = "prompts/agent_system.md",
                template = resolvedTemplate,
                variables = baseVariables,
            )
        }
        return listOf(
            basePrompt,
            installedToolsSection,
            memorySection,
            planSection,
            subagentSection,
            toolCallSection,
            workspaceSection,
            workspaceGuidance,
            projectContext,
            privilegeSection,
            thinkingLanguageSection,
        ).filter { it.isNotBlank() }.joinToString("\n\n") { it.trim() }
    }

    /**
     * 技能选择策略：默认不常驻注入任何技能（保持 Prompt 精简零污染）；
     * 仅在会话显式钉选或当前轮次 @ 提及该专精技能时才注入生效。
     */
    internal fun selectSkills(
        allSkills: List<AgentSkill>,
        mentionedNames: Set<String>,
    ): List<AgentSkill> {
        if (mentionedNames.isEmpty()) {
            return emptyList()
        }
        return allSkills.filter { skill ->
            val nameLower = skill.name.lowercase()
            val idLower = skill.id.lowercase()
            val cmdLower = skill.triggerCommand?.removePrefix("/")?.lowercase().orEmpty()
            nameLower in mentionedNames || idLower in mentionedNames || (cmdLower.isNotEmpty() && cmdLower in mentionedNames)
        }
    }

    private suspend fun buildSubagentGuidance(toolCallMode: ToolCallMode): String {
        if (toolCallMode == ToolCallMode.DISABLED) return ""
        val profiles = runCatching { subagentRepository.enabledProfiles() }.getOrDefault(emptyList())
        if (profiles.isEmpty()) {
            return context.getString(R.string.harness_prompt_subagent_none)
        }
        val autoEnabled = runCatching { subagentRepository.autoDelegationEnabled.first() }.getOrDefault(true)
        val roleList = profiles.joinToString("\n") { profile ->
            "- role=\"${profile.id}\"：${profile.name}。${profile.description}"
        }
        val triggerPolicy = if (autoEnabled) {
            promptAssets.render("prompts/subagent_trigger_auto.md")
        } else {
            context.getString(R.string.harness_prompt_subagent_trigger_manual)
        }
        return promptAssets.render(
            "prompts/subagent_guidance.md",
            mapOf("TRIGGER_POLICY" to triggerPolicy, "ROLE_LIST" to roleList),
        )
    }

    private suspend fun loadProjectContext(workspacePath: String): String {
        if (workspacePath.isBlank()) return ""
        val sections = buildList {
            for (name in listOf("AGENTS.md", "CLAUDE.md", "README.md")) {
                val content = runCatching {
                    // WorkspaceFileAccess understands the canonical /workspace/... form.
                    fileAccess.read("$workspacePath/$name").getOrNull()
                }.getOrNull() ?: continue
                val trimmed = content.take(PROJECT_CONTEXT_MAX_BYTES)
                add(
                    "<project_instructions path=\"" + name + "\">\n" + trimmed +
                        (if (content.length > PROJECT_CONTEXT_MAX_BYTES) "\n…（文件过长已截断）" else "") +
                        "\n</project_instructions>",
                )
            }
        }
        if (sections.isEmpty()) return ""
        return "\n\n<project_context>\n当前工作区的项目说明与约定（自动加载，编码时务必遵守）：\n\n" +
            sections.joinToString("\n\n") + "\n</project_context>"
    }

    /**
     * Workspace context is deliberately injected independently of user skills.
     * A linked project must remain actionable even when the user has disabled
     * optional skills or never mentions them in the first message.
     */
    private suspend fun buildWorkspaceGuidance(
        workspacePath: String,
        projectTypeOverride: String,
        distroName: String,
        toolCallMode: ToolCallMode,
        installedTools: List<top.wkbin.taixu.core.database.ToolEntity>,
    ): String {
        if (workspacePath.isBlank()) return ""
        val entries = fileAccess.list(workspacePath).getOrNull().orEmpty().map { it.name }.toSet()
        val appEntries = if ("app" in entries) {
            fileAccess.list("$workspacePath/app").getOrNull().orEmpty().map { it.name }.toSet()
        } else {
            emptySet()
        }
        val detectedProjectType = detectProjectType(entries, appEntries)
        val projectType = when (projectTypeOverride.trim().uppercase()) {
            "ANDROID" -> "Android"
            "FLUTTER" -> "Flutter"
            "REVERSE" -> "Android APK 逆向"
            "GENERAL" -> "通用工程"
            else -> detectedProjectType
        }
        val markerText = entries.sorted().joinToString(", ").ifBlank { "（目录为空或暂时不可读）" }
        val toolNames = if (toolCallMode == ToolCallMode.DISABLED) {
            "工具调用已禁用"
        } else {
            "read, write, edit, base, memory, plan, scratchpad, history_search, history_read, invoke_subagent" +
                if (toolCallMode == ToolCallMode.JSON_TEXT) "（JSON 文本调用模式）" else ""
        }
        val installedToolNames = installedTools.joinToString(", ") { it.name }.ifBlank { "暂无已安装套件记录" }
        val typeAsset = when (projectType) {
            "Android" -> "prompts/workspace_android.md"
            "Flutter" -> "prompts/workspace_flutter.md"
            "Android APK 逆向" -> "prompts/workspace_reverse.md"
            else -> "prompts/workspace_general.md"
        }
        val typeGuidance = promptAssets.render(
            typeAsset,
            mapOf("MARKER_TEXT" to markerText, "WORKSPACE_PATH" to workspacePath),
        )
        return promptAssets.render(
            "prompts/workspace_context.md",
            mapOf(
                "WORKSPACE_PATH" to workspacePath,
                "DISTRO_NAME" to distroName,
                "TOOL_NAMES" to toolNames,
                "INSTALLED_TOOL_NAMES" to installedToolNames,
                "TYPE_GUIDANCE" to typeGuidance,
            ),
        )
    }

    companion object {
        const val PROJECT_CONTEXT_MAX_BYTES = 16 * 1024

        internal fun detectProjectType(entries: Set<String>, appEntries: Set<String>): String = when {
            "pubspec.yaml" in entries -> "Flutter"
            "settings.gradle.kts" in entries || "settings.gradle" in entries ||
                "build.gradle.kts" in entries || "build.gradle" in entries ||
                ("app" in entries && appEntries.any { it == "build.gradle" || it == "build.gradle.kts" }) -> "Android"
            "apk-info.properties" in entries || entries.any { it.endsWith(".apk", ignoreCase = true) } -> "Android APK 逆向"
            else -> "通用工程"
        }
    }
}
