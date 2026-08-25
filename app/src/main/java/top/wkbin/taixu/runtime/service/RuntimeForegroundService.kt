package top.wkbin.taixu.runtime.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
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
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelfResult(startId)
            }
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, notification())
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun notification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.taixu_logo)
        .setContentTitle(getString(R.string.taixu_runtime_running))
        .setContentText(getString(R.string.taixu_runtime_background_available))
        .setOngoing(true)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .addAction(
            NotificationCompat.Action(
                R.drawable.taixu_logo,
                getString(R.string.taixu_notification_stop),
                PendingIntent.getService(
                    this,
                    1002,
                    Intent(this, RuntimeForegroundService::class.java).setAction(ACTION_STOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            ),
        )
        .build()

    companion object {
        const val ACTION_STOP = "top.wkbin.taixu.action.STOP_RUNTIME_SERVICE"
        private const val CHANNEL_ID = "linux-runtime"
        private const val NOTIFICATION_ID = 1001
    }
}
