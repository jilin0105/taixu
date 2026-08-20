package top.wkbin.taixu.core.security

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecretRedactor @Inject constructor() {
    fun redact(value: String): String = value
        .replace(Regex("(?i)(OPENAI_API_KEY|API_KEY|TOKEN|AUTHORIZATION)\\s*[=:]\\s*[^\\s,;]+"), "$1=[REDACTED]")
        .replace(Regex("(?i)Bearer\\s+[A-Za-z0-9._~+/=-]+"), "Bearer [REDACTED]")
        .replace(Regex("\\b(?:sk-(?:ant-|proj-)?|AIza|xox[bap]-)[A-Za-z0-9._-]{12,}"), "[REDACTED]")
}
