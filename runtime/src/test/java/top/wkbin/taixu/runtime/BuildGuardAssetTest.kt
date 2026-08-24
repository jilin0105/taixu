package top.wkbin.taixu.runtime

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildGuardAssetTest {
    private val assets = File("../app/src/main/assets")

    @Test
    fun managedBuildEntriesHavePortableShebangs() {
        listOf("gradle", "gradlew", "flutter", "taixu-build", "taixu-build-guard").forEach { name ->
            val script = File(assets, "bin/$name")
            assertTrue("missing $script", script.isFile)
            assertTrue("$name must use /bin/sh", script.readText().startsWith("#!/bin/sh\n"))
            assertFalse("$name must not contain CRLF", script.readText().contains('\r'))
        }
        listOf("taixu-build.sh", "taixu-build-analyze.sh", "taixu-build-verify.sh").forEach { name ->
            val script = File(assets, "scripts/$name")
            assertTrue("missing $script", script.isFile)
            assertTrue("$name must use /bin/sh", script.readText().startsWith("#!/bin/sh\n"))
            assertFalse("$name must not contain CRLF", script.readText().contains('\r'))
        }
    }

    @Test
    fun androidGuardPinsArmToolsAndDisablesSdkDownloads() {
        val guard = File(assets, "bin/taixu-build-guard").readText()
        assertTrue(guard.contains("android.builder.sdkDownload=false"))
        assertTrue(guard.contains("android.aapt2FromMavenOverride"))
        assertTrue(guard.contains("android-arm64"))
        assertTrue(guard.contains("/opt/taixu/scripts/taixu-build.sh"))
        assertTrue(File(assets, "scripts/taixu-build-verify.sh").isFile)
        assertTrue(File(assets, "scripts/taixu-build-analyze.sh").isFile)
    }

    @Test
    fun qemuFallbackIsExplicitAndNeverPretendsToModifyArmSession() {
        val guard = File(assets, "bin/taixu-build-guard").readText()
        assertTrue(guard.contains("[--qemu]"))
        val engine = File(assets, "scripts/taixu-build.sh").readText()
        assertTrue(engine.contains("普通 ARM64 终端不能直接切换"))
        assertTrue(engine.contains("analyze"))
        assertTrue(engine.contains("TAIXU_OFFLINE"))
    }
}
