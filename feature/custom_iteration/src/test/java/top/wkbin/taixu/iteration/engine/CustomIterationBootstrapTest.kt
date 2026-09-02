package top.wkbin.taixu.iteration.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CustomIterationBootstrap 单元测试：
 * 验证双包共存工作区隔离与引导 Prompt 关键约束（不依赖 Android Context）。
 */
class CustomIterationBootstrapTest {

    @Test
    fun workspaceNameMatchesIsolatedDirectory() {
        assertEquals("custom_taixu", CustomIterationBootstrap.WORKSPACE_NAME)
    }

    @Test
    fun officialRepoPointsToUpstream() {
        assertEquals("https://github.com/wkbin/taixu", CustomIterationBootstrap.OFFICIAL_REPO)
    }

    @Test
    fun bootstrapPromptContainsDualFlavorConstraints() {
        val prompt = CustomIterationBootstrap.BOOTSTRAP_PROMPT

        // 双包共存核心约束必须出现在引导 Prompt 中
        assertTrue("Prompt 必须约束包名 top.wkbin.taixu.dev", prompt.contains("top.wkbin.taixu.dev"))
        assertTrue("Prompt 必须约束应用名 TaiXuDev", prompt.contains("TaiXuDev"))
        // 凭据安全约束
        assertTrue("Prompt 必须包含 Token 安全约束", prompt.contains("不要把 Token、私钥写入命令历史或日志中"))
    }

    @Test
    fun bootstrapPromptDoesNotEmbedSensitiveTokens() {
        val prompt = CustomIterationBootstrap.BOOTSTRAP_PROMPT

        // 引导 Prompt 本身不得携带任何真实凭据或私钥片段
        assertFalse(prompt.contains("ghp_"))
        assertFalse(prompt.contains("-----BEGIN"))
        assertFalse(prompt.contains("PRIVATE KEY"))
    }
}