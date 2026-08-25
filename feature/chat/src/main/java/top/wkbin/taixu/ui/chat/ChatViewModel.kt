package top.wkbin.taixu.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import top.wkbin.taixu.core.model.McpConnectionState
import top.wkbin.taixu.core.model.ApprovalMode
import top.wkbin.taixu.core.database.AiModelRepository
import top.wkbin.taixu.core.database.AiModelEntity
import top.wkbin.taixu.core.database.HarnessSessionRepository
import top.wkbin.taixu.core.database.HarnessSessionEntity
import top.wkbin.taixu.core.database.AgentSkillRepository
import top.wkbin.taixu.core.database.McpServerRepository
import top.wkbin.taixu.core.database.AgentApprovalRepository
import top.wkbin.taixu.core.database.AgentApprovalRequestEntity
import top.wkbin.taixu.core.datastore.AgentPreferences
import top.wkbin.taixu.harness.HarnessLoop
import top.wkbin.taixu.harness.HarnessMessage
import top.wkbin.taixu.harness.PendingMessage
import top.wkbin.taixu.harness.ContextWindowPolicy
import top.wkbin.taixu.harness.mcp.McpManager
import top.wkbin.taixu.runtime.WorkspaceManager
import top.wkbin.taixu.runtime.WorkspaceProject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import top.wkbin.taixu.feature.chat.R
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import top.wkbin.taixu.runtime.terminal.TerminalSessionManager

@HiltViewModel
class ChatViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val harnessLoop: HarnessLoop,
    private val sessionDao: HarnessSessionRepository,
    private val aiModelDao: AiModelRepository,
    private val workspaceManager: WorkspaceManager,
    private val settingsDataStore: AgentPreferences,
    private val linuxRuntime: top.wkbin.taixu.runtime.LinuxRuntime,
    private val terminalSessionManager: TerminalSessionManager,
    private val mcpManager: McpManager,
    private val agentSkillRepository: AgentSkillRepository,
    private val mcpServerRepository: McpServerRepository,
    private val approvalRepository: AgentApprovalRepository,
) : ViewModel() {

    val activeDistroId: StateFlow<String> = linuxRuntime.activeDistroId
    val installedDistros: StateFlow<List<top.wkbin.taixu.core.model.InstalledDistro>> = linuxRuntime.installedDistros

    fun switchDistro(distroId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            // 先关闭所有旧系统 PTY 会话，再切换发行版
            terminalSessionManager.closeAllSessions()
            linuxRuntime.switchActiveDistro(distroId)
        }
    }

    val messages: StateFlow<List<HarnessMessage>> = harnessLoop.messages
    val running: StateFlow<Boolean> = harnessLoop.running
    val error: StateFlow<String?> = harnessLoop.error
    val status: StateFlow<String?> = harnessLoop.status
    val thinkingLive: StateFlow<Boolean> = harnessLoop.thinkingLive
    val workspace: StateFlow<String> = harnessLoop.workspace
    val projectType: StateFlow<String> = harnessLoop.projectType
    /** 运行中排队的待发送消息（当前任务结束后自动接续）。 */
    val pendingMessages: StateFlow<List<PendingMessage>> = harnessLoop.pendingMessages

    /** 当前选中的会话 ID */
    val currentSessionId: StateFlow<String> = harnessLoop.currentSessionId
    /** 所有会话的多 Agent 并发运行状态映射 (IDLE / RUNNING / COMPLETED / FAILED) */
    val sessionRunStates: StateFlow<Map<String, top.wkbin.taixu.core.model.SessionRunState>> = harnessLoop.sessionRunStates
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val pendingApprovals: StateFlow<List<AgentApprovalRequestEntity>> = harnessLoop.currentSessionId.flatMapLatest { sessionId ->
        if (sessionId.isBlank()) kotlinx.coroutines.flow.flowOf(emptyList()) else approvalRepository.pendingForSession(sessionId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun resolveApproval(requestId: String, approved: Boolean) {
        harnessLoop.resolveApproval(requestId, approved)
    }

    val sessions: StateFlow<List<HarnessSessionEntity>> = sessionDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setCurrentSessionApprovalMode(mode: ApprovalMode) {
        val sessionId = currentSessionId.value
        if (sessionId.isBlank()) return
        viewModelScope.launch {
            sessionDao.setApprovalMode(sessionId, mode.id, System.currentTimeMillis())
        }
    }

    val models: StateFlow<List<AiModelEntity>> = aiModelDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val workspaces: StateFlow<List<WorkspaceProject>> = workspaceManager.observeProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 思考过程块是否默认展开（持久化，重启后保留）。 */
    val thinkingExpanded: StateFlow<Boolean> = settingsDataStore.thinkingExpanded
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setThinkingExpanded(value: Boolean) {
        viewModelScope.launch { settingsDataStore.setThinkingExpanded(value) }
    }

    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input.asStateFlow()

    val activeSkills: StateFlow<List<top.wkbin.taixu.core.model.AgentSkill>> = agentSkillRepository.activeSkills
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allSkills: StateFlow<List<top.wkbin.taixu.core.model.AgentSkill>> = agentSkillRepository.allSkills
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val mcpServers: StateFlow<List<top.wkbin.taixu.core.model.McpServerConfig>> = mcpServerRepository.servers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 当前会话上下文用量的 UI 估算。Harness 发请求时会用同一字符/token 近似值再做最终压缩，
     * 因此这里明确是预估值，而不是 provider 返回的精确 tokenizer 计数。
     */
    val contextUsage: StateFlow<ContextUsage> = combine(
        messages,
        models,
        allSkills,
        mcpServers,
        settingsDataStore.contextBudgetTokens,
    ) { currentMessages, currentModels, skills, mcps, defaultBudget ->
        ContextUsageInputs(
            currentMessages = currentMessages,
            activeModel = currentModels.firstOrNull { it.isActive },
            skills = skills,
            mcps = mcps,
            defaultBudget = defaultBudget,
        )
    }.combine(settingsDataStore.contextCompactionEnabled) { inputs, compactionEnabled ->
        val activeModel = inputs.activeModel
        val systemTokens = if (activeModel?.pureChatMode == true) {
            0
        } else {
            val skillTokens = inputs.skills.filter { it.isEnabled }.sumOf { ContextWindowPolicy.estimateTokens(it.systemPrompt) }
            val mcpTokens = inputs.mcps.filter { it.isEnabled }.sumOf {
                ContextWindowPolicy.estimateTokens("${it.name}\n${it.description}\n${it.command}\n${it.args.joinToString(" ")}")
            }
            1_600 + skillTokens + mcpTokens
        }
        val effectiveUsage = ContextWindowPolicy.estimateEffectiveUsage(
            messages = inputs.currentMessages,
            budget = (activeModel?.contextTokens ?: inputs.defaultBudget).coerceAtLeast(1),
            systemTokens = systemTokens,
            compactionEnabled = compactionEnabled,
        )
        ContextUsage(
            usedTokens = effectiveUsage.totalTokens,
            limitTokens = (activeModel?.contextTokens ?: inputs.defaultBudget).coerceAtLeast(1),
            systemTokens = systemTokens,
            toolTokens = effectiveUsage.toolTokens,
            conversationTokens = effectiveUsage.conversationTokens,
            compacted = effectiveUsage.keepFromIndex > 0,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ContextUsage())

    /** 各 MCP 服务的实时连通性状态（与 McpManager 共享，聊天挂载面板 / 设置页联动）。 */
    val mcpConnectionStates: StateFlow<Map<String, McpConnectionState>> = mcpManager.connectionStates

    fun refreshMcpConnections() {
        viewModelScope.launch { mcpManager.refreshConnections() }
    }

    fun setSkillEnabled(skillId: String, enabled: Boolean) {
        viewModelScope.launch {
            agentSkillRepository.setEnabled(skillId, enabled)
        }
    }

    fun setMcpServerEnabled(serverId: String, enabled: Boolean) {
        viewModelScope.launch {
            mcpServerRepository.setEnabled(serverId, enabled)
            mcpManager.refreshConnections()
        }
    }

    /** 斜杠指令建议列表（当输入以 / 开头时实时过滤展示，自动合并已激活的专精技能）。 */
    val matchingCommands: StateFlow<List<SlashCommandItem>> = kotlinx.coroutines.flow.combine(_input, agentSkillRepository.activeSkills) { text, skills ->
        if (text.startsWith("/")) SlashCommands.filterCommands(context, text, skills)
        else emptyList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** @ 艾特唤醒建议列表（当输入包含 @ 时实时过滤技能与 MCP 插件）。 */
    val matchingMentions: StateFlow<List<MentionItem>> = kotlinx.coroutines.flow.combine(
        _input,
        agentSkillRepository.allSkills,
        mcpServerRepository.servers,
    ) { text, skills, mcps ->
        val atIndex = text.lastIndexOf('@')
        if (atIndex < 0) return@combine emptyList()
        val mentionToken = text.substring(atIndex + 1)
        if (mentionToken.any { it.isWhitespace() }) return@combine emptyList()
        val query = mentionToken.lowercase()

        val skillMentions = skills.filter { it.isEnabled }.map { skill ->
            MentionItem(
                id = skill.id,
                name = skill.name,
                description = skill.description,
                category = context.getString(R.string.chat_skill_category),
                type = MentionType.SKILL,
                icon = top.wkbin.taixu.ui.components.RuntimeIconName.Brain,
            )
        }
        val mcpMentions = mcps.filter { it.isEnabled }.map { mcp ->
            MentionItem(
                id = mcp.id,
                name = mcp.name,
                description = context.getString(R.string.chat_mcp_service_description, mcp.transportType),
                category = context.getString(R.string.chat_mcp_category),
                type = MentionType.MCP_SERVER,
                icon = top.wkbin.taixu.ui.components.RuntimeIconName.Cpu,
            )
        }
        val all = skillMentions + mcpMentions
        if (query.isEmpty()) all
        else all.filter { it.name.lowercase().contains(query) || it.description.lowercase().contains(query) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 当前输入框中已挂载的技能与 MCP 标签列表（用于输入框顶部展示高亮双排 Chips）。 */
    val attachedMentions: StateFlow<List<MentionItem>> = kotlinx.coroutines.flow.combine(
        _input,
        agentSkillRepository.allSkills,
        mcpServerRepository.servers,
    ) { text, skills, mcps ->
        if (!text.contains("@")) return@combine emptyList()
        val regex = Regex("""@([^\s@,，:：\n]+)""")
        val matchedNames = regex.findAll(text).map { it.groupValues[1].trim().lowercase() }.toSet()
        if (matchedNames.isEmpty()) return@combine emptyList()

        val matchedSkills = skills.filter { skill ->
            skill.isEnabled && (
                skill.name.lowercase() in matchedNames || skill.id.lowercase() in matchedNames
            )
        }.map { skill ->
            MentionItem(
                id = skill.id,
                name = skill.name,
                description = skill.description,
                category = context.getString(R.string.chat_skill_category),
                type = MentionType.SKILL,
                icon = top.wkbin.taixu.ui.components.RuntimeIconName.Brain,
            )
        }

        val matchedMcps = mcps.filter { mcp ->
            mcp.isEnabled && (
                mcp.name.lowercase() in matchedNames || mcp.id.lowercase() in matchedNames
            )
        }.map { mcp ->
            MentionItem(
                id = mcp.id,
                name = mcp.name,
                description = mcp.description,
                category = context.getString(R.string.chat_mcp_category),
                type = MentionType.MCP_SERVER,
                icon = top.wkbin.taixu.ui.components.RuntimeIconName.Cpu,
            )
        }

        matchedSkills + matchedMcps
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _initializing = MutableStateFlow(true)
    val initializing: StateFlow<Boolean> = _initializing.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            // 恢复最近会话；没有则新建
            val latest = sessionDao.observeAll().first().firstOrNull()
            if (latest != null) {
                harnessLoop.loadSession(latest.id)
            } else {
                harnessLoop.newSession(context.getString(R.string.chat_new_session))
            }
            _initializing.value = false
        }
    }

    fun onInputChanged(value: String) {
        _input.value = value
    }

    fun applySlashCommand(command: SlashCommandItem) {
        if (command.command == "/clear") {
            createSession(context.getString(R.string.chat_new_session))
            _input.value = ""
        } else {
            _input.value = command.template
        }
    }

    fun applyMention(item: MentionItem) {
        val text = _input.value
        val atIndex = text.lastIndexOf('@')
        val prefix = if (atIndex >= 0) text.substring(0, atIndex) else text
        // Persist the stable id so names containing spaces or punctuation cannot be
        // truncated by the mention parser; the attached chip still shows the friendly name.
        _input.value = "${prefix}@${item.id} "
    }

    /** 从输入框中整块移除某个已挂载的 @能力 标签 */
    fun removeMention(item: MentionItem) {
        val current = _input.value
        // 正则替换 @name 及其后可能跟随的空格
        val updated = current.replace(Regex("""@${Regex.escape(item.name)}\s*"""), "")
            .replace(Regex("""@${Regex.escape(item.id)}\s*"""), "")
            .trimStart()
        _input.value = updated
    }

    fun triggerMentionInput() {
        val current = _input.value
        if (!current.endsWith("@")) {
            _input.value = if (current.isBlank()) "@" else "$current @"
        }
    }

    fun send(customText: String? = null, imageUrls: List<String> = emptyList()) {
        val text = (customText ?: _input.value).trim()
        if (text.isBlank() && imageUrls.isEmpty()) return
        _input.value = ""
        // 运行中不拦截：HarnessLoop 会把消息放入排队，当前任务结束后自动接续执行。
        // @ 仅引用已经显式启用的能力，不在发送阶段修改全局开关。
        harnessLoop.send(text, imageUrls = imageUrls)
    }

    /** 创建针对工具安装或沙箱异常的专属自愈会话并立即启动诊断 */
    fun startHealingTask(title: String, prompt: String) {
        viewModelScope.launch {
            harnessLoop.newSession(title = title)
            _input.value = ""
            harnessLoop.send(prompt)
        }
    }

    /** 重新生成最后一次回复 */
    fun regenerateLast() {
        if (running.value) return
        harnessLoop.regenerateLast()
    }

    /** 编辑并重新发送某条用户消息 */
    fun editAndResend(userMessageId: String, newText: String) {
        if (running.value || newText.isBlank()) return
        harnessLoop.truncateAndResend(userMessageId, newText)
    }

    /** 删除单条消息 */
    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            harnessLoop.deleteMessage(messageId)
        }
    }

    fun stop() = harnessLoop.cancel()

    fun removePendingMessage(index: Int) = harnessLoop.removePendingMessage(index)

    fun clearPendingMessages() = harnessLoop.clearPendingMessages()

    fun clearError() = harnessLoop.clearError()

    /** 新建会话（支持自定义标题并关联工作区）。 */
    fun createSession(title: String = "", workspace: String = "", projectType: String = "") {
        viewModelScope.launch {
            harnessLoop.newSession(title.trim().ifBlank { context.getString(R.string.chat_new_session) }, workspace, projectType)
        }
    }

    fun switchSession(id: String) {
        viewModelScope.launch { harnessLoop.loadSession(id) }
    }

    fun deleteSession(id: String) {
        viewModelScope.launch { harnessLoop.deleteSession(id) }
    }

    fun renameSession(id: String, title: String) {
        viewModelScope.launch { harnessLoop.renameSession(id, title) }
    }

    // ---- 模型管理 ----

    fun addModel(name: String, provider: String, model: String, baseUrl: String) {
        val trimmedName = name.trim().ifBlank { model }
        val trimmedModel = model.trim()
        if (trimmedModel.isBlank()) return
        viewModelScope.launch {
            val existing = aiModelDao.observeAll().first()
            val isFirst = existing.isEmpty()
            val id = "${provider.trim().lowercase()}-${trimmedModel.lowercase()}"
                .replace(Regex("[^a-z0-9-]"), "-")
            aiModelDao.upsert(
                AiModelEntity(
                    id = id,
                    name = trimmedName,
                    provider = provider.trim().ifBlank { trimmedModel },
                    model = trimmedModel,
                    baseUrl = baseUrl.trim(),
                    secretRef = "",
                    isActive = isFirst,
                    createdAt = System.currentTimeMillis(),
                ),
            )
            if (isFirst) aiModelDao.setActive(id)
        }
    }

    fun setActiveModel(id: String) {
        viewModelScope.launch {
            aiModelDao.clearActive()
            aiModelDao.setActive(id)
        }
    }

    fun updateActiveModelReasoning(mode: String?, effort: String?) {
        viewModelScope.launch {
            val active = aiModelDao.activeModel() ?: return@launch
            aiModelDao.updateReasoning(active.id, mode, effort)
        }
    }

    fun deleteModel(id: String) {
        viewModelScope.launch {
            aiModelDao.findById(id)?.secretRef?.takeIf { it.isNotBlank() }?.let { settingsDataStore.removeModelApiKey(it) }
            aiModelDao.delete(id)
        }
    }
}

private data class ContextUsageInputs(
    val currentMessages: List<HarnessMessage>,
    val activeModel: AiModelEntity?,
    val skills: List<top.wkbin.taixu.core.model.AgentSkill>,
    val mcps: List<top.wkbin.taixu.core.model.McpServerConfig>,
    val defaultBudget: Int,
)

data class ContextUsage(
    val usedTokens: Int = 0,
    val limitTokens: Int = 128_000,
    val systemTokens: Int = 0,
    val toolTokens: Int = 0,
    val conversationTokens: Int = 0,
    val compacted: Boolean = false,
)

data class MentionItem(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val type: MentionType,
    val icon: top.wkbin.taixu.ui.components.RuntimeIconName,
)

enum class MentionType {
    SKILL, MCP_SERVER
}
