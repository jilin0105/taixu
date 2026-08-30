# 📍 太墟 (TaiXu) — 关键文件与职责速查表 (Key File Index)

---

## 1. 核心与 Harness 智能体引擎 (Core & Agent Harness)

| 业务领域 | 关键文件路径 | 核心职责 |
| :--- | :--- | :--- |
| **Agent 核心循环** | [`harness/.../HarnessLoop.kt`](../harness/src/main/java/top/wkbin/taixu/harness/HarnessLoop.kt) | Agent 思考与工具调用闭环调度（并发内核 + 公共 API 门面） |
| **会话消息投影** | [`harness/.../projection/SessionMessageProjector.kt`](../harness/src/main/java/top/wkbin/taixu/harness/projection/SessionMessageProjector.kt) | 实时消息流、前台镜像与流式上屏；配套 `SessionStateMirrors` 与 `CurrentSessionTracker` |
| **上下文组装** | [`harness/.../session/ApiContextAssembler.kt`](../harness/src/main/java/top/wkbin/taixu/harness/session/ApiContextAssembler.kt) | 会话树 → 提供商协议消息（NATIVE / JSON_TEXT 双协议、预算折叠、视觉剥离） |
| **系统提示词构建** | [`harness/.../prompt/SystemPromptBuilder.kt`](../harness/src/main/java/top/wkbin/taixu/harness/prompt/SystemPromptBuilder.kt) | 基础模板/技能/记忆/规划/工作区聚合；权限章节经 `PrivilegeSectionRenderer` 接缝注入 |
| **悬空调用修复** | [`harness/.../effects/DanglingToolCallPlanner.kt`](../harness/src/main/java/top/wkbin/taixu/harness/effects/DanglingToolCallPlanner.kt) | 中断/进程死亡后 ToolCall 的重放与占位策略（策略-执行分离） |
| **审批恢复裁决** | [`harness/.../approval/ApprovalResumePolicy.kt`](../harness/src/main/java/top/wkbin/taixu/harness/approval/ApprovalResumePolicy.kt) | 恢复执行前四重校验（过期/参数摘要/工作区/operation 归属）与状态映射 |
| **内置工具执行器** | [`harness/.../ToolExecutor.kt`](../harness/src/main/java/top/wkbin/taixu/harness/ToolExecutor.kt) | `base` 超时解析、`process` 生命周期与文件工具分派 |
| **工具协议与 Schema** | [`harness/.../ProviderClient.kt`](../harness/src/main/java/top/wkbin/taixu/harness/ProviderClient.kt) | 向模型暴露内置工具定义和参数边界 |
| **工具审批策略** | [`harness/.../ApprovalPolicyEngine.kt`](../harness/src/main/java/top/wkbin/taixu/harness/ApprovalPolicyEngine.kt) | 根据工具、动作和安全模式计算审批要求 |
| **持久化仓储边界** | [`core/database/.../PersistenceRepositories.kt`](../core/database/src/main/java/top/wkbin/taixu/core/database/PersistenceRepositories.kt) | 隔离 Room DAO 与业务模块 |
| **本地偏好配置** | [`core/datastore/.../SettingsDataStore.kt`](../core/datastore/src/main/java/top/wkbin/taixu/core/datastore/SettingsDataStore.kt) | 存储挂载、外观、模型配置持久化 |
| **偏好领域分面** | [`core/datastore/.../PreferenceFacades.kt`](../core/datastore/src/main/java/top/wkbin/taixu/core/datastore/PreferenceFacades.kt) | 向 UI/runtime/harness 暴露最小配置接口 |

---

## 2. 运行时与沙箱 (Runtime & Linux Sandbox)

| 业务领域 | 关键文件路径 | 核心职责 |
| :--- | :--- | :--- |
| **PRoot 命令构建** | [`runtime/.../ProotCommandBuilder.kt`](../runtime/src/main/java/top/wkbin/taixu/runtime/proot/ProotCommandBuilder.kt) | PRoot 启动参数与 `-b` 存储挂载注入 |
| **后台进程注册表** | [`runtime/.../ProcessRegistry.kt`](../runtime/src/main/java/top/wkbin/taixu/runtime/shell/ProcessRegistry.kt) | 托管 LinuxSession、PID、状态和日志 |
| **终端核心状态** | [`runtime/.../terminal/TerminalSessionManager.kt`](../runtime/src/main/java/top/wkbin/taixu/runtime/terminal/TerminalSessionManager.kt) | UI 无关的 PTY 会话、VT100 缓冲和持久化 |
| **工作区领域入口** | [`runtime/.../WorkspaceManager.kt`](../runtime/src/main/java/top/wkbin/taixu/runtime/WorkspaceManager.kt) | 创建、ZIP/GitHub 导入、ZIP 导出、项目元数据和文件入口 |
| **工作区构建执行** | [`runtime/.../WorkspaceBuildRunner.kt`](../runtime/src/main/java/top/wkbin/taixu/runtime/build/WorkspaceBuildRunner.kt) | Android/Flutter 预检、构建、日志、APK 验证与安装 |
| **Native PTY C** | [`app/src/main/cpp/pty_native.c`](../app/src/main/cpp/pty_native.c) | JNI PTY 核心底层实现 |

---

## 3. 工具生态与插件 (Tools & Plugins)

| 业务领域 | 关键文件路径 | 核心职责 |
| :--- | :--- | :--- |
| **模型 Provider 仓储** | [`tools/.../ProviderRepository.kt`](../tools/src/main/java/top/wkbin/taixu/core/tools/ProviderRepository.kt) | Provider 读取、校验与安全策略 |
| **工具注册表** | [`app/.../registry/tools.json`](../app/src/main/assets/registry/tools.json) | 内置工具 manifest（端口/启动命令/环境变量） |
| **本地插件注册** | [`tools/.../ToolRegistry.kt`](../tools/src/main/java/top/wkbin/taixu/core/tools/ToolRegistry.kt) | 本地插件导入、版本去重、manifest 与 payload 定位 |
| **本地插件 Payload** | [`tools/.../LocalPluginPayloadManager.kt`](../tools/src/main/java/top/wkbin/taixu/core/tools/LocalPluginPayloadManager.kt) | 将导入资源流式复制到发行版 `/opt/taixu/imports` 并上报字节进度 |
| **通用插件安装器** | [`tools/.../GenericRecipeInstaller.kt`](../tools/src/main/java/top/wkbin/taixu/runtime/tools/GenericRecipeInstaller.kt) | 沙箱目录准备、安装事务与 recipe 执行 |
| **Android 离线套件** | [`assets/plugins/android-suite-offline/manifest.json`](../assets/plugins/android-suite-offline/manifest.json) | ARM64 Android/Flutter 工具链版本、环境和安装入口 |
| **移动端 Gradle 策略** | [`assets/.../gradle.properties`](../assets/plugins/android-suite-offline/payload/config/gradle.properties) | 沙箱构建的 daemon、并行度、worker 和 JVM 内存上限 |

---

## 4. UI 界面与交互层 (UI & Features)

| 业务领域 | 关键文件路径 | 核心职责 |
| :--- | :--- | :--- |
| **设计系统与基础组件** | [`feature/components/.../RuntimeComponents.kt`](../feature/components/src/main/java/top/wkbin/taixu/ui/components/RuntimeComponents.kt) | `RuntimeCard`, `RuntimeTopBar`, `RuntimeAlertDialog`, `StatusBadge`, 图标 |
| **仪表盘** | [`feature/home/.../HomeScreen.kt`](../feature/home/src/main/java/top/wkbin/taixu/ui/home/HomeScreen.kt) | Linux 沙箱监控仪表盘、RAM/磁盘/进程看板 |
| **对讲界面与双栏** | [`feature/chat/.../ChatScreen.kt`](../feature/chat/src/main/java/top/wkbin/taixu/ui/chat/ChatScreen.kt) | 入口状态装配与弹窗编排、ChatPaneContent 双栏/单栏分屏联动；UI 已模块化：`ChatTopBar`·`ChatMessageList`·`ChatComposer`·`ChatMessageBubbles`·`ChatToolCards`·`ChatDialogs`·`ChatSheets`·`ChatPopups`·`ChatMentionText`·`ChatUiHelpers` 同包拆分 |
| **任务看板与压缩横幅** | [`feature/chat/.../PlanBoard.kt`](../feature/chat/src/main/java/top/wkbin/taixu/ui/chat/PlanBoard.kt)、[`CompactionBanner.kt`](../feature/chat/src/main/java/top/wkbin/taixu/ui/chat/CompactionBanner.kt) | plan 工具数据源的步骤看板；上下文折叠透明度横幅 |
| **记忆与草稿抽屉** | [`feature/chat/.../SessionMemorySheet.kt`](../feature/chat/src/main/java/top/wkbin/taixu/ui/chat/SessionMemorySheet.kt) | AgentMemoryEntity 全量观察 + 当前会话 Scratchpad 的查看与删除入口 |
| **产物交付预览浮层** | [`feature/chat/.../ArtifactPreviewSheet.kt`](../feature/chat/src/main/java/top/wkbin/taixu/ui/chat/artifact/ArtifactPreviewSheet.kt) | Markdown/代码高亮与就地编辑 |
| **终端与触觉交互** | [`feature/terminal/.../TerminalScreen.kt`](../feature/terminal/src/main/java/top/wkbin/taixu/ui/terminal/TerminalScreen.kt) | 原生 PTY 渲染、辅助按键与 Haptic 震动 |
| **工作区构建保活** | [`feature/workspace/.../WorkspaceBuildTaskCoordinator.kt`](../feature/workspace/src/main/java/top/wkbin/taixu/ui/workspace/WorkspaceBuildTaskCoordinator.kt) | 单例持有构建 Job/StateFlow，跨页面重建保留进度 |
| **工作区 UI** | [`feature/workspace/.../WorkspaceScreen.kt`](../feature/workspace/src/main/java/top/wkbin/taixu/ui/workspace/WorkspaceScreen.kt) | 项目导入导出、开发套件、构建进度与日志入口 |
| **工具中心详情** | [`feature/settings/.../ToolDetailScreen.kt`](../feature/settings/src/main/java/top/wkbin/taixu/ui/settings/ToolDetailScreen.kt) | 工具安装/网关/模型/Token 配置详情 UI |
