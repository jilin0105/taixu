package top.wkbin.taixu.core.tools

import android.content.Context
import android.util.Base64
import top.wkbin.taixu.core.common.logging.AppLogger
import top.wkbin.taixu.core.common.result.AppError
import top.wkbin.taixu.core.common.result.AppResult
import top.wkbin.taixu.core.common.result.ErrorCode
import top.wkbin.taixu.core.model.ToolManifest
import top.wkbin.taixu.core.model.ToolRegistryDocument
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

data class SignedRegistryRequest(
    val manifestUrl: String,
    val signatureUrl: String,
    /** Base64 encoded Ed25519 SubjectPublicKeyInfo. */
    val publicKeyBase64: String,
)

@Singleton
class ToolRegistry @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient,
    private val logger: AppLogger,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val persistedFile: File
        get() = File(context.filesDir, "registry/tools.json")

    fun load(): List<ToolManifest> {
        val persisted = persistedFile.takeIf { it.isFile }?.let { file ->
            runCatching { parseAndValidate(file.readText()) }.getOrNull()
        }
        return persisted ?: parseAndValidate(
            context.assets.open(REGISTRY_ASSET).bufferedReader().use { it.readText() },
        )
    }

    suspend fun updateSigned(request: SignedRegistryRequest): AppResult<Int> = withContext(Dispatchers.IO) {
        try {
            require(request.manifestUrl.startsWith("https://", ignoreCase = true)) { "工具清单必须使用 HTTPS" }
            require(request.signatureUrl.startsWith("https://", ignoreCase = true)) { "签名地址必须使用 HTTPS" }

            val publicKeyBase64 = effectivePublicKey(request)
            // 信任锚说明：官方内置清单（assets/registry/tools.json）在构建期随 APK 分发、运行时只读，
            // 不依赖本下载流程。远程更新使用用户自填公钥时，只能检测传输损坏与内容一致性，
            // 无法防篡改——能控制清单地址的人同样能提供公钥。真正防篡改需要启用下方内置固定公钥。
            val trustMode =
                if (PINNED_REGISTRY_KEY_BASE64.isNotBlank()) "pinned" else "user-supplied"
            logger.w(
                "Tool registry update: verifying with $trustMode public key — " +
                    "integrity-only trust, NOT end-to-end tamper protection.",
            )

            val manifestBytes = download(request.manifestUrl)
            val signatureBytes = decodeSignature(download(request.signatureUrl))
            verifySignature(manifestBytes, signatureBytes, publicKeyBase64)
            val manifests = parseAndValidate(manifestBytes.toString(Charsets.UTF_8))
            val parent = persistedFile.parentFile ?: error("无法创建 Registry 目录")
            check(parent.exists() || parent.mkdirs()) { "无法创建 Registry 目录" }
            val staging = File(parent, "tools.json.part")
            staging.writeBytes(manifestBytes)
            commitPersistedRegistry(staging)
            AppResult.Success(manifests.size)
        } catch (throwable: Throwable) {
            AppResult.Failure(
                AppError(
                    code = if (throwable is SecurityException) ErrorCode.SECURITY else ErrorCode.NETWORK,
                    message = "工具清单更新失败：${throwable.message ?: "未知错误"}",
                    cause = throwable,
                ),
            )
        }
    }

    /**
     * 选择验签公钥：内置固定锚点未启用（为空）时使用调用方提供的公钥；
     * 一旦 [PINNED_REGISTRY_KEY_BASE64] 被填入正式公钥，则强制使用它，
     * 忽略外部传入的密钥（防止“控制清单地址的人同时换掉公钥”）。
     */
    private fun effectivePublicKey(request: SignedRegistryRequest): String {
        if (PINNED_REGISTRY_KEY_BASE64.isNotBlank()) {
            return PINNED_REGISTRY_KEY_BASE64
        }
        require(request.publicKeyBase64.isNotBlank()) { "未配置签名公钥" }
        return request.publicKeyBase64
    }

    fun clearRemoteRegistry() {
        persistedFile.delete()
    }

    private fun download(url: String): ByteArray {
        val response = httpClient.newCall(Request.Builder().url(url).build()).execute()
        response.use {
            if (!it.request.url.isHttps) {
                throw RegistrySecurityException("工具清单请求被重定向到非 HTTPS 地址")
            }
            check(it.isSuccessful) { "HTTP ${it.code}" }
            val body = it.body ?: error("服务器返回空内容")
            check(body.contentLength() <= MAX_REGISTRY_BYTES) { "工具清单超过大小限制" }
            val output = ByteArrayOutputStream(minOf(body.contentLength().coerceAtLeast(0), MAX_REGISTRY_BYTES).toInt())
            body.byteStream().use { input ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (output.size() + read > MAX_REGISTRY_BYTES) {
                        throw IllegalStateException("工具清单超过大小限制")
                    }
                    output.write(buffer, 0, read)
                }
            }
            return output.toByteArray()
        }
    }

    private fun decodeSignature(bytes: ByteArray): ByteArray {
        val text = bytes.toString(Charsets.UTF_8).trim()
        return runCatching { Base64.decode(text, Base64.DEFAULT) }.getOrElse { bytes }
    }

    private fun verifySignature(
        payload: ByteArray,
        signatureBytes: ByteArray,
        publicKeyBase64: String,
    ) {
        val publicKey = runCatching {
            KeyFactory.getInstance("Ed25519")
                .generatePublic(X509EncodedKeySpec(Base64.decode(publicKeyBase64, Base64.DEFAULT)))
        }.getOrElse { throw RegistrySecurityException("工具清单公钥无效", it) }
        Signature.getInstance("Ed25519").apply {
            initVerify(publicKey)
            update(payload)
            if (!verify(signatureBytes)) throw RegistrySecurityException("工具清单签名校验失败")
        }
    }

    private class RegistrySecurityException(
        message: String,
        cause: Throwable? = null,
    ) : SecurityException(message, cause)

    private fun parseAndValidate(text: String): List<ToolManifest> {
        val manifests = runCatching {
            json.decodeFromString<List<ToolManifest>>(text)
        }.getOrElse {
            val document = json.decodeFromString<ToolRegistryDocument>(text)
            require(document.schemaVersion == 1) { "不支持的 Registry Schema：${document.schemaVersion}" }
            require(document.version > 0) { "Registry 版本必须为正数" }
            document.tools
        }
        return ToolManifestValidator.validateAll(manifests)
    }

    private fun commitPersistedRegistry(staging: File) {
        runCatching {
            Files.move(
                staging.toPath(),
                persistedFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        }.recoverCatching {
            Files.move(
                staging.toPath(),
                persistedFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }.getOrElse { throw IllegalStateException("无法提交工具清单", it) }
    }

    private companion object {
        const val REGISTRY_ASSET = "registry/tools.json"
        const val MAX_REGISTRY_BYTES = 1024 * 1024L

        /**
         * 可选的内置固定公钥（APK 内信任锚）：
         * - 留空 = 当前接受“自定义公钥仅防传输损坏”的语义，[effectivePublicKey] 使用调用方传入的公钥；
         * - 填入项目正式 Ed25519 SPKI（Base64）后，所有远程清单更新强制用该密钥校验，
         *   外部传入的公钥被忽略，能防“控制清单地址者同时提供公钥”的篡改场景。
         */
        const val PINNED_REGISTRY_KEY_BASE64 = ""
    }
}
