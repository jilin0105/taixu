package top.wkbin.taixu.runtime.rootfs

import top.wkbin.taixu.core.common.logging.AppLogger
import top.wkbin.taixu.runtime.DistributionSpec
import top.wkbin.taixu.runtime.DownloadProgress
import top.wkbin.taixu.runtime.RegistryRoute
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

@Singleton
class OciRegistryClient @Inject constructor(
    private val http: OkHttpClient,
    private val logger: AppLogger,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun pull(
        distribution: DistributionSpec,
        route: RegistryRoute,
        cacheDir: File,
        onProgress: suspend (DownloadProgress) -> Unit,
        resetDestination: suspend () -> Unit,
        applyLayer: suspend (File, String) -> Unit,
    ): String = withContext(Dispatchers.IO) {
        val endpoints = when (route) {
            RegistryRoute.OFFICIAL -> listOf(Endpoint.dockerHub())
            RegistryRoute.CHINA_ACCELERATED -> listOf(Endpoint.daoCloud())
            RegistryRoute.AUTO -> listOf(Endpoint.daoCloud(), Endpoint.dockerHub())
        }
        var lastFailure: Throwable? = null
        for ((index, endpoint) in endpoints.withIndex()) {
            try {
                if (index > 0) resetDestination()
                val metadataClient = if (route == RegistryRoute.AUTO && index == 0) {
                    http.newBuilder()
                        .connectTimeout(6, TimeUnit.SECONDS)
                        .readTimeout(12, TimeUnit.SECONDS)
                        .callTimeout(15, TimeUnit.SECONDS)
                        .build()
                } else {
                    http
                }
                return@withContext pullFrom(
                    endpoint,
                    distribution,
                    cacheDir,
                    onProgress,
                    applyLayer,
                    metadataClient,
                )
            } catch (failure: Throwable) {
                lastFailure = failure
                logger.w("OCI registry route ${endpoint.name} failed", failure)
            }
        }
        throw lastFailure ?: IllegalStateException("没有可用的 OCI Registry 下载线路")
    }

    private suspend fun pullFrom(
        endpoint: Endpoint,
        distribution: DistributionSpec,
        cacheDir: File,
        onProgress: suspend (DownloadProgress) -> Unit,
        applyLayer: suspend (File, String) -> Unit,
        metadataClient: OkHttpClient,
    ): String {
        cacheDir.mkdirs()
        val parsed = ImageReference.parse(distribution.imageReference)
        val repo = endpoint.rewriteRepo(parsed.repo)
        val token = endpoint.token(repo, metadataClient, json)
        var manifest = getJson(
            "${endpoint.registryBase}/v2/$repo/manifests/${parsed.tag}", token,
            ACCEPT_MANIFESTS,
            metadataClient,
        )
        if (manifest["manifests"] != null) {
            val target = manifest.getValue("manifests").jsonArray.firstOrNull { entry ->
                val platform = entry.jsonObject["platform"]?.jsonObject ?: return@firstOrNull false
                platform["os"]?.jsonPrimitive?.content == "linux" &&
                    platform["architecture"]?.jsonPrimitive?.content == "arm64"
            }?.jsonObject ?: error("镜像 ${distribution.imageReference} 不提供 linux/arm64")
            val digest = target.getValue("digest").jsonPrimitive.content
            manifest = getJson(
                "${endpoint.registryBase}/v2/$repo/manifests/$digest", token,
                ACCEPT_MANIFESTS,
                metadataClient,
            )
        }
        val layers = manifest["layers"]?.jsonArray ?: error("OCI manifest 没有文件系统层")
        val total = layers.sumOf { it.jsonObject["size"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L }
        var completed = 0L
        layers.forEachIndexed { index, element ->
            val layer = element.jsonObject
            val digest = layer.getValue("digest").jsonPrimitive.content
            val mediaType = layer["mediaType"]?.jsonPrimitive?.content.orEmpty()
            val blob = downloadBlob(endpoint, repo, digest, token, cacheDir) { current ->
                onProgress(DownloadProgress(completed + current, total.takeIf { it > 0 }))
            }
            applyLayer(blob, mediaType)
            completed += layer["size"]?.jsonPrimitive?.content?.toLongOrNull() ?: blob.length()
            onProgress(DownloadProgress(completed, total.takeIf { it > 0 }))
            logger.i("Applied OCI layer ${index + 1}/${layers.size}: ${digest.takeLast(12)}")
        }
        return "oci-5.7.0-${distribution.id}-${parsed.tag}"
    }

    private fun getJson(url: String, token: String, accept: String, client: OkHttpClient): JsonObject {
        val request = Request.Builder().url(url).header("Accept", accept).header("User-Agent", USER_AGENT)
            .apply { if (token.isNotBlank()) header("Authorization", "Bearer $token") }.build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Registry 请求失败 HTTP ${response.code}: $url" }
            return json.parseToJsonElement(response.body.string()).jsonObject
        }
    }

    private suspend fun downloadBlob(
        endpoint: Endpoint,
        repo: String,
        digest: String,
        token: String,
        cacheDir: File,
        progress: suspend (Long) -> Unit,
    ): File {
        val expected = validateDigest(digest)
        val target = File(cacheDir, "sha256-$expected.layer")
        if (target.isFile && sha256(target) == expected) return target
        val partial = File(cacheDir, "sha256-$expected.part")
        partial.delete()
        val request = Request.Builder().url("${endpoint.registryBase}/v2/$repo/blobs/$digest")
            .header("User-Agent", USER_AGENT)
            .apply { if (token.isNotBlank()) header("Authorization", "Bearer $token") }.build()
        http.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "下载 OCI layer 失败 HTTP ${response.code}" }
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
                        progress(count)
                    }
                }
            }
            val actual = md.digest().joinToString("") { "%02x".format(it) }
            check(actual == expected) { "OCI layer 摘要校验失败：期望 $expected，实际 $actual" }
        }
        check(partial.renameTo(target)) { "无法提交 OCI layer 缓存" }
        return target
    }

    private fun validateDigest(value: String): String {
        val match = Regex("^sha256:([0-9a-fA-F]{64})$").matchEntire(value)
            ?: throw SecurityException("非法或不支持的 OCI digest：$value")
        return match.groupValues[1].lowercase()
    }

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

    private data class ImageReference(val repo: String, val tag: String) {
        companion object {
            fun parse(value: String): ImageReference {
                val last = value.substringAfterLast('/')
                val tag = if (':' in last) last.substringAfterLast(':') else "latest"
                val name = if (':' in last) value.substringBeforeLast(':') else value
                return ImageReference(if ('/' in name) name else "library/$name", tag)
            }
        }
    }

    private data class Endpoint(
        val name: String,
        val registryBase: String,
        val rewriteRepo: (String) -> String,
        val tokenProvider: (String, OkHttpClient, Json) -> String,
    ) {
        fun token(repo: String, http: OkHttpClient, json: Json) = tokenProvider(repo, http, json)

        companion object {
            fun dockerHub() = Endpoint("Docker Hub", "https://registry-1.docker.io", { it }) { repo, http, json ->
                val scope = URLEncoder.encode("repository:$repo:pull", Charsets.UTF_8.name())
                val request = Request.Builder().url("https://auth.docker.io/token?service=registry.docker.io&scope=$scope")
                    .header("User-Agent", USER_AGENT).build()
                http.newCall(request).execute().use { response ->
                    check(response.isSuccessful) { "Docker Hub 鉴权失败 HTTP ${response.code}" }
                    val body = json.parseToJsonElement(response.body.string()).jsonObject
                    body["token"]?.jsonPrimitive?.content ?: body["access_token"]?.jsonPrimitive?.content.orEmpty()
                }
            }

            fun daoCloud() = Endpoint(
                "DaoCloud public mirror",
                "https://docker.m.daocloud.io",
                { it },
                { repo, http, json ->
                    val scope = URLEncoder.encode("repository:$repo:pull", Charsets.UTF_8.name())
                    val request = Request.Builder()
                        .url("https://m.daocloud.io/auth/token?service=docker.m.daocloud.io&scope=$scope")
                        .header("User-Agent", USER_AGENT)
                        .build()
                    http.newCall(request).execute().use { response ->
                        check(response.isSuccessful) { "DaoCloud 鉴权失败 HTTP ${response.code}" }
                        json.parseToJsonElement(response.body.string()).jsonObject["token"]
                            ?.jsonPrimitive
                            ?.content
                            .orEmpty()
                    }
                },
            )
        }
    }

    private companion object {
        const val USER_AGENT = "TaiXu/proot-distro-5.7.0-compatible"
        const val ACCEPT_MANIFESTS =
            "application/vnd.oci.image.index.v1+json, application/vnd.docker.distribution.manifest.list.v2+json, application/vnd.oci.image.manifest.v1+json, application/vnd.docker.distribution.manifest.v2+json"
    }
}
