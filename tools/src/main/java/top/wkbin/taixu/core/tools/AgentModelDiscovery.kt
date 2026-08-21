package top.wkbin.taixu.core.tools

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

@Singleton
class AgentModelDiscovery @Inject constructor(
    private val http: OkHttpClient,
    private val catalog: AgentProviderCatalog,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun discover(
        provider: AgentProviderDefinition,
        baseUrl: String,
        apiKey: String?,
    ): List<String> = withContext(Dispatchers.IO) {
        val cleanBaseUrl = ProviderEndpointPolicy.normalizeUrl(baseUrl)
        val url = when {
            provider.id == "custom" -> "${cleanBaseUrl.trimEnd('/')}/models"
            cleanBaseUrl.isNotBlank() && cleanBaseUrl.trimEnd('/') != provider.baseUrl.trimEnd('/') -> "${cleanBaseUrl.trimEnd('/')}/models"
            provider.modelsUrl.isNotBlank() -> provider.modelsUrl
            else -> "${provider.baseUrl.trimEnd('/')}/models"
        }
        val targetUrl = ProviderEndpointPolicy.normalizeUrl(url)
        require(targetUrl.isNotBlank() && ProviderEndpointPolicy.isSafeBaseUrl(targetUrl)) { "模型发现地址不安全或为空" }
        val isAnthropic = targetUrl.contains("api.anthropic.com")
        val request = Request.Builder().url(targetUrl)
            .apply {
                when {
                    isAnthropic -> {
                        // Anthropic 模型列表接口用 x-api-key 而非 Bearer
                        if (!apiKey.isNullOrBlank()) header("x-api-key", apiKey)
                        header("anthropic-version", "2023-06-01")
                    }
                    else -> if (!apiKey.isNullOrBlank()) header("Authorization", "Bearer $apiKey")
                }
            }
            .get().build()
        http.newCall(request).execute().use { response ->
            val body = response.body.string()
            check(response.isSuccessful) { "获取模型失败 HTTP ${response.code}：${body.take(200)}" }
            val root = json.parseToJsonElement(body).jsonObject
            val ids = root["data"]?.jsonArray?.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.content }
                ?: root["models"]?.jsonArray?.mapNotNull { item ->
                    val obj = item.jsonObject
                    obj["name"]?.jsonPrimitive?.content ?: obj["model"]?.jsonPrimitive?.content
                }.orEmpty()
            ids.filter(::isAgentModel).distinct().sorted()
        }
    }

    private fun isAgentModel(id: String): Boolean {
        val value = id.lowercase()
        return MEDIA_OR_NON_CHAT.none { token -> value.contains(token) }
    }

    private companion object {
        val MEDIA_OR_NON_CHAT = listOf(
            "embedding", "embed-", "rerank", "whisper", "tts", "speech", "audio",
            "image", "imagen", "dall-e", "flux", "stable-diffusion", "recraft",
            "video", "veo", "sora", "moderation", "guard", "classifier",
        )
    }
}
