package top.wkbin.taixu.runtime.build

import top.wkbin.taixu.runtime.ProjectType

/**
 * Generates a side-effect-free command used before a build starts. Keeping
 * this as a small pure component makes the contract testable without Android
 * or a live PRoot session.
 */
object BuildEnvironmentPreflight {
    private const val ARM64_ELF_MACHINE = "183"
    private const val X86_64_ELF_MACHINE = "62"

    fun command(projectPath: String, projectType: ProjectType, qemu: Boolean = false): String {
        require(projectType == ProjectType.ANDROID || projectType == ProjectType.FLUTTER) {
            "Preflight is only supported for Android and Flutter projects"
        }
        val project = shellQuote(projectPath)
        val lines = mutableListOf(
            "set -eu",
            "fail() { echo \"TAIXU_PREFLIGHT_FAIL: ${'$'}1\"; exit 2; }",
            "PROJECT_PATH=$project",
            "test -x /bin/sh || fail shell",
            "test -d \"\$PROJECT_PATH\" || fail project_missing",
            "elf_machine() { od -An -tu2 -j18 -N2 \"\$1\" 2>/dev/null | tr -d '[:space:]'; }",
        )
        if (projectType == ProjectType.ANDROID) {
            lines += "test -f \"\$PROJECT_PATH/settings.gradle\" -o -f \"\$PROJECT_PATH/settings.gradle.kts\" || fail android_settings"
            lines += "test -f \"\$PROJECT_PATH/build.gradle\" -o -f \"\$PROJECT_PATH/build.gradle.kts\" || fail android_build_file"
            lines += "if test -f \"\$PROJECT_PATH/gradlew\" -o -d \"\$PROJECT_PATH/gradle/wrapper\"; then test -f \"\$PROJECT_PATH/gradlew\" -a -f \"\$PROJECT_PATH/gradle/wrapper/gradle-wrapper.jar\" -a -f \"\$PROJECT_PATH/gradle/wrapper/gradle-wrapper.properties\" || fail wrapper_incomplete; fi"
        } else {
            lines += "test -f \"\$PROJECT_PATH/pubspec.yaml\" || fail flutter_pubspec"
            lines += "test -d \"\$PROJECT_PATH/android\" || fail flutter_android_host"
        }

        if (qemu) {
            lines += "test \"\$(uname -m)\" = x86_64 || fail qemu_guest"
            lines += "COMPAT_ROOT=/opt/taixu/compat/x86_64"
            lines += "JAVA_HOME=\$COMPAT_ROOT/jdk-17"
            lines += "ANDROID_HOME=\$COMPAT_ROOT/android-sdk"
            lines += "GRADLE_HOME=\$COMPAT_ROOT/gradle-8.14.2"
            lines += "JAVA_BIN=\$JAVA_HOME/bin/java"
            lines += "test -x \"\$JAVA_BIN\" || fail java_missing"
            lines += "test \"\$(elf_machine \"\$JAVA_BIN\")\" = $X86_64_ELF_MACHINE || fail java_arch"
            lines += "test -f \"\$ANDROID_HOME/platforms/android-34/android.jar\" || fail android_platform"
            lines += "AAPT2=\$ANDROID_HOME/build-tools/35.0.0/aapt2"
            lines += "test -x \"\$AAPT2\" || fail aapt2_missing"
            lines += "test \"\$(elf_machine \"\$AAPT2\")\" = $X86_64_ELF_MACHINE || fail aapt2_arch"
            lines += "test -f \"\$ANDROID_HOME/build-tools/35.0.0/lib/d8.jar\" || fail build_tools"
            lines += "test -x \"\$GRADLE_HOME/bin/gradle\" -o -d \"\$GRADLE_HOME/lib\" || test -f \"\$PROJECT_PATH/gradle/wrapper/gradle-wrapper.jar\" || fail gradle_missing"
            if (projectType == ProjectType.FLUTTER) {
                lines += "FLUTTER_HOME=\$COMPAT_ROOT/flutter"
                lines += "FLUTTER=\$FLUTTER_HOME/bin/flutter"
                lines += "DART=\$FLUTTER_HOME/bin/cache/dart-sdk/bin/dart"
                lines += "test -x \"\$FLUTTER\" -a -x \"\$DART\" || fail flutter_missing"
                lines += "test \"\$(elf_machine \"\$DART\")\" = $X86_64_ELF_MACHINE || fail dart_arch"
            }
        } else {
            lines += "ANDROID_HOME=\${ANDROID_HOME:-/opt/android-sdk}"
            lines += "JAVA_BIN=\$(command -v java 2>/dev/null || true)"
            lines += "test -n \"\$JAVA_BIN\" -a -x \"\$JAVA_BIN\" || fail java_missing"
            lines += "test \"\$(elf_machine \"\$JAVA_BIN\")\" = $ARM64_ELF_MACHINE || fail java_arch"
            lines += "test -f \"\$ANDROID_HOME/platforms/android-34/android.jar\" || fail android_platform"
            lines += "test -f \"\$ANDROID_HOME/build-tools/35.0.0/lib/d8.jar\" || fail build_tools"
            lines += "AAPT2=\${TAIXU_AAPT2_PATH:-}"
            lines += "case \"\$AAPT2\" in /opt/taixu/toolchains/android/sdk-tools/artifacts/*/build-tools/aapt2) ;; *) fail aapt2_path ;; esac"
            lines += "test -x \"\$AAPT2\" -a \"\$(elf_machine \"\$AAPT2\")\" = $ARM64_ELF_MACHINE || fail aapt2_arch"
            lines += "NDK_PATH=\${TAIXU_NDK_PATH:-}"
            lines += "NDK_CLANG=\$NDK_PATH/toolchains/llvm/prebuilt/linux-x86_64/bin/clang"
            lines += "NDK_STRIP=\$NDK_PATH/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip"
            lines += "test -f \"\$NDK_PATH/source.properties\" -a -x \"\$NDK_CLANG\" -a -x \"\$NDK_STRIP\" || fail ndk_missing"
            lines += "test \"\$(elf_machine \"\$NDK_CLANG\")\" = $ARM64_ELF_MACHINE -a \"\$(elf_machine \"\$NDK_STRIP\")\" = $ARM64_ELF_MACHINE || fail ndk_arch"
            lines += "grep -Fqx 'android.builder.sdkDownload=false' /root/.gradle/gradle.properties 2>/dev/null || fail sdk_download_enabled"
            lines += "test -x /opt/gradle-8.14.2/bin/gradle -o -d /opt/gradle-8.14.2/lib -o -f \"\$PROJECT_PATH/gradle/wrapper/gradle-wrapper.jar\" -o -n \"\$(command -v gradle 2>/dev/null || true)\" || fail gradle_missing"
            if (projectType == ProjectType.FLUTTER) {
                lines += "test -f /opt/flutter/.taixu-arm64 || fail flutter_marker"
                lines += "FLUTTER=\${FLUTTER_BIN:-/opt/flutter/bin/flutter}"
                lines += "DART=/opt/flutter/bin/cache/dart-sdk/bin/dart"
                lines += "test -x \"\$FLUTTER\" -a -x \"\$DART\" || fail flutter_missing"
                lines += "test \"\$(elf_machine \"\$DART\")\" = $ARM64_ELF_MACHINE || fail dart_arch"
            }
        }
        lines += "echo TAIXU_PREFLIGHT_OK"
        return lines.joinToString("; ")
    }

    private fun shellQuote(value: String): String = "'${value.replace("'", "'\\\''")}'"
}
