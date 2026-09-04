# 借鉴 DeepSeek-Reasonix：太墟 Harness 四阶段改进计划

## Context

调研开源 coding-agent [DeepSeek-Reasonix](https://github.com/esengine/DeepSeek-Reasonix)（Go，6200+ commits）后，提炼出四条对太墟 harness 有实际价值的工程实践。用户确认全部采纳，**分期交付**：每阶段独立可验收，任一段后可喊停。

太墟 harness 现状盘点（本次规划已核实）：

| Reasonix 借鉴点 | 太墟是否有 | 差距 |
| --- | --- | --- |
| Checkpoints & Rewind（每轮文件快照/撤销） | **无** | `AgentStateMachine.checkpoint` 只是任务状态持久化点，非文件快照，无 undo |
| 记忆冲突/新鲜度/pinned-relevant | 部分 | 已有 `AgentMemoryEntity`(scope/kind/key/value)+Room+注入 prompt，无冲突去重、无 revision、无 freshness、无 pinned/relevant 分层 |
| 子智能体上下文隔离精化 | 部分 | 已隔离（回父级只传 summary），无 write_paths 文件租约、无父级 facts pack |
| use_capability（schema 稳定性） | 无 | 收敛到"聚焦动态端口"：浏览器内置 MCP 端口顺延绑定影响 provider 可见 schema |

四条均从简单、防回归的增量入手，复用现有抽象（WorkspaceFileAccess 原子写、WorkspaceFileAccess.resolve 路径校验、Room DAO、LaneManager lane 隔离、ToolRoundDispatcher 互斥）。

---

## 阶段一：Checkpoints & Rewind（每轮文件快照安全网）

**目标**：让用户能把会话撤回到之前的用户轮次，恢复代码/对话/两者，不碰 git。

**关键文件**：
- 新增 `harness/checkpoint/CheckpointStore.kt`（快照存储）
- 新增 `harness/checkpoint/RewindController.kt`（prepare/commit 恢复 API）
- 改 `harness/ToolExecutor.kt`（捕获缝：WRITE/EDIT 执行前快照）
- 改 `harness/TurnRunner.kt` / `HarnessLoop.kt`（每用户轮次 open checkpoint）

**实现要点**：
1. **数据模型**：每轮一个 `Checkpoint(turn, time, prompt, files)`；每个 `FileSnap(path, content: String?)`，`content=null` 表示当时文件不存在（恢复即删除）。
2. **捕获缝（单一集中点）**：借鉴 Reasonix 的 `Previewer` 思路——在 `ToolExecutor.executeTool` 的 `WRITE`/`EDIT` 分支，调用 `WorkspaceFileAccess` 前先解析并读取该文件**改前内容**快照进本轮 checkpoint。同轮同路径去重（只保留轮初内容）。`BASE`/`PROCESS`/`HOST`/`bash` 副作用不追踪（无法知道命令动了什么）。
3. **存储**：会话旁路目录 `<session-dir>/.ckpt/turns/<turn>/xxx.before`，与消息 JSONL（`SessionTreeStore`）分离；断点续传时重载。保留最近 100 轮、单文件 ≤32 MiB、软 1GiB 预算。
4. **恢复 API**（只此一套，供 UI/未来 MCP 调用）：
   - `RewindScope = Code | Conversation | Both`
   - `Checkpoints()`, `PrepareRewind(turn, scope) → RewindPlan`, `CommitRewind(planId) → RewindResult`。
   - Code：从 `turn` 到最新逐路径取最早 `FileSnap` 恢复（`null` 即删），路径重校验工作区边界（复用 `WorkspaceFileAccess.resolve`）。
   - Conversation：`SessionTreeStore` fork 新会话于该 turn 边界，父 transcript 不截断。

**验证**：
- 单元测试：`CheckpointStoreTest`（快照/去重/保留/大小上限）、`RewindControllerTest`（code/delete/both/fork、路径逃逸拒绝）。
- 集成：写→改→删一轮后 revert 到首轮，断言文件内容恢复、不存在的文件被删除。

---

## 阶段二：记忆冲突 / 新鲜度 / pinned-relevant 模型

**目标**：让 `AgentMemoryEntity` 具备 Reasonix "Context Engine v2" 的关键语义：同主题冲突去重、不可变修订审计、新鲜度分级、pinned/recall 正交分层。

**关键文件**：
- 改 `core/database/AgentContextEntities.kt`（`AgentMemoryEntity` 加字段）
- 改 `core/database/AgentContextDao.kt`（冲突去重/版本/检索）
- 改 `harness/AgentContextExecutor.kt`（save/query 逻辑承接新语义）
- 改 `harness/prompt/SystemPromptBuilder.kt`（pinned 进稳定前缀、recall 检索注入）

**实现要点**：
1. **字段扩展**：加 `subjectKey`（dotted key，如 `project.package_manager`）、`revision: Int`、`pinned: Boolean`（默认 false）、`expiresAt: Long?`、`lastVerifiedAt: Long?`、`volatility`（reference/project/user）。迁移：给存量记忆打 `legacy-`id 与 revision=1（幂等，避免破坏现有 DB）。
2. **冲突模型（subject_key）**：同 `(scope, ownerId, subjectKey)` 只有一个 active 修订。DAO `saveMemory` 新增"同主题已存在"检查——命中则拒绝并返回 holder id（模型可改为"更新同主题"），使 `npm→pnpm` 成为 revision 而非两条矛盾记忆。`value` 变更时 `revision+1` 存不可变新行。
3. **pinned vs relevant 正交**：
   - pinned 条目（总字符上限 ~1500）在 `SystemPromptBuilder` 稳定前缀注入（`## 长期指令记忆`）。
   - 非 pinned 条目走检索：每次用户轮前由 `AgentContextExecutor` 用原始消息查询，注入为用户轮次尾部低权威摘要（不污染 system prompt）。
4. **新鲜度分级**：reference/project/user 各有默认新鲜窗口 + `expiresAt` 硬边界（过期不自动召回但仍可显式查） + `lastVerifiedAt` 续期。陈旧是降权信号，不删除。

**验证**：
- `AgentMemoryDaoTest`：同主题保存被拒、升级为 revision、pinned/relevant 互斥、过期不召回。
- `SystemPromptBuilderTest` 映射：pinned 条目出现在稳定前缀，recall 摘要出现在用户轮尾。
- 迁移：旧库升级不丢数据、幂等。

---

## 阶段三：子智能体上下文精化（write_paths 租约 + 父级 facts pack）

**目标**：给已隔离的子智能体补上 Reasonix 的并行写文件协调与父级事实浓缩，降低多写者互覆风险、提升子任务质量。

**关键文件**：
- 改 `harness/SubagentArgsParser.kt`（`invoke_subagent` 解析 `write_paths`）
- 改 `harness/SubagentOrchestrator.kt`（文件级租约调度、facts pack 组装）
- 改 `harness/SubagentLaneRunner.kt` 或其配置传入（父级 facts pack 注入子 system prompt）
- 复用 `harness/ToolRoundDispatcher.kt` 的 mutation 互斥模型

**实现要点**：
1. **write_paths 文件级租约**：子任务声明写目标；声明不相交的写者可并行启动，撞同一文件名才串行（借鉴现有 "全局 mutation 互斥" 收敛到"文件租约"）。缺省视为整工作区租约（串行启动）。
2. **父级 facts pack**：把父会话已确认决策、证据摘要、关键文件锚点压缩成一段 `## 父级上下文事实包` 注入子 lane 开头，而非整段父 transcript。
3. **结果分页读取（可选增强）**：多子智能体的最终答案若过大，提供 `read_subagent_result` 按偏移分页读取，避免全部注入父上下文。若当前 `buildSummaryMarkdown` 已足以控制输出量则保持现状，仅在超限时走分页。

**验证**：
- `SubagentOrchestratorTest`：声明 disjoint write_paths 并行、同文件串行、缺省整区串行。
- facts pack 注入断言：子 lane 首条 assistant/user 消息含 `## 父级上下文事实包`，但不含父 transcript 全文。

---

## 阶段四：浏览器 MCP 动态端口 → provider schema 收敛

**目标**：聚焦 `McpHttpTransport.effectiveUrlOf` 的动态端口顺延问题，确保 provider 可见工具 schema 在会话期间稳定（不破坏 prompt cache、不引发工具描述 churn）。不做全量 `use_capability` 代理重构。

**关键文件**：
- `harness/mcp/McpHttpTransport.kt`（`effectiveUrlOf`，已有动态端口逻辑）
- `harness/mcp/server/BuiltinBrowserMcpAccess` / 发现装配处（provider schema 注入点）
- 新增快照测试锁定 provider 可见 schema 稳定性

**实现要点**：
1. **schema 口径稳定**：内置 browser server 在 provider 可见工具描述/示例等文案里**不拼接运行时端口**，改用稳定占位（如 `http://localhost:<port>` 的占位形式或去掉端口示例），真实端口仅在 `tools/call` 时经 `effectiveUrlOf` 运行时解析。
2. **发现与调用分离**：`McpManager`/`McpToolDispatcher` 发现工具时给出稳定 schema；绑定端口变化不再改 provider 可见表面。
3. **快照测试**：新增测试断言工具 schema 在"端口 A/端口 B/未绑定"三种状态下保持一致。

**验证**：`McpHttpTransportTest` / 新增 schema 稳定性快照测试：构造不同 runtime port，断言 provider 可见工具描述字节级一致；`tools/call` 仍正确路由到实际端口（`effectiveUrlOf` 单测已覆盖，补一条回归）。

---

## 总体验证

- 每阶段跑对应 `harness`/`core:database` 单元测试：`./gradlew :harness:testDebugUnitTest :core:database:testDebugUnitTest`（以 docs/COMMANDS.md 为准）。
- 涉及 DB 变更（阶段二）需补迁移测试；涉及 provider schema（阶段四）需快照测试。
- 不引入第三方依赖；全程复用现有 `WorkspaceFileAccess`、Room DAO、`LaneManager`、`ToolRoundDispatcher`，避免过度工程。

## 交付顺序

阶段一 → 阶段二 → 阶段三 → 阶段四，逐段推进并 review；用户可在任一段后要求暂停或调整。