package top.wkbin.taixu.core.model

import kotlinx.serialization.Serializable

/**
 * 🧩 插件子组件定义 (Plugin Sub-Component)
 * 隶属于某个聚合插件大套件下的原子能力组件。
 */
@Serializable
data class PluginComponent(
    val id: String,
    val name: String,
    val description: String,
    val isRequired: Boolean = false, // 是否为必选核心组件（不可取消勾选）
    val aptPackages: List<String> = emptyList(),
    val postInstallSteps: List<String> = emptyList(),
    val checkCommand: String, // 状态探针命令，返回 0 表示已就绪
)

/**
 * 📦 聚合插件大套件定义 (Plugin Bundle)
 * 将领域相关的能力（基础环境、扩展工具、逆向调试等）深度聚合成单一插件大类。
 */
@Serializable
data class PluginBundle(
    val id: String,
    val name: String,
    val summary: String,
    val description: String,
    val iconName: String = "Code",
    val category: String = "开发套件",
    val components: List<PluginComponent> = emptyList(),
)

object BuiltinPluginBundles {
    /** 基础核心包：始终隐式自动预装，保证 Linux 基础终端与工具可用 */
    val baseRequiredPackages: List<String> = listOf(
        "curl", "wget", "git", "ca-certificates", "ripgrep", "fd-find", "jq", "tmux", "tar", "gzip", "xz-utils",
    )

    /** 核心聚合大插件清单 */
    val bundles: List<PluginBundle> = listOf(
        PluginBundle(
            id = "android-suite",
            name = "Android & 移动全栈开发套件",
            summary = "Android CLI、Gradle 8.7+、AAPT/ADB 与可选 Flutter、NDK、逆向审计",
            description = "集成 Google 官方原生 Android CLI、OpenJDK 17、Gradle 8.7+、AAPT、ADB 与可选的 Flutter 跨端 SDK、C/C++ NDK 原生编译和 JADX/APKTool 逆向代码审计工具链。",
            iconName = "Android",
            category = "移动开发",
            components = listOf(
                PluginComponent(
                    id = "android-core",
                    name = "Android 核心基础环境",
                    description = "Google Android CLI (android)、OpenJDK 17、Gradle 8.7+、AAPT、ADB 与 zipalign",
                    isRequired = true,
                    aptPackages = listOf("openjdk-17-jdk-headless", "adb", "aapt", "zipalign", "unzip", "curl", "ca-certificates"),
                    postInstallSteps = listOf(
                        "mkdir -p /opt /usr/local/bin /usr/bin \$TAIXU_TOOL_DIR/bin 2>/dev/null || true",
                        "if [ ! -x /opt/gradle-8.7/bin/gradle ]; then (curl -fsSL -m 120 https://mirrors.cloud.tencent.com/gradle/gradle-8.7-bin.zip -o /tmp/gradle-8.7.zip 2>/dev/null && (unzip -qo /tmp/gradle-8.7.zip -d /opt/ 2>/dev/null || python3 -c 'import zipfile; zipfile.ZipFile(\"/tmp/gradle-8.7.zip\").extractall(\"/opt/\")' 2>/dev/null || busybox unzip /tmp/gradle-8.7.zip -d /opt/ 2>/dev/null) && rm -f /tmp/gradle-8.7.zip && chmod +x /opt/gradle-8.7/bin/gradle 2>/dev/null || true); fi",
                        "if [ -d /opt/gradle-8.7/bin ]; then chmod +x /opt/gradle-8.7/bin/gradle 2>/dev/null; ln -sf /opt/gradle-8.7/bin/gradle /usr/local/bin/gradle 2>/dev/null; ln -sf /opt/gradle-8.7/bin/gradle /usr/bin/gradle 2>/dev/null || true; fi",
                        "if [ -f /opt/taixu/assets/tools/android ]; then cp -f /opt/taixu/assets/tools/android /usr/local/bin/android && chmod +x /usr/local/bin/android 2>/dev/null || true; fi",
                    ),
                    checkCommand = "(command -v aapt || test -x /usr/bin/aapt) && (command -v java || test -x /usr/lib/jvm/java-17-openjdk-arm64/bin/java || test -d /usr/lib/jvm)",
                ),
                PluginComponent(
                    id = "flutter",
                    name = "Flutter 跨平台开发环境",
                    description = "Flutter ARM64 跨平台 SDK、Dart 运行时与国内 pub 镜像加速",
                    isRequired = false,
                    aptPackages = listOf("git", "curl", "unzip", "ca-certificates", "xz-utils"),
                    postInstallSteps = listOf(
                        "mkdir -p /opt/flutter /usr/local/bin /usr/bin 2>/dev/null || true",
                        "if [ ! -f /opt/flutter/bin/flutter ]; then (cd /opt && (git clone -b stable --depth 1 https://mirrors.tuna.tsinghua.edu.cn/git/flutter-sdk.git flutter 2>/dev/null || git clone -b stable --depth 1 https://gitee.com/mirrors/Flutter.git flutter 2>/dev/null || true)); fi",
                        "if [ -f /opt/flutter/bin/flutter ]; then chmod +x /opt/flutter/bin/flutter /opt/flutter/bin/dart 2>/dev/null; ln -sf /opt/flutter/bin/flutter /usr/local/bin/flutter 2>/dev/null; ln -sf /opt/flutter/bin/flutter /usr/bin/flutter 2>/dev/null; ln -sf /opt/flutter/bin/dart /usr/local/bin/dart 2>/dev/null || true; fi",
                    ),
                    checkCommand = "command -v flutter || test -f /opt/flutter/bin/flutter",
                ),
                PluginComponent(
                    id = "android-ndk",
                    name = "C/C++ & NDK 原生构建链",
                    description = "CMake、Ninja、GCC/G++、Clang 与本地高性能底层 C/C++ 交叉编译套件",
                    isRequired = false,
                    aptPackages = listOf("cmake", "ninja-build", "gcc", "g++", "clang", "make", "pkg-config"),
                    checkCommand = "command -v cmake && (command -v gcc || command -v clang)",
                ),
                PluginComponent(
                    id = "android-re",
                    name = "Android 逆向分析与代码审计",
                    description = "APKTool 资源回编译与 JADX-CLI Java 源码反编译器",
                    isRequired = false,
                    aptPackages = listOf("openjdk-17-jdk-headless", "curl", "unzip", "apktool"),
                    postInstallSteps = listOf(
                        "mkdir -p /opt/jadx /usr/local/bin /usr/bin 2>/dev/null || true",
                        "if [ ! -x /opt/jadx/bin/jadx ]; then (curl -fsSL -m 120 https://mirror.ghproxy.com/https://github.com/skylot/jadx/releases/download/v1.5.0/jadx-1.5.0.zip -o /tmp/jadx.zip 2>/dev/null && (unzip -qo /tmp/jadx.zip -d /opt/jadx/ 2>/dev/null || python3 -c 'import zipfile; zipfile.ZipFile(\"/tmp/jadx.zip\").extractall(\"/opt/jadx/\")' 2>/dev/null || busybox unzip /tmp/jadx.zip -d /opt/jadx/ 2>/dev/null) && rm -f /tmp/jadx.zip && chmod +x /opt/jadx/bin/jadx 2>/dev/null || true); fi",
                        "if [ -f /opt/jadx/bin/jadx ]; then chmod +x /opt/jadx/bin/jadx 2>/dev/null; ln -sf /opt/jadx/bin/jadx /usr/local/bin/jadx 2>/dev/null; ln -sf /opt/jadx/bin/jadx /usr/bin/jadx 2>/dev/null || true; fi",
                    ),
                    checkCommand = "command -v apktool || command -v jadx || test -x /opt/jadx/bin/jadx",
                ),
            ),
        ),
        PluginBundle(
            id = "python-suite",
            name = "Python & AI 开发者套件",
            summary = "Python 3 运行时、pip、venv 虚拟环境与 AI 科学计算编译依赖",
            description = "包含完整的 Python 3 运行环境、pip 包管理、venv 隔离环境以及编译 Python C 扩展轮子所需的 build-essential 基础库。",
            iconName = "Code",
            category = "AI 与脚本",
            components = listOf(
                PluginComponent(
                    id = "python-core",
                    name = "Python 3 核心运行基座",
                    description = "Python 3 解释器、pip 包管理器与 venv 虚拟环境工具",
                    isRequired = true,
                    aptPackages = listOf("python3", "python3-pip", "python3-venv"),
                    checkCommand = "command -v python3 && command -v pip3",
                ),
                PluginComponent(
                    id = "python-ai-dev",
                    name = "AI 科学计算与 C 扩展编译库",
                    description = "python3-dev、build-essential、pkg-config 与底层系统头文件",
                    isRequired = false,
                    aptPackages = listOf("python3-dev", "build-essential", "pkg-config", "libffi-dev"),
                    checkCommand = "dpkg -s python3-dev 2>/dev/null || test -f /usr/include/python3*/Python.h",
                ),
            ),
        ),
        PluginBundle(
            id = "nodejs-suite",
            name = "Node.js & Web 全栈套件",
            summary = "Node.js 运行时、npm、pnpm 与现代前端全栈生态",
            description = "集成 Node.js 现代 LTS 运行时、npm 包管理器，支持 pnpm 等现代包管理与 JavaScript / TypeScript 全栈开发。",
            iconName = "Globe",
            category = "全栈开发",
            components = listOf(
                PluginComponent(
                    id = "nodejs-core",
                    name = "Node.js 核心运行时",
                    description = "Node.js 运行时与 npm 包管理器",
                    isRequired = true,
                    aptPackages = listOf("nodejs", "npm"),
                    checkCommand = "command -v node && command -v npm",
                ),
                PluginComponent(
                    id = "nodejs-pkg",
                    name = "现代包管理器与编译加速 (pnpm / yarn)",
                    description = "pnpm 与 yarn 高性能本地包缓存管理器",
                    isRequired = false,
                    postInstallSteps = listOf(
                        "npm install -g pnpm yarn --registry=https://registry.npmmirror.com 2>/dev/null || true",
                    ),
                    checkCommand = "command -v pnpm || command -v yarn",
                ),
            ),
        ),
        PluginBundle(
            id = "flutter-suite",
            name = "Flutter 跨平台开发套件",
            summary = "Flutter SDK (ARM64)、Dart 运行时与国内镜像加速",
            description = "集成 Flutter 跨平台 SDK、Dart 运行环境，预置国内加速镜像源与 Android 打包编译工具链。",
            iconName = "Flutter",
            category = "跨端开发",
            components = listOf(
                PluginComponent(
                    id = "flutter-core",
                    name = "Flutter SDK & Dart 核心",
                    description = "Flutter ARM64 跨平台 SDK 与国内 pub 镜像加速",
                    isRequired = true,
                    aptPackages = listOf("git", "curl", "unzip", "ca-certificates", "xz-utils"),
                    postInstallSteps = listOf(
                        "mkdir -p /opt/flutter /usr/local/bin 2>/dev/null || true",
                        "if [ ! -f /opt/flutter/bin/flutter ]; then (cd /opt && (git clone -b stable --depth 1 https://mirrors.tuna.tsinghua.edu.cn/git/flutter-sdk.git flutter 2>/dev/null || git clone -b stable --depth 1 https://gitee.com/mirrors/Flutter.git flutter 2>/dev/null || true)); fi",
                        "if [ -f /opt/flutter/bin/flutter ]; then ln -sf /opt/flutter/bin/flutter /usr/local/bin/flutter; ln -sf /opt/flutter/bin/dart /usr/local/bin/dart 2>/dev/null || true; fi",
                    ),
                    checkCommand = "command -v flutter || test -f /opt/flutter/bin/flutter",
                ),
            ),
        ),
    )

    /**
     * 批量聚合生成单条安全、极速的安装脚本流水线
     */
    fun buildBatchInstallScript(selectedComponentIds: Set<String>): List<String> {
        val allComponents = bundles.flatMap { it.components }.filter { it.id in selectedComponentIds }
        val allAptPackages = (baseRequiredPackages + allComponents.flatMap { it.aptPackages }).distinct()

        val steps = mutableListOf<String>()
        // 1. dpkg 锁与环境自愈
        steps.add("mkdir -p /etc/dpkg/dpkg.cfg.d 2>/dev/null || true")
        steps.add("printf 'force-unsafe-io\\nforce-overwrite\\n' > /etc/dpkg/dpkg.cfg.d/taixu-proot 2>/dev/null || true")
        steps.add("rm -rf /var/lib/dpkg/updates/* /var/lib/dpkg/lock* /var/lib/apt/lists/lock /var/cache/apt/archives/lock 2>/dev/null || true")
        steps.add("DEBIAN_FRONTEND=noninteractive dpkg --configure -a 2>/dev/null || true")

        // 2. 批量聚合 APT 安装（仅执行 1 次 update 和 1 次 install）
        if (allAptPackages.isNotEmpty()) {
            val packageArg = allAptPackages.joinToString(" ")
            steps.add("DEBIAN_FRONTEND=noninteractive apt-get update -y && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends $packageArg || true")
        }

        // 3. 各子组件后置处理
        allComponents.forEach { comp ->
            steps.addAll(comp.postInstallSteps)
        }

        return steps
    }
}

// 保持兼容别名
typealias DevEnvironmentSuite = PluginBundle
typealias BuiltinDevSuites = BuiltinPluginBundles
