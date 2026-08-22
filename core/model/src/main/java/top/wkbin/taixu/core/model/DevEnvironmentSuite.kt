package top.wkbin.taixu.core.model

import kotlinx.serialization.Serializable

/**
 * 🛠️ 太墟 · 开发者环境套件包 (Dev Environment Suite)
 * 将零散的独立包聚合为语义化、结构化、支持批量原子安装的开发环境。
 */
@Serializable
data class DevEnvironmentSuite(
    val id: String,
    val name: String,
    val subtitle: String,
    val description: String,
    val iconName: String = "Code",
    val isDefaultSelected: Boolean = false,
    val isCoreRequired: Boolean = false,
    val aptPackages: List<String> = emptyList(),
    val postInstallSteps: List<String> = emptyList(),
    val environmentVariables: Map<String, String> = emptyMap(),
    val verifyCommand: String,
)

object BuiltinDevSuites {
    /** 基础核心包：始终隐式自动预装，保证 Linux 基础终端与工具可用 */
    val baseRequiredPackages: List<String> = listOf(
        "curl", "wget", "git", "ca-certificates", "ripgrep", "fd-find", "jq", "tmux", "tar", "gzip", "xz-utils",
    )

    /** 业务可选环境套件清单（完美对齐工业级开发环境准备标准） */
    val presets: List<DevEnvironmentSuite> = listOf(
        DevEnvironmentSuite(
            id = "python3",
            name = "Python 3",
            subtitle = "Python、pip 与虚拟环境",
            description = "Python 3 运行环境、pip 包管理器、venv 虚拟环境与基础 C 编译依赖",
            iconName = "Code",
            isDefaultSelected = true,
            aptPackages = listOf("python3", "python3-pip", "python3-venv", "python3-dev", "build-essential"),
            verifyCommand = "python3 --version 2>&1 && pip3 --version 2>&1",
        ),
        DevEnvironmentSuite(
            id = "nodejs",
            name = "Node.js / npm",
            subtitle = "JavaScript 与 npm 工具链",
            description = "Node.js 运行时、npm 包管理器，支持现代前端与全栈开发",
            iconName = "Globe",
            isDefaultSelected = true,
            aptPackages = listOf("nodejs", "npm"),
            verifyCommand = "node -v 2>&1 && npm -v 2>&1",
        ),
        DevEnvironmentSuite(
            id = "jdk17",
            name = "JDK 17",
            subtitle = "Java、Gradle 与 Smali/Dex 编译环境",
            description = "OpenJDK 17 Headless 与 Gradle 8.7+ 自动化构建工具链",
            iconName = "Code",
            isDefaultSelected = true,
            aptPackages = listOf("openjdk-17-jdk-headless", "unzip", "curl", "ca-certificates"),
            postInstallSteps = listOf(
                "mkdir -p /opt /usr/local/bin 2>/dev/null || true",
                "if [ ! -x /opt/gradle-8.7/bin/gradle ]; then curl -fsSL -m 60 https://mirrors.cloud.tencent.com/gradle/gradle-8.7-bin.zip -o /tmp/gradle-8.7.zip && python3 -c 'import zipfile; zipfile.ZipFile(\"/tmp/gradle-8.7.zip\").extractall(\"/opt/\")' 2>/dev/null && rm -f /tmp/gradle-8.7.zip; fi",
                "if [ -x /opt/gradle-8.7/bin/gradle ]; then ln -sf /opt/gradle-8.7/bin/gradle /usr/local/bin/gradle 2>/dev/null || true; fi",
            ),
            environmentVariables = mapOf("JAVA_HOME" to "/usr/lib/jvm/java-17-openjdk-arm64"),
            verifyCommand = "java -version 2>&1 || gradle -v 2>&1",
        ),
        DevEnvironmentSuite(
            id = "android_sdk",
            name = "Android SDK & Tools",
            subtitle = "Android 官方 CLI、构建工具与 ADB",
            description = "Google Android CLI (android)、AAPT、ADB、zipalign 与真机极速安装链",
            iconName = "Android",
            isDefaultSelected = true,
            aptPackages = listOf("adb", "aapt", "zipalign"),
            postInstallSteps = listOf(
                "mkdir -p /opt/taixu/bin /usr/local/bin 2>/dev/null || true",
                "if [ -f /opt/taixu/assets/tools/android ]; then cp -f /opt/taixu/assets/tools/android /usr/local/bin/android && chmod +x /usr/local/bin/android 2>/dev/null || true; fi",
            ),
            environmentVariables = mapOf(
                "ANDROID_HOME" to "/opt/android-sdk",
                "ANDROID_SDK_ROOT" to "/opt/android-sdk",
            ),
            verifyCommand = "aapt version 2>&1 || adb version 2>&1 || android --version 2>&1",
        ),
        DevEnvironmentSuite(
            id = "android_re",
            name = "Android 逆向工具包",
            subtitle = "APKTool、JADX-CLI 源码反编译",
            description = "集成本地 Java 源码全自动反编译器与 Smali 代码审计环境",
            iconName = "Shield",
            isDefaultSelected = false,
            aptPackages = listOf("openjdk-17-jdk-headless", "curl", "unzip"),
            postInstallSteps = listOf(
                "mkdir -p /opt/jadx /usr/local/bin 2>/dev/null || true",
                "if [ ! -x /opt/jadx/bin/jadx ]; then curl -fsSL -m 60 https://mirror.ghproxy.com/https://github.com/skylot/jadx/releases/download/v1.5.0/jadx-1.5.0.zip -o /tmp/jadx.zip && python3 -c 'import zipfile; zipfile.ZipFile(\"/tmp/jadx.zip\").extractall(\"/opt/jadx/\")' 2>/dev/null && rm -f /tmp/jadx.zip && chmod +x /opt/jadx/bin/jadx 2>/dev/null || true; fi",
                "if [ -x /opt/jadx/bin/jadx ]; then ln -sf /opt/jadx/bin/jadx /usr/local/bin/jadx 2>/dev/null || true; fi",
            ),
            verifyCommand = "jadx --version 2>&1",
        ),
        DevEnvironmentSuite(
            id = "cpp",
            name = "C / C++",
            subtitle = "编译器、CMake、Ninja 与基础构建工具",
            description = "GCC、G++、Clang、Make、CMake 与 Ninja 高性能本地编译套件",
            iconName = "Code",
            isDefaultSelected = false,
            aptPackages = listOf("gcc", "g++", "clang", "make", "cmake", "ninja-build", "pkg-config"),
            verifyCommand = "gcc --version 2>&1 && cmake --version 2>&1",
        ),
        DevEnvironmentSuite(
            id = "flutter",
            name = "Flutter 开发环境",
            subtitle = "Flutter SDK (ARM64) 与国内镜像",
            description = "Flutter 跨平台开发工具包与 Dart 运行环境",
            iconName = "Flutter",
            isDefaultSelected = false,
            aptPackages = listOf("git", "curl", "unzip", "ca-certificates"),
            environmentVariables = mapOf(
                "PUB_HOSTED_URL" to "https://pub.flutter-io.cn",
                "FLUTTER_STORAGE_BASE_URL" to "https://storage.flutter-io.cn",
            ),
            verifyCommand = "flutter --version 2>&1",
        ),
    )

    /**
     * 将多个选中的环境套件聚合生成单条安全、极速的批量安装命令列表
     */
    fun buildBatchInstallScript(selectedSuiteIds: Set<String>): List<String> {
        val selectedSuites = presets.filter { it.id in selectedSuiteIds }
        val allAptPackages = (baseRequiredPackages + selectedSuites.flatMap { it.aptPackages }).distinct()

        val steps = mutableListOf<String>()
        // 1. dpkg 锁与环境自愈
        steps.add("mkdir -p /etc/dpkg/dpkg.cfg.d 2>/dev/null || true")
        steps.add("printf 'force-unsafe-io\\nforce-overwrite\\n' > /etc/dpkg/dpkg.cfg.d/taixu-proot 2>/dev/null || true")
        steps.add("rm -rf /var/lib/dpkg/updates/* /var/lib/dpkg/lock* /var/lib/apt/lists/lock /var/cache/apt/archives/lock 2>/dev/null || true")
        steps.add("DEBIAN_FRONTEND=noninteractive dpkg --configure -a 2>/dev/null || true")

        // 2. 批量聚合 APT 安装（仅执行 1 次 update 和 1 次 install）
        val packageArg = allAptPackages.joinToString(" ")
        steps.add("DEBIAN_FRONTEND=noninteractive apt-get update -y && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends $packageArg || true")

        // 3. 各套件后置处理
        selectedSuites.forEach { suite ->
            steps.addAll(suite.postInstallSteps)
        }

        return steps
    }
}
