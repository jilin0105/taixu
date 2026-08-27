package top.wkbin.taixu.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import top.wkbin.taixu.core.model.ExecutionMode
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
import top.wkbin.taixu.harness.QueuedPrompt
import top.wkbin.taixu.harness.ContextWindowPolicy
import top.wkbin.taixu.harness.events.HarnessEvent
import top.wkbin.taixu.harness.events.HarnessEventBus
import top.wkbin.taixu.harness.mcp.McpManager
import top.wkbin.taixu.harness.queue.PromptQueue
import top.wkbin.taixu.harness.session.ConversationBranch
import top.wkbin.taixu.harness.session.LaneManager
import top.wkbin.taixu.runtime.WorkspaceManager
import top.wkbin.taixu.runtime.WorkspaceProject
import top.wkbin.taixu.core.tools.AgentModelDiscovery
import top.wkbin.taixu.core.tools.AgentProviderCatalog
import top.wkbin.taixu.core.tools.ProviderEndpointPolicy
import top.wkbin.taixu.core.tools.ProviderRepository
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import top.wkbin.taixu.runtime.terminal.TerminalSessionManager

private const val MAX_RUNTIME_EVENTS = 160

/** 空会话首屏的权限感知引导档位；决定开场提示卡的文案与色调。 */
enum class OnboardingPrivilege { SANDBOX, SANDBOX_UNLOCKABLE, SHIZUKU_READY, ROOT_READY }

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
    private val agentContextDao: top.wkbin.taixu.core.database.AgentContextRepository,
    private val compactionManager: top.wkbin.taixu.harness.compaction.CompactionManager,
    private val quickPhraseRepository: top.wkbin.taixu.core.database.QuickPhraseRepository,
    private val laneManager: LaneManager,
    private val eventBus: HarnessEventBus,
    private val modelDiscovery: AgentModelDiscovery,
    private val providerCatalog: AgentProviderCatalog,
    private val providerRepository: ProviderRepository,
    private val privilegeManager: top.wkbin.taixu.runtime.privilege.PrivilegeManager,
) : ViewModel() {

    /** 空会话首屏权限感知引导：按实际特权状态给出不同玩法提示。 */
    val privilegeOnboarding: StateFlow<OnboardingPrivilege?> =
        flow { emit(privilegeManager.getPrivilegeInfo()) }
            .map { info ->
                when {
                    info.mode == ExecutionMode.ROOT && info.modeActive -> OnboardingPrivilege.ROOT_READY
                    info.mode == ExecutionMode.SHIZUKU && info.modeActive -> OnboardingPrivilege.SHIZUKU_READY
                    info.shizukuAvailable || info.rootAvailable -> OnboardingPrivilege.SANDBOX_UNLOCKABLE
                    else -> OnboardingPrivilege.SANDBOX
                }
            }
            .stateIn(viewModelScope, SharingStarted.Lazily, null)

    private val _eventHistory = MutableStateFlow<Map<String, List<HarnessEvent>>>(emptyMap())
    private val _permissionRequests = kotlinx.coroutines.flow.MutableSharedFlow<HarnessEvent.PermissionRequired>(
        extraBufferCapacity = 8,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
    )
    val permissionRequests: kotlinx.coroutines.flow.SharedFlow<HarnessEvent.PermissionRequired> = _permissionRequests

    init {
        viewModelScope.launch {
            quickPhraseRepository.ensureInitialized()
        }
        viewModelScope.launch {
            eventBus.events.collect { event ->
                _eventHistory.value = _eventHistory.value.toMutableMap().apply {
                    this[event.sessionId] = (this[event.sessionId].orEmpty() + event).takeLast(MAX_RUNTIME_EVENTS)
                }
                if (event is HarnessEvent.PermissionRequired) {
                    _permissionRequests.tryEmit(event)
                }
            }
        }
    }

    val quickPhrases: StateFlow<List<top.wkbin.taixu.core.model.QuickPhrase>> = quickPhraseRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
    val queuedPrompts: StateFlow<List<QueuedPrompt>> = harnessLoop.queuedPrompts

    private val _sendMode = MutableStateFlow(ComposerSendMode.NEXT_RUN)
    val sendMode: StateFlow<ComposerSendMode> = _sendMode.asStateFlow()

    val runtimeEvents: StateFlow<List<HarnessEvent>> = combine(harnessLoop.currentSessionId, _eventHistory) { sessionId, history ->
        history[sessionId].orEmpty()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _branchRefresh = MutableStateFlow(0)
    private val branchMessageRevision = messages.map { list -> list.size to list.lastOrNull()?.id }.distinctUntilChanged()
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val branches: StateFlow<List<ConversationBranch>> = combine(
        harnessLoop.currentSessionId,
        branchMessageRevision,
        _branchRefresh,
    ) { sessionId, _, _ -> sessionId }.mapLatest { sessionId ->
        if (sessionId.isBlank()) emptyList() else runCatching { laneManager.branches(sessionId) }.getOrDefault(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 当前选中的会话 ID */
    val currentSessionId: StateFlow<String> = harnessLoop.currentSessionId
    /** 所有会话的多 Agent 并发运行状态映射 (IDLE / RUNNING / COMPLETED / FAILED) */
    val sessionRunStates: StateFlow<Map<String, top.wkbin.taixu.core.model.SessionRunState>> = harnessLoop.sessionRunStates
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val pendingApprovals: StateFlow<List<AgentApprovalRequestEntity>> = harnessLoop.currentSessionId.flatMapLatest { sessionId ->
        if (sessionId.isBlank()) kotlinx.coroutines.flow.flowOf(emptyList()) else approvalRepository.pendingForSession(sessionId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 当前会话的活跃结构化任务规划（模型通过 plan 工具写入的 AgentPlanEntity）。
     * 以 currentSessionId + 运行状态为键重新读取：一轮执行内状态多次变化，
     * 借此近似实时刷新看板进度；无规划或非活跃时为 null。
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val activePlan: StateFlow<top.wkbin.taixu.core.database.AgentPlanEntity?> =
        combine(harnessLoop.currentSessionId, harnessLoop.status) { sessionId, _ -> sessionId }
            .distinctUntilChanged()
            .flatMapLatest { sessionId ->
                kotlinx.coroutines.flow.flow {
                    emit(if (sessionId.isBlank()) null else agentContextDao.getActivePlan(sessionId)?.takeIf { it.status == "active" })
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * 当前会话最近一次上下文压缩的快照（折叠条数 + 摘要预览）。
     * 会话从未压缩时为 null——UI 据此隐藏提示横幅。
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val activeCompaction: StateFlow<top.wkbin.taixu.harness.compaction.CompactionSnapshot?> =
        combine(harnessLoop.currentSessionId, harnessLoop.status) { sessionId, _ -> sessionId }
            .distinctUntilChanged()
            .flatMapLatest { sessionId ->
                kotlinx.coroutines.flow.flow {
                    emit(if (sessionId.isBlank()) null else compactionManager.latestSnapshot(sessionId))
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 全量长期记忆（memory 工具写入），供记忆抽屉管理与模型上下文核对。 */
    val memories: StateFlow<List<top.wkbin.taixu.core.database.AgentMemoryEntity>> =
        agentContextDao.observeAllMemories()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val scratchpadRefresh = MutableStateFlow(0)

    /** 当前会话的草稿便签；随运行状态变化与手动刷新重建。 */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val scratchpads: StateFlow<List<top.wkbin.taixu.core.database.AgentScratchpadEntity>> =
        combine(
            harnessLoop.currentSessionId,
            harnessLoop.status,
            scratchpadRefresh,
        ) { sessionId, _, _ -> sessionId }
            .distinctUntilChanged()
            .flatMapLatest { sessionId ->
                flow {
                    emit(if (sessionId.isBlank()) emptyList() else agentContextDao.listScratchpads(sessionId))
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun deleteMemory(id: String) {
        viewModelScope.launch { agentContextDao.deleteMemoryById(id) }
    }

    fun deleteScratchpad(key: String) {
        val sessionId = currentSessionId.value
        if (sessionId.isBlank()) return
        viewModelScope.launch {
            agentContextDao.deleteScratchpad(sessionId, key)
            scratchpadRefresh.value++
        }
    }

    fun clearScratchpads() {
        val sessionId = currentSessionId.value
        if (sessionId.isBlank()) return
        viewModelScope.launch {
            agentContextDao.clearScratchpads(sessionId)
            scratchpadRefresh.value++
        }
    }

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

    fun applyQuickPhrase(phrase: top.wkbin.taixu.core.model.QuickPhrase) {
        if (phrase.content.trim() == "/clear") {
            createSession(context.getString(R.string.chat_new_session))
            _input.value = ""
        } else {
            _input.value = phrase.content
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

    fun setSendMode(mode: ComposerSendMode) {
        _sendMode.value = mode
    }

    fun send(customText: String? = null, imageUrls: List<String> = emptyList()) {
        val text = (customText ?: _input.value).trim()
        if (text.isBlank() && imageUrls.isEmpty()) return
        _input.value = ""
        // @ 仅引用已经显式启用的能力，不在发送阶段修改全局开关。
        if (!running.value) {
            harnessLoop.send(text, imageUrls = imageUrls)
        } else {
            when (_sendMode.value) {
                ComposerSendMode.STEER -> harnessLoop.steer(text, imageUrls = imageUrls)
                ComposerSendMode.FOLLOW_UP -> harnessLoop.followUp(text, imageUrls = imageUrls)
                ComposerSendMode.NEXT_RUN -> harnessLoop.send(text, imageUrls = imageUrls)
            }
        }
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

    fun retryToolCall(toolCallId: String) {
        if (running.value) return
        harnessLoop.retryToolCall(toolCallId)
    }

    fun createBranch(messageId: String, displayName: String) {
        val sessionId = currentSessionId.value
        if (sessionId.isBlank() || running.value) return
        viewModelScope.launch {
            runCatching {
                laneManager.createConversationBranch(sessionId, displayName, messageId)
                harnessLoop.activateBranch(messageId, sessionId)
            }
            _branchRefresh.value++
        }
    }

    fun switchBranch(branch: ConversationBranch) {
        val sessionId = currentSessionId.value
        if (sessionId.isBlank() || running.value) return
        viewModelScope.launch {
            harnessLoop.activateBranch(branch.leafId, sessionId)
            _branchRefresh.value++
        }
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

    fun removeQueuedPrompt(prompt: QueuedPrompt) {
        val sameQueue = queuedPrompts.value.filter { it.queue == prompt.queue }
        val index = sameQueue.indexOfFirst { it.id == prompt.id }
        if (index >= 0) harnessLoop.removeQueuedPrompt(prompt.queue, index)
    }

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

    private val _providerModelIds = MutableStateFlow<List<String>>(emptyList())
    val providerModelIds: StateFlow<List<String>> = _providerModelIds.asStateFlow()

    private val _discoveringProviderModels = MutableStateFlow(false)
    val discoveringProviderModels: StateFlow<Boolean> = _discoveringProviderModels.asStateFlow()

    private val _providerModelDiscoveryError = MutableStateFlow<String?>(null)
    val providerModelDiscoveryError: StateFlow<String?> = _providerModelDiscoveryError.asStateFlow()

    private val _modelPickerProfileId = MutableStateFlow<String?>(null)
    val modelPickerProfileId: StateFlow<String?> = _modelPickerProfileId.asStateFlow()

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
        selectModel(id)
    }

    fun selectModel(id: String, subModel: String? = null) {
        viewModelScope.launch {
            val entity = aiModelDao.findById(id) ?: return@launch
            aiModelDao.clearActive()
            if (!subModel.isNullOrBlank()) {
                val models = entity.model.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                if (models.contains(subModel) && models.firstOrNull() != subModel) {
                    val reordered = listOf(subModel) + models.filter { it != subModel }
                    aiModelDao.upsert(entity.copy(model = reordered.joinToString(", "), isActive = true))
                    return@launch
                }
            }
            aiModelDao.setActive(id)
        }
    }

    /** 打开某供应商档案后，通过 v1/models 拉取同端点可用模型列表。 */
    fun openProviderModelPicker(profileId: String) {
        _modelPickerProfileId.value = profileId
        discoverProviderModels(profileId)
    }

    fun closeProviderModelPicker() {
        _modelPickerProfileId.value = null
        _providerModelIds.value = emptyList()
        _providerModelDiscoveryError.value = null
        _discoveringProviderModels.value = false
    }

    fun discoverProviderModels(profileId: String) {
        viewModelScope.launch {
            val profile = aiModelDao.findById(profileId) ?: return@launch
            _discoveringProviderModels.value = true
            _providerModelDiscoveryError.value = null
            _providerModelIds.value = emptyList()
            val provider = providerCatalog.find(profile.provider)
            val baseUrl = profile.baseUrl.ifBlank { provider.baseUrl }
            val cleanUrl = ProviderEndpointPolicy.normalizeUrl(baseUrl)
            if (!ProviderEndpointPolicy.isSafeBaseUrl(cleanUrl)) {
                _providerModelDiscoveryError.value = context.getString(R.string.chat_model_discovery_bad_url)
                _discoveringProviderModels.value = false
                return@launch
            }
            val apiKey = profile.secretRef.takeIf { it.isNotBlank() }
                ?.let { providerRepository.readModelApiKeys(it).firstOrNull() }
                ?: providerRepository.readApiKey()
            runCatching { modelDiscovery.discover(provider, cleanUrl, apiKey) }
                .onSuccess { ids ->
                    _providerModelIds.value = ids
                    if (ids.isEmpty()) {
                        _providerModelDiscoveryError.value = context.getString(R.string.chat_model_discovery_empty)
                    }
                }
                .onFailure {
                    _providerModelDiscoveryError.value = it.message
                        ?: context.getString(R.string.chat_model_discovery_failed)
                }
            _discoveringProviderModels.value = false
        }
    }

    /**
     * 在同一供应商档案内切换模型 ID（复用 baseUrl / Key / 推理参数）。
     * 不新建档案，只更新当前档案的 model 字段。
     */
    fun switchModelInProfile(profileId: String, modelId: String) {
        val trimmed = modelId.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            val profile = aiModelDao.findById(profileId) ?: return@launch
            val displayName = when {
                profile.name.isBlank() -> trimmed
                profile.name == profile.model -> trimmed
                else -> profile.name
            }
            aiModelDao.upsert(
                profile.copy(
                    name = displayName,
                    model = trimmed,
                ),
            )
            aiModelDao.clearActive()
            aiModelDao.setActive(profileId)
            closeProviderModelPicker()
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

enum class ComposerSendMode(val queue: PromptQueue) {
    STEER(PromptQueue.STEER),
    FOLLOW_UP(PromptQueue.FOLLOW_UP),
    NEXT_RUN(PromptQueue.NEXT_RUN),
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
