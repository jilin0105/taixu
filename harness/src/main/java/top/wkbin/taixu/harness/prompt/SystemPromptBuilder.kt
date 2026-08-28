package top.wkbin.taixu.harness.prompt

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import top.wkbin.taixu.core.database.AgentContextRepository
import top.wkbin.taixu.core.database.AgentSkillRepository
import top.wkbin.taixu.core.database.AgentSubagentRepository
import top.wkbin.taixu.core.database.McpServerRepository
import top.wkbin.taixu.core.datastore.AgentPreferences
import top.wkbin.taixu.core.model.AgentSkill
import top.wkbin.taixu.core.model.BuiltinMcpPresets
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
    private val mcpServerRepository: McpServerRepository,
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

        // 系统核心 MCP 能力引导：内置 MCP 默认关闭，但 harness 必须知道其存在；
        // 未授权时引导 LLM 提示用户开启，授权开启后常驻本会话随时可调用。
        val mcpCapabilitySection = buildMcpCapabilitySection()

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
            mcpCapabilitySection,
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
     * 内置 MCP 能力引导章节：让 harness 知道太墟具备哪些系统核心 MCP 能力（即便默认关闭）。
     * - 已启用：常驻本会话，可直接调用对应 mcp__ 工具；
     * - 未启用：任务需要该能力时，先向用户说明并请求授权开启，待用户开启后再调用。
     */
    private suspend fun buildMcpCapabilitySection(): String {
        val enabledIds = runCatching {
            mcpServerRepository.servers.first().filter { it.isEnabled }.map { it.id }.toSet()
        }.getOrDefault(emptySet())
        val lines = BuiltinMcpPresets.presets.map { preset ->
            val enabled = preset.id in enabledIds
            val status = if (enabled) "已启用·常驻" else "未启用（默认关闭）"
            val trigger = mcpUsageGuidance[preset.id]
            val triggerLine = if (!trigger.isNullOrBlank()) "使用时机：$trigger。" else ""
            val usage = if (enabled) {
                "已授权常驻，可直接调用对应 mcp__ 工具。"
            } else {
                "未授权：一旦任务命中上述使用时机，请先向用户说明该能力并请求其到「设置 → MCP 插件与协议生态」开启，授权常驻后再调用；未授权前不得绕过或模拟。"
            }
            val desc = preset.description.replace(Regex("\\s+"), " ").trim()
            val brief = if (desc.length > 120) desc.take(117) + "…" else desc
            "- [${status}] ${preset.name}：${brief}。${triggerLine}${usage}"
        }
        if (lines.isEmpty()) return ""
        return "\n\n## 系统核心 MCP 能力（内置，授权后常驻生效）\n" +
            "太墟内置以下系统级 MCP 能力，默认关闭。任务命中其「使用时机」时应优先考虑该能力：" +
            "若已启用则直接调用对应 mcp__ 工具（常驻本会话，随时可用）；若未启用则先向用户说明并请求授权开启，未授权前不得绕过。\n" +
            lines.joinToString("\n")
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

        /**
         * 内置 MCP 的「使用时机」引导：让 harness 在任务发生前就知道该优先调用哪个系统核心能力，
         * 而不是等用户点名。key = 内置 MCP 的 serverId。
         */
        internal val mcpUsageGuidance: Map<String, String> = mapOf(
            "mcp_codegraph" to "代码检索（查找类/函数/符号定义、搜索代码实现）、项目重构、架构分析、代码理解、定位符号定义、梳理调用链与影响面时【优先使用】，替代盲目全量 grep",
            "mcp_websearch" to "需要联网搜索、获取最新信息、查找外部资料，或搜索到结果后抓取对应网页正文时使用",
            "mcp_git" to "分析 Git 历史提交、分支拓扑、Diff 差异与仓库状态时使用",
            "mcp_sqlite" to "查询、分析沙箱或工作区内的 SQLite 数据库时使用",
            "mcp_apktool" to "APK 逆向、清单权限解析、硬编码凭据提取、Smali 敏感代码检索时使用",
        )

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
