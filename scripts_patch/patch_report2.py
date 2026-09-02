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

# 把"未列入修复"行替换为更新后的修复记录
apply(
    '<tr><td>#4/#12/#13/#15（未列入修复）</td><td style="color:var(--orange);">△ 未动</td><td>外接能力死代码、超时后副作用、cleartext 配置等未在本次范围内，可后续跟进</td></tr>',
    '''<tr><td>#4/#15 外接能力（低危）</td><td style="color:var(--green);">✔ 已修</td><td>bootstrap 按 allowRemoteConnect 偏好决定 0.0.0.0 绑定并生成 Bearer Token（McpAuthFilter 强制校验）；desktopUserAgent 偏好接入引擎；删除 builtInConfig() 死代码</td></tr>
      <tr><td>#12 超时后副作用（低危）</td><td style="color:var(--green);">✔ 已修</td><td>postMain/JsEvaluator 超时语义注释 + 日志：明确副作用不可取消、结果已丢弃，提示勿重试同参数操作</td></tr>
      <tr><td>#13 cleartext（低危）</td><td style="color:var(--green);">✔ 复核</td><td>全局 networkSecurityConfig cleartextTrafficPermitted=true 已允许，http:// 页面可加载，无需改码</td></tr>
      <tr><td>#14 prefs 写死（低危）</td><td style="color:var(--green);">✔ 已修</td><td>McpServerModule 注入 datastore.BrowserPreferences，启动时读真实偏好映射为工具层快照（失败兜底 DEFAULT）</td></tr>''',
    "fix record update")

# 更新验证说明（追加 app 编译）
apply(
    '<p style="color:var(--secondary);font-size:12px;">验证：<code>gradlew :harness:compileDebugKotlin :core:browser:test</code> BUILD SUCCESSFUL（120 tasks，含 runtime/browser 与 core/datastore）。修复 commit 随附。</p>',
    '<p style="color:var(--secondary);font-size:12px;">验证：<code>gradlew :harness:compileDebugKotlin :core:browser:test</code> 与 <code>:app:compileDebugKotlin</code> BUILD SUCCESSFUL（含 Hilt 装配图）。两轮修复 commit 随附。</p>',
    "verify note")

io.open(f, 'w', encoding='utf-8', newline='\n').write(t)
print("DONE")
