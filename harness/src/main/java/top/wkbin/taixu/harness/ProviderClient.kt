package top.wkbin.taixu.harness

import top.wkbin.taixu.core.database.AiModelDao
import top.wkbin.taixu.core.tools.ProviderRepository
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** 可独立测试的 HTTP 层：OpenAI 兼容 chat/completions 请求与响应解析。 */
internal class ChatApi(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
) {
    suspend fun chat(model: ModelConfig, messages: List<ApiMessage>): ChatResult =
        withContext(Dispatchers.IO) {
            okHttpClient.newCall(buildRequest(model, messages, stream = false)).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException(ProviderClient.formatHttpErrorMessage(response.code, body))
                }
                val parsed = json.decodeFromString(ChatCompletionResponse.serializer(), body)
                val message = parsed.choices.firstOrNull()?.message ?: ChatResponseMessage()
                val calls = message.tool_calls.orEmpty().mapNotNull { call ->
                    call.function.let { fn ->
                        if (fn.name.isBlank()) null else ApiToolCallSpec(call.id, fn.name, fn.arguments)
                    }
                }
                ChatResult(
                    content = message.content,
                    toolCalls = calls,
                    reasoningContent = message.reasoning_content,
                )
            }
        }

    /**
     * 流式调用：逐行读取 SSE（data: ...），每个内容增量立即通过 [onDelta] 回调
     * 交给 UI；工具调用参数按 index 分片累积。推理增量通过 [onReasoning] 回调。
     */
    @OptIn(InternalCoroutinesApi::class)
    suspend fun chatStream(
        model: ModelConfig,
        messages: List<ApiMessage>,
        onReasoning: (String) -> Unit = {},
        onDelta: (String) -> Unit,
    ): ChatResult = withContext(Dispatchers.IO) {
        val call = okHttpClient.newCall(buildRequest(model, messages, stream = true))
        // 关键：阻塞式 readUtf8Line() 不感知协程取消。用户点"停止"时必须主动 call.cancel()
        // 关闭底层 socket，阻塞读才会立刻抛出 IOException 退出——否则要等读超时（最长 3 分钟），
        // 表现为"停止按钮没反应"。
        val cancelHandle = coroutineContext[Job]?.invokeOnCompletion(onCancelling = true) { call.cancel() }
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val rawBody = response.body?.string().orEmpty().take(512)
                    throw IllegalStateException(ProviderClient.formatHttpErrorMessage(response.code, rawBody))
                }
                val source = response.body?.source()
                    ?: throw IllegalStateException("LLM 流式响应无内容")
                val text = StringBuilder()
                // 推理模型的 thinking 内容（如 DeepSeek-R1 的 reasoning_content），后续轮次需原样传回
                val reasoningText = StringBuilder()
                val toolCalls = mutableMapOf<Int, ToolCallAccumulator>()
                while (true) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data == "[DONE]") break
                    val choice = runCatching {
                        (json.parseToJsonElement(data) as? JsonObject)
                            ?.get("choices")?.let { it as? JsonArray }?.firstOrNull() as? JsonObject
                    }.getOrNull() ?: continue
                    val delta = choice["delta"] as? JsonObject
                    delta?.get("content")?.let { it as? JsonPrimitive }?.contentOrNull?.let { chunk ->
                        if (chunk.isNotEmpty()) {
                            text.append(chunk)
                            onDelta(chunk)
                        }
                    }
                    // 推理增量：兼容 reasoning_content（DeepSeek/GLM 等）与 reasoning（OpenRouter 等网关）两种字段名
                    val reasoningChunk = (delta?.get("reasoning_content") ?: delta?.get("reasoning"))
                        ?.let { it as? JsonPrimitive }?.contentOrNull
                    reasoningChunk?.let { chunk ->
                        if (chunk.isNotEmpty()) {
                            reasoningText.append(chunk)
                            onReasoning(chunk)
                        }
                    }
                    delta?.get("tool_calls")?.let { it as? JsonArray }?.forEach { call2 ->
                        val callObj = call2 as? JsonObject ?: return@forEach
                        val index = callObj["index"]?.let { it as? JsonPrimitive }?.contentOrNull?.toIntOrNull() ?: 0
                        val accum = toolCalls.getOrPut(index) { ToolCallAccumulator() }
                        callObj["id"]?.let { it as? JsonPrimitive }?.contentOrNull
                            ?.takeIf { it.isNotEmpty() }?.let { accum.id = it }
                        val function = callObj["function"] as? JsonObject
                        function?.get("name")?.let { it as? JsonPrimitive }?.contentOrNull
                            ?.takeIf { it.isNotEmpty() }?.let { accum.name = it }
                        function?.get("arguments")?.let { it as? JsonPrimitive }?.contentOrNull
                            ?.let { accum.arguments.append(it) }
                    }
                }
                val calls = toolCalls.values.map { ApiToolCallSpec(it.id, it.name, it.arguments.toString()) }
                ChatResult(
                    content = text.toString().ifEmpty { null },
                    toolCalls = calls,
                    reasoningContent = reasoningText.toString().ifEmpty { null },
                )
            }
        } finally {
            cancelHandle?.dispose()
        }
    }

    private fun buildRequest(model: ModelConfig, messages: List<ApiMessage>, stream: Boolean): Request {
        val tools = ProviderClient.buildDynamicTools(model.dynamicMcpTools)
        val requestBody = json.encodeToString(
            ChatCompletionRequest.serializer(),
            ChatCompletionRequest(
                model = model.model,
                messages = messages,
                tools = tools,
                stream = stream,
                temperature = model.temperature,
                max_tokens = model.maxTokens,
                top_p = model.topP,
            ),
        )
        return Request.Builder()
            .url("${model.baseUrl.trimEnd('/')}/chat/completions")
            .header("Content-Type", "application/json")
            .apply {
                model.apiKey?.let { header("Authorization", "Bearer $it") }
            }
            .post(requestBody.toRequestBody(ProviderClient.JSON_MEDIA_TYPE))
            .build()
    }
}

/** 分片累积一次工具调用的 id/name/arguments（OpenAI 与 Anthropic 流式均复用）。 */
internal data class ToolCallAccumulator(
    var id: String = "",
    var name: String = "",
    val arguments: StringBuilder = StringBuilder(),
)

/** 解析后的模型运行配置。 */
data class ModelConfig(
    val name: String,
    val provider: String,
    val model: String,
    val baseUrl: String,
    val apiKey: String?,
    /** 接入协议：OPENAI 兼容或 Anthropic Messages API。 */
    val protocol: ApiProtocol = ApiProtocol.OPENAI,
    /** 推理参数（null = 服务端默认）。 */
    val temperature: Float? = null,
    val maxTokens: Int? = null,
    val topP: Float? = null,
    val dynamicMcpTools: List<top.wkbin.taixu.core.model.McpToolInfo> = emptyList(),
)

/** LLM 接入协议：绝大多数厂商提供 OpenAI 兼容端点；Anthropic Claude 需要专用适配。 */
enum class ApiProtocol { OPENAI, ANTHROPIC }

/** LLM 返回的一轮结果：纯文本 或 一个/多个工具调用。 */
data class ChatResult(
    val content: String?,
    val toolCalls: List<ApiToolCallSpec>,
    /** 推理模型输出的思考内容（DeepSeek 等），多轮对话需原样传回 API。 */
    val reasoningContent: String? = null,
) {
    val hasToolCalls: Boolean get() = toolCalls.isNotEmpty()
}

data class ApiToolCallSpec(
    val id: String,
    val name: String,
    val argumentsJson: String,
)

// ---------- OpenAI 兼容 chat/completions DTO ----------

@Serializable
data class ApiMessage(
    val role: String,
    val content: String? = null,
    val reasoning_content: String? = null,
    val tool_calls: List<ApiToolCall>? = null,
    val tool_call_id: String? = null,
)

@Serializable
data class ApiToolCall(
    val id: String,
    val type: String = "function",
    val function: ApiFunctionCall,
)

@Serializable
data class ApiFunctionCall(
    val name: String,
    val arguments: String,
)

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ApiMessage>,
    val tools: List<ApiToolDefinition>? = null,
    val tool_choice: String = "auto",
    val stream: Boolean = false,
    val temperature: Float? = null,
    val max_tokens: Int? = null,
    val top_p: Float? = null,
)

@Serializable
data class ApiToolDefinition(
    val type: String = "function",
    val function: ApiFunctionDefinition,
)

@Serializable
data class ApiFunctionDefinition(
    val name: String,
    val description: String,
    val parameters: kotlinx.serialization.json.JsonObject,
)

@Serializable
data class ChatCompletionResponse(
    val choices: List<ChatChoice> = emptyList(),
)

@Serializable
data class ChatChoice(
    val message: ChatResponseMessage = ChatResponseMessage(),
)

@Serializable
data class ChatResponseMessage(
    val content: String? = null,
    val reasoning_content: String? = null,
    val tool_calls: List<ApiToolCall>? = null,
)

/**
 * 调用 LLM（OpenAI 兼容 chat/completions，支持 tools/tool_calls）。
 *
 * 模型配置优先取 [AiModelDao] 中激活的 [top.wkbin.taixu.core.database.AiModelEntity]，
 * 未配置时回退到 [ProviderRepository]；API Key 始终从加密存储读取，绝不落库/落日志。
 */
@Singleton
class ProviderClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val providerRepository: ProviderRepository,
    private val modelDao: AiModelDao,
    private val mcpManager: top.wkbin.taixu.harness.mcp.McpManager,
    private val json: Json,
) {
    private val httpClient: OkHttpClient = okHttpClient.newBuilder()
        .callTimeout(CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .build()

    suspend fun resolveModel(): ModelConfig = withContext(Dispatchers.IO) {
        val active = modelDao.activeModel()
        val baseConfig = if (active != null) {
            active.toModelConfig(providerRepository)
        } else {
            ModelConfig(
                name = "默认",
                provider = providerRepository.provider.first(),
                model = providerRepository.model.first().ifBlank { DEFAULT_MODEL },
                baseUrl = providerRepository.baseUrl.first().ifBlank { DEFAULT_BASE_URL },
                apiKey = providerRepository.readApiKey(),
            )
        }
        val dynamicMcp = runCatching { mcpManager.getActiveMcpTools() }.getOrDefault(emptyList())
        baseConfig.copy(dynamicMcpTools = dynamicMcp)
    }

    /**
     * 同 [resolveModel]，但额外做最小配置校验：无激活模型且未设置 API Key 时
     * 直接抛出明确异常，让发送前就能拦截，而不是让 Agent 空转后以 401 告警收场。
     */
    suspend fun resolveConfigured(): ModelConfig = withContext(Dispatchers.IO) {
        val active = modelDao.activeModel()
        val providerKey = providerRepository.readApiKey().orEmpty()
        if (active == null && providerKey.isBlank()) {
            throw IllegalStateException("未配置模型或 API Key，请先在「设置 → 模型」中添加并激活一个模型")
        }
        val baseConfig = if (active != null) {
            active.toModelConfig(providerRepository)
        } else {
            val provider = providerRepository.provider.first()
            val baseUrl = providerRepository.baseUrl.first().ifBlank { DEFAULT_BASE_URL }
            ModelConfig(
                name = "默认",
                provider = provider,
                model = providerRepository.model.first().ifBlank { DEFAULT_MODEL },
                baseUrl = baseUrl,
                apiKey = providerKey.ifBlank { null },
                protocol = inferProtocol(baseUrl, provider),
            )
        }
        val dynamicMcp = runCatching { mcpManager.getActiveMcpTools() }.getOrDefault(emptyList())
        baseConfig.copy(dynamicMcpTools = dynamicMcp)
    }

    suspend fun chat(model: ModelConfig, messages: List<ApiMessage>): ChatResult = when (model.protocol) {
        ApiProtocol.ANTHROPIC -> AnthropicApi(httpClient, json).chat(model, messages)
        ApiProtocol.OPENAI -> ChatApi(httpClient, json).chat(model, messages)
    }

    /** 流式调用：内容增量通过 [onDelta] 实时回调，推理增量通过 [onReasoning] 实时回调。 */
    suspend fun chatStream(
        model: ModelConfig,
        messages: List<ApiMessage>,
        onReasoning: (String) -> Unit = {},
        onDelta: (String) -> Unit,
    ): ChatResult = when (model.protocol) {
        ApiProtocol.ANTHROPIC -> AnthropicApi(httpClient, json).chatStream(model, messages, onReasoning, onDelta)
        ApiProtocol.OPENAI -> ChatApi(httpClient, json).chatStream(model, messages, onReasoning, onDelta)
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.openai.com/v1"
        const val DEFAULT_MODEL = "gpt-4o-mini"
        private const val CALL_TIMEOUT_MS = 3 * 60 * 1000L

        /** Room 实体 → 运行配置：推理参数原样透传，协议按 Base URL / 厂商名自动推断。 */
        private suspend fun top.wkbin.taixu.core.database.AiModelEntity.toModelConfig(
            providerRepository: top.wkbin.taixu.core.tools.ProviderRepository,
        ): ModelConfig {
            val baseUrl = this.baseUrl.ifBlank { DEFAULT_BASE_URL }
            return ModelConfig(
                name = name,
                provider = provider,
                model = model,
                baseUrl = baseUrl,
                apiKey = apiKey.ifBlank { providerRepository.readApiKey().orEmpty() }.ifBlank { null },
                protocol = inferProtocol(baseUrl, provider),
                temperature = temperature,
                maxTokens = maxTokens,
                topP = topP,
            )
        }

        /** Anthropic 协议自动识别：官方域名或厂商名含 anthropic/claude。 */
        fun inferProtocol(baseUrl: String, provider: String): ApiProtocol {
            val host = runCatching { java.net.URI(baseUrl.trim()).host?.lowercase() }.getOrNull().orEmpty()
            val providerLower = provider.lowercase()
            return if (host == "api.anthropic.com" ||
                providerLower.contains("anthropic") ||
                providerLower.contains("claude")
            ) {
                ApiProtocol.ANTHROPIC
            } else {
                ApiProtocol.OPENAI
            }
        }

        fun formatHttpErrorMessage(code: Int, rawBody: String): String {
            val errorMsg = runCatching {
                val obj = Json.parseToJsonElement(rawBody) as? JsonObject
                val err = obj?.get("error") as? JsonObject
                err?.get("message")?.let { it as? JsonPrimitive }?.contentOrNull
            }.getOrNull()?.trim() ?: rawBody.take(300).trim()

            val lowerMsg = errorMsg.lowercase()
            return when {
                code == 403 && (lowerMsg.contains("free quota") || lowerMsg.contains("quota exhausted") || lowerMsg.contains("free tier")) ->
                    "API 免费额度已耗尽 (HTTP 403)：请前往模型服务商控制台充值、关闭免费层限制，或在太墟中切换其他可用模型。"
                code == 401 || lowerMsg.contains("invalid api key") || lowerMsg.contains("unauthorized") ->
                    "API Key 无效或未授权 (HTTP 401)：请在模型设置中检查并更新该服务商的 API Key。"
                code == 429 || lowerMsg.contains("rate limit") || lowerMsg.contains("insufficient_quota") || lowerMsg.contains("quota") ->
                    "API 额度已用尽或请求频率超限 (HTTP $code)：$errorMsg"
                code == 404 ->
                    "模型名称或 API 地址不存在 (HTTP 404)：请检查模型名称是否拼写正确。"
                errorMsg.isNotBlank() ->
                    "LLM 请求失败 (HTTP $code)：$errorMsg"
                else ->
                    "LLM 请求失败 (HTTP $code)"
            }
        }

        private const val READ_TIMEOUT_MS = 3 * 60 * 1000L
        internal val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        /** 工具 JSON Schema，与 ToolExecutor 的参数契约一一对应。 */
        val TOOLS: List<ApiToolDefinition> = listOf(
            ApiToolDefinition(
                function = ApiFunctionDefinition(
                    name = "read",
                    description = "读取文件内容（UTF-8，单文件上限 1MB）。路径可用相对路径或以 /workspace/ 开头。优先用它检查文件内容，而不是用 cat/sed。大文件用 offset（1 起始行号）和 limit（行数）分页读取，返回头部会标注总行数与当前窗口。若文件不存在或读取失败，用 base 的 ls/find 定位。",
                    parameters = Json.parseToJsonElement(
                        """{"type":"object","properties":{"path":{"type":"string","description":"文件路径"},"offset":{"type":"integer","description":"起始行号（1 起始），可选"},"limit":{"type":"integer","description":"读取的最大行数，可选"}},"required":["path"]}""",
                    ).jsonObject,
                ),
            ),
            ApiToolDefinition(
                function = ApiFunctionDefinition(
                    name = "write",
                    description = "创建或完全覆盖文件内容，自动创建父目录。只用于新文件或完整重写；若只想修改局部内容，请改用 edit。",
                    parameters = Json.parseToJsonElement(
                        """{"type":"object","properties":{"path":{"type":"string"},"content":{"type":"string"}},"required":["path","content"]}""",
                    ).jsonObject,
                ),
            ),
            ApiToolDefinition(
                function = ApiFunctionDefinition(
                    name = "edit",
                    description = "在文件中做精确文本替换。oldText 必须与原文逐字匹配且唯一，一次可传多个替换，但每个不能重叠或嵌套。oldText 重复或匹配多处会失败——先 read 确认内容再改。",
                    parameters = Json.parseToJsonElement(
                        """{"type":"object","properties":{"path":{"type":"string"},"oldText":{"type":"string"},"newText":{"type":"string"}},"required":["path","oldText","newText"]}""",
                    ).jsonObject,
                ),
            ),
            ApiToolDefinition(
                function = ApiFunctionDefinition(
                    name = "base",
                    description = "在 Debian Linux 沙箱中执行 shell 命令，返回退出码/stdout/stderr。用于安装软件（apt-get/npm/pip install）、运行脚本、查看文件/进程/网络、执行任意 bash。命令有超时与输出截断；可用 cwd 指定工作目录（会话关联工作区时默认在其目录执行）。",
                    parameters = Json.parseToJsonElement(
                        """{"type":"object","properties":{"command":{"type":"string","description":"要执行的 shell 命令"},"cwd":{"type":"string","description":"工作目录，默认 /root"}},"required":["command"]}""",
                    ).jsonObject,
                ),
            ),
            ApiToolDefinition(
                function = ApiFunctionDefinition(
                    name = "invoke_subagent",
                    description = "并发派发一个或多个专业角色子智能体（Subagents）执行调研、编写、编译或测试等特定子任务，并在完成后汇总结构化结论。每个子智能体在专属子会话中独立运行。",
                    parameters = Json.parseToJsonElement(
                        """{"type":"object","properties":{"subagents":{"type":"array","description":"子任务列表","items":{"type":"object","properties":{"taskName":{"type":"string","description":"子任务名称（如: 数据库结构调研 / 编写测试用例）"},"role":{"type":"string","description":"子智能体角色（如: researcher / coder / tester）"},"prompt":{"type":"string","description":"详细的任务指令与要求"}},"required":["taskName","role","prompt"]}}},"required":["subagents"]}""",
                    ).jsonObject,
                ),
            ),
        )

        /** 组装静态基础工具 + 动态 MCP 插件工具 */
        fun buildDynamicTools(mcpTools: List<top.wkbin.taixu.core.model.McpToolInfo> = emptyList()): List<ApiToolDefinition> {
            val list = TOOLS.toMutableList()
            mcpTools.forEach { mcp ->
                val fullToolName = "mcp__${mcp.serverId}__${mcp.name}"
                val params = runCatching {
                    Json.parseToJsonElement(mcp.parametersJson).jsonObject
                }.getOrDefault(JsonObject(emptyMap()))
                list.add(
                    ApiToolDefinition(
                        function = ApiFunctionDefinition(
                            name = fullToolName,
                            description = "【MCP 插件: ${mcp.serverName}】${mcp.description}",
                            parameters = params,
                        ),
                    ),
                )
            }
            return list
        }
    }
}
