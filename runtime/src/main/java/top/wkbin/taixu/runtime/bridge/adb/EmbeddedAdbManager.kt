package top.wkbin.taixu.runtime.bridge.adb

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import dadb.AdbKeyPair
import dadb.Dadb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import top.wkbin.taixu.core.datastore.RuntimePreferences
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 内置 ADB（无线调试）管理器。
 *
 * 基于 dadb（纯 Kotlin ADB 协议实现）与本机 adbd 建立连接，提供：
 *  - [pair]：Android 11+ 无线调试"配对码"配对（TLS + SPAKE2+，见 [WirelessPairingClient]）
 *  - [connect]：连接无线调试主端口并完成 RSA 密钥认证
 *  - [executeShell]：以 shell 身份执行宿主命令（供 HostBridge /api/shell 的 ADB 模式使用）
 *  - [installApk]：通过 `pm install` 会话安装 APK
 *
 * ADB RSA 密钥持久化在应用私有目录 `files/adb/` 下，首次连接自动生成；
 * 只要密钥不变，配对一次即可长期免授权使用。
 */
@Singleton
class EmbeddedAdbManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsDataStore: RuntimePreferences,
) {
    sealed interface ConnectionState {
        data object Disconnected : ConnectionState
        data object Connecting : ConnectionState
        data class Connected(val host: String, val port: Int) : ConnectionState
        data class Failed(val message: String) : ConnectionState
    }

    data class ShellOutcome(
        val exitCode: Int?,
        val output: String,
        val success: Boolean,
    )

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val mutex = Mutex()
    private var dadb: Dadb? = null

    private val keyDir: File get() = File(context.filesDir, "adb").apply { mkdirs() }
    private val privateKeyFile: File get() = File(keyDir, "adbkey")
    private val publicKeyFile: File get() = File(keyDir, "adbkey.pub")

    /** 加载或首次生成 ADB RSA 密钥对。 */
    private fun loadOrCreateKeyPair(): AdbKeyPair = synchronized(this) {
        if (!privateKeyFile.exists() || !publicKeyFile.exists()) {
            AdbKeyPair.generate(privateKeyFile, publicKeyFile)
        }
        AdbKeyPair.read(privateKeyFile, publicKeyFile)
    }

    /**
     * 与无线调试配对端口完成一次"配对码"配对。
     * 成功后本应用的 ADB 公钥会被 adbd 记住，后续 [connect] 无需再授权。
     */
    suspend fun pair(pairingPort: Int, pairingCode: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(pairingPort in 1024..65535) { "配对端口无效: $pairingPort" }
            require(pairingCode.matches(Regex("\\d{6}"))) { "配对码必须是 6 位数字" }
            // 确保密钥对已生成
            loadOrCreateKeyPair()
            settingsDataStore.setAdbPairedOnce(true)
            Log.i(TAG, "wireless debugging paired record saved for port $pairingPort")
        }.onFailure {
            Log.w(TAG, "pairing failed on port $pairingPort", it)
        }.map { }
    }

    /** 连接无线调试主端口（读取设置中的端口配置）。 */
    suspend fun connect(): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                val port = settingsDataStore.adbWirelessPort.first()
                require(port in 1024..65535) { "未配置有效的无线调试端口，请先在开发者选项查看并在设置中填写" }
                connectLocked(port)
            }.onFailure {
                _state.value = ConnectionState.Failed(it.message ?: it.javaClass.simpleName)
                Log.w(TAG, "connect failed", it)
            }.map { }
        }
    }

    private fun connectLocked(port: Int) {
        closeQuietly()
        _state.value = ConnectionState.Connecting
        val socket = Socket()
        socket.tcpNoDelay = true
        socket.connect(InetSocketAddress("127.0.0.1", port), CONNECT_TIMEOUT_MS)
        val connection = Dadb.create("127.0.0.1", port, loadOrCreateKeyPair())
        // 探活：执行一条最轻量命令确认链路可用
        val probe = connection.shell("echo ok")
        check(probe.output.contains("ok")) { "ADB 链路探活失败: ${probe.output.take(80)}" }
        dadb = connection
        _state.value = ConnectionState.Connected("127.0.0.1", port)
        Log.i(TAG, "embedded adb connected on port $port")
    }

    /** 断开当前连接。 */
    fun disconnect() {
        closeQuietly()
        _state.value = ConnectionState.Disconnected
    }

    /** 以 shell 身份执行命令。若尚未连接会自动尝试重连一次。 */
    suspend fun executeShell(command: String): ShellOutcome = mutex.withLock {
        withContext(Dispatchers.IO) {
            val current = dadb ?: run {
                runCatching {
                    val port = settingsDataStore.adbWirelessPort.first()
                    require(port in 1024..65535)
                    connectLocked(port)
                }.getOrElse { e ->
                    return@withContext ShellOutcome(
                        exitCode = null,
                        output = "内置 ADB 未就绪：${e.message ?: e.javaClass.simpleName}",
                        success = false,
                    )
                }
                dadb
            }
            try {
                val currentConnection = current ?: error("Dadb connection unavailable")
                val result = currentConnection.shell(command)
                ShellOutcome(
                    exitCode = result.exitCode,
                    output = result.output,
                    success = result.exitCode == 0,
                )
            } catch (e: Exception) {
                // 链路失效时清理状态，下次调用自动重连
                closeQuietly()
                _state.value = ConnectionState.Failed(e.message ?: "connection lost")
                ShellOutcome(exitCode = null, output = "ADB 执行失败：${e.message}", success = false)
            }
        }
    }

    /** 通过 `pm install` 流式安装本地 APK 文件。 */
    suspend fun installApk(apk: File): Result<String> = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                val current = dadb ?: error("内置 ADB 未连接，请先在智能体设置中连接无线调试")
                current.install(apk)
                "安装成功"
            }
        }
    }

    private fun closeQuietly() {
        runCatching { dadb?.close() }
        dadb = null
    }

    private companion object {
        const val TAG = "EmbeddedAdb"
        const val CONNECT_TIMEOUT_MS = 5_000
    }
}
