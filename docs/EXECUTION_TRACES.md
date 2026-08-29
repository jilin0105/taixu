# ⚡ 太墟 (TaiXu) — 核心调用链路与执行时序 (Execution Traces)

---

## 1. Agent 发起与工具执行循环 (Harness Loop)

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

---

## 2. 宿主与沙箱存储挂载 (Storage Mount)

```text
StorageMountSettingsScreen (UI)
  └─► SettingsDataStore (保存 mountDownloadEnabled / customMountBindings)
        └─► ProotCommandBuilder.build()
              └─► 动态追加 "-b /storage/emulated/0/Download:/sdcard/Download"
                    └─► PRoot 进程启动，沙箱内可直接访问 /sdcard
```

---

## 3. 终端交互与原生 PTY (Matrix Terminal)

```text
TerminalScreen (UI)
  └─► TerminalViewModel
        └─► TerminalPtyManager.createPty()
              └─► JNI pty.c (openpty / fork / execve proot)
                    ├─► PtyInputStream ➔ TerminalViewModel.screen (VT100 状态机解析)
                    └─► PtyOutputStream ◄─ TerminalScreen 键盘与辅助按键输入 (ExtraKeys)
```

---

## 4. 任务拆解与进度卡片 (Task Plan Checkpoints)

```text
Model Output (包含 - [ ] / - [x] 格式文本)
  └─► ChatScreen.kt -> extractTaskPlanSteps(message.text)
        └─► 当步骤数 >= 2 时 ➔ 渲染 TaskPlanCard
              ├─► 动态计算进度百分比与完成度徽章 (LinearProgressIndicator)
              ├─► 提供触觉反馈与平滑展开/折叠动效
```

---

## 5. 宽屏与折叠屏双栏联动 (Dual-Pane Layout)

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

## 6. Agent 前台命令与托管进程 (Command Lifecycle)

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

> **进程生命周期特别说明**：
> PRoot 启动参数包含 `--kill-on-exit`。普通 `base` 中的 `nohup`、`setsid`、`&` 或自行 daemonize 不能保证跨 PRoot 会话存活；必须使用 `process` 托管，并让被托管命令保持前台运行。Android 强制停止、系统回收应用进程或设备重启不属于进程托管保证范围。

---

## 7. 工作区导入、导出与构建 (Workspace Lifecycle)

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

---

## 8. ARM64 Android 离线套件安装与构建 (Sandbox Android Toolchain)

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
