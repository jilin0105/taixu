package top.wkbin.taixu.harness.mcp.server

/**
 * MCP server 侧 Token 校验。
 *
 * 原则：
 * - 内置 server 在 loopback 上空 token 不拒（仅进程内自用）；
 * - 一旦开启 `browser.allowRemoteConnect=true`，则无论 loopback 还是远程都必须带 Bearer Token。
 * - Token 不在响应日志/异常中露出。
 */
object McpAuthFilter {

    const val LOOPBACK_TOKEN = "loopback"

    fun isAcceptable(
        authHeader: String?,
        loopback: Boolean,
        configuredToken: String?,
    ): Boolean {
        if (loopback && configuredToken.isNullOrEmpty()) {
            return authHeader.isNullOrBlank() || authHeader == "Bearer $LOOPBACK_TOKEN"
        }
        if (configuredToken.isNullOrEmpty()) return false
        return authHeader == "Bearer $configuredToken"
    }
}
