package top.wkbin.taixu.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import top.wkbin.taixu.core.database.AiModelDao
import top.wkbin.taixu.core.database.AiModelEntity
import top.wkbin.taixu.core.database.HarnessSessionDao
import top.wkbin.taixu.core.database.HarnessSessionEntity
import top.wkbin.taixu.core.datastore.SettingsDataStore
import top.wkbin.taixu.harness.HarnessLoop
import top.wkbin.taixu.harness.HarnessMessage
import top.wkbin.taixu.runtime.WorkspaceManager
import top.wkbin.taixu.runtime.WorkspaceProject
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import top.wkbin.taixu.ui.terminal.TerminalSessionManager

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val harnessLoop: HarnessLoop,
    private val sessionDao: HarnessSessionDao,
    private val aiModelDao: AiModelDao,
    private val workspaceManager: WorkspaceManager,
    private val settingsDataStore: SettingsDataStore,
    private val linuxRuntime: top.wkbin.taixu.runtime.LinuxRuntime,
    private val terminalSessionManager: TerminalSessionManager,
) : ViewModel() {

    val activeDistroId: StateFlow<String> = linuxRuntime.activeDistroId
    val installedDistros: StateFlow<List<top.wkbin.taixu.core.model.InstalledDistro>> = linuxRuntime.installedDistros

    fun switchDistro(distroId: String) {
        viewModelScope.launch {
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
    /** 运行中排队的待发送消息（当前任务结束后自动接续）。 */
    val pendingMessages: StateFlow<List<String>> = harnessLoop.pendingMessages

    /** 当前选中的会话 ID */
    val currentSessionId: StateFlow<String> = harnessLoop.currentSessionId
    /** 所有会话的多 Agent 并发运行状态映射 (IDLE / RUNNING / COMPLETED / FAILED) */
    val sessionRunStates: StateFlow<Map<String, top.wkbin.taixu.core.model.SessionRunState>> = harnessLoop.sessionRunStates

    val sessions: StateFlow<List<HarnessSessionEntity>> = sessionDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    val activeSkills: StateFlow<List<top.wkbin.taixu.core.model.AgentSkill>> = settingsDataStore.activeSkills
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allSkills: StateFlow<List<top.wkbin.taixu.core.model.AgentSkill>> = settingsDataStore.allSkills
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val mcpServers: StateFlow<List<top.wkbin.taixu.core.model.McpServerConfig>> = settingsDataStore.mcpServers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setSkillEnabled(skillId: String, enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setSkillEnabled(skillId, enabled)
        }
    }

    fun setMcpServerEnabled(serverId: String, enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.toggleMcpServer(serverId, enabled)
        }
    }

    /** 斜杠指令建议列表（当输入以 / 开头时实时过滤展示，自动合并已激活的专精技能）。 */
    val matchingCommands: StateFlow<List<SlashCommandItem>> = kotlinx.coroutines.flow.combine(_input, settingsDataStore.activeSkills) { text, skills ->
        if (text.startsWith("/")) SlashCommands.filterCommands(text, skills)
        else emptyList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** @ 艾特唤醒建议列表（当输入包含 @ 时实时过滤技能与 MCP 插件）。 */
    val matchingMentions: StateFlow<List<MentionItem>> = kotlinx.coroutines.flow.combine(
        _input,
        settingsDataStore.allSkills,
        settingsDataStore.mcpServers,
    ) { text, skills, mcps ->
        val atIndex = text.lastIndexOf('@')
        if (atIndex < 0) return@combine emptyList()
        val query = text.substring(atIndex + 1).trim().lowercase()

        val skillMentions = skills.filter { !it.isImmutable }.map { skill ->
            MentionItem(
                id = skill.id,
                name = skill.name,
                description = skill.description,
                category = "专精技能",
                type = MentionType.SKILL,
                icon = top.wkbin.taixu.ui.components.RuntimeIconName.Brain,
            )
        }
        val mcpMentions = mcps.map { mcp ->
            MentionItem(
                id = mcp.id,
                name = mcp.name,
                description = if (mcp.isEnabled) "MCP 工具服务 (${mcp.transportType})" else "MCP 工具服务（点击将自动启用）",
                category = "MCP 插件",
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
        settingsDataStore.allSkills,
        settingsDataStore.mcpServers,
    ) { text, skills, mcps ->
        if (!text.contains("@")) return@combine emptyList()
        val regex = Regex("""@([^\s@,，:：\n]+)""")
        val matchedNames = regex.findAll(text).map { it.groupValues[1].trim().lowercase() }.toSet()
        if (matchedNames.isEmpty()) return@combine emptyList()

        val matchedSkills = skills.filter { !it.isImmutable && (it.name.lowercase() in matchedNames || it.id.lowercase() in matchedNames) }.map { skill ->
            MentionItem(
                id = skill.id,
                name = skill.name,
                description = skill.description,
                category = "专精技能",
                type = MentionType.SKILL,
                icon = top.wkbin.taixu.ui.components.RuntimeIconName.Brain,
            )
        }

        val matchedMcps = mcps.filter { mcp ->
            mcp.name.lowercase() in matchedNames || mcp.id.lowercase() in matchedNames
        }.map { mcp ->
            MentionItem(
                id = mcp.id,
                name = mcp.name,
                description = mcp.description,
                category = "MCP 插件",
                type = MentionType.MCP_SERVER,
                icon = top.wkbin.taixu.ui.components.RuntimeIconName.Cpu,
            )
        }

        matchedSkills + matchedMcps
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            // 恢复最近会话；没有则新建
            val latest = sessionDao.observeAll().first().firstOrNull()
            if (latest != null) {
                harnessLoop.loadSession(latest.id)
            } else {
                harnessLoop.newSession("新会话")
            }
        }
    }

    fun onInputChanged(value: String) {
        _input.value = value
    }

    fun applySlashCommand(command: SlashCommandItem) {
        if (command.command == "/clear") {
            createSession("新会话")
            _input.value = ""
        } else {
            _input.value = command.template
        }
    }

    fun applyMention(item: MentionItem) {
        val text = _input.value
        val atIndex = text.lastIndexOf('@')
        val prefix = if (atIndex >= 0) text.substring(0, atIndex) else text
        _input.value = "${prefix}@${item.name} "
        if (item.type == MentionType.MCP_SERVER) {
            setMcpServerEnabled(item.id, true)
        }
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
        // 运行中不拦截：HarnessLoop 会把消息放入排队，当前任务结束后自动接续执行
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
    fun createSession(title: String = "新会话", workspace: String = "") {
        viewModelScope.launch { harnessLoop.newSession(title.trim().ifBlank { "新会话" }, workspace) }
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
        viewModelScope.launch { aiModelDao.delete(id) }
    }
}

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
