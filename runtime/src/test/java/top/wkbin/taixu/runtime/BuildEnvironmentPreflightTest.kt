package top.wkbin.taixu.runtime.build

import org.junit.Assert.assertFalse
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
        assertTrue(command.contains("/opt/taixu/toolchains/android/jdk/bin/java"))
        assertTrue(command.contains("\$ANDROID_HOME/build-tools/35.0.0/aapt2"))
        assertTrue(command.contains("/opt/taixu/toolchains/android/ndk"))
        assertTrue(command.contains("-name clang"))
        assertTrue(command.contains("od -An -t x1 -j 18 -N 2"))
        assertTrue(command.contains("= b700"))
        assertTrue(command.contains("java_arch_machine"))
        assertTrue(command.contains("libjvm.so"))
        assertTrue(command.contains("readlink -f"))
        // Java 启动器必须解析为真正的 ELF：包装脚本回环（脚本 exec 软链、
        // 软链又指回脚本）在 PRoot 下是零输出、CPU 满载的无限 exec 循环，
        // 预检必须在 JVM 启动前用魔数拦下，而不是只查 libjvm.so。
        assertTrue(command.contains("7f454c46"))
        assertTrue(command.contains("not_elf"))
        assertFalse(command.contains("-tu2"))
        assertFalse(command.contains("wrapper_incomplete"))
        assertTrue(command.contains("fail aapt2_arch"))
        assertTrue(command.contains("a project's app".replace("'", "'\\\''")))
    }

    @Test
    fun flutterQemuPreflightRequiresX86DartAndAndroidHost() {
        val command = BuildEnvironmentPreflight.command("/workspace/flutter", ProjectType.FLUTTER, qemu = true)
        assertTrue(command.contains("uname -m"))
        assertTrue(command.contains("= 3e00"))
        assertTrue(command.contains("java_arch_machine"))
        assertTrue(command.contains("libjvm.so"))
        assertTrue(command.contains("fail dart_arch"))
        assertTrue(command.contains("pubspec.yaml"))
        assertTrue(command.contains("android"))
    }

    @Test
    fun flutterArmPreflightAcceptsOfflineSuiteLayoutWithoutLegacyMarker() {
        val command = BuildEnvironmentPreflight.command("/workspace/flutter", ProjectType.FLUTTER)

        assertTrue(command.contains("/opt/taixu/toolchains/android/jdk/bin/java"))
        assertTrue(command.contains("/opt/flutter/bin/flutter"))
        assertTrue(command.contains("/opt/flutter/bin/cache/dart-sdk/bin/dart"))
        assertFalse(command.contains("flutter_marker"))
        assertTrue(command.contains("java_arch_machine"))
        assertTrue(command.contains("libjvm.so"))
        assertTrue(command.contains("readlink -f"))
    }
}

