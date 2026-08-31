<p align="center">
  <img src="app/src/main/res/drawable/taixu_logo.webp" width="96" alt="太墟 Logo" />
</p>

<h1 align="center">太墟 · TaiXu</h1>

<p align="center"><strong>掌中归墟，万象可期。</strong></p>

<p align="center">
  Android 无 Root Linux Runtime · Agent Harness · 原生 PTY · 移动开发工作区
</p>

<p align="center">
  <code>v0.8.1</code> · <code>Android 10+</code> · <code>arm64-v8a</code>
</p>

---

## 何为太墟

《列子》称众水所归、无增无减之处为“归墟”。太墟借其意，在 Android 有限的应用沙盒中，建立一方可运行、可观察、可恢复的 Linux 世界。

它不是给聊天套上终端，也不试图把手机伪装成无限的机器。它让模型、工具、终端与工作区共享同一份上下文，使一句意图能够落成真实的文件、命令、进程与构建结果。

> 于太墟中立极，于方寸间创世。

## 已有之物

| 领域 | 现有能力 |
| --- | --- |
| Linux 沙箱 | 基于 PRoot 无 Root 运行 10 种 ARM64 发行版；通过 OCI 拉取并校验 RootFS，支持多系统切换、更新回滚、持久化目录与 Android 存储挂载。 |
| Agent Harness | 支持 OpenAI 兼容接口与 Anthropic Messages API、流式文本/推理/工具调用，以及计划、规则、技能、记忆、上下文折叠、子智能体和分级审批。 |
| 行动工具 | 覆盖文件读写与编辑、前台命令、托管进程、HTTPS 下载、构建、历史检索等；MCP 支持 Stdio、Streamable HTTP 与 Legacy SSE。 |
| 原生终端 | JNI `forkpty`、多会话、ANSI/VT100、UTF-8、信号与动态 Resize；原生后端不可用时回退到 `script` PTY。 |
| 工作区 | 创建空项目，或从 ZIP、Git 仓库导入；提供文件编辑、会话绑定、后台构建、APK 校验安装，以及 Android、Flutter 与 APK 逆向工作流。 |
| 工具生态 | 本地插件、内置/签名 Registry、安装事务、校验、更新、卸载与回滚；可装配移动开发、Rust、逆向、CLI Agent、Web 服务和 QEMU 兼容组件。 |
| 端侧协作 | 支持 llama.cpp 本地 GGUF 模型、图片消息、智枢悬浮窗、局域网 WebChat、运行仪表盘、日志诊断与敏感配置保护。 |

```text
人的意图 → 计划 → 工具 / MCP / Linux → 结果验证
              ↑                       ↓
              └──── 未完成则继续 ─────┘
```

语言不是终点。只有行动留下可检查的结果，意图才开始成为工程。

## 快速开始

1. 在 ARM64、Android 10 或更高版本的设备上安装 [最新 Release](https://github.com/wkbin/taixu/releases)。
2. 选择 Linux 发行版，完成首次 RootFS 初始化。
3. 配置模型 Provider，或在沙箱内运行 llama.cpp 本地模型。
4. 创建工作区，或导入 ZIP / Git 项目，并将会话绑定到它。
5. 向智枢描述目标；它会规划、调用工具、检查结果并继续推进。

> RootFS 不打包进 APK。首次初始化需要网络与足够存储空间；持续后台任务需要允许通知和前台服务。

## 从源码构建

环境要求：Android Studio JBR、Android SDK 37、NDK `30.0.15729638`、CMake 3.22.1。项目使用 Kotlin 2.4、Jetpack Compose、Hilt、Room、Navigation3 与 Gradle 9.7。

```powershell
# 首次检出后准备 PRoot ARM64 原生组件
.\tools\prepare-proot-runtime.ps1

$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat architectureCheck --console=plain
.\gradlew.bat testDebugUnitTest --console=plain
.\gradlew.bat assembleDebug --console=plain
```

Debug APK：`app/build/outputs/apk/debug/taixu-v0.8.1-debug.apk`

工程按 `app`、`core`、`runtime`、`tools`、`harness` 与 `feature` 分层。架构与调用链见 [AI 导航](docs/AI_NAVIGATION.md)。

## 边界

- 当前仅支持 `arm64-v8a`；其他 ABI 会在初始化阶段停止。
- PRoot 是用户态兼容层，不是虚拟机，也不提供 Root、完整内核能力或任意容器兼容性。
- RootFS、移动工具链和第三方 Agent 依赖上游资源，实际兼容性仍受设备、网络与版本变化影响。
- 复杂 TUI、输入法组合文本、后台保活与重型构建仍需要更多 ARM64 真机验证。
- 外部模型与下载端点要求 HTTPS；HTTP 仅向受控的本地端点开放。请勿提交 API Key、令牌或私有配置。

更多信息：[架构](docs/ARCHITECTURE.md) · [插件指南](docs/TAIXU_PLUGINS_GUIDE_FOR_BEGINNERS.md) · [已知问题](docs/KNOWN_ISSUES.md) · [路线图](docs/ROADMAP.md) · [第三方许可](docs/THIRD_PARTY_LICENSES.md)

## 注脚

太墟不许诺消除边界。它选择看见边界，命名边界，并在其中建立能够理解、验证与返回的秩序。

限制从未消失；自由也可以来自身处限制之中，仍有能力建立自己的世界。

> 须弥纳于芥子，太墟纳于掌中。

欢迎通过 Issue、Pull Request 与真机验证记录参与建设。
