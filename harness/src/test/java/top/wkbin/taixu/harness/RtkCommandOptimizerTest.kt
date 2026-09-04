package top.wkbin.taixu.harness

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RtkCommandOptimizerTest {
    @Test
    fun `eligible git command is wrapped with a same-shell fallback`() {
        val prepared = RtkCommandOptimizer.prepare("git status --short", enabled = true)

        assertTrue(prepared.commandLine.contains("/opt/taixu/bin/rtk\" rewrite 'git status --short'"))
        assertTrue(prepared.commandLine.contains("else\n        git status --short"))
        assertEquals("0", prepared.environment["RTK_TEE"])
    }

    @Test
    fun `shell syntax and file reads remain raw`() {
        assertEquals(
            "git status && git log --oneline",
            RtkCommandOptimizer.prepare("git status && git log --oneline", enabled = true).commandLine,
        )
        assertEquals("cat build.gradle.kts", RtkCommandOptimizer.prepare("cat build.gradle.kts", enabled = true).commandLine)
    }

    @Test
    fun `disabled setting bypasses RTK`() {
        val prepared = RtkCommandOptimizer.prepare("./gradlew test", enabled = false)

        assertEquals("./gradlew test", prepared.commandLine)
        assertFalse(prepared.environment.isNotEmpty())
    }

    @Test
    fun `single quoted arguments are preserved for rewrite`() {
        val prepared = RtkCommandOptimizer.prepare("git log --format='%h %s'", enabled = true)

        assertTrue(prepared.commandLine.contains("rewrite 'git log --format='\"'\"'%h %s'\"'\"''"))
        assertFalse(prepared.commandLine.contains("\\\\\""))
    }
}
