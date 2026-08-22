package top.wkbin.taixu.runtime.shell

data class ShellCommand(
    val commandLine: String,
    val workingDirectory: String = "/root",
    val environment: Map<String, String> = emptyMap(),
    val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    val onOutput: ((String) -> Unit)? = null,
) {
    companion object {
        const val DEFAULT_TIMEOUT_MS = 30_000L
    }
}

