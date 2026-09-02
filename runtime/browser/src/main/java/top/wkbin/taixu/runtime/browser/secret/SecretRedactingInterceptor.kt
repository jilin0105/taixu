package top.wkbin.taixu.runtime.browser.secret

import top.wkbin.taixu.core.security.SecretRedactor

/**
 * 把浏览器侧产出的字符串统一过 [SecretRedactor]，避免带 cookie/Authorization 的页面源码、
 * network headers、console message 直接回灌到 LLM。
 */
object SecretRedactingInterceptor {

    private val redactor by lazy { SecretRedactor() }

    fun apply(input: String): String = redactor.redact(input)

    /**
     * 对 headers 字典做敏感字段处理：对 name 命中（authorization / cookie / set-cookie）值打码，
     * 其余原样返回；非命中也走一次 [SecretRedactor.redact]，以防 value 里夹带密钥。
     */
    fun apply(headers: Map<String, String>): Map<String, String> {
        if (headers.isEmpty()) return headers
        val sensitiveKeys = setOf("authorization", "cookie", "set-cookie", "x-api-key", "x-auth-token")
        return headers.mapValues { (k, v) ->
            val redacted = redactor.redact(v)
            if (k.lowercase() in sensitiveKeys) "[REDACTED_${k.uppercase()}]" else redacted
        }
    }
}
