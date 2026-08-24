# 🧭 太墟 (TaiXu / LinuxAIRuntime) — AI 架构与开发全局导航

> **专供 AI Coding Assistant (Antigravity / Cursor / Claude / GPT / Copilot) 全局感知与开发**
> 本文档包含太墟的核心架构、模块职责、高频调用链路、文件索引与架构铁律，AI 无需额外泛搜全文即可精准定位并编写代码。

---

## 1. 🚀 项目定位与技术栈摘要 (Tech Stack Snapshot)

- **核心定位**：在 **Android 无 Root 用户态** 下，基于 **PRoot** 运行完整的 **Linux 沙箱**，并深度集成 **AI Agent Harness 智能体引擎**、**原生 PTY 终端**、**工作区文件管理** 与 **Material 3 Expressive UI**。
- **Android / UI 层**：
  - **语言**：Kotlin 2.4 (100% Kotlin + Jetpack Compose)
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
  - **本地端侧模型**：沙箱内原生支持 `llama.cpp` 与 `Ollama`，无需 API Key

---

## 2. 🗺️ 模块拓扑与职责索引 (Module Topology)

```text
LinuxAIRuntime/
├── app/                  # 应用壳工程：MainActivity、Hilt 初始化、JNI C 代码、前台保活 Service
├── core/
│   ├── model/           # 纯 Kotlin 数据模型 (不含 Android SDK 依赖)
│   ├── common/          # 协程调度器、日志、通用工具类
│   ├── database/        # Room 数据库：Harness 对话记录、会话、工具执行历史
│   ├── datastore/       # Jetpack DataStore：用户偏好、存储挂载开关、激活模型
│   ├── network/         # OkHttp 网络客户端、SSE 流式解析器、超时策略
│   └── security/        # API Key 本地安全加解密
├── runtime/              # Linux 沙箱与 PRoot 核心：ProotCommandBuilder, PTY/终端会话, 工作区
├── harness/              # Agent 智能体核心：Agent 循环、流式推理、工具调度、MCP
├── tools/                # 工具生态中心：Registry、安装事务、Provider 安全
└── feature/              # Compose UI 业务特性层
    ├── components/      # 太墟 M3 Expressive 设计规范、通用组件 (RuntimeCard, TopBar, Icons)
    ├── theme/           # Material 3 调色板、字体、主题配置
    ├── home/            # 首页运行仪表盘：Linux 系统状态、内存/磁盘/进程实时监控
    ├── chat/            # 智枢 Agent 对话界面、TaskPlanCard 任务拆解卡片、宽屏双栏布局
    ├── terminal/        # 终端 Compose UI 与触觉按键条；会话/VT100 状态机位于 runtime
    ├── workspace/       # 工作区管理器、项目代码浏览器、/sdcard 宿主直通
    ├── settings/        # 设置中心：模型档案管理、工具中心、存储挂载管理、外观与诊断
    ├── developer/       # 开发者原生沙箱与诊断面板
    ├── welcome/         # 首次启动引导与 RootFS 解压就绪流程
    └── navigation/      # 顶层导航路由与 NavHost 调度
```

## 2.5 🔗 跨模块要点 (Cross-Module Rules)

- **依赖方向**：`feature/*` → `core/*` / `runtime` / `harness` / `tools`；业务 feature 仅可依赖共享 UI `feature/components` / `feature/theme`，顶层 `feature/navigation` 负责装配各页面。
- **持久化边界**：feature / runtime / harness 只依赖 `PersistenceRepositories.kt` 中的仓储接口，不得直接注入 Room DAO；偏好按领域使用 `PreferenceFacades.kt`。
- **纯模型层隔离**：`:core:model` 必须保持 Pure Kotlin，**严禁**引入 `android.*` 或 Compose 依赖。
- **单模块编译验证**（Windows PowerShell）：
  ```powershell
  $env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :模块名:compileDebugKotlin --console=plain -q
  ```
- **新增工具适配器**：实现 `ToolRuntimeAdapter`（安装/启动/卸载/校验），并通过 Hilt `@Binds` 注册到 `app/.../di/ToolAdapterModule.kt`。

---

## 3. ⚡ 5 大高频核心调用链路 (Top 5 Execution Traces)

### 3.1 Agent 发起与工具执行循环 (Harness Loop)
```text
ChatScreen (UI)
  └─► ChatViewModel.send(prompt)
        └─► HarnessLoop.send()
              ├─► ProviderRepository 读取 BaseURL / Model / ApiKey
              ├─► ModelClient.streamChat() (core:network) ➔ 流式接收 reasoning & text
              └─► 当模型返回 tool_calls:
                    ├─► HarnessTool (bash_exec / write_file / view_file / read_file)
                    ├─► RuntimeManager.executeCommand() (runtime) ➔ PRoot 容器执行
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

---

## 4. 📍 关键文件绝对路径速查表 (Key File Index)

| 业务领域 | 关键文件路径 | 核心职责 |
| :--- | :--- | :--- |
| **Agent 核心** | [`harness/.../HarnessLoop.kt`](../harness/src/main/java/top/wkbin/taixu/harness/HarnessLoop.kt) | Agent 思考与工具调用闭环调度 |
| **模型 Provider** | [`tools/.../ProviderRepository.kt`](../tools/src/main/java/top/wkbin/taixu/core/tools/ProviderRepository.kt) | Provider 读取、校验与安全策略 |
| **工具注册表** | [`app/.../registry/tools.json`](../app/src/main/assets/registry/tools.json) | 内置工具 manifest（端口/启动命令/环境变量） |
| **PRoot 命令构建** | [`runtime/.../ProotCommandBuilder.kt`](../runtime/src/main/java/top/wkbin/taixu/runtime/proot/ProotCommandBuilder.kt) | PRoot 启动参数与 `-b` 存储挂载注入 |
| **本地偏好配置** | [`core/datastore/.../SettingsDataStore.kt`](../core/datastore/src/main/java/top/wkbin/taixu/core/datastore/SettingsDataStore.kt) | 存储挂载、外观、模型配置持久化 |
| **偏好领域分面** | [`core/datastore/.../PreferenceFacades.kt`](../core/datastore/src/main/java/top/wkbin/taixu/core/datastore/PreferenceFacades.kt) | 向 UI/runtime/harness 暴露最小配置接口 |
| **持久化仓储边界** | [`core/database/.../PersistenceRepositories.kt`](../core/database/src/main/java/top/wkbin/taixu/core/database/PersistenceRepositories.kt) | 隔离 Room DAO 与业务模块 |
| **对讲界面与双栏** | [`feature/chat/.../ChatScreen.kt`](../feature/chat/src/main/java/top/wkbin/taixu/ui/chat/ChatScreen.kt) | M3 对话流、TaskPlanCard、双栏分屏联动 |
| **终端与触觉交互** | [`feature/terminal/.../TerminalScreen.kt`](../feature/terminal/src/main/java/top/wkbin/taixu/ui/terminal/TerminalScreen.kt) | 原生 PTY 渲染、辅助按键与 Haptic 震动 |
| **终端核心状态** | [`runtime/.../terminal/TerminalSessionManager.kt`](../runtime/src/main/java/top/wkbin/taixu/runtime/terminal/TerminalSessionManager.kt) | UI 无关的 PTY 会话、VT100 缓冲和持久化 |
| **工具中心详情** | [`feature/settings/.../ToolDetailScreen.kt`](../feature/settings/src/main/java/top/wkbin/taixu/ui/settings/ToolDetailScreen.kt) | 工具安装/网关/模型/Token 配置详情 UI |
| **设计系统与基础组件** | [`feature/components/.../RuntimeComponents.kt`](../feature/components/src/main/java/top/wkbin/taixu/ui/components/RuntimeComponents.kt) | `RuntimeCard`, `RuntimeTopBar`, `StatusBadge`, 图标 |
| **仪表盘** | [`feature/home/.../HomeScreen.kt`](../feature/home/src/main/java/top/wkbin/taixu/ui/home/HomeScreen.kt) | Linux 沙箱监控仪表盘、RAM/磁盘/进程看板 |
| **Native PTY C** | [`app/src/main/cpp/pty_native.c`](../app/src/main/cpp/pty_native.c) | JNI PTY 核心底层实现 |

---

## 5. 🛡️ 架构铁律与 AI 编码避坑指南 (Rules & Gotchas for AI)

1. **Gradle 构建环境变量（Windows PowerShell）**：
   - 必须通过 `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat <task>` 执行 Gradle 任务，以确保使用正确的 Android Studio JDK 17。
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

---

## 6. 🛠️ AI 常用自动化指令速查 (Runbook)

```powershell
# 1. 运行所有单元测试
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat testDebugUnitTest

# 2. 仅编译并打包 Debug APK
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat assembleDebug

# 3. 安装到已连接的真机或模拟器
adb install -r app/build/outputs/apk/debug/taixu-v0.4.1-debug.apk

# 4. 启动太墟主 Activity
adb shell am start -n top.wkbin.taixu/.MainActivity

# 5. 过滤太墟日志
adb logcat -s TaiXu:V HarnessLoop:V ProotProcess:V
```
