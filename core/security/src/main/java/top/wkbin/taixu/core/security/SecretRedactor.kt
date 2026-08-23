package top.wkbin.taixu.core.security

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecretRedactor @Inject constructor() {
    fun redact(value: String): String = value
        .replace(Regex("(?i)(OPENAI_API_KEY|API_KEY|TOKEN|AUTHORIZATION)\\s*[=:]\\s*[^\\s,;]+"), "$1=[REDACTED]")
        .replace(Regex("(?i)Bearer\\s+[A-Za-z0-9._~+/=-]+"), "Bearer [REDACTED]")
        .replace(Regex("\\b(?:sk-(?:ant-|proj-)?|AIza|xox[bap]-)[A-Za-z0-9._-]{12,}"), "[REDACTED]")

    fun redact(value: String, secretValues: Collection<String>, privacyMode: Boolean = true): String {
        if (!privacyMode) return redact(value)
        var result = redact(value)
        secretValues.asSequence()
            .filter { it.length >= 5 }
            .distinct()
            .sortedByDescending { it.length }
            .forEach { secret -> result = result.replace(secret, mask(secret)) }
        return result
    }

    private fun mask(secret: String): String = if (secret.length < 8) "*".repeat(secret.length)
    else secret.take(2) + "*".repeat(secret.length - 4) + secret.takeLast(2)
}
