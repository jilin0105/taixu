package top.wkbin.taixu.harness

/**
 * Prepares safe, foreground Agent commands for RTK without changing terminal,
 * MCP, process, or file-tool behaviour. RTK itself decides whether a supported
 * command has an equivalent; a missing/incompatible binary always falls back to
 * the exact original command in the same shell invocation.
 */
internal object RtkCommandOptimizer {
    private const val RTK_BINARY = "/opt/taixu/bin/rtk"

    private val rtkEnvironment = mapOf(
        // Raw failure output can include project secrets and is already returned to the Agent.
        "RTK_TEE" to "0",
        "XDG_CONFIG_HOME" to "/opt/taixu/data/rtk/config",
        "XDG_DATA_HOME" to "/opt/taixu/data/rtk/data",
    )

    private val supportedCommands = setOf(
        "git", "rg", "grep", "find", "ls", "tree", "wc", "du",
        "gradle", "gradlew", "mvn", "mvnw", "cargo", "go", "pytest",
        "npm", "pnpm", "yarn", "bun", "npx",
    )

    /** Shell operators, expansions and globs are intentionally left untouched. */
    private val unsupportedShellSyntax = setOf(
        '&', '|', ';', '\n', '\r', '<', '>', '`', '$', '*', '?', '[', ']', '{', '}', '~',
    )

    data class PreparedCommand(
        val commandLine: String,
        val environment: Map<String, String> = emptyMap(),
    )

    fun prepare(command: String, enabled: Boolean): PreparedCommand {
        if (!enabled || !isEligible(command)) return PreparedCommand(command)
        return PreparedCommand(
            commandLine = wrapWithFallback(command),
            environment = rtkEnvironment,
        )
    }

    private fun isEligible(command: String): Boolean {
        val trimmed = command.trim()
        if (trimmed.isEmpty() || command.any { it in unsupportedShellSyntax }) return false
        val executable = trimmed.substringBeforeFirstWhitespace()
            .substringAfterLast('/')
            .lowercase()
        return executable in supportedCommands
    }

    private fun String.substringBeforeFirstWhitespace(): String {
        val index = indexOfFirst(Char::isWhitespace)
        return if (index >= 0) substring(0, index) else this
    }

    private fun wrapWithFallback(command: String): String {
        val quotedCommand = shellQuote(command)
        return """
            if [ -x "$RTK_BINARY" ]; then
                _taixu_rtk_rewritten="${'$'}("$RTK_BINARY" rewrite $quotedCommand 2>/dev/null)"
                _taixu_rtk_status=${'$'}?
                if [ "${'$'}_taixu_rtk_status" -eq 0 ] && [ -n "${'$'}_taixu_rtk_rewritten" ]; then
                    eval "${'$'}_taixu_rtk_rewritten"
                else
                    $command
                fi
            else
                $command
            fi
        """.trimIndent()
    }

    private fun shellQuote(value: String): String = "'${value.replace("'", "'\"'\"'")}'"
}
