package top.wkbin.taixu.iteration.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TaiXuDevBuildCoordinator 单元测试：
 * 验证 GitHub Actions 云端构建调度指令模板与双包共存关键约束。
 */
class TaiXuDevBuildCoordinatorTest {

    @Test
    fun triggerWorkflowUsesExplicitBranchRef() {
        val cmd = TaiXuDevBuildCoordinator.CliCommands.triggerWorkflow(
            workflowName = "taixudev-build.yml",
            branch = "feature/taiuxdev-dual-flavor",
        )

        assertTrue("必须携带 --ref 指定特性分支", cmd.contains("--ref feature/taiuxdev-dual-flavor"))
        assertEquals(
            "gh workflow run taixudev-build.yml --ref feature/taiuxdev-dual-flavor",
            cmd,
        )
    }

    @Test
    fun watchWorkflowWaitsForExitStatus() {
        val cmd = TaiXuDevBuildCoordinator.CliCommands.watchWorkflow("1234567890")

        assertEquals("gh run watch 1234567890 --exit-status", cmd)
    }

    @Test
    fun downloadArtifactTargetsTaiXuDevArtifactName() {
        val cmd = TaiXuDevBuildCoordinator.CliCommands.downloadArtifact(
            runId = "1234567890",
            outputDir = "/storage/emulated/0/Download",
        )

        assertTrue("产物名必须为 taixudev-apk", cmd.contains("-n taixudev-apk"))
        assertTrue("必须指定下载目录", cmd.contains("-D /storage/emulated/0/Download"))
    }

    @Test
    fun checkFailedLogUsesLogFailedFlag() {
        val cmd = TaiXuDevBuildCoordinator.CliCommands.checkFailedLog("1234567890")

        assertEquals("gh run view 1234567890 --log-failed", cmd)
    }
}