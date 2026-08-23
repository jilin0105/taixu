package top.wkbin.taixu.core.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecretRedactorTest {
    private val redactor = SecretRedactor()

    @Test
    fun `redacts openai api key`() {
        val out = redactor.redact("config OPENAI_API_KEY=sk-abcdefghijklmnopqrstuvwxyz123 rest")
        assertFalse(out.contains("sk-abcdefghijklmnopqrstuvwxyz123"))
        assertTrue(out.contains("[REDACTED]"))
    }

    @Test
    fun `leaves normal text intact`() {
        val out = redactor.redact("installed node v22.22.3")
        assertEqualsIgnoreCase("installed node v22.22.3", out)
    }

    @Test
    fun `masks configured environment secret`() {
        val out = redactor.redact("value=ghp_1234567890abcdef", listOf("ghp_1234567890abcdef"))
        assertFalse(out.contains("ghp_1234567890abcdef"))
        assertTrue(out.contains("gh") && out.contains("ef"))
    }

    @Test
    fun `privacy mode can be disabled`() {
        val secret = "local-development-secret"
        val out = redactor.redact(secret, listOf(secret), privacyMode = false)
        assertTrue(out.contains(secret))
    }

    private fun assertEqualsIgnoreCase(a: String, b: String) {
        assertTrue(a.equals(b, ignoreCase = true))
    }
}
