package top.wkbin.taixu.runtime.rootfs

import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import top.wkbin.taixu.core.common.logging.AppLogger
import top.wkbin.taixu.runtime.DistributionSpec
import top.wkbin.taixu.runtime.DownloadProgress

/**
 * 清华大学 TUNA 镜像站 rootfs 下载通道（images.linuxcontainers.org 的完整同步）。
 *
 * 与 OciRegistryClient 的 OCI 分层拉取不同，这里直接下载单文件 `rootfs.tar.xz`，
 * 并使用目录内的 `SHA256SUMS` 做摘要校验。仅作为 OCI 线路（DaoCloud / Docker Hub）
 * 全部失败后的国内兜底，且只支持映射到 lxc-images 的发行版。
 */
@Singleton
class LxcImagesClient @Inject constructor(
    private val http: OkHttpClient,
    private val logger: AppLogger,
) {
    /** 该发行版是否提供 TUNA 兜底镜像。 */
    fun supports(distributionId: String): Boolean = lxcPathFor(distributionId) != null

    suspend fun pull(
        distribution: DistributionSpec,
        cacheDir: File,
        onProgress: suspend (DownloadProgress) -> Unit,
        applyLayer: suspend (File, String) -> Unit,
    ): String = withContext(Dispatchers.IO) {
        val lxcPath = lxcPathFor(distribution.id)
            ?: error("清华镜像站暂不支持发行版 ${distribution.id}")
        cacheDir.mkdirs()
        val buildDir = resolveLatestBuild(lxcPath)
        val expectedSha = fetchExpectedSha256(lxcPath, buildDir)
        val blob = downloadRootfs(lxcPath, buildDir, expectedSha, cacheDir, onProgress)
        applyLayer(blob, MEDIA_TYPE_ROOTFS_TAR_XZ)
        "lxc-5.7.0-${distribution.id}-$buildDir"
    }

    /** 抓取目录列表并解析出最新一次构建的时间戳目录（形如 20260820_05:24）。 */
    private fun resolveLatestBuild(lxcPath: String): String {
        val url = "$MIRROR_BASE/$lxcPath/arm64/default/"
        val body = fetchText(url, metadataClient())
        val builds = Regex("(\\d{8}_\\d{2}(?:%3A|:)\\d{2})/")
            .findAll(body)
            .map { it.groupValues[1].replace("%3A", ":") }
            .distinct()
            .sorted()
            .toList()
        check(builds.isNotEmpty()) { "TUNA 镜像目录解析失败：$url" }
        return builds.last()
    }

    /** 读取构建目录中的 SHA256SUMS，取 rootfs.tar.xz 的摘要。 */
    private fun fetchExpectedSha256(lxcPath: String, buildDir: String): String {
        val url = buildUrl(lxcPath, buildDir, "SHA256SUMS")
        val body = fetchText(url, metadataClient())
        val expected = body.lineSequence()
            .firstOrNull { it.trimEnd().endsWith("rootfs.tar.xz") }
            ?.trim()
            ?.split(Regex("\\s+"))
            ?.firstOrNull()
            ?.lowercase()
            ?: error("SHA256SUMS 中找不到 rootfs.tar.xz 条目：$url")
        check(expected.matches(Regex("[0-9a-f]{64}"))) { "TUNA SHA256SUMS 摘要格式非法：$expected" }
        return expected
    }

    private suspend fun downloadRootfs(
        lxcPath: String,
        buildDir: String,
        expectedSha: String,
        cacheDir: File,
        onProgress: suspend (DownloadProgress) -> Unit,
    ): File {
        val target = File(cacheDir, "sha256-$expectedSha.rootfs.tar.xz")
        if (target.isFile && sha256(target) == expectedSha) {
            logger.i("TUNA lxc-images layer cache hit: ${target.name}")
            return target
        }
        val partial = File(cacheDir, "sha256-$expectedSha.part")
        partial.delete()
        val url = buildUrl(lxcPath, buildDir, "rootfs.tar.xz")
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        http.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "下载 TUNA rootfs 失败 HTTP ${response.code}" }
            val total = response.header("Content-Length")?.toLongOrNull()?.takeIf { it > 0 }
            val md = MessageDigest.getInstance("SHA-256")
            var count = 0L
            response.body.byteStream().use { input ->
                FileOutputStream(partial).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        md.update(buffer, 0, read)
                        count += read
                        onProgress(DownloadProgress(count, total))
                    }
                }
            }
            val actual = md.digest().joinToString("") { "%02x".format(it) }
            check(actual == expectedSha) { "TUNA rootfs 摘要校验失败：期望 $expectedSha，实际 $actual" }
        }
        check(partial.renameTo(target)) { "无法提交 TUNA rootfs 缓存" }
        return target
    }

    /** 时间戳目录名中的冒号需要编码，避免部分 HTTP 栈拒绝路径。 */
    private fun buildUrl(lxcPath: String, buildDir: String, fileName: String): String =
        "$MIRROR_BASE/$lxcPath/arm64/default/${buildDir.replace(":", "%3A")}/$fileName"

    private fun fetchText(url: String, client: OkHttpClient): String {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "TUNA 镜像元数据请求失败 HTTP ${response.code}: $url" }
            return response.body.string()
        }
    }

    /** 元数据请求（目录列表 / SHA256SUMS）使用短超时，避免兜底线路拖慢整体安装。 */
    private fun metadataClient(): OkHttpClient = http.newBuilder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                md.update(buffer, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val MIRROR_BASE = "https://mirrors.tuna.tsinghua.edu.cn/lxc-images/images"
        const val USER_AGENT = "TaiXu/proot-distro-5.7.0-compatible"
        const val MEDIA_TYPE_ROOTFS_TAR_XZ = "application/x-tar.xz"

        /**
         * 发行版 id → lxc-images 路径映射。
         * 注意：lxc-images 只有 default（极简）rootfs，不含 buildpack-deps 工具链，
         * 因此仅作为兜底线路，不能等价替换 OCI 镜像。
         */
        fun lxcPathFor(distributionId: String): String? = when (distributionId.lowercase()) {
            "debian" -> "debian/bookworm"
            "ubuntu" -> "ubuntu/noble"
            "kali" -> "kali/current"
            else -> null
        }
    }
}
