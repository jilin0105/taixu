package top.wkbin.taixu.harness

import top.wkbin.taixu.core.database.AiModelDao
import top.wkbin.taixu.core.tools.ProviderRepository
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
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
                    throw IllegalStateException("LLM 请求失败 HTTP ${response.code}：${body.take(512)}")
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
    suspend fun chatStream(
        model: ModelConfig,
        messages: List<ApiMessage>,
        onReasoning: (String) -> Unit = {},
        onDelta: (String) -> Unit,
    ): ChatResult = withContext(Dispatchers.IO) {
        okHttpClient.newCall(buildRequest(model, messages, stream = true)).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("LLM 请求失败 HTTP ${response.code}：${response.body?.string().orEmpty().take(512)}")
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
                delta?.get("tool_calls")?.let { it as? JsonArray }?.forEach { call ->
                    val callObj = call as? JsonObject ?: return@forEach
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
    }

    private fun buildRequest(model: ModelConfig, messages: List<ApiMessage>, stream: Boolean): Request {
        val requestBody = json.encodeToString(
            ChatCompletionRequest.serializer(),
            ChatCompletionRequest(
                model = model.model,
                messages = messages,
                tools = ProviderClient.TOOLS,
                stream = stream,
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

    /** 分片累积一次工具调用的 id/name/arguments。 */
    private data class ToolCallAccumulator(
        var id: String = "",
        var name: String = "",
        val arguments: StringBuilder = StringBuilder(),
    )
}

/** 解析后的模型运行配置。 */
data class ModelConfig(
    val name: String,
    val provider: String,
    val model: String,
    val baseUrl: String,
    val apiKey: String?,
)

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
    private val json: Json,
) {
    private val httpClient: OkHttpClient = okHttpClient.newBuilder()
        .callTimeout(CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .build()

    suspend fun resolveModel(): ModelConfig = withContext(Dispatchers.IO) {
        val active = modelDao.activeModel()
        if (active != null) {
            ModelConfig(
                name = active.name,
                provider = active.provider,
                model = active.model,
                baseUrl = active.baseUrl.ifBlank { DEFAULT_BASE_URL },
                apiKey = active.apiKey.ifBlank { providerRepository.readApiKey().orEmpty() }.ifBlank { null },
            )
        } else {
            ModelConfig(
                name = "默认",
                provider = providerRepository.provider.first(),
                model = providerRepository.model.first().ifBlank { DEFAULT_MODEL },
                baseUrl = providerRepository.baseUrl.first().ifBlank { DEFAULT_BASE_URL },
                apiKey = providerRepository.readApiKey(),
            )
        }
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
        if (active != null) {
            ModelConfig(
                name = active.name,
                provider = active.provider,
                model = active.model,
                baseUrl = active.baseUrl.ifBlank { DEFAULT_BASE_URL },
                apiKey = active.apiKey.ifBlank { providerKey }.ifBlank { null },
            )
        } else {
            ModelConfig(
                name = "默认",
                provider = providerRepository.provider.first(),
                model = providerRepository.model.first().ifBlank { DEFAULT_MODEL },
                baseUrl = providerRepository.baseUrl.first().ifBlank { DEFAULT_BASE_URL },
                apiKey = providerKey.ifBlank { null },
            )
        }
    }

    suspend fun chat(model: ModelConfig, messages: List<ApiMessage>): ChatResult =
        ChatApi(httpClient, json).chat(model, messages)

    /** 流式调用：内容增量通过 [onDelta] 实时回调，推理增量通过 [onReasoning] 实时回调。 */
    suspend fun chatStream(
        model: ModelConfig,
        messages: List<ApiMessage>,
        onReasoning: (String) -> Unit = {},
        onDelta: (String) -> Unit,
    ): ChatResult = ChatApi(httpClient, json).chatStream(model, messages, onReasoning, onDelta)

    companion object {
        const val DEFAULT_BASE_URL = "https://api.openai.com/v1"
        const val DEFAULT_MODEL = "gpt-4o-mini"
        private const val CALL_TIMEOUT_MS = 3 * 60 * 1000L
        private const val READ_TIMEOUT_MS = 3 * 60 * 1000L
        internal val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        /** 工具 JSON Schema，与 ToolExecutor 的参数契约一一对应。 */
        val TOOLS: List<ApiToolDefinition> = listOf(
            ApiToolDefinition(
                function = ApiFunctionDefinition(
                    name = "read",
                    description = "读取文件内容（UTF-8，单文件上限 1MB）。路径可用相对路径或以 /workspace/ 开头。优先用它检查文件内容，而不是用 cat/sed。若文件不存在或读取失败，用 base 的 ls/find 定位。",
                    parameters = Json.parseToJsonElement(
                        """{"type":"object","properties":{"path":{"type":"string","description":"文件路径"}},"required":["path"]}""",
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
        )
    }
}
