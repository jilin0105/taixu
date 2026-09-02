package top.wkbin.taixu.core.browser

/**
 * Browser 文件系统操作的纯 Kotlin 接口 —— 由上层（harness/feature）注入实现，
 * runtime/browser 模块仅调用，避免反向依赖。
 *
 * 设计要点：
 * - 与 WorkspaceFileService 等价，但**只暴露浏览器工具能用到的方法**，避免把运行时细节漏给浏览器工具；
 * - 文件读取天然经过 [SecretRedactor]（由实现负责），不暴露原始内容给 agent；
 * - 返回值为 UTF-8 文本；二进制内容留给 `file.open`/`file.share` 系统调用。
 */
interface BrowserFileOps {
    /** 工作目录的根（绝对路径）；所有相对路径解析的起点。 */
    fun workdir(): String

    suspend fun read(path: String, maxBytes: Int = 1024 * 1024): String
    suspend fun write(path: String, content: String, append: Boolean = false): Boolean
    suspend fun list(path: String, limit: Int = 500): List<String>
    suspend fun tree(path: String, depth: Int = 3): List<String>
    suspend fun search(path: String, query: String, limit: Int = 100): List<String>
    suspend fun stat(path: String): String
    suspend fun exists(path: String): Boolean
    suspend fun mkdir(path: String): Boolean
    suspend fun delete(path: String): Boolean
    suspend fun copy(src: String, dst: String): Boolean
    suspend fun move(src: String, dst: String): Boolean
    suspend fun hash(path: String, algorithm: String = "sha256"): String
    suspend fun zip(paths: List<String>, archive: String): Boolean
    suspend fun unzip(archive: String, into: String): Boolean
    suspend fun open(path: String): Boolean
    suspend fun share(path: String): Boolean

    companion object {
        /** Always refuse these paths regardless of workdir configuration. */
        val FORBIDDEN_PREFIXES = listOf("/proc", "/sys", "/dev", "/system", "/root/.ssh")
    }
}
