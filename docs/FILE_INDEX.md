# 📚 太墟 (TaiXu) — 关键文件索引速查 (File Index)

> 用于 AI 编码助手快速定位某个功能/类。详细架构细节见 [`ARCHITECTURE.md`](ARCHITECTURE.md)。

---

## 🤖 Agent Harness（核心调度）

| 模块 | 关键文件 | 职责 |
| --- | --- | --- |
| `harness/HarnessLoop.kt` | 主循环，多会话并发 | Agent 主循环 |
| `harness/ToolExecutor.kt` | `read / write / edit / base / process / host / download / build_script / subagent` 等内置工具分派 |
| `harness/ApprovalPolicyEngine.kt` | 工具调用的审批策略（normal / high / critical 三档） |
| `harness/ToolRoundDispatcher.kt` | 单回合多工具并发调度（mutation 互斥 / read-only 4 并发） |
| `harness/SubagentOrchestrator.kt` | 子智能体 Lane 编排 |
| `harness/mcp/*` | MCP 协议：`McpManager` / `McpHttpTransport` / `McpJsonRpc` |
| `harness/browser/BrowserMcpBootstrap.kt` | 内置 Browser MCP Server 启动 + 注册引擎 |
| `harness/mcp/server/*` | in-process MCP Server：`McpServerRuntime` / Auth / Tool+Resource Dispatcher |
| `harness/HarnessMessage.kt` | `HarnessTool` 枚举 + `ToolResult` (含 `imageAttachments`) |

## 🌐 内置浏览器（Browser）

| 模块 | 关键文件 | 职责 |
| --- | --- | --- |
| `core/browser/...` | `BrowserFamily / Risk / Capability / SelectionPolicy / Preferences / FileOps` | Pure Kotlin 模型 + 策略 |
| `runtime/browser/BrowserRegistry.kt` | 浏览器注册中心 interface | 多家族管理 |
| `runtime/browser/BrowserRegistryImpl.kt` | 单一 in-app WebView 实现 | 注册 / 启动 / 选 family |
| `runtime/browser/BrowserEngine.kt` | 引擎操作 interface（24 个动作）| 抽象所有浏览器动作 |
| `runtime/browser/AndroidInAppBrowserEngine.kt` | in-app WebView 引擎实现 | 全部动作落地 |
| `runtime/browser/engine/WebViewTabPool.kt` | 多 tab 复用池 | 主线程 + StateFlow |
| `runtime/browser/snapshot/SnapshotBuilder.kt` | DOM 扫描脚本 + ref 注入 | PageSnapshot 生成 |
| `runtime/browser/screenshot/ScreenshotRecorder.kt` | `view.draw` 软渲截图落 PNG | ToolImageRef |
| `runtime/browser/network/NetworkInterceptor.kt` | `shouldInterceptRequest` 拦截 | CapturedRequest |
| `runtime/browser/storage/StorageController.kt` | Cookie + local/session 操作 | WebView eval |
| `runtime/browser/secret/SecretRedactingInterceptor.kt` | 接入现有 `SecretRedactor` | 工具产物脱敏 |
| `runtime/browser/tools/BrowserMcpTools.kt` | `mcp__browser__*` 工具分派 + 风险等级 | 36+ tools |
| `runtime/browser/tools/BrowserMcpResources.kt` | `browser://*` resources | 6 resources |
| `feature/browser/BrowserScreen.kt` | 内置浏览器 Compose 主屏 | UI 入口 |
| `feature/browser/BrowserViewModel.kt` | 持有 Registry + EventBus + Snapshot State | 状态 |
| `feature/browser/BrowserActionCard.kt` | 给 Chat 复用的产物卡（缩略图） | 跨模块复用 |
| `feature/browser/BrowserNavRoute.kt` | BrowserRoute 常量与跳转助手 | 路由入口 |
| `docs/BROWSER_DESIGN.md` | 内置浏览器设计文档 | 决策 + ADR |

## 🖥️ Linux 运行时（PRoot）

| 模块 | 关键文件 | 职责 |
| --- | --- | --- |
| `runtime/.../LinuxRuntime.kt` | PRoot 启动入口 / `base` 命令面板 | 命令执行边界 |
| `runtime/.../ProcessRegistry.kt` | `process` 命令的 PID / 日志环形缓冲 | 后台进程托管 |
| `runtime/.../ProotCommandBuilder.kt` | `-b` 挂载点规范化 + Shell 注入防护 | 安全 |
| `runtime/.../WorkspaceFileService.kt` | 工作区读/写/搜/hash/zip/share | 与 file.* 工具对齐 |
| `runtime/.../shell/VT100.kt` | 终端 VT100 状态机 | 终端渲染 |

## 🤝 Web Reverse MCP 参考

项目内置浏览器/MCP 设计借鉴自 `mnjh666/WebReverse-MCP`（模块切分 / 工具动词集 / 风险矩阵），不复用其代码。
