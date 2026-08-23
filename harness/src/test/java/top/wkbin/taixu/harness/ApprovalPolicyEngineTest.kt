package top.wkbin.taixu.harness

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import top.wkbin.taixu.core.model.ApprovalMode

class ApprovalPolicyEngineTest {
    private val policy = ApprovalPolicyEngine()
    private val workspace = "/workspace/project"

    private fun args(vararg pairs: Pair<String, String>) = buildJsonObject {
        pairs.forEach { (key, value) -> put(key, value) }
    }

    @Test
    fun `request mode allows read but gates state changing and external tools`() {
        assertFalse(policy.decide(ApprovalMode.REQUEST, HarnessTool.READ, args("path" to "a.txt"), workspace).required)
        assertTrue(policy.decide(ApprovalMode.REQUEST, HarnessTool.WRITE, args("path" to "a.txt", "content" to "x"), workspace).required)
        assertTrue(policy.decide(ApprovalMode.REQUEST, HarnessTool.BASE, args("command" to "git status"), workspace).required)
        assertTrue(policy.decide(ApprovalMode.REQUEST, HarnessTool.MCP, args("name" to "search"), workspace).required)
        assertTrue(policy.decide(ApprovalMode.REQUEST, HarnessTool.DOWNLOAD, args("destination" to "a.zip"), workspace).required)
    }

    @Test
    fun `assisted mode allows workspace edits and routine inspection or verification`() {
        assertFalse(policy.decide(ApprovalMode.ASSISTED, HarnessTool.WRITE, args("path" to "src/Main.kt", "content" to "x"), workspace).required)
        assertFalse(policy.decide(ApprovalMode.ASSISTED, HarnessTool.EDIT, args("path" to "/workspace/project/src/Main.kt"), workspace).required)
        assertFalse(policy.decide(ApprovalMode.ASSISTED, HarnessTool.BASE, args("command" to "./gradlew test"), workspace).required)
        assertFalse(policy.decide(ApprovalMode.ASSISTED, HarnessTool.BASE, args("command" to "git status"), workspace).required)
    }

    @Test
    fun `assisted mode gates outside workspace writes and external or risky commands`() {
        val outside = policy.decide(ApprovalMode.ASSISTED, HarnessTool.WRITE, args("path" to "/etc/hosts", "content" to "x"), workspace)
        assertTrue(outside.required)
        assertEquals("high", outside.riskLevel)
        assertTrue(policy.decide(ApprovalMode.ASSISTED, HarnessTool.WRITE, args("path" to "/workspace/other/file.txt", "content" to "x"), workspace).required)
        assertTrue(policy.decide(ApprovalMode.ASSISTED, HarnessTool.WRITE, args("path" to "../other/file.txt", "content" to "x"), workspace).required)

        assertTrue(policy.decide(ApprovalMode.ASSISTED, HarnessTool.BASE, args("command" to "rm -rf /"), workspace).required)
        assertEquals("critical", policy.decide(ApprovalMode.ASSISTED, HarnessTool.BASE, args("command" to "rm -rf /"), workspace).riskLevel)
        assertTrue(policy.decide(ApprovalMode.ASSISTED, HarnessTool.BASE, args("command" to "curl https://example.com"), workspace).required)
        assertTrue(policy.decide(ApprovalMode.ASSISTED, HarnessTool.BASE, args("command" to "apt-get install ripgrep"), workspace).required)
        assertTrue(policy.decide(ApprovalMode.ASSISTED, HarnessTool.BASE, args("command" to "git push"), workspace).required)
        assertTrue(policy.decide(ApprovalMode.ASSISTED, HarnessTool.BASE, args("command" to "git status; rm -rf build"), workspace).required)
        assertTrue(policy.decide(ApprovalMode.ASSISTED, HarnessTool.BASE, args("command" to "cat README.md > backup.txt"), workspace).required)
        assertTrue(policy.decide(ApprovalMode.ASSISTED, HarnessTool.BASE, args("command" to "find . -delete"), workspace).required)
        assertTrue(policy.decide(ApprovalMode.ASSISTED, HarnessTool.MCP, args("name" to "query"), workspace).required)
        assertTrue(policy.decide(ApprovalMode.ASSISTED, HarnessTool.DOWNLOAD, args("destination" to "a.zip"), workspace).required)
    }

    @Test
    fun `full access bypasses approval for every tool`() {
        val tools = HarnessTool.entries
        tools.forEach { tool ->
            val toolArgs = when (tool) {
                HarnessTool.WRITE, HarnessTool.EDIT -> args("path" to "/etc/hosts", "content" to "x")
                HarnessTool.BASE -> args("command" to "rm -rf /")
                HarnessTool.DOWNLOAD -> args("url" to "https://example.com/a.zip", "destination" to "a.zip")
                HarnessTool.READ -> args("path" to "/etc/passwd")
                else -> args("name" to "anything")
            }
            assertFalse("$tool should be unrestricted", policy.decide(ApprovalMode.FULL_ACCESS, tool, toolArgs, workspace).required)
        }
    }
}
