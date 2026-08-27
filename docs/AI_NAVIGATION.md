# 🧭 太墟 (TaiXu / LinuxAIRuntime) — AI 架构与开发全局导航

> **专供 AI Coding Assistant (Antigravity / Cursor / Claude / GPT / Copilot) 全局感知与开发**
> 本文档包含太墟的核心架构、模块职责、高频调用链路、文件索引与架构铁律，AI 无需额外泛搜全文即可精准定位并编写代码。

---

## 1. 🚀 项目定位与技术栈摘要 (Tech Stack Snapshot)

- **核心定位**：在 **Android 无 Root 用户态** 下，基于 **PRoot** 运行完整的 **Linux 沙箱**，并深度集成 **AI Agent Harness 智能体引擎**、**原生 PTY 终端**、**工作区文件管理** 与 **Material 3 Expressive UI**。
- **Android / UI 层**：
  - **主工程构建链**：Gradle 9.7.0 / AGP 9.3.1 / Kotlin 2.4.10 / compileSdk & targetSdk 37 / NDK 30.0.15729638
  - **语言**：Kotlin（100% Kotlin + Jetpack Compose）
  - **UI 风格**：Material 3 Expressive (M3 动态表面、Haptic 触觉反馈、双栏自适应 Dual-Pane)
  - **依赖注入**：Hilt / Dagger
  - **状态与异步**：Kotlin Coroutines, StateFlow, SharedFlow
  - **本地存储**：Jetpack DataStore (Preferences), Room Database (SQLite)
- **底层运行时 (Runtime & Native)**：
  - **C/C++ JNI**：`app/src/main/cpp/pty.c` (openpty, fork, exec, ioctl 终端 PTY 桥接，编译为 `libtaixu_pty.so`, `arm64-v8a`)
  - **Linux 容器**：内置 PRoot 引擎，启动 Debian rootfs
  - **宿主存储映射**：PRoot `-b <host>:<guest>` 原生绑定机制（Download, Documents, /sdcard）
- **AI 智能体引擎 (Agent Harness)**：
  - **协议兼容**：OpenAI Chat Completion API 标准（支持流式 SSE、`reasoning_content` 思考过程、函数工具调用 `tool_calls`）
  - **内置工具**：`read` / `write` / `edit` / `base` / `process`，并可扩展 MCP 与子智能体工具
  - **命令生命周期**：`base` 用于有界前台命令，默认超时由用户配置；`process` 通过 Runtime 进程注册表托管跨工具调用持续运行的命令
  - **本地端侧模型**：沙箱内原生支持 `llama.cpp` 与 `Ollama`，无需 API Key
- **沙箱 Android 开发套件（不要与主工程构建链混淆）**：Gradle 8.14.2 / AGP 8.11.1 / Kotlin 2.2.20 / compileSdk & targetSdk 34 / NDK 29.0.14206865，仅面向 ARM64 沙箱内的 Android/Flutter APK 构建。

---

## 2. 🗺️ 模块拓扑与职责索引 (Module Topology)

```text
LinuxAIRuntime/
├── app/                  # 应用壳工程：MainActivity、Hilt 初始化、JNI C 代码、前台保活 Service
├── core/
│   ├── model/           # 纯 Kotlin 数据模型 (不含 Android SDK 依赖)
│   ├── common/          # 协程调度器、日志、通用工具类
│   ├── database/        # Room 数据库：Harness 对话记录、会话、工具执行历史
│   ├── datastore/       # Jetpack DataStore：用户偏好、存储挂载、激活模型、Agent 命令超时
│   ├── network/         # OkHttp 网络客户端、SSE 流式解析器、超时策略
│   └── security/        # API Key 本地安全加解密
├── runtime/              # Linux 沙箱与 PRoot 核心：命令/进程注册、PTY、工作区导入导出与构建
├── project-template/     # 标准化项目模板：manifest、变量表单协议、导入导出、物化与内置模板资产
├── harness/              # Agent 智能体核心：Agent 循环、流式推理、内置工具/审批、MCP
├── tools/                # 工具生态中心：Registry、本地插件、安装事务、批量组件安装、Provider 安全
└── feature/              # Compose UI 业务特性层
    ├── components/      # 太墟 M3 Expressive 设计规范、通用组件 (RuntimeCard, TopBar, Icons)
    ├── theme/           # Material 3 调色板、字体、主题配置
    ├── home/            # 首页运行仪表盘：Linux 系统状态、内存/磁盘/进程实时监控
    ├── chat/            # 智枢 Agent 对话界面、TaskPlanCard 任务拆解卡片、宽屏双栏布局
    ├── terminal/        # 终端 Compose UI 与触觉按键条；会话/VT100 状态机位于 runtime
    ├── workspace/       # 工作区管理器：创建/ZIP/GitHub 导入、导出、代码浏览、后台构建
    ├── settings/        # 设置中心：模型档案、Agent 超时、工具/本地插件、存储挂载、外观与诊断
    ├── developer/       # 开发者原生沙箱与诊断面板
    ├── welcome/         # 首次启动引导与 RootFS 解压就绪流程
    └── navigation/      # 顶层导航路由与 NavHost 调度
```

## 2.5 🔗 跨模块要点 (Cross-Module Rules)

- **依赖方向**：`feature/*` → `core/*` / `runtime` / `harness` / `tools`；业务 feature 仅可依赖共享 UI `feature/components` / `feature/theme`，顶层 `feature/navigation` 负责装配各页面。
- **持久化边界**：feature / runtime / harness 只依赖 `PersistenceRepositories.kt` 中的仓储接口，不得直接注入 Room DAO；偏好按领域使用 `PreferenceFacades.kt`。
- **纯模型层隔离**：`:core:model` 必须保持 Pure Kotlin，**严禁**引入 `android.*` 或 Compose 依赖。
- **命令执行边界**：短时、有明确结束点的命令走 `LinuxRuntime.execute()` / Harness `base`；服务和长任务走 `LinuxRuntime.startBackground()` / Harness `process`，由 `ProcessRegistry` 统一记录 PID、状态与最多 500 行日志。
- **工作区边界**：项目元数据与文件操作集中在 `WorkspaceManager` / `WorkspaceFileService`；ZIP 导入必须做路径穿越、条目数、单文件和总大小校验，Git clone 必须在 PRoot 内执行。
- **构建状态边界**：工作区构建由单例 `WorkspaceBuildTaskCoordinator` 持有，页面重建不能取消任务；实际预检、Gradle/Flutter 命令、产物校验在 `WorkspaceBuildRunner`。
- **单模块编译验证**（Windows PowerShell）：
  ```powershell
  $env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :模块名:compileDebugKotlin --console=plain -q
  ```
- **新增工具适配器**：实现 `ToolRuntimeAdapter`（安装/启动/卸载/校验），并通过 Hilt `@Binds` 注册到 `app/.../di/ToolAdapterModule.kt`。

---

## 3. ⚡ 8 大高频核心调用链路 (Top 8 Execution Traces)

### 3.1 Agent 发起与工具执行循环 (Harness Loop)
```text
ChatScreen (UI)
  └─► ChatViewModel.send(prompt)
        └─► HarnessLoop.send()
              ├─► ProviderRepository 读取 BaseURL / Model / ApiKey
              ├─► ProviderClient / ChatApi ➔ 流式接收 reasoning & text / tool_calls
              └─► 当模型返回 tool_calls:
                    ├─► HarnessApiMapper 映射 read / write / edit / base / process
                    ├─► ApprovalPolicyEngine 计算审批要求
                    ├─► ToolExecutor 分派文件访问或 LinuxRuntime
                    ├─► 回传 ToolResult 到对话历史 (core:database)
                    └─► 继续进入下一轮推理，直至任务全部完成
```

### 3.2 宿主与沙箱存储挂载 (Storage Mount)
```text
StorageMountSettingsScreen (UI)
  └─► SettingsDataStore (保存 mountDownloadEnabled / customMountBindings)
        └─► ProotCommandBuilder.build()
              └─► 动态追加 "-b /storage/emulated/0/Download:/sdcard/Download"
                    └─► PRoot 进程启动，沙箱内可直接访问 /sdcard
```

### 3.3 终端交互与原生 PTY (Matrix Terminal)
```text
TerminalScreen (UI)
  └─► TerminalViewModel
        └─► TerminalPtyManager.createPty()
              └─► JNI pty.c (openpty / fork / execve proot)
                    ├─► PtyInputStream ➔ TerminalViewModel.screen (VT100 状态机解析)
                    └─► PtyOutputStream ◄─ TerminalScreen 键盘与辅助按键输入 (ExtraKeys)
```

### 3.4 任务拆解与进度卡片 (Task Plan Checkpoints)
```text
Model Output (包含 - [ ] / - [x] 格式文本)
  └─► ChatScreen.kt -> extractTaskPlanSteps(message.text)
        └─► 当步骤数 >= 2 时 ➔ 渲染 TaskPlanCard
              ├─► 动态计算进度百分比与完成度徽章 (LinearProgressIndicator)
              ├─► 提供触觉反馈与平滑展开/折叠动效
```

### 3.5 宽屏与折叠屏双栏联动 (Dual-Pane Layout)
```text
ChatScreen.kt (BoxWithConstraints)
  ├─► maxWidth >= 720.dp:
  │     ├─► 左栏 (48% 宽度): ChatPaneContent (Agent 对话与输入框)
  │     ├─► 中间: VerticalDivider
  │     └─► 右栏 (52% 宽度): TerminalScreen(project = workspace) (实时 Linux 终端)
  └─► maxWidth < 720.dp:
        └─► 单栏 Phone 视图
```

### 3.6 Agent 前台命令与托管进程 (Command Lifecycle)

```text
AgentSettingsScreen
  └─► SettingsDataStore.baseCommandTimeoutSeconds（1–60 分钟，默认 10 分钟）
        └─► ToolExecutor.executeBase()
              ├─► 未传 timeout_seconds：读取用户默认值
              └─► 单次覆盖：校验 1–3600 秒 ➔ LinuxRuntime.execute(ShellCommand)

Harness process(action=start, id, command)
  └─► ToolExecutor.executeProcess()
        └─► LinuxRuntime.startBackground(type=COMMAND)
              └─► ProcessRegistry
                    ├─► 保存 agent-process:<id> / PID / LinuxSession
                    ├─► 缓存并暴露日志
                    └─► status / logs / list / stop
```

> PRoot 启动参数包含 `--kill-on-exit`。普通 `base` 中的 `nohup`、`setsid`、`&` 或自行 daemonize 不能保证跨 PRoot 会话存活；必须使用 `process` 托管，并让被托管命令保持前台运行。Android 强制停止、系统回收应用进程或设备重启不属于进程托管保证范围。

### 3.7 工作区导入、导出与构建 (Workspace Lifecycle)

```text
WorkspaceScreen
  └─► WorkspaceViewModel
        ├─► WorkspaceManager.createProject()
        ├─► importProjectArchive() ➔ SAF URI ➔ 安全解压到 /workspace
        ├─► importGithubProject() ➔ LinuxRuntime.execute(git clone)
        ├─► exportProject() ➔ ZIP ➔ SAF 目标目录
        └─► WorkspaceBuildTaskCoordinator.start(project)
              └─► WorkspaceBuildRunner.runProject()
                    ├─► BuildEnvironmentPreflight
                    ├─► Android / Flutter：最长 30 分钟构建
                    ├─► 持续 StateFlow、通知与构建日志
                    └─► ApkArtifactVerifier ➔ APK 安装入口
```

### 3.8 ARM64 Android 离线套件安装与构建 (Sandbox Android Toolchain)

```text
WorkspaceScreen / ToolCenterScreen
  └─► ToolManager.batchInstallComponents() / GenericRecipeInstaller
        ├─► LocalPluginPayloadManager 流式复制 payload，并按字节上报 [COPY] 进度
        └─► android-suite-offline manifest + install-android-suite.sh
              ├─► [TAIXU_PROGRESS:n] 协议 ➔ InstallEvent.Progress
              ├─► 安装不可变 JDK / SDK / AAPT2 / NDK / Gradle / CMake / Ninja / Flutter
              ├─► /root/.gradle/init.d/taixu-android-ndk.gradle 唯一注入 android.ndkPath
              └─► gradle.properties 固定移动端资源策略
                    ├─► daemon=false / parallel=false / workers.max=2
                    └─► Gradle Xmx=1024m / Metaspace=384m / SerialGC

WorkspaceBuildRunner
  └─► build_android.sh / build_flutter.sh
        ├─► 清理 local.properties 中遗留 ndk.dir，不再写回
        └─► --no-daemon --max-workers=2 ➔ 构建与产物校验
```

---

## 4. 📍 关键文件绝对路径速查表 (Key File Index)

| 业务领域 | 关键文件路径 | 核心职责 |
| :--- | :--- | :--- |
| **Agent 核心** | [`harness/.../HarnessLoop.kt`](../harness/src/main/java/top/wkbin/taixu/harness/HarnessLoop.kt) | Agent 思考与工具调用闭环调度（并发内核 + 公共 API 门面） |
| **会话消息投影** | [`harness/.../projection/SessionMessageProjector.kt`](../harness/src/main/java/top/wkbin/taixu/harness/projection/SessionMessageProjector.kt) | 实时消息流、前台镜像与流式上屏；配套 `SessionStateMirrors`（状态机镜像）与 `CurrentSessionTracker`（前台聚焦） |
| **上下文组装** | [`harness/.../session/ApiContextAssembler.kt`](../harness/src/main/java/top/wkbin/taixu/harness/session/ApiContextAssembler.kt) | 会话树 → 提供商协议消息（NATIVE / JSON_TEXT 双协议、预算折叠、视觉剥离） |
| **系统提示词构建** | [`harness/.../prompt/SystemPromptBuilder.kt`](../harness/src/main/java/top/wkbin/taixu/harness/prompt/SystemPromptBuilder.kt) | 基础模板/技能/记忆/规划/工作区聚合；权限章节经 `PrivilegeSectionRenderer` 接缝注入 |
| **悬空调用修复** | [`harness/.../effects/DanglingToolCallPlanner.kt`](../harness/src/main/java/top/wkbin/taixu/harness/effects/DanglingToolCallPlanner.kt) | 中断/进程死亡后 ToolCall 的重放与占位策略（策略-执行分离） |
| **审批恢复裁决** | [`harness/.../approval/ApprovalResumePolicy.kt`](../harness/src/main/java/top/wkbin/taixu/harness/approval/ApprovalResumePolicy.kt) | 恢复执行前四重校验（过期/参数摘要/工作区/operation 归属）与状态映射 |
| **内置工具执行器** | [`harness/.../ToolExecutor.kt`](../harness/src/main/java/top/wkbin/taixu/harness/ToolExecutor.kt) | `base` 超时解析、`process` 生命周期与文件工具分派 |
| **工具协议与 Schema** | [`harness/.../ProviderClient.kt`](../harness/src/main/java/top/wkbin/taixu/harness/ProviderClient.kt) | 向模型暴露内置工具定义和参数边界 |
| **工具审批策略** | [`harness/.../ApprovalPolicyEngine.kt`](../harness/src/main/java/top/wkbin/taixu/harness/ApprovalPolicyEngine.kt) | 根据工具、动作和安全模式计算审批要求 |
| **模型 Provider** | [`tools/.../ProviderRepository.kt`](../tools/src/main/java/top/wkbin/taixu/core/tools/ProviderRepository.kt) | Provider 读取、校验与安全策略 |
| **工具注册表** | [`app/.../registry/tools.json`](../app/src/main/assets/registry/tools.json) | 内置工具 manifest（端口/启动命令/环境变量） |
| **PRoot 命令构建** | [`runtime/.../ProotCommandBuilder.kt`](../runtime/src/main/java/top/wkbin/taixu/runtime/proot/ProotCommandBuilder.kt) | PRoot 启动参数与 `-b` 存储挂载注入 |
| **后台进程注册表** | [`runtime/.../ProcessRegistry.kt`](../runtime/src/main/java/top/wkbin/taixu/runtime/shell/ProcessRegistry.kt) | 托管 LinuxSession、PID、状态和日志 |
| **本地偏好配置** | [`core/datastore/.../SettingsDataStore.kt`](../core/datastore/src/main/java/top/wkbin/taixu/core/datastore/SettingsDataStore.kt) | 存储挂载、外观、模型配置持久化 |
| **偏好领域分面** | [`core/datastore/.../PreferenceFacades.kt`](../core/datastore/src/main/java/top/wkbin/taixu/core/datastore/PreferenceFacades.kt) | 向 UI/runtime/harness 暴露最小配置接口 |
| **持久化仓储边界** | [`core/database/.../PersistenceRepositories.kt`](../core/database/src/main/java/top/wkbin/taixu/core/database/PersistenceRepositories.kt) | 隔离 Room DAO 与业务模块 |
| **对讲界面与双栏** | [`feature/chat/.../ChatScreen.kt`](../feature/chat/src/main/java/top/wkbin/taixu/ui/chat/ChatScreen.kt) | M3 对话流、TaskPlanCard、双栏分屏联动 |
| **任务看板与压缩横幅** | [`feature/chat/.../PlanBoard.kt`](../feature/chat/src/main/java/top/wkbin/taixu/ui/chat/PlanBoard.kt)、[`CompactionBanner.kt`](../feature/chat/src/main/java/top/wkbin/taixu/ui/chat/CompactionBanner.kt) | plan 工具数据源的步骤看板；上下文折叠透明度横幅（累计条数 + 摘要展开） |
| **记忆与草稿抽屉** | [`feature/chat/.../SessionMemorySheet.kt`](../feature/chat/src/main/java/top/wkbin/taixu/ui/chat/SessionMemorySheet.kt) | AgentMemoryEntity 全量观察 + 当前会话 Scratchpad 的查看与删除入口 |
| **终端与触觉交互** | [`feature/terminal/.../TerminalScreen.kt`](../feature/terminal/src/main/java/top/wkbin/taixu/ui/terminal/TerminalScreen.kt) | 原生 PTY 渲染、辅助按键与 Haptic 震动 |
| **终端核心状态** | [`runtime/.../terminal/TerminalSessionManager.kt`](../runtime/src/main/java/top/wkbin/taixu/runtime/terminal/TerminalSessionManager.kt) | UI 无关的 PTY 会话、VT100 缓冲和持久化 |
| **工作区领域入口** | [`runtime/.../WorkspaceManager.kt`](../runtime/src/main/java/top/wkbin/taixu/runtime/WorkspaceManager.kt) | 创建、ZIP/GitHub 导入、ZIP 导出、项目元数据和文件入口 |
| **工作区构建执行** | [`runtime/.../WorkspaceBuildRunner.kt`](../runtime/src/main/java/top/wkbin/taixu/runtime/build/WorkspaceBuildRunner.kt) | Android/Flutter 预检、构建、日志、APK 验证与安装 |
| **工作区构建保活** | [`feature/workspace/.../WorkspaceBuildTaskCoordinator.kt`](../feature/workspace/src/main/java/top/wkbin/taixu/ui/workspace/WorkspaceBuildTaskCoordinator.kt) | 单例持有构建 Job/StateFlow，跨页面重建保留进度 |
| **工作区 UI** | [`feature/workspace/.../WorkspaceScreen.kt`](../feature/workspace/src/main/java/top/wkbin/taixu/ui/workspace/WorkspaceScreen.kt) | 项目导入导出、开发套件、构建进度与日志入口 |
| **工具中心详情** | [`feature/settings/.../ToolDetailScreen.kt`](../feature/settings/src/main/java/top/wkbin/taixu/ui/settings/ToolDetailScreen.kt) | 工具安装/网关/模型/Token 配置详情 UI |
| **本地插件注册** | [`tools/.../ToolRegistry.kt`](../tools/src/main/java/top/wkbin/taixu/core/tools/ToolRegistry.kt) | 本地插件导入、版本去重、manifest 与 payload 定位 |
| **本地插件 Payload** | [`tools/.../LocalPluginPayloadManager.kt`](../tools/src/main/java/top/wkbin/taixu/core/tools/LocalPluginPayloadManager.kt) | 将导入资源流式复制到发行版 `/opt/taixu/imports` 并上报字节进度 |
| **通用插件安装器** | [`tools/.../GenericRecipeInstaller.kt`](../tools/src/main/java/top/wkbin/taixu/runtime/tools/GenericRecipeInstaller.kt) | 沙箱目录准备、安装事务与 recipe 执行 |
| **Android 离线套件** | [`assets/plugins/android-suite-offline/manifest.json`](../assets/plugins/android-suite-offline/manifest.json) | ARM64 Android/Flutter 工具链版本、环境和安装入口 |
| **移动端 Gradle 策略** | [`assets/.../gradle.properties`](../assets/plugins/android-suite-offline/payload/config/gradle.properties) | 沙箱构建的 daemon、并行度、worker 和 JVM 内存上限 |
| **设计系统与基础组件** | [`feature/components/.../RuntimeComponents.kt`](../feature/components/src/main/java/top/wkbin/taixu/ui/components/RuntimeComponents.kt) | `RuntimeCard`, `RuntimeTopBar`, `StatusBadge`, 图标 |
| **仪表盘** | [`feature/home/.../HomeScreen.kt`](../feature/home/src/main/java/top/wkbin/taixu/ui/home/HomeScreen.kt) | Linux 沙箱监控仪表盘、RAM/磁盘/进程看板 |
| **Native PTY C** | [`app/src/main/cpp/pty_native.c`](../app/src/main/cpp/pty_native.c) | JNI PTY 核心底层实现 |

---

## 5. 🛡️ 架构铁律与 AI 编码避坑指南 (Rules & Gotchas for AI)

1. **Gradle 构建环境变量（Windows PowerShell）**：
   - 必须通过 `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat <task>` 执行 Gradle 任务，以确保使用 Android Studio 随附的 JBR（当前环境为 Java 25.0.2）。
   - 主工程版本以 `gradle/wrapper/gradle-wrapper.properties`、`gradle/libs.versions.toml` 和 `app/build.gradle.kts` 为准；不要套用沙箱 Android 离线套件的 Gradle/AGP/NDK 版本。
2. **纯粹模型层隔离**：
   - `:core:model` **严禁** 引入 `android.*` 或 Compose 依赖，必须保持 Pure Kotlin。
   - `architectureCheck` 已接入 `app:preBuild`，会阻止模型层平台化、非法 feature 横向依赖和业务层直连 DAO。
3. **M3 Expressive 组件规范**：
   - 使用统一的 `RuntimeCard`（支持 `containerColor`, `borderColor`, `contentPadding`, `onClick`），不要直接手动魔改带有生硬边框的普通 Card。
   - 顶部导航统一使用 `RuntimeTopBar`（支持 `title`, `statusText`, `onBack`）。
   - 底部导航统一使用基于 M3 原生 NavigationBar 的 `RuntimeBottomBar`。
4. **安全与网络策略**：
   - 本地模型（`LOCAL` 分组）允许 `http://127.0.0.1:*` 或 `http://localhost:*`，且 `apiKeyOptional = true`。
   - 外部模型（`OFFICIAL` / `CHINA` / `AGGREGATOR`）强制要求 HTTPS。
5. **PRoot 挂载安全性**：
   - 挂载路径必须经过 `ProotCommandBuilder` 正规化处理，避免 Shell 注入与危险的系统根目录穿越。
6. **PRoot 进程生命周期**：
   - `base` 只用于有界前台命令；默认 10 分钟，可由用户配置为 1–60 分钟，也可用 `timeout_seconds` 对单次调用覆盖 1–3600 秒。
   - 不要在 `base` 中用 `nohup` / `setsid` / `&` 模拟守护进程。长任务必须调用 `process(action="start", ...)`，后续用 `status` / `logs` / `list` / `stop` 管理。
   - `process` 的外部 ID 会加 `agent-process:` 命名空间，禁止与工具中心服务进程混用。
7. **移动端 Gradle 资源策略**：
   - 沙箱 Android/Flutter 构建固定 `--no-daemon --max-workers=2`，全局配置保持 `org.gradle.daemon=false`、`org.gradle.parallel=false`、`org.gradle.workers.max=2`。
   - NDK 只允许由 init policy 的 `android.ndkPath` 注入。构建脚本可清理旧 `local.properties` 的 `ndk.dir`，不得同时重新写入 `ndk.dir` 或在项目中再声明 `ndkPath`，否则 AGP 会报双 locator 冲突。
8. **工作区导入安全**：
   - ZIP 导入必须使用 `WorkspaceManager.importProjectArchive()`，禁止直接解压不受信路径；Git HTTP(S) 与 SSH URL 必须按用户选择的 transport 分别校验。
   - 导入类型写入 `.taixu-project.properties`，不能只靠文件扩展名或目录内容重新猜测用户选择。
9. **本地插件幂等性**：
   - 同一插件 ID + version 已存在时返回“已导入”，不得递归覆盖现有 payload；安装框架应先保证 `$toolDir/bin` 存在。

---

## 6. 🛠️ AI 常用自动化指令速查 (Runbook)

```powershell
# 1. 运行所有单元测试
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat testDebugUnitTest --console=plain

# 2. 仅编译并打包 Debug APK
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat assembleDebug --console=plain

# 3. 安装到已连接的真机或模拟器
adb install -r app/build/outputs/apk/debug/taixu-v0.5.0-debug.apk

# 4. 启动太墟主 Activity
adb shell am start -n top.wkbin.taixu/.MainActivity

# 5. 过滤太墟日志
adb logcat -s TaiXu:V HarnessLoop:V ProotProcess:V

# 6. 仅验证 Harness 内置工具契约与执行策略
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :harness:testDebugUnitTest --tests "top.wkbin.taixu.harness.*" --console=plain

# 7. 验证架构边界
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat architectureCheck --console=plain
```
