# -*- coding: utf-8 -*-
import io
f = r'C:\Users\wangk\Desktop\LinuxAIRuntime\docs\BROWSER_HARNESS_REVIEW.html'
t = io.open(f, encoding='utf-8').read().replace('\r\n', '\n')

def apply(old, new, label):
    global t
    if old in t:
        t = t.replace(old, new, 1)
        print("OK:", label)
    else:
        print("NOT FOUND:", label)

# 1. 链路图节点④：修正 runBlocking 表述
apply(
    '<div class="node"><b>④ McpServerRuntime · POST /mcp</b><span>runBlocking 包整个 JSON-RPC（阻塞 CIO 线程）<span class="badge">#12</span></span></div>',
    '<div class="node"><b>④ McpServerRuntime · POST /mcp</b><span>suspend handler 直接 await（无 runBlocking）</span></div>',
    "chain node 4")

# 2. #16 明细修正
apply(
    '<li><b>#16</b> McpServerRuntime：<code>runBlocking</code> 包整个 JSON-RPC（CIO 线程被阻塞，并发受限）；<code>notifications/initialized</code> 返回 method-not-found（客户端忽略，但会留脏日志）；bootstrap 幂等不校验"运行中的 server 是不是浏览器 server"。</li>',
    '<li><b>#16</b> McpServerRuntime：<s>runBlocking 包整个 JSON-RPC（CIO 线程被阻塞，并发受限）</s> —— <i>该条不成立：POST /mcp handler 为 suspend 直接 await，未使用 runBlocking（复核确认，本条降为“小瑕疵”档）</i>；<code>notifications/initialized</code> 返回 method-not-found（客户端忽略，但会留脏日志）；bootstrap 幂等不校验"运行中的 server 是不是浏览器 server"。</li>',
    "#16 fix")

# 3. 在 foot 前插入修复记录
foot_marker = '  <div class="foot">'
fix_section = '''  <h2>六、修复记录（2026-09-02）</h2>
  <div class="card">
    <table>
      <tr><th>问题</th><th>状态</th><th>改动要点</th></tr>
      <tr><td>#1 snapshot 竞态（P0）</td><td style="color:var(--green);">✔ 已修</td><td>SnapshotBuilder.refresh() 同步 publish 并直接返回 PageSnapshot；engine.snapshot() 不再读全局 eventBus 值</td></tr>
      <tr><td>#3 脱敏未接线（P0）</td><td style="color:var(--green);">✔ 已修</td><td>McpToolDispatcher.dispatch() 对所有工具输出统一过 SecretRedactingInterceptor.apply()（含 headers 字典重载）</td></tr>
      <tr><td>#2 审批矩阵未落地（P1）</td><td style="color:var(--green);">✔ 已修</td><td>ApprovalPolicyEngine.decide() 新增 rawToolName 参数，按工具名映射 LOW/MEDIUM/HIGH/CRITICAL：LOW 免审、HIGH/CRITICAL 按档提示，ToolExecutor 已传 rawToolName</td></tr>
      <tr><td>#5 多 tab 串扰（P1）</td><td style="color:var(--green);">✔ 已修</td><td>BrowserEventBus 按 tabId 分片（tabUrls/tabTitles/tabSnapshots），全局 StateFlow 只反映活跃 tab；新增 urlOf/titleOf/snapshotOf；storage 工具按 tokenOf 取 tab</td></tr>
      <tr><td>#6 click/type/press（P1）</td><td style="color:var(--green);">✔ 已修</td><td>去掉嵌套 runBlocking（全 suspend）；ref→selector 直接映射 data-taixu-ref，不再每次全量重扫 DOM</td></tr>
      <tr><td>#7 refMap 线程安全</td><td style="color:var(--green);">✔ 已修</td><td>ResolverRegistry 改用 ConcurrentHashMap&lt;String, ConcurrentHashMap&gt;</td></tr>
      <tr><td>#8 Unicode 反转义</td><td style="color:var(--green);">✔ 已修</td><td>unwrap() 改用 Json.parseToJsonElement 解码（带旧逻辑 fallback）</td></tr>
      <tr><td>#9 离屏截图空白</td><td style="color:var(--green);">✔ 已修</td><td>ScreenshotRecorder 对未 layout 的 view 先 measure+layout 再 draw</td></tr>
      <tr><td>#10 console_clear</td><td style="color:var(--green);">✔ 已修</td><td>调用 engine.eventBus.clearConsole()</td></tr>
      <tr><td>#11 domFingerprint 随机</td><td style="color:var(--green);">✔ 已修</td><td>改为对 refs 内容做 SHA-256 稳定指纹</td></tr>
      <tr><td>#14 prefs 写死（低危）</td><td style="color:var(--orange);">△ 部分</td><td>本轮保留 DEFAULT 快照并加 TODO 注释，未引入 Hilt 构造期 DataStore 阻塞；留待后续动态注入</td></tr>
      <tr><td>#16 runBlocking（原低危）</td><td style="color:var(--green);">✔ 复核</td><td>确认 McpServerRuntime 无 runBlocking，该子项不成立；notifications/initialized 脏日志、bootstrap 幂等未校验两点保留为小瑕疵</td></tr>
      <tr><td>#4/#12/#13/#15（未列入修复）</td><td style="color:var(--orange);">△ 未动</td><td>外接能力死代码、超时后副作用、cleartext 配置等未在本次范围内，可后续跟进</td></tr>
    </table>
    <p style="color:var(--secondary);font-size:12px;">验证：<code>gradlew :harness:compileDebugKotlin :core:browser:test</code> BUILD SUCCESSFUL（120 tasks，含 runtime/browser 与 core/datastore）。修复 commit 随附。</p>
  </div>

'''
if foot_marker in t:
    t = t.replace(foot_marker, fix_section + foot_marker, 1)
    print("OK: fix record section")
else:
    print("NOT FOUND: foot marker")

io.open(f, 'w', encoding='utf-8', newline='\n').write(t)
print("DONE")
