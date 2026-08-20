package top.wkbin.taixu.core.tools

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class ProviderManager @Inject constructor(
    private val providerRepository: ProviderRepository,
) {
    suspend fun environment(): Map<String, String> {
        val provider = providerRepository.provider.first().trim().lowercase()
        val apiKey = providerRepository.readApiKey()
        val baseUrl = providerRepository.baseUrl.first().trim()
        val model = providerRepository.model.first().trim()
        val environment = linkedMapOf<String, String>()
        val variable = when (provider) {
            "openai" -> "OPENAI_API_KEY"
            "anthropic", "claude" -> "ANTHROPIC_API_KEY"
            "google", "gemini" -> "GEMINI_API_KEY"
            "deepseek" -> "DEEPSEEK_API_KEY"
            "openrouter" -> "OPENROUTER_API_KEY"
            else -> null
        }
        if (!apiKey.isNullOrBlank() && variable != null) environment[variable] = apiKey
        if (baseUrl.isNotBlank() && ProviderEndpointPolicy.isSafeBaseUrl(baseUrl)) {
            environment["LINUXAI_BASE_URL"] = baseUrl
            providerEnvironmentName(provider, "BASE_URL")?.let { environment[it] = baseUrl }
        }
        if (model.isNotBlank()) {
            environment["LINUXAI_MODEL"] = model
            providerEnvironmentName(provider, "MODEL")?.let { environment[it] = model }
        }
        return environment
    }

    private fun providerEnvironmentName(provider: String, suffix: String): String? = when (provider) {
        "openai" -> "OPENAI_$suffix"
        "anthropic", "claude" -> "ANTHROPIC_$suffix"
        "google", "gemini" -> "GEMINI_$suffix"
        "deepseek" -> "DEEPSEEK_$suffix"
        "openrouter" -> "OPENROUTER_$suffix"
        else -> null
    }

}

object ProviderEndpointPolicy {
    fun isSafeBaseUrl(value: String): Boolean {
        val uri = runCatching { java.net.URI(value.trim()) }.getOrNull() ?: return false
        if (uri.userInfo != null || uri.host.isNullOrBlank()) return false
        return when (uri.scheme?.lowercase()) {
            "https" -> true
            "http" -> uri.host.lowercase() in LOOPBACK_HOSTS
            else -> false
        }
    }

    private val LOOPBACK_HOSTS = setOf("localhost", "127.0.0.1", "[::1]", "::1")
}
