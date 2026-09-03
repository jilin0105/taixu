<p align="center">
  <img src="app/src/main/res/drawable/taixu_logo.webp" width="96" alt="TaiXu Logo" />
</p>

<h1 align="center">TaiXu · 太墟</h1>

<p align="center"><strong>The Myriad Manifestations in the Great Void.</strong></p>

<p align="center">
  Android No-Root Linux Runtime · Native Agent Harness · PTY Terminal · Workspace & Tool Ecosystem
</p>

<p align="center">
  <a href="README.md">简体中文</a> · <strong>English</strong>
</p>

---

## Prologue: What is TaiXu

In *Liezi: Questions of Tang*, it is written:

> To the east of the Bohai Sea... there is a vast ravine, indeed a bottomless valley. Its depths are unfathomable, and it is called GuiXu (The Return to the Void). The waters of the eight horizons and nine heavens, the flow of the celestial river, all pour into it, yet it neither increases nor diminishes.

**GuiXu** is the place where all waters converge. It appears empty, yet it is not void; old boundaries dissolve here, and new orders find the possibility of emergence.

**TaiXu (太墟)**, the Great Void, is exactly such a space within the palm of your hand.

It is not just a chat application, nor merely a UI wrapper for a terminal. TaiXu attempts to build a runnable, observable, and continuously evolving Linux world within the restricted sandbox of Android: here, models gain language, tools gain hands, terminals preserve causality, and workspaces grant memory a location.

Upon the first launch, nothing has unfolded. There is no RootFS, no processes, no projects, and no questions waiting to be answered. When the first Linux system is initialized, the first workspace established, and the first task handed to the Agent, a tiny computational order begins to grow from the void.

> Establish the pole in the Great Void; create the world within an inch.

---

## Current Status

TaiXu is currently in version **0.3.0**, targeting **Android 10+ and ARM64** devices. It is in a stage of rapid evolution. It has formed a complete main chain from Linux runtime, model integration, and Agent tool loops to terminal and workspace management, though it still requires more validation on real devices with complex TUI and third-party tool combinations.

```text
Human Intent
   ↓
Model Inference ─→ Tool Selection ─→ Linux / MCP Execution ─→ Result Verification
   ↑                                                            ↓
   └─────────────────── Continue if unfinished ─────────────────┘
```

This loop is the core of TaiXu: Language is not the end, but the first layer of structure before action takes form.

---

## Capabilities

### I. Evolution of Worlds · No-Root Multi-Distro Linux

TaiXu uses **PRoot** as its boundary to run a full Linux user space without requiring Root privileges or modifying Android system partitions.

- Supports Ubuntu 24.04, Debian 12, Kali Rolling, Arch Linux, Fedora 40, Alpine 3.19, AlmaLinux 9, and openSUSE Tumbleweed.
- Supports installation, switching, space statistics, and lifecycle management of multiple distributions.
- RootFS is fetched via OCI Registry, supporting ARM64 manifests, SHA-256 layer verification, gzip/zstd decompression, and OCI whiteout merging.
- RootFS updates utilize staging, health checks, and two-phase commits; recoverable from failed activations or interruptions.
- Independent persistence bindings for `/root`, `/opt/taixu`, workspaces, and temp directories; system updates do not wipe user tools and configs.
- Supports mounting of Downloads, Documents, shared storage, and constrained custom host directories.
- Provides environment configurations and self-healing paths for common PRoot issues like dpkg hardlinks, apt locks, and Git virtual UIDs.

Boundaries have not vanished. What TaiXu does is acknowledge the boundary, then establish a sufficiently complete set of laws within it.

### II. The Harness · Moving from Answers to Completion

The built-in Harness connects dialogue, inference, tool calls, execution results, and next-round judgments into a continuous cycle.

- Supports OpenAI-compatible `chat/completions` and Anthropic Messages API.
- Supports SSE streaming, `reasoning_content` / `reasoning` increments, and chunked `tool_calls` parameter accumulation.
- **Commands as Law**: Supports `/run`, `/install`, `/init`, `/git`, etc., to streamline high-frequency dev workflows, making intent lead directly to action.
- **Subagent Synergy**: Supports the main Agent dispatching and observing multiple specialized sub-agents (Researcher, Coder, Tester) to advance complex engineering tasks in parallel across isolated contexts.
- Built-in `read`, `write`, `edit`, and `base` tools for workspace and Linux execution.
- Supports MCP STDIO and SSE services to discover tool definitions, dynamically inject them into models, and execute calls.
- Persistence of sessions, messages, tool executions, model profiles, and inference content via Room.
- Supports image visual input and file attachments; files are mirrored into the Linux sandbox.
- Extracts task plans from Markdown checklists to display steps and progress in the dialogue.

Models do not naturally possess the ability to act. The significance of the Harness is to turn "I think it should be so" into "I have executed, and I see the result is so."

### III. The Primal Gate · Native PTY & Multi-Session Terminal

TaiXu does not simulate a shell in a text box; it maintains real Linux sessions and process lifecycles.

- JNI `forkpty` native backend is integrated, providing control terminal, window sizing, and signal semantics.
- **Visual Insight**: Directly renders visual Diffs (red/green line comparisons) for file changes within the dialogue, supporting "one-click jump" to the specific line in the editor.
- Automatically falls back to the Debian `script` PTY path if the native backend is unavailable.
- Supports UTF-8 incremental decoding, ANSI/VT100 states, Ctrl+C, dynamic resizing, and scroll buffering.
- Supports creating, switching, closing, and renaming multiple terminal sessions.
- Session metadata is persisted; shell sessions can be reconstructed after app restarts.
- Login links (OAuth, Device Auth) are confirmed by the user and opened in the host browser.

In the terminal, every character is a condition, and every Enter key is a cause. Abstraction can temporarily exit; the system speaks in its own way.

### IV. Workspaces · Establishing the Pole for All Things

The workspace is the coordinate origin shared by the Agent, terminal, and file system.

- Create, select, and manage project workspaces.
- Browse directories and files, performing reads and writes constrained by the sandbox boundary.
- Bind dialogue sessions to projects, ensuring tools act in the correct context by default.
- Enables Agent and terminal dual-pane linkage on wide screens or foldables; maintains single-pane paths on phones.
- Connects the sandbox and host files via `/workspace`, `/attachments`, and configurable `/sdcard` mappings.

A world does not become a world simply by possessing many files. Only when files gain a position, tasks gain context, and actions can leave a trace, does chaos begin to become engineering.

### V. The Myriad Tools · Tool Center & Service Management

TaiXu provides more than just pre-installed commands; it offers a verifiable, rollable tool lifecycle.

Current built-in list:

| Tool | Form | Capabilities |
| --- | --- | --- |
| Claude Code | PTY | In-sandbox installation, command entry, and interactive sessions |
| Codex | PTY | Independent installation, verification, and interactive sessions |
| OpenClaw | Web Gateway | LAN Gateway, access tokens, status directories, and background process management |
| Hermes Agent | Web Dashboard | Python dependencies, Dashboard service, and background process management |
| Base DevTools | Toolset | ripgrep, fd, jq, tmux |
| Android DevTools | PTY / Toolset | ADB, OpenJDK 17, Gradle, AAPT, zipalign |
| Android RE Tools | PTY / Toolset | APKTool, JADX-CLI, Smali reverse engineering environment |
| Hello Tool | Test Tool | Verifies installation, startup, validation, and rollback chains |

The tool system also features:

- Dependency resolution and reference counting for shared Runtimes constrained by versions.
- Program isolation at `/opt/taixu/tools/{toolId}` and data persistence at `/opt/taixu/data/{toolId}`.
- Transactions for installation, update, verification, uninstallation, failure rollback, and interruption recovery.
- Background startup, log observation, and auto-start for Web tools with secure access tokens.
- APK-built-in Registry and remote Registry updates via HTTPS + Ed25519 signature verification.
- Security checks for download protocols, response sizes, redirect targets, and log secrets.

Tools extend capability but also amplify risk. A tool truly belongs to the user only when its source, permissions, state, and failure paths are visible.

### VI. Observing Heaven & Earth · Dashboard, Settings & Diagnostics

- Displays runtime status, architecture, memory, storage, processes, and active tasks.
- **Microscopic Vision**: Persistent local logs for Agent execution flows and tool call details, supporting one-click sanitized copying and clearing.
- **Shell Runner**: Executes one-shot Shell commands in an isolated Linux environment for quick diagnostics.
- Statistics for space usage by RootFS, Runtime, tools, workspaces, and caches.
- Manage Model Providers, API Keys, MCP services, storage mounts, distros, and tool services.
- Supports local model endpoints like llama.cpp and Ollama's OpenAI-compatible APIs.
- External Providers enforce HTTPS; HTTP is allowed only for exact loopback addresses.
- API Keys and tokens are protected via Android's secure components.
- Material 3 Expressive UI, dynamic themes, haptic feedback, and adaptive layouts.

Observation is not decoration. An invisible system can only be guessed at, but a system that can be understood is worthy of being entrusted with tasks.

---

## Architecture

TaiXu uses Kotlin, Jetpack Compose, Hilt, Coroutines, Room, and a multi-module architecture.

```text
app                 Entry point, Hilt assembly, JNI, and foreground services
core:model          Pure Kotlin data models
core:common         Logging, scheduling, and general utilities
core:database       Room for sessions, messages, tools, and terminal metadata
core:datastore      Preferences, mounts, Registry, MCP, and secret references
core:network        OkHttp, SSE parser, and network policies
core:security       Local sensitive data protection
runtime             PRoot, OCI RootFS, Shell, PTY, processes, and workspaces
tools               Registry, dependency management, transactions, and tool adapters
harness             Model protocols, Agent loop, built-in tools, MCP, and sub-agents
feature             Themes, Home, Chat, Terminal, Workspace, Settings, and Navigation
```

---

## Current Boundaries

TaiXu is functional but has not yet claimed stability.

- Currently officially supports ARM64 only.
- Terminal touch scrolling and some IME combinations still need refinement.
- Complex TUIs require more coverage on diverse ARM64 real devices.
- OCI RootFS and tool updates have rollback mechanisms, but third-party software may write to non-persistent system directories.
- PRoot provides a user-space compatibility environment; it is not a VM and does not guarantee compatibility with all kernel capabilities or container technologies.

These are not cracks to be hidden. Once boundaries are accurately named, engineering knows where to grow next.

See [`docs/KNOWN_ISSUES.md`](docs/KNOWN_ISSUES.md) and [`docs/ROADMAP.md`](docs/ROADMAP.md) for more details.

---

## Roadmap

| Phase | Status | What has been opened |
| --- | --- | --- |
| Primal Chaos | ✅ Done | PRoot, OCI RootFS, Shell, persistent directories, and basic diagnostics |
| Establishing Pole | ✅ Done | Workspaces, Agent Harness, streaming models, tool calls, and persistence |
| Evolving Realms | ✅ Done | Multi-distro, JNI PTY, multi-terminal, MCP, sub-agents, and tool transactions |
| Myriad Things | 🔄 In Progress | Real-device compatibility, upstream tool validation, terminal interaction, and safety |
| Unity | 📋 Planned | Migratable environments, trusted remote collaboration, and cross-device continuity |

Roadmaps express direction, not promised dates. TaiXu is still an early world; every structure should be allowed to re-grow in the face of evidence.

---

## Philosophical Footnote

TaiXu does not try to prove that a phone can replace everything, nor does it pretend that limited devices have infinite resources.

It simply proposes a choice: when a thought appears on a commuting subway, a waiting bench, or a late-night bedside, you don't have to wait for a so-called "proper place" to write a piece of code, run a script, test a hypothesis, or push a task to the next certain state.

We often understand freedom as the absence of boundaries. But the freedom that engineering can provide often comes precisely from clear boundaries: knowing what can happen and what cannot; knowing where data stays and where commands will act; knowing how to return after failure.

Constraints never disappear. Freedom does not always come from the disappearance of constraints.

It can also come from our ability to establish our own order while being within those constraints.

> Mount Sumeru is contained within a mustard seed; the Great Void is contained within the palm.

---

## Participate

If you also believe in "creating freedom within constraints," welcome to contribute to TaiXu:

- Submit an Issue: Point out the cracks in the abyss.
- Submit a Pull Request: Add a verifiable law to this world.
- Join the Discussion: Share device compatibility results, design thoughts, and new creations.

Please do not submit API Keys, private model configs, local environment files, access tokens, or build artifacts.
