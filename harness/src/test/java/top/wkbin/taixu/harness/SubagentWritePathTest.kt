package top.wkbin.taixu.harness

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import top.wkbin.taixu.core.model.SubagentTaskSpec

/**
 * 阶段三写租约：验证同一波内写路径互不相交（可并行）、撞同文件被拆到不同波（串行）、
 * 缺省整工作区租约独占一波（串行）。
 */
class SubagentWritePathTest {

    private fun spec(taskName: String, vararg paths: String) = SubagentTaskSpec(
        taskName = taskName,
        role = "coder",
        prompt = "$taskName 任务",
        writePaths = paths.toList(),
    )

    @Test
    fun `disjoint write paths are packed into the same wave for parallel execution`() {
        val waves = buildWriteCleanWaves(
            listOf(
                spec("前端", "app/src/ui"),
                spec("后端", "server/src"),
                spec("测试", "tests/"),
            ),
        )

        assertEquals(1, waves.size)
        assertEquals(3, waves.single().size)
    }

    @Test
    fun `two writers of the same file are split into different waves (serial)`() {
        val waves = buildWriteCleanWaves(
            listOf(
                spec("改造A", "app/src/model/core.kt"),
                spec("改造B", "app/src/model/core.kt"),
            ),
        )

        assertEquals(2, waves.size)
        assertEquals(1, waves[0].size)
        assertEquals(1, waves[1].size)
    }

    @Test
    fun `whole-workspace task gets its own exclusive serial wave`() {
        val waves = buildWriteCleanWaves(
            listOf(
                spec("打包", "build.gradle.kts"),
                spec("全量整理"),         // 未声明 write_paths → 整工作区租约
                spec("迁移", "migrations/"),
            ),
        )

        // 全量整理独占一波（不得与其他任何任务同波）；
        // 打包与迁移路径不冲突 → 可与全量整理不同波、彼此同波并行。
        assertEquals(2, waves.size)
        val wholeWave = waves.first { wave -> wave.any { it.taskName == "全量整理" } }
        assertEquals(1, wholeWave.size)
        val otherWave = waves.first { wave -> wave.none { it.taskName == "全量整理" } }
        assertEquals(setOf("打包", "迁移"), otherWave.map { it.taskName }.toSet())
    }

    @Test
    fun `a declared-path task never shares a wave with a whole-workspace task`() {
        val waves = buildWriteCleanWaves(
            listOf(
                spec("任意改动"),
                spec("局部", "docs/readme.md"),
            ),
        )

        assertEquals(2, waves.size)
        assertTrue(waves.all { it.size == 1 })
    }

    @Test
    fun `path normalization treats leading slashes and trailing slashes as equivalent`() {
        val a = spec("A", "app/src/ui")
        val b = spec("B", "/app/src/ui/")
        val c = spec("C", "app/src/other")

        // a 与 b 归一化后同路径 → 冲突 → 不同波；c 与 a 不冲突 → 可与 a 同波。
        val waves = buildWriteCleanWaves(listOf(a, b, c))

        assertEquals(2, waves.size)
        val waveOfA = waves.first { "A" in it.map { s -> s.taskName } }
        assertTrue("C" in waveOfA.map { s -> s.taskName })
        assertFalse("B" in waveOfA.map { s -> s.taskName })
    }
}