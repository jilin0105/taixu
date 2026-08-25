package top.wkbin.taixu.runtime

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildGuardAssetTest {
    private val assets = File("../app/src/main/assets")
    private val offlineAndroidInstaller =
        File("../assets/plugins/android-suite-offline/payload/scripts/install-android-suite.sh")

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

    @Test
    fun offlineAndroidInstallerDoesNotRequireSystemUnzip() {
        val script = offlineAndroidInstaller.readText()
        assertTrue(script.startsWith("#!/bin/sh\n"))
        assertFalse(script.contains('\r'))
        assertTrue(script.contains("extract_zip()"))
        assertTrue(script.contains("\"${'$'}JDK_HOME/bin/jar\" xf \"${'$'}archive\""))
        assertTrue(script.contains("extract_zip \"${'$'}ARCHIVES/gradle-${'$'}GRADLE_VERSION-bin.zip\""))
        assertTrue(script.contains("extract_zip \"${'$'}ARCHIVES/platform-34-ext7_r03.zip\""))
        assertTrue(script.contains("extract_zip \"${'$'}ARCHIVES/build-tools_r35_linux.zip\""))
        assertTrue(script.contains("extract_zip \"${'$'}ARCHIVES/android-sdk-tools-static-aarch64.zip\""))
        assertTrue(script.contains("extract_zip \"${'$'}ARCHIVES/ninja-linux-aarch64.zip\""))
    }

    @Test
    fun offlinePluginCompatibilityShimUsesJdkJar() {
        val installer = File("../tools/src/main/java/top/wkbin/taixu/runtime/tools/GenericRecipeInstaller.kt").readText()
        assertTrue(installer.contains("localUnzipCompatibilityCommand"))
        assertTrue(installer.contains("/opt/taixu/toolchains/android/jdk/bin/jar"))
        assertTrue(installer.contains("jar_bin"))
        assertTrue(installer.contains("TAIXU_TOOL_DIR/bin/unzip"))
    }
}
