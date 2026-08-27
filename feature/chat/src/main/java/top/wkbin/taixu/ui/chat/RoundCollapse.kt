package top.wkbin.taixu.ui.chat

import top.wkbin.taixu.harness.AssistantText
import top.wkbin.taixu.harness.CapabilityEvent
import top.wkbin.taixu.harness.HarnessMessage
import top.wkbin.taixu.harness.ToolCall
import top.wkbin.taixu.harness.ToolResult
import top.wkbin.taixu.harness.UserMessage

/**
 * 聊天流投影渲染项：
 * 将扁平消息流投影为包含折叠按钮与可见消息的渲染结构。
 */
sealed interface ChatRenderItem {
    val stableKey: String

    data class MessageItem(
        val message: HarnessMessage,
    ) : ChatRenderItem {
        override val stableKey: String get() = message.id
    }

    data class CollapseButtonItem(
        val roundKey: String,
        val hiddenSteps: Int,
        val totalSteps: Int,
        val hiddenDurationMs: Long,
        val isExpanded: Boolean,
    ) : ChatRenderItem {
        override val stableKey: String get() = "collapse_btn_$roundKey"
    }
}

/**
 * 轮次（Round）内部中间表示
 */
internal data class ChatRound(
    val roundKey: String,
    val userMessage: UserMessage?,
    val nonResultMessages: List<HarnessMessage>,
    val toolCalls: List<ToolCall>,
    val isLastRound: Boolean,
)

/**
 * 纯函数投影：将原始消息列表按轮次与折叠规则投影为 LazyColumn 的渲染项。
 *
 * @param messages 原始 Harness 消息流
 * @param toolResults 工具执行结果映射表（用于提取 durationMs）
 * @param expandedOverrides 手动展开/收起记忆表（roundKey -> isExpanded）
 */
fun projectChatMessages(
    messages: List<HarnessMessage>,
    toolResults: Map<String, ToolResult> = emptyMap(),
    expandedOverrides: Map<String, Boolean> = emptyMap(),
): List<ChatRenderItem> {
    if (messages.isEmpty()) return emptyList()

    // 1. 按 UserMessage 切分轮次
    val rounds = splitIntoRounds(messages)
    val result = mutableListOf<ChatRenderItem>()

    // 2. 遍历各轮并应用折叠规则
    for (round in rounds) {
        // 用户气泡优先放入
        if (round.userMessage != null) {
            result.add(ChatRenderItem.MessageItem(round.userMessage))
        }

        val totalSteps = round.toolCalls.size

        // 判定是否应当收拢：
        // 1) 仅当整轮步数 > 2 且非末轮时默认收拢；
        // 2) 手动状态覆盖优先 (true -> 摊开, false -> 收拢)。
        val manualOverride = expandedOverrides[round.roundKey]
        val shouldCollapse = when {
            totalSteps <= 2 -> false
            manualOverride != null -> !manualOverride
            round.isLastRound -> false
            else -> true
        }

        if (!shouldCollapse) {
            // 摊开态：如果总步数 > 2 且处于手动展开状态，在最顶部展示「收起」按钮
            if (totalSteps > 2 && manualOverride == true) {
                result.add(
                    ChatRenderItem.CollapseButtonItem(
                        roundKey = round.roundKey,
                        hiddenSteps = 0,
                        totalSteps = totalSteps,
                        hiddenDurationMs = 0L,
                        isExpanded = true,
                    ),
                )
            }
            // 放入轮内除 UserMessage 外的全部可见消息（跳过 ToolResult）
            for (msg in round.nonResultMessages) {
                if (msg !is UserMessage) {
                    result.add(ChatRenderItem.MessageItem(msg))
                }
            }
        } else {
            // 收拢态：隐藏最旧 (totalSteps - 2) 步，保留最新 2 步
            val hiddenCount = totalSteps - 2
            val hiddenToolCalls = round.toolCalls.take(hiddenCount)
            val hiddenToolCallIds = hiddenToolCalls.map { it.id }.toSet()

            // 累计隐藏步骤的执行耗时
            val hiddenDurationMs = hiddenToolCalls.sumOf { toolResults[it.id]?.durationMs ?: 0L }

            // 添加「展开更多」折叠按钮
            result.add(
                ChatRenderItem.CollapseButtonItem(
                    roundKey = round.roundKey,
                    hiddenSteps = hiddenCount,
                    totalSteps = totalSteps,
                    hiddenDurationMs = hiddenDurationMs,
                    isExpanded = false,
                ),
            )

            // 按「跟随规则」过滤轮内可见消息
            val visibleMessages = filterVisibleMessagesByFollowRule(
                messages = round.nonResultMessages,
                hiddenToolCallIds = hiddenToolCallIds,
            )

            for (msg in visibleMessages) {
                if (msg !is UserMessage) {
                    result.add(ChatRenderItem.MessageItem(msg))
                }
            }
        }
    }

    return result
}

/**
 * 将消息流切分为多个轮次（Round）
 */
private fun splitIntoRounds(messages: List<HarnessMessage>): List<ChatRound> {
    val nonResultMessages = messages.filter { it !is ToolResult }
    if (nonResultMessages.isEmpty()) return emptyList()

    val rounds = mutableListOf<ChatRound>()
    var currentRoundKey: String? = null
    var currentUserMessage: UserMessage? = null
    val currentMessages = mutableListOf<HarnessMessage>()

    // 检查是否有首个 UserMessage 之前的初始消息（如自愈/欢迎语）
    val firstUserIndex = nonResultMessages.indexOfFirst { it is UserMessage }

    for (i in nonResultMessages.indices) {
        val msg = nonResultMessages[i]
        if (msg is UserMessage) {
            // 结束上一轮
            if (currentRoundKey != null || currentMessages.isNotEmpty()) {
                val roundKey = currentRoundKey ?: "__initial_round__"
                val toolCalls = currentMessages.filterIsInstance<ToolCall>()
                rounds.add(
                    ChatRound(
                        roundKey = roundKey,
                        userMessage = currentUserMessage,
                        nonResultMessages = currentMessages.toList(),
                        toolCalls = toolCalls,
                        isLastRound = false,
                    ),
                )
                currentMessages.clear()
            }
            currentRoundKey = msg.id
            currentUserMessage = msg
            currentMessages.add(msg)
        } else {
            if (currentRoundKey == null && firstUserIndex != 0) {
                // 首个 UserMessage 之前
                currentRoundKey = "__initial_round__"
            }
            currentMessages.add(msg)
        }
    }

    // 处理末轮
    if (currentRoundKey != null || currentMessages.isNotEmpty()) {
        val roundKey = currentRoundKey ?: "__initial_round__"
        val toolCalls = currentMessages.filterIsInstance<ToolCall>()
        rounds.add(
            ChatRound(
                roundKey = roundKey,
                userMessage = currentUserMessage,
                nonResultMessages = currentMessages.toList(),
                toolCalls = toolCalls,
                isLastRound = true,
            ),
        )
    }

    return rounds
}

/**
 * 按照跟随规则过滤单轮内的可见消息：
 * 1. 隐藏 ToolCall 列表中属于 hiddenToolCallIds 的项；
 * 2. 轮内首条正文（出现在任何 ToolCall 之前）永远显示；
 * 3. 轮内最终正文（最后一条 AssistantText）永远显示；
 * 4. 中间正文片段跟随其前方最近的 ToolCall：该 ToolCall 被隐藏则同藏，可见则可见；
 * 5. CapabilityEvent 等事件消息保持可见。
 */
private fun filterVisibleMessagesByFollowRule(
    messages: List<HarnessMessage>,
    hiddenToolCallIds: Set<String>,
): List<HarnessMessage> {
    val itemsWithoutUser = messages.filter { it !is UserMessage }
    if (itemsWithoutUser.isEmpty()) return emptyList()

    val firstToolCallIndex = itemsWithoutUser.indexOfFirst { it is ToolCall }
    val lastAssistantIndex = itemsWithoutUser.indexOfLast { it is AssistantText }

    val visibleList = mutableListOf<HarnessMessage>()
    var latestPrecedingToolCallIsHidden = false

    for (index in itemsWithoutUser.indices) {
        val msg = itemsWithoutUser[index]
        when (msg) {
            is ToolCall -> {
                val isHidden = msg.id in hiddenToolCallIds
                latestPrecedingToolCallIsHidden = isHidden
                if (!isHidden) {
                    visibleList.add(msg)
                }
            }
            is AssistantText -> {
                val isFirstTextBeforeTools = firstToolCallIndex == -1 || index < firstToolCallIndex
                val isFinalText = index == lastAssistantIndex

                if (isFirstTextBeforeTools || isFinalText) {
                    // 首正文与最终正文永远显示
                    visibleList.add(msg)
                } else if (!latestPrecedingToolCallIsHidden) {
                    // 中间正文片段跟随前方最近的工具卡
                    visibleList.add(msg)
                }
            }
            is CapabilityEvent -> {
                visibleList.add(msg)
            }
            else -> {
                visibleList.add(msg)
            }
        }
    }

    return visibleList
}
