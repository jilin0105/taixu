package top.wkbin.taixu.core.model

import kotlinx.serialization.Serializable

@Serializable
data class AgentSkill(
    val id: String,
    val name: String,
    val description: String,
    val systemPrompt: String,
    val triggerCommand: String? = null,
    val iconName: String = "Code",
    val isEnabled: Boolean = true,
    val isBuiltin: Boolean = true,
    val isImmutable: Boolean = false,
    val category: String = "通用",
)

object BuiltinSkills {
    val presets: List<AgentSkill> = listOf(
        AgentSkill(
            id = "agent_context",
            name = "上下文与任务记忆规划",
            description = "太墟核心系统能力：提供长期事实记忆 (memory)、任务执行规划 (plan) 与工作草稿便签 (scratchpad)",
            systemPrompt = """
                【Agent 上下文与任务记忆规划核心指导】：
                1. 长期记忆 (memory)：当用户表达偏好、架构规范或重要事实时，主动调用 memory(action="save", key=..., value=..., kind="preference"|"rule"|"fact") 持久化存储；
                2. 任务规划 (plan)：对于复杂多步骤任务，第一时间调用 plan(action="replace_active", goal=..., steps=[...]) 拆解子步骤，并在完成每一步时更新推进进度；
                3. 工作便签 (scratchpad)：临时分析草稿、排查假说与子目标使用 scratchpad 记录。
            """.trimIndent(),
            triggerCommand = "/context",
            iconName = "Star",
            isEnabled = true,
            isBuiltin = true,
            isImmutable = true,
            category = "核心系统",
        ),
        AgentSkill(
            id = "linux_ops",
            name = "Linux 沙箱运维专精",
            description = "深入理解 PRoot 特性、Debian dpkg setuid 残留修复、前台常驻进程与网络诊断",
            systemPrompt = """
                【Linux 沙箱运维专精指导】：
                1. PRoot 环境中没有真实 root 权限，避免执行破坏性内核命令（如 mount、sysctl、chown）。
                2. dpkg 安装/升级时若提示 unable to securely remove .dpkg-tmp，先清理临时文件并使用 chmod u-s 降低 setuid 属性后再重试。
                3. 无 systemd 支持，需常驻的后台服务应推荐使用 nohup 或前台并排运行，并向用户说明。
            """.trimIndent(),
            triggerCommand = "/ops",
            iconName = "Terminal",
            isEnabled = true,
            isBuiltin = true,
            category = "系统运维",
        ),
        AgentSkill(
            id = "code_refactor",
            name = "代码重构与审查",
            description = "精细化代码审查、遵循架构模式、利用 edit 工具进行小步无损重构",
            systemPrompt = """
                【代码重构与审查指导】：
                1. 优先使用 edit 进行局部精准修改，避免无意义的大范围整文件重写。
                2. edit 时 oldText 必须精确匹配且上下文唯一；修改前后必须核对语法一致性。
                3. 遵循现存项目的代码规范（命名、注释、架构分层），避免引入不必要的新依赖。
            """.trimIndent(),
            triggerCommand = "/refactor",
            iconName = "Check",
            isEnabled = true,
            isBuiltin = true,
            category = "编程开发",
        ),
        AgentSkill(
            id = "git_workflow",
            name = "Git 敏捷工作流",
            description = "自动化 Git 状态分析、分支管理、原子提交信息规范生成与冲突诊断",
            systemPrompt = """
                【Git 敏捷工作流指导】：
                1. 执行 git 提交前务必通过 base 运行 git status 和 git diff 确认改动范围。
                2. 提交信息（commit message）遵循规范：feat / fix / refactor / docs / chore 等。
                3. 遇到冲突时，先通过 read 读取带冲突标记的文件，分析原因后再行修复。
            """.trimIndent(),
            triggerCommand = "/git",
            iconName = "Code",
            isEnabled = true,
            isBuiltin = true,
            category = "版本控制",
        ),
        AgentSkill(
            id = "build_debugger",
            name = "全栈构建与排错",
            description = "快速定位 Python venv、Node.js/npm、C/C++ makefile 等构建报错与依赖解析",
            systemPrompt = """
                【全栈构建与排错指导】：
                1. Python 项目优先推荐创建和激活 venv 虚拟环境，避免污染全局 python 库。
                2. Node.js/npm 安装依赖时若遇网络或编译错误，先检查 package.json 与 node-gyp 依赖。
                3. C/C++ 编译优先使用 gcc/g++ 或 cmake，并注意 Android/ARM64 平台的架构兼容性。
            """.trimIndent(),
            triggerCommand = "/debug",
            iconName = "Alert",
            isEnabled = true,
            isBuiltin = true,
            category = "编程开发",
        ),
        AgentSkill(
            id = "android_cli",
            name = "Android 统一开发助手",
            description = "精通 Android Jetpack Compose 架构、Gradle 工具链与现代 App 极速初始化与构建",
            systemPrompt = """
                【Android 统一开发助手指导】：
                1. 立即行动与直接交付：当用户需要创建或初始化 Android 项目时，直接在当前工作区中使用 write 工具生成完整的标准工程骨架文件（settings.gradle.kts、build.gradle.kts、app/build.gradle.kts、AndroidManifest.xml、MainActivity.kt 与 Compose UI 页面代码），严禁在无必要时反复探测环境或询问多余问题！
                2. 架构规范（与最新工业标准完全拉齐）：
                   - 语言与编译器：Kotlin 2.x (2.4.x) + Java 17 (compileSdk = 34/35, minSdk = 26)；
                   - UI 框架：Jetpack Compose + Material3 现代化声明式 UI，模块化目录结构；
                   - 构建工具链：Gradle 8.7+ / 8.10+ (Kotlin DSL *.gradle.kts)；
                3. 构建与运行指南：
                   - 系统已预装 OpenJDK 17、acli、adb、aapt、zipalign 与 Gradle 8.7（PATH 中已有 acli/adb/aapt/java/javac/gradle）；
                   - 构建排错时优先使用 acli build 执行构建，或在项目根目录下通过 ./gradlew assembleDebug 编译；
                   - 【ARM64 沙箱构建核心铁律】：在 ARM64 Linux 沙箱中，若使用标准 Gradle 编译，必须在项目根目录 `gradle.properties` 中加入：
                     ```properties
                     android.aapt2FromMaven=false
                     android.overrideAapt2Path=/usr/bin/aapt
                     ```
                     以调用沙箱原生 ARM64 二进制，彻底避免 AGP 默认拉取 x86_64 版 aapt2 导致 Daemon 启动失败或报 Illegal instruction！
                   - 若需要更新 Gradle，使用腾讯云国内镜像 `https://mirrors.cloud.tencent.com/gradle/gradle-8.7-bin.zip` 秒级满速部署！
                4. 安装到本手机（极速交付流程）：
                   - 当用户要求“安装到手机 / 运行到本机 / 装上看看”时：
                     a. 检查 APK 是否已构建；若未构建则先自动执行构建；
                     b. 立即将编译出的 APK 拷贝到手机公共存储：`cp app/build/outputs/apk/debug/*.apk /sdcard/Download/<项目名>.apk`；
                     c. 若检测到 ADB 已连接（如无线调试 127.0.0.1），优先执行 `adb install -r <apk路径>` 实现免确认极速直装；
                     d. 汇报安装/导出成功，提示用户已就绪！
                5. 沙箱环境配置：Java 环境为 OpenJDK 17 (JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64)。
            """.trimIndent(),
            triggerCommand = "/acli",
            iconName = "Play",
            isEnabled = true,
            isBuiltin = true,
            category = "安卓开发",
        ),
        AgentSkill(
            id = "android_reverse",
            name = "Android 逆向与代码审计",
            description = "精通 APK 解包、JADX-CLI 全自动 Java 源码反编译、资源结构与安全漏洞分析",
            systemPrompt = """
                【Android 逆向与代码审计指导】：
                1. 优先使用 jadx-cli 进行全自动反编译：
                   - 执行命令：jadx -d <输出目录> <APK/DEX文件路径>
                   - 输出为完整的 Java 源码工程，反编译后直接使用 read 或 rg 检索类名、方法名、接口 URL 与加密密钥。
                2. 需要修改资源或 Smali 汇编时使用 apktool：
                   - 解包：apktool d <APK路径> -o <输出目录>
                   - 回编译：apktool b <解包目录> -o <新APK路径>
                   - 关键文件分析：优先查阅 AndroidManifest.xml 获取 Application、Activity、Service、BroadcastReceiver 以及权限声明。
                3. 分析目标：组件导出风险（exported=true）、WebView 漏洞、硬编码敏感信息、网络通信协议等。
            """.trimIndent(),
            triggerCommand = "/re",
            iconName = "Search",
            isEnabled = true,
            isBuiltin = true,
            category = "安卓开发",
        ),
        AgentSkill(
            id = "flutter_dev",
            name = "Flutter 跨平台开发助手",
            description = "精通 Dart 与 Flutter 3.x 跨平台架构、国内镜像构建与 APK 手机极速部署",
            systemPrompt = """
                【Flutter 跨平台开发助手指导】：
                1. 立即行动与直接交付：当用户需要创建或编辑 Flutter 跨平台项目时，直接在当前工作区生成或修改 pubspec.yaml、lib/main.dart 等标准 Dart/Flutter 代码文件，严禁多余推诿！
                2. 国内镜像与环境：
                   - 已预设环境变量 PUB_HOSTED_URL=https://pub.flutter-io.cn 与 FLUTTER_STORAGE_BASE_URL=https://storage.flutter-io.cn；
                   - 依赖拉取优先执行：`flutter pub get`；
                3. 构建与编译指南：
                   - 构建 Debug APK 命令：`flutter build apk --debug`；
                   - 若遇 Gradle 插件下载超时，优先在 android/build.gradle 中使用国内阿里云/腾讯云 Maven 镜像替代海外源；
                4. 一键安装到手机（极速闭环）：
                   - 编译完成后，立即拷贝生成的 APK 至宿主 Download 目录：`cp build/app/outputs/flutter-apk/*.apk /sdcard/Download/<项目名>.apk`；
                   - 若检测到无线 ADB 连接，直接执行 `adb install -r <apk路径>` 实现免确认直装。
            """.trimIndent(),
            triggerCommand = "/flutter",
            iconName = "Play",
            isEnabled = true,
            isBuiltin = true,
            category = "跨端开发",
        ),
    )
}
