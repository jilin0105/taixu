package top.wkbin.taixu.runtime.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import top.wkbin.taixu.R
import top.wkbin.taixu.runtime.shell.ProcessRegistry
import top.wkbin.taixu.runtime.SshServiceManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@AndroidEntryPoint
class RuntimeForegroundService : Service() {
    @Inject lateinit var processRegistry: ProcessRegistry
    @Inject lateinit var localServiceLauncher: LocalServiceLauncher
    @Inject lateinit var sshServiceManager: SshServiceManager
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** CPU 唤醒锁：息屏后保持 CPU 运行，防止 Linux 后台进程/构建/安装被冻结。 */
    private var wakeLock: PowerManager.WakeLock? = null
    /** Wi-Fi 锁：息屏后防止 Wi-Fi 无线电源进入省电模式导致沙箱内网络断连。 */
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onCreate() {
        super.onCreate()
        sshServiceManager.startObserving()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.taixu_runtime_notification_channel), NotificationManager.IMPORTANCE_LOW),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            serviceScope.launch {
                runCatching { localServiceLauncher.stopAll() }
                runCatching { processRegistry.stopAll() }
                releaseLocks()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelfResult(startId)
            }
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, notification())
        acquireLocks()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        releaseLocks()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun acquireLocks() {
        if (wakeLock?.isHeld != true) {
            runCatching {
                wakeLock = getSystemService(PowerManager::class.java)
                    .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
                    .also { it.acquire(LOCK_TIMEOUT_MS) }
                Log.i(TAG, "Acquired partial wake lock for runtime service")
            }.onFailure { Log.w(TAG, "获取 CPU 唤醒锁失败", it) }
        }
        if (wifiLock?.isHeld != true) {
            runCatching {
                @Suppress("DEPRECATION")
                wifiLock = getSystemService(WifiManager::class.java)
                    .createWifiLock(WifiManager.WIFI_MODE_FULL, WIFI_LOCK_TAG)
                    .also { it.acquire() }
                Log.i(TAG, "Acquired Wi-Fi lock for runtime service")
            }.onFailure { Log.w(TAG, "获取 Wi-Fi 锁失败", it) }
        }
    }

    private fun releaseLocks() {
        runCatching {
            wakeLock?.takeIf { it.isHeld }?.release()
            Log.i(TAG, "Released wake lock")
        }.onFailure { Log.w(TAG, "释放 CPU 唤醒锁失败", it) }
        wakeLock = null
        runCatching {
            wifiLock?.takeIf { it.isHeld }?.release()
            Log.i(TAG, "Released Wi-Fi lock")
        }.onFailure { Log.w(TAG, "释放 Wi-Fi 锁失败", it) }
        wifiLock = null
    }

    private fun notification(): Notification {
        val stopPending = PendingIntent.getService(
            this,
            1002,
            Intent(this, RuntimeForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return top.wkbin.taixu.service.LiveCapsuleHelper.buildRunningCapsuleNotification(
            context = this,
            channelId = CHANNEL_ID,
            sessionId = "linux_runtime",
            sessionTitle = "Linux 沙箱",
            rawStatus = getString(R.string.taixu_runtime_running),
            elapsedSeconds = 0L,
            stopPendingIntent = stopPending,
        )
    }

    companion object {
        const val ACTION_STOP = "top.wkbin.taixu.action.STOP_RUNTIME_SERVICE"
        private const val CHANNEL_ID = "linux-runtime"
        private const val NOTIFICATION_ID = 1001
        private const val TAG = "RuntimeForegroundService"
        private const val WAKE_LOCK_TAG = "taixu:runtime-service"
        private const val WIFI_LOCK_TAG = "taixu:runtime-wifi"
        /** 唤醒锁超时：8 小时兜底，避免异常情况下永久持有。 */
        private const val LOCK_TIMEOUT_MS = 8 * 60 * 60 * 1000L
    }
}
