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

    private fun assertEqualsIgnoreCase(a: String, b: String) {
        assertTrue(a.equals(b, ignoreCase = true))
    }
}
