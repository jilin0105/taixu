<p align="center">
  <img src="app/src/main/res/drawable/logo.webp" width="96" alt="太墟 Logo" />
</p>

<h1 align="center">太墟 · TaiXu</h1>

<p align="center"><strong>掌中归墟，万象可期。</strong></p>

<p align="center">
  Android 无 Root Linux Runtime · 原生 Agent Harness · PTY 终端 · 工作区与工具生态
</p>

---

## 序：何为太墟

《列子·汤问》载：

> 渤海之东，不知几亿万里，有大壑焉，实惟无底之谷，其下无底，名曰归墟。八纮九野之水，天汉之流，莫不注之，而无增无减焉。

归墟，是众水所归之处。它看似空无，却并非虚无；旧有的边界在此消解，新的秩序也由此获得发生的可能。

**太墟**，正是掌中的这样一方空间。

它不是普通的聊天应用，也不只是为终端披上一层界面。太墟试图在 Android 受限的应用沙盒中，建立一个可运行、可观察、可持续演化的 Linux 世界：模型在这里获得语言，工具获得双手，终端保存因果，工作区赋予记忆以位置。

第一次启动时，一切尚未展开。没有 RootFS，没有进程，没有工程，也没有等待被回答的问题。当第一个 Linux 系统被初始化、第一个工作区被建立、第一项任务被交给 Agent，一种微小的计算秩序便从空处生长出来。

> 于太墟中立极，于方寸间创世。

---

## 当前形态

太墟当前版本为 **0.1.0**，面向 **Android 10+、ARM64** 设备，仍处于快速演进阶段。它已经形成从 Linux 运行时、模型接入、Agent 工具循环到终端与工作区的完整主链路，同时仍需要更多真机、复杂 TUI 和第三方工具组合验证。

```text
人的意图
   ↓
模型推理 ─→ 工具选择 ─→ Linux / MCP 执行 ─→ 结果校验
   ↑                                             ↓
   └────────────── 未完成则继续 ─────────────────┘
```

这条循环是太墟的核心：语言不是终点，而是行动开始形成之前的第一重结构。

---

## 已具备的能力

### 一、衍化诸界 · 无 Root 多发行版 Linux

太墟以 **PRoot** 为边界，在不获取 Root 权限、不修改 Android 系统分区的前提下运行完整 Linux 用户空间。

- 支持 Ubuntu 24.04、Debian 12、Kali Rolling、Arch Linux、Fedora 40、Alpine 3.19、AlmaLinux 9 与 openSUSE Tumbleweed。
- 支持多发行版安装、切换、空间统计与生命周期管理。
- RootFS 通过 OCI Registry 获取，支持 ARM64 manifest、SHA-256 layer 校验、gzip/zstd 解压与 OCI whiteout 合并。
- RootFS 更新采用 staging、健康检查与两阶段提交；激活失败或异常中断时可恢复旧系统。
- `/root`、`/opt/taixu`、工作区与临时目录使用独立持久化绑定，系统更新不应带走用户工具和配置。
- 支持 Download、Documents、共享存储及受约束的自定义宿主目录挂载。
- 针对 PRoot 下的 dpkg 硬链接、apt 锁、Git 虚拟 UID 等常见问题提供环境配置与自愈路径。

边界没有消失。太墟所做的，是承认边界，然后在边界之内建立一套足够完整的法则。

### 二、智枢 Harness · 让模型从回答走向完成

内置 Harness 将对话、推理、工具调用、执行结果与下一轮判断连接成持续循环。

- 支持 OpenAI 兼容 `chat/completions` 接口，以及 Anthropic Messages API 适配。
- 支持 SSE 流式文本、`reasoning_content` / `reasoning` 推理增量与分片 `tool_calls` 参数累积。
- **指令如律**：支持 `/run`、`/install`、`/init`、`/git` 等快捷指令，精简高频开发链路，让意图直达行动。
- **专精赋能**：支持挂载 `@Android 统一开发助手`、`@Android 逆向与代码审计`、`@全栈构建与排错` 等专精技能，从工程骨架生成、现代 Compose UI 编写到构建后一键安装至本机。
- **诸智共鸣**：支持主智能体派发并观测多个专业子智能体（Researcher, Coder, Tester），在隔离上下文中并发推进复杂工程。
- **多模态视觉感知**：支持超大手机照片（>6MB/20MB）原生自适应下采样与高保真压缩，直接将高清视觉流交付多模态模型，对话气泡内嵌图像预览。
- 内置 `read`、`write`、`edit`、`base` 等工作区与 Linux 执行工具。
- 支持 MCP STDIO 与 SSE 服务，能够发现工具定义、动态注入模型并执行调用。
- 会话、消息、工具执行、模型档案与推理内容通过 Room 持久化。
- 能从 Markdown 检查项提取任务计划，在对话中展示步骤与进度。

模型并不天然拥有行动能力。Harness 的意义，是把“我认为应该如此”变成“我已经执行，并看见结果如此”。

### 三、玄牝之门 · 原生 PTY 与多会话终端

太墟不是在文本框中模拟 Shell，而是维护真实 Linux 会话与进程生命周期。

- JNI `forkpty` 原生后端已接入，具备控制终端、窗口尺寸与信号语义。
- **变更洞察**：在对话中直接渲染文件变更的视觉化 Diff（红绿行对比），支持点击直达编辑器相关代码行。
- 原生后端不可用时自动回退至 Debian `script` PTY 路径。
- 支持 UTF-8 增量解码、ANSI/VT100 状态、Ctrl+C、动态 Resize 与滚动缓冲。
- 支持终端多会话的新建、切换、关闭与重命名。
- 会话元数据持久化；应用进程重启后可重建同名会话外壳。
- OAuth、设备授权等登录链接由用户主动确认后交给浏览器打开。

在终端里，每一个字符都是条件，每一次回车都是因果。抽象可以暂时退场，系统以它本来的方式说话。

### 四、工作区 · 为万物立极

工作区是 Agent、终端与文件系统共享的坐标原点。

- 创建、选择和管理项目工作区。
- **动态基准感知**：会话绑定工作区子目录时，Agent 的相对路径操作自动对齐项目根目录，杜绝路径错位。
- 浏览目录与文件，进行受边界约束的读取和写入。
- 将对话会话绑定到项目，让工具默认在正确上下文中行动。
- 在宽屏或折叠屏上启用 Agent 与终端双栏联动；手机上保持单栏路径。
- 通过 `/workspace`、`/attachments` 与可配置 `/sdcard` 映射连接沙箱和宿主文件。

世界并不因拥有许多文件而成为世界。只有当文件获得位置、任务获得上下文、行动能够留下痕迹，混沌才开始成为工程。

### 五、百工之器 · 工具中心与服务管理

太墟提供的不只是几个预装命令，而是一套可验证、可回滚的工具生命周期。

当前内置清单包括：

| 工具 | 形态 | 当前接入能力 |
| --- | --- | --- |
| Claude Code | PTY | 沙箱内安装、命令入口与交互会话 |
| Codex | PTY | 独立安装适配、验证与交互会话 |
| OpenClaw | Web Gateway | LAN Gateway、访问令牌、状态目录与后台进程管理 |
| Hermes Agent | Web Dashboard | Python 依赖、Dashboard 服务与后台进程管理 |
| Base DevTools | 一次性工具包 | ripgrep、fd、jq、tmux |
| Android DevTools | PTY / 工具包 | OpenJDK 17、Gradle 8.7+、ADB、AAPT、acli、zipalign |
| Android RE Tools | PTY / 工具包 | APKTool、JADX-CLI、Smali 逆向分析环境 |
| Hello Tool | 测试工具 | 验证安装、启动、校验与回滚链路 |

工具系统同时具备：

- **步骤化配方架构 (`installSteps`)**：配方全面拆解为清晰的命令列表，告别单行转义地狱，安装过程进度透明。
- **国内 CDN 镜像加速**：深度整合腾讯云 Gradle 镜像、国内 NPM 镜像与 GitHub 代理镜像，海外依赖秒级部署。
- **沙箱权限与环境自愈**：针对 PRoot 环境下的 dpkg 锁、unzip ownership 冲突及 Python 内存解压提供系统级自愈。
- 受版本约束的共享 Runtime 依赖解析与引用计数。
- `/opt/taixu/tools/{toolId}` 程序隔离、`/opt/taixu/data/{toolId}` 数据持久化与稳定命令入口。
- 安装、更新、验证、卸载、失败回滚和中断恢复事务。
- Web 工具后台启动、日志观察、停止、自动启动与安全访问令牌。
- APK 内置 Registry，以及 HTTPS + Ed25519 验签的远程 Registry 更新能力。
- 下载协议、响应大小、重定向目标、端点地址与日志秘密的安全检查。

器物扩展能力，也放大风险。因此一件工具只有在来源、权限、状态和失败路径都能被看见时，才真正属于使用者。

### 六、观天察地 · 仪表盘、设置与诊断

- 展示运行时状态、架构、内存、存储、进程和活跃任务。
- **显微观象**：智能体执行流、工具调用细节本地持久化日志，支持一键脱敏复制与清理。
- **Shell Runner**：在隔离的 Linux 环境中执行一次性 Shell 命令，辅助快速诊断。
- 按 RootFS、Runtime、工具程序、工具数据、工作区与缓存统计空间。
- 管理模型 Provider、API Key、MCP 服务、存储挂载、发行版和工具服务。
- 支持本地模型端点，如 llama.cpp 与 Ollama 的 OpenAI 兼容接口。
- 外部 Provider 强制 HTTPS；HTTP 仅允许精确回环地址。
- API Key、工具访问令牌与敏感配置通过 Android 侧安全组件保护，并包含旧明文配置迁移。
- Material 3 Expressive 界面、动态主题、触觉反馈与自适应布局。

观测不是装饰。不可见的系统只能被猜测，而能够被理解的系统，才有资格被托付任务。

---

## 架构

```text
┌──────────────────────── Android 应用沙盒 ────────────────────────┐
│                                                                  │
│  Compose UI                                                      │
│  首页 · Agent · 终端 · 工作区 · 工具中心 · 设置 · 开发者诊断      │
│                 │                           │                    │
│                 ▼                           ▼                    │
│  ┌────────────────────────┐    ┌─────────────────────────────┐  │
│  │ Agent Harness          │    │ Runtime / Tool Control      │  │
│  │ 流式推理 · 工具循环     │    │ 安装事务 · Registry · 服务   │  │
│  │ 子智能体 · MCP          │    │ 依赖解析 · 日志 · 回滚        │  │
│  └───────────┬────────────┘    └──────────────┬──────────────┘  │
│              │                                 │                 │
│              └──────────────┬──────────────────┘                 │
│                             ▼                                    │
│  ┌──────────────────── PRoot Linux Runtime ───────────────────┐  │
│  │ 多发行版 RootFS · Shell · JNI PTY · 进程 · /workspace       │  │
│  │ /root 与 /opt/taixu 持久化 · Android 存储受控映射            │  │
│  └─────────────────────────────────────────────────────────────┘  │
│                                                                  │
│  Room · DataStore · Android Keystore · OkHttp · 前台保活服务      │
└──────────────────────────────────────────────────────────────────┘
```

项目采用 Kotlin、Jetpack Compose、Hilt、Coroutines、Room 与多模块架构：

```text
app                 应用入口、Hilt 装配、JNI 与前台服务
core:model          纯 Kotlin 数据模型
core:common         日志、调度与通用能力
core:database       Room 会话、消息、工具与终端元数据
core:datastore      偏好、挂载、Registry、MCP 与秘密引用
core:network        OkHttp、SSE 与网络策略
core:security       本地敏感数据保护
runtime             PRoot、OCI RootFS、Shell、PTY、进程与工作区
tools               Registry、依赖管理、安装事务与工具适配器
harness             模型协议、Agent 循环、内置工具、MCP 与子智能体
feature             主题、首页、对话、终端、工作区、设置与导航
```

---

## 快速开始

### 运行要求

- Android 10（API 29）或更高版本
- ARM64（`arm64-v8a`）设备
- 首次初始化 RootFS 时需要网络连接与足够存储空间
- 使用持续后台任务时，建议允许通知与前台服务运行

### 首次创世

1. 安装并打开太墟。
2. 选择一个 Linux 发行版和镜像线路，等待 OCI RootFS 下载、合并与健康检查完成。
3. 在模型设置中配置 Provider、模型名称与 API Key；也可以填写本机 llama.cpp/Ollama 地址。
4. 创建工作区，并将 Agent 会话关联到该项目。
5. 打开终端验证环境：

```bash
uname -a
cat /etc/os-release
```

6. 向 Agent 描述目标，观察它读取上下文、调用工具、检查结果并继续推进。
7. 按需进入工具中心安装 Codex、OpenClaw、Hermes 或开发工具包。

> API Key、Gateway Token 与设备授权码都是秘密。不要把它们写入源码、提交记录、截图或公开日志。

---

## 从源码构建

### 开发环境

- Android Studio 与 JDK 17
- Android SDK 37
- Android NDK `30.0.15729638`
- CMake 3.22.1
- PowerShell（运行项目辅助脚本）

仓库不把 Linux RootFS 打进 APK；PRoot ARM64 loader 需要先准备：

```powershell
.\tools\prepare-proot-runtime.ps1
```

按照项目约定使用 Android Studio JBR 构建：

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

Debug APK 位于：

```text
app/build/outputs/apk/debug/app-debug.apk
```

可选的真机安装与启动：

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n top.wkbin.taixu/.MainActivity
```

---

## 当前边界

太墟已经能够运行，但尚未宣称抵达稳定。

- 当前仅正式面向 ARM64；其他 ABI 会在初始化阶段停止。
- 终端触摸滚动与部分中文输入法组合文本仍需完善。
- JNI PTY 已启用并带有回退路径，但复杂 TUI 仍需更多 ARM64 真机覆盖。
- Codex、OpenClaw 与 Hermes 的上游安装脚本和 CLI 会持续变化，发布前仍需逐版本锁定、审计与整机验收。
- 远程工具 Registry 的验证机制已经存在，但项目尚未预置正式签名 Registry 地址。
- RootFS 与工具更新具备回滚机制，但第三方软件可能写入未持久化的系统目录，复杂组合仍需迁移验证。
- UI 层自动化测试覆盖仍少于 Runtime、Harness 与数据层。
- PRoot 提供的是用户态兼容环境，不等同于虚拟机，也不承诺兼容所有内核能力、容器技术或系统服务。

这些不是需要藏起来的裂隙。边界被准确命名之后，工程才知道下一步应向何处生长。

更多实现细节见 [`docs/KNOWN_ISSUES.md`](docs/KNOWN_ISSUES.md) 与 [`docs/ROADMAP.md`](docs/ROADMAP.md)。

---

## 路线图

| 阶段 | 状态 | 所开辟之物 |
| --- | --- | --- |
| 鸿蒙 | ✅ 已完成 | PRoot、OCI RootFS、Shell、持久化目录与基础诊断 |
| 立极 | ✅ 已完成 | 工作区、Agent Harness、流式模型接入、工具调用与会话持久化 |
| 衍界 | ✅ 已完成 | 多发行版、JNI PTY、多终端、MCP、子智能体与工具事务 |
| 万象 | 🔄 进行中 | 真机兼容、上游工具验收、终端交互、测试覆盖与发布安全 |
| 归一 | 📋 规划中 | 可迁移环境、可信远程协作与更完整的跨设备连续性 |

路线图表达方向，而非承诺日期。太墟仍是早期世界，一切结构都应允许在证据面前重新生长。

---

## 哲学注脚

太墟不试图证明手机可以替代一切，也不假装有限的设备拥有无限资源。

它只是提出一种选择：当一个念头在通勤的地铁、候机的长椅或深夜的床头出现时，你不必等待所谓“合适的地方”，才能写下一段代码、运行一个脚本、检验一个猜想，或把一项任务推进到下一个确定的状态。

我们常把自由理解为没有边界。但工程所能提供的自由，往往恰恰来自清楚的边界：知道什么能够发生，什么不能发生；知道数据停留在哪里，命令将作用于何处，失败之后如何返回。

限制从未消失。自由也并不总来自限制的消失。

它也可以来自我们身处限制之中，仍有能力建立自己的秩序。

> 须弥纳于芥子，太墟纳于掌中。

---

## 参与

如果你也相信“在限制中开辟自由”，欢迎参与太墟的建设：

- 提交 Issue：指出深渊中的裂隙。
- 提交 Pull Request：为这方世界补上一条可验证的法则。
- 参与讨论：分享设备兼容结果、设计思考与新的造物。

提交代码前，请至少运行：

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

请勿提交 API Key、私有模型配置、本地环境文件、访问令牌或构建产物。
