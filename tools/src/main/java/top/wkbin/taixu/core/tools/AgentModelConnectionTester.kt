package top.wkbin.taixu.core.tools

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Singleton
class AgentModelConnectionTester @Inject constructor(private val http: OkHttpClient) {
    suspend fun test(baseUrl: String, model: String, apiKey: String?) = withContext(Dispatchers.IO) {
        require(ProviderEndpointPolicy.isSafeBaseUrl(baseUrl)) { "Base URL 不安全或为空" }
        val body = """{"model":"${model.replace("\\", "\\\\").replace("\"", "\\\"")}","messages":[{"role":"user","content":"Reply with OK"}],"max_tokens":8}"""
        val request = Request.Builder().url("${baseUrl.trimEnd('/')}/chat/completions")
            .header("Content-Type", "application/json")
            .apply { if (!apiKey.isNullOrBlank()) header("Authorization", "Bearer $apiKey") }
            .post(body.toRequestBody("application/json".toMediaType())).build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            check(response.isSuccessful) { "连接失败 HTTP ${response.code}：${text.take(240)}" }
        }
    }
}
