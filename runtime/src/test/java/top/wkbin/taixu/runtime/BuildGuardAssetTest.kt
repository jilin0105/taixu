package top.wkbin.taixu.runtime

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildGuardAssetTest {
    private val assets = File("../app/src/main/assets")
    private val offlineAndroidInstaller =
        File("../assets/plugins/android-suite-offline/payload/scripts/install-android-suite.sh")
    private val offlineAndroidVerifier =
        File("../assets/plugins/android-suite-offline/payload/scripts/verify-android-suite.sh")
    private val offlineAndroidManifest =
        File("../assets/plugins/android-suite-offline/manifest.json")

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
    fun managedArmBuildsUseOnlyGradleNdkPathLocator() {
        val androidBuild = File(assets, "scripts/build_android.sh").readText()
        val flutterBuild = File(assets, "scripts/build_flutter.sh").readText()
        val ndkSetup = File(assets, "scripts/setup_termux_ndk.sh").readText()
        val managedNdkPolicy = File(assets, "scripts/taixu-android-ndk.gradle").readText()
        val buildEntry = File(assets, "scripts/taixu-build.sh").readText()

        listOf(androidBuild, flutterBuild).forEach { script ->
            assertTrue(script.contains("ndk\\.dir"))
            assertFalse(script.contains("ndk.dir=%s"))
        }
        assertTrue(ndkSetup.contains("androidExtension.ndkPath = taixuNdkPath"))
        assertTrue(managedNdkPolicy.contains("androidExtension.ndkPath = taixuNdkPath"))
        assertTrue(managedNdkPolicy.contains("/opt/taixu/toolchains/android/ndk"))
        assertTrue(buildEntry.contains("cp \"${'$'}managed_ndk_policy\" /root/.gradle/init.d/taixu-android-ndk.gradle"))
        listOf(androidBuild, flutterBuild, buildEntry).forEach { script ->
            assertTrue(script.contains("od -An -t x1 -j 18 -N 2"))
            assertTrue(script.contains("b700"))
            assertFalse(script.contains("-tu2"))
        }
    }

    @Test
    fun managedGradleConfigurationUsesConsistentMobileLimits() {
        val androidBuild = File(assets, "scripts/build_android.sh").readText()
        val qemuBuild = File(assets, "scripts/build_android_qemu.sh").readText()
        val androidSetup = File(assets, "scripts/setup_android_core.sh").readText()
        val offlineProperties = File("../assets/plugins/android-suite-offline/payload/config/gradle.properties").readText()

        listOf(androidBuild, qemuBuild).forEach { script ->
            assertTrue(script.contains("--no-daemon"))
            assertTrue(script.contains("--max-workers=2"))
            assertTrue(script.contains("-Xmx1024m"))
            assertTrue(script.contains("-XX:MaxMetaspaceSize=384m"))
        }
        listOf(androidSetup, offlineProperties).forEach { config ->
            assertTrue(config.contains("org.gradle.daemon=false"))
            assertTrue(config.contains("org.gradle.parallel=false"))
            assertTrue(config.contains("org.gradle.workers.max=2"))
            assertTrue(config.contains("org.gradle.jvmargs=-Xmx1024m"))
        }
    }

    @Test
    fun offlineAndroidInstallerDoesNotRequireSystemUnzip() {
        val script = offlineAndroidInstaller.readText()
        assertTrue(script.startsWith("#!/bin/sh\n"))
        assertFalse(script.contains('\r'))
        assertTrue(offlineAndroidVerifier.readText().startsWith("#!/bin/sh\n"))
        assertFalse(offlineAndroidVerifier.readText().contains('\r'))
        assertTrue(script.contains("extract_zip()"))
        assertTrue(script.contains("\"${'$'}JDK_HOME/bin/jar\" xf \"${'$'}archive\""))
        assertTrue(script.contains("extract_zip \"${'$'}ARCHIVES/gradle-${'$'}GRADLE_VERSION-bin.zip\""))
        assertTrue(script.contains("extract_zip \"${'$'}ARCHIVES/platform-34-ext7_r03.zip\""))
        assertTrue(script.contains("extract_zip \"${'$'}ARCHIVES/build-tools_r35_linux.zip\""))
        assertTrue(script.contains("extract_zip \"${'$'}ARCHIVES/android-sdk-tools-static-aarch64.zip\""))
        assertTrue(script.contains("extract_zip \"${'$'}ARCHIVES/ninja-linux-aarch64.zip\""))
    }

    @Test
    fun offlineAndroidInstallerDoesNotRequireOptionalFileOrXzCommands() {
        val installer = offlineAndroidInstaller.readText()
        val verifier = offlineAndroidVerifier.readText()
        val optionalFileCommand = Regex("""(?m)(^|[;&|]\s*|if\s+)file\s+""")

        listOf(installer, verifier).forEach { script ->
            assertTrue(script.contains("od -An -t x1"))
            assertTrue(script.contains("b700"))
            assertFalse(optionalFileCommand.containsMatchIn(script))
        }
        assertFalse(installer.contains("tar -xJ"))
        assertTrue(installer.contains("android-ndk-r29-aarch64.tar.gz"))
        assertTrue(installer.contains("tar -xzf"))
    }

    @Test
    fun offlineZipExtractionRestoresRequiredExecutableBits() {
        val script = offlineAndroidInstaller.readText()

        assertTrue(script.contains("chmod 755 \"/opt/gradle-${'$'}GRADLE_VERSION/bin/gradle\""))
        assertTrue(script.contains("for executable in aapt aapt2 aidl zipalign d8 apksigner"))
        assertTrue(script.contains("chmod 755 \"${'$'}ANDROID_HOME/build-tools/${'$'}BUILD_TOOLS_VERSION/${'$'}executable\""))
    }

    @Test
    fun offlineNdkDiscoveryAcceptsToolSymlinksAndNamesMissingResources() {
        val script = offlineAndroidInstaller.readText()
        val verifier = offlineAndroidVerifier.readText()

        assertTrue(script.contains("\\( -type f -o -type l \\) -name clang"))
        assertTrue(script.contains("\\( -type f -o -type l \\) -name llvm-strip"))
        assertTrue(script.contains("need \"${'$'}NDK_CLANG\" \"NDK clang\""))
        assertTrue(script.contains("need \"${'$'}NDK_STRIP\" \"NDK llvm-strip\""))
        assertTrue(script.contains("missing offline resource: ${'$'}{resource_name:-unknown}"))
        assertFalse(verifier.contains("test -x /opt/taixu/toolchains/android/ndk/toolchains/llvm/prebuilt/*"))
        assertTrue(verifier.contains("-name llvm-strip -print -quit"))
        assertTrue(verifier.contains("require_executable \"${'$'}NDK_STRIP\""))
        assertTrue(verifier.contains("require_aarch64 \"${'$'}NDK_STRIP\""))
    }

    @Test
    fun offlineCommandLinksArePublishedBeforeFinalVerification() {
        val manifest = offlineAndroidManifest.readText()
        val installer = offlineAndroidInstaller.readText()
        val commandLinksBlock = Regex("\"commandLinks\"\\s*:\\s*\\[([^]]+)]")
            .find(manifest)
            ?.groupValues
            ?.get(1)
            ?: error("commandLinks is missing from the offline Android manifest")
        val commandLinks = Regex("\"([^\"]+)\"")
            .findAll(commandLinksBlock)
            .map { it.groupValues[1] }
            .toSet()
        val publishedCommands = Regex("""for command in ([^;]+); do""")
            .find(installer)
            ?.groupValues
            ?.get(1)
            ?.split(Regex("""\s+"""))
            ?.filter { it.isNotBlank() }
            ?.toSet()
            ?: error("global command-link loop is missing from the offline Android installer")

        assertTrue(
            "commandLinks missing from global link loop: ${commandLinks - publishedCommands}",
            publishedCommands.containsAll(commandLinks),
        )
        assertTrue(installer.contains("/bin/sh \"${'$'}PAYLOAD/scripts/verify-android-suite.sh\""))
    }

    @Test
    fun offlineInstallerPublishesStructuredMonotonicProgress() {
        val script = offlineAndroidInstaller.readText()
        val percentages = Regex("""progress (\d{1,3}) """)
            .findAll(script)
            .map { it.groupValues[1].toInt() }
            .toList()

        assertTrue(percentages.size >= 12)
        assertTrue(percentages.zipWithNext().all { (previous, next) -> next >= previous })
        assertTrue(percentages.first() > 0)
        assertTrue(percentages.last() == 100)
        assertTrue(script.contains("[EXTRACT]"))
        assertTrue(script.contains("[COMMAND]"))
        assertTrue(script.contains("[VERIFY]"))
        assertTrue(script.contains("[TAIXU_PROGRESS:%s]"))
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
