package top.wkbin.taixu.runtime.build

import org.junit.Assert.assertTrue
import org.junit.Test
import top.wkbin.taixu.runtime.ProjectType

class BuildEnvironmentPreflightTest {
    @Test
    fun androidArmPreflightRequiresPinnedArmArtifactsAndDisablesSdkDownload() {
        val command = BuildEnvironmentPreflight.command("/workspace/a project's app", ProjectType.ANDROID)
        assertTrue(command.contains("android.builder.sdkDownload=false"))
        assertTrue(command.contains("TAIXU_NDK_PATH"))
        assertTrue(command.contains("TAIXU_AAPT2_PATH"))
        assertTrue(command.contains("gradle-wrapper.jar"))
        assertTrue(command.contains("fail aapt2_arch"))
        assertTrue(command.contains("a project's app".replace("'", "'\\\''")))
    }

    @Test
    fun flutterQemuPreflightRequiresX86DartAndAndroidHost() {
        val command = BuildEnvironmentPreflight.command("/workspace/flutter", ProjectType.FLUTTER, qemu = true)
        assertTrue(command.contains("uname -m"))
        assertTrue(command.contains("fail dart_arch"))
        assertTrue(command.contains("pubspec.yaml"))
        assertTrue(command.contains("android"))
    }
}

