# -*- coding: utf-8 -*-
import io
f = r'C:\Users\wangk\Desktop\LinuxAIRuntime\harness\src\main\java\top\wkbin\taixu\harness\ApprovalPolicyEngine.kt'
t = io.open(f, encoding='utf-8').read().replace('\r\n', '\n')

def apply(old, new, label):
    global t
    if old in t:
        t = t.replace(old, new, 1)
        print("OK:", label)
    else:
        print("NOT FOUND:", label)

# 1. FULL_ACCESS 后插入 MCP-LOW 免审
apply("""        if (mode == ApprovalMode.FULL_ACCESS) return ApprovalDecision(false)
        if (tool == HarnessTool.READ || tool == HarnessTool.MEMORY || tool == HarnessTool.PLAN ||""",
"""        if (mode == ApprovalMode.FULL_ACCESS) return ApprovalDecision(false)
        // 内置浏览器 MCP 工具按风险矩阵细化审批：只读（LOW）工具在任何模式下免审
        if (tool == HarnessTool.MCP && mcpBrowserRisk(rawToolName) == "low") {
            return ApprovalDecision(false)
        }
        if (tool == HarnessTool.READ || tool == HarnessTool.MEMORY || tool == HarnessTool.PLAN ||""",
"insert MCP-LOW bypass")

# 2. MCP 分支按风险
apply('''            HarnessTool.MCP -> ApprovalDecision(true, "high", "MCP 工具可能访问外部服务或产生工作区之外的副作用。", summary)''',
'''            HarnessTool.MCP -> when (mcpBrowserRisk(rawToolName)) {
                "medium" -> ApprovalDecision(true, "medium", "浏览器操作会改变页面状态或新开会话。", summary)
                "high" -> ApprovalDecision(true, "high", "浏览器操作将修改页面内容或写入本地存储。", summary)
                "critical" -> ApprovalDecision(true, "critical", "浏览器操作涉及代码执行或读取敏感数据（Cookie/页面源码）。", summary)
                else -> ApprovalDecision(true, "high", "MCP 工具可能访问外部服务或产生工作区之外的副作用。", summary)
            }''',
"MCP branch by risk")

# 3. summarize 签名
apply('''    private fun summarize(tool: HarnessTool, args: JsonObject): String = when (tool) {''',
'''    private fun summarize(tool: HarnessTool, args: JsonObject, rawToolName: String? = null): String = when (tool) {''',
"summarize signature")

# 4. MCP summarize 分支显示工具名
apply('''        HarnessTool.MCP -> "MCP ${args["name"]?.jsonPrimitive?.content ?: "工具调用"}"''',
'''        HarnessTool.MCP -> {
            val toolName = rawToolName?.substringAfter("__")?.substringAfter("__")?.substringBefore("__")
                ?: args["name"]?.jsonPrimitive?.content
            "MCP ${toolName ?: "工具调用"}"
        }''',
"MCP summarize")

# 5. summary 计算传 rawToolName
apply('''        val summary = summarize(tool, args)''',
'''        val summary = summarize(tool, args, rawToolName)''',
"summary passes rawToolName")

# 6. 新增 mcpBrowserRisk 函数
apply('''    private fun processAction(args: JsonObject): String =''',
'''    /** 解析 MCP 工具名（mcp__<server>__<tool>__<hash>）并映射内置浏览器工具风险档位；非浏览器工具返回 null。 */
    private fun mcpBrowserRisk(rawToolName: String?): String? {
        if (rawToolName == null) return null
        val tool = rawToolName.substringAfter("__").substringAfter("__").substringBefore("__")
        return when (tool) {
            "browser_back", "browser_forward", "browser_refresh", "browser_list_tabs", "browser_close_tab",
            "browser_snapshot", "browser_scroll", "browser_screenshot", "browser_current_url", "browser_title",
            "browser_console_list", "browser_network_list" -> "low"
            "browser_open", "browser_navigate", "browser_page_source", "browser_console_clear",
            "browser_local_keys", "browser_session_keys" -> "medium"
            "browser_click", "browser_type", "browser_press", "browser_cookies_set", "browser_cookies_delete",
            "browser_local_get", "browser_local_set", "browser_local_delete",
            "browser_session_get", "browser_session_set", "browser_session_delete" -> "high"
            "browser_evaluate", "browser_cookies_get" -> "critical"
            else -> null
        }
    }

    private fun processAction(args: JsonObject): String =''',
"add mcpBrowserRisk")

io.open(f, 'w', encoding='utf-8', newline='\n').write(t)
print("DONE")
