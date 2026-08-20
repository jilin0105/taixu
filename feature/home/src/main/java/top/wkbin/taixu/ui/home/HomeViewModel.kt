package top.wkbin.taixu.ui.home

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import top.wkbin.taixu.core.common.logging.AppLogger
import top.wkbin.taixu.core.datastore.SettingsDataStore
import top.wkbin.taixu.core.model.RuntimeState
import top.wkbin.taixu.runtime.DistributionCatalog
import top.wkbin.taixu.runtime.LinuxRuntime
import kotlinx.coroutines.flow.first
import javax.inject.Inject

data class SystemResourceMetrics(
    val memoryUsedMb: Long = 0,
    val memoryTotalMb: Long = 0,
    val memoryUsagePercent: Int = 0,
    val appHeapUsedMb: Long = 0,
    val storageUsedGb: Double = 0.0,
    val storageTotalGb: Double = 0.0,
    val storageUsagePercent: Int = 0,
    val activeProcessCount: Int = 0,
    val runningServicesCount: Int = 0,
    val cpuArch: String = "aarch64",
    val linuxDistro: String = "Ubuntu 24.04 LTS",
    val engineVersion: String = "PRoot 5.1 · Link2Symlink",
    val hostAndroidVersion: String = "Android",
    val uptimeFormatted: String = "00:00",
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val linuxRuntime: LinuxRuntime,
    private val settingsDataStore: SettingsDataStore,
    private val logger: AppLogger,
) : ViewModel() {

    val runtimeState: StateFlow<RuntimeState> = linuxRuntime.state

    private val _initializing = MutableStateFlow(false)
    val initializing: StateFlow<Boolean> = _initializing.asStateFlow()

    private val _metrics = MutableStateFlow(SystemResourceMetrics())
    val metrics: StateFlow<SystemResourceMetrics> = _metrics.asStateFlow()

    private var initializationJob: Job? = null
    private val appStartTime = SystemClock.elapsedRealtime()

    init {
        startMetricsMonitoring()
    }

    private fun startMetricsMonitoring() {
        viewModelScope.launch {
            while (isActive) {
                refreshMetrics()
                delay(3000)
            }
        }
    }

    fun refreshMetrics() {
        try {
            // 1. 内存指标 (RAM)
            val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            actManager?.getMemoryInfo(memInfo)
            val totalMemMb = memInfo.totalMem / (1024 * 1024)
            val availMemMb = memInfo.availMem / (1024 * 1024)
            val usedMemMb = (totalMemMb - availMemMb).coerceAtLeast(0)
            val memPercent = if (totalMemMb > 0) ((usedMemMb * 100) / totalMemMb).toInt() else 0

            val rt = Runtime.getRuntime()
            val heapUsedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)

            // 2. 存储空间 (Disk / Rootfs)
            val rootfsDir = try { linuxRuntime.rootfsPath() } catch (e: Exception) { context.filesDir }
            val stat = StatFs(if (rootfsDir.exists()) rootfsDir.absolutePath else context.filesDir.absolutePath)
            val totalBytes = stat.totalBytes
            val availBytes = stat.availableBytes
            val usedBytes = (totalBytes - availBytes).coerceAtLeast(0)
            val totalGb = String.format("%.1f", totalBytes.toDouble() / (1024 * 1024 * 1024)).toDoubleOrNull() ?: 0.0
            val usedGb = String.format("%.1f", usedBytes.toDouble() / (1024 * 1024 * 1024)).toDoubleOrNull() ?: 0.0
            val storagePercent = if (totalBytes > 0) ((usedBytes * 100) / totalBytes).toInt() else 0

            // 3. 活跃进程与后台任务
            val bgProcesses = try { linuxRuntime.listBackground() } catch (e: Exception) { emptyList() }
            val activeProcs = bgProcesses.size

            // 4. 运行时间
            val elapsedSec = (SystemClock.elapsedRealtime() - appStartTime) / 1000
            val hours = elapsedSec / 3600
            val minutes = (elapsedSec % 3600) / 60
            val seconds = elapsedSec % 60
            val uptime = if (hours > 0) String.format("%02d:%02d:%02d", hours, minutes, seconds)
                         else String.format("%02d:%02d", minutes, seconds)

            val arch = Build.SUPPORTED_ABIS.firstOrNull() ?: "aarch64"
            val androidVer = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

            viewModelScope.launch {
                val selectedDistroId = runCatching { settingsDataStore.selectedDistribution.first() }.getOrDefault("ubuntu")
                val distroDisplayName = DistributionCatalog.require(selectedDistroId).displayName

                _metrics.value = SystemResourceMetrics(
                    memoryUsedMb = usedMemMb,
                    memoryTotalMb = totalMemMb,
                    memoryUsagePercent = memPercent,
                    appHeapUsedMb = heapUsedMb,
                    storageUsedGb = usedGb,
                    storageTotalGb = totalGb,
                    storageUsagePercent = storagePercent,
                    activeProcessCount = activeProcs,
                    runningServicesCount = bgProcesses.count { it.type == top.wkbin.taixu.runtime.shell.ProcessType.SERVICE },
                    cpuArch = arch,
                    linuxDistro = distroDisplayName,
                    engineVersion = "PRoot 5.1 · Link2Symlink",
                    hostAndroidVersion = androidVer,
                    uptimeFormatted = uptime,
                )
            }
        } catch (e: Exception) {
            logger.w("HomeViewModel: Failed to refresh metrics: ${e.message}", e)
        }
    }

    fun initializeRuntime() {
        if (_initializing.value || initializationJob?.isActive == true) return
        initializationJob = viewModelScope.launch {
            _initializing.value = true
            try {
                linuxRuntime.initialize()
            } finally {
                _initializing.value = false
                initializationJob = null
                refreshMetrics()
            }
        }
    }

    fun cancelInitialization() {
        initializationJob?.cancel()
    }
}
