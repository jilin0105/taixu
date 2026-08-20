package top.wkbin.taixu.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import top.wkbin.taixu.R
import top.wkbin.taixu.harness.HarnessLoop
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Agent 后台执行前台服务：当 Agent 开始执行时启动，让进程在后台存活，
 * 通知实时显示进度；执行结束后保留一条带【回复】输入框的通知，可继续追加任务。
 */
@AndroidEntryPoint
class AgentForegroundService : Service() {

    @Inject lateinit var harnessLoop: HarnessLoop

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var collecting = false

    override fun onCreate() {
        super.onCreate()
        runCatching {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Agent 执行", NotificationManager.IMPORTANCE_LOW),
            )
        }.onFailure { Log.w(TAG, "创建通知渠道失败", it) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                runCatching { harnessLoop.cancel() }.onFailure { Log.w(TAG, "取消 Agent 失败", it) }
                stopForegroundSafely(STOP_FOREGROUND_REMOVE)
                stopSelfResult(startId)
                return START_NOT_STICKY
            }
            else -> {
                safeStartForeground(NOTIFICATION_ID, notification("Agent 执行中…"))
                if (!collecting) {
                    collecting = true
                    serviceScope.launch {
                        harnessLoop.running.collectLatest { running ->
                            if (running) {
                                notify(harnessLoop.status.value ?: "思考中")
                            } else {
                                finishExecution()
                            }
                        }
                    }
                }
                return START_STICKY
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun notify(status: String) {
        safeNotify(NOTIFICATION_ID, notification(status, ongoing = true))
    }

    private fun finishExecution() {
        safeNotify(NOTIFICATION_ID, replyNotification())
        stopForegroundSafely(STOP_FOREGROUND_DETACH) // 保留完成后通知，不再在前台
        stopSelf()
    }

    private fun notification(status: String, ongoing: Boolean = true): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.logo)
            .setContentTitle("Agent 执行中")
            .setContentText(status)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .addAction(
                NotificationCompat.Action(
                    R.drawable.logo,
                    "停止",
                    PendingIntent.getService(
                        this,
                        2002,
                        Intent(this, AgentForegroundService::class.java).setAction(ACTION_STOP),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                ),
            )
            .build()

    /** 执行完成后的通知：带 RemoteInput 回复框，用户可继续交代新任务。 */
    private fun replyNotification(): Notification {
        // Android 12+（API 31）要求带 RemoteInput 的 action 的 PendingIntent 必须为 mutable，
        // 否则 NotificationManager 会抛 IllegalArgumentException（见崩溃线程）。
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val replyPending = PendingIntent.getBroadcast(
            this,
            2003,
            Intent(this, AgentReplyReceiver::class.java),
            flags,
        )
        val remoteInput = RemoteInput.Builder(KEY_REPLY).setLabel("继续执行").build()
        val replyAction = NotificationCompat.Action.Builder(
            R.drawable.logo,
            "回复",
            replyPending,
        ).addRemoteInput(remoteInput).build()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.logo)
            .setContentTitle("Agent 已完成")
            .setContentText("点下方回复，告诉 Agent 下一步做什么")
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .addAction(replyAction)
            .build()
    }

    // ---- 通知发布异常隔离：任何通知操作的失败都不应拖垮主线程 ----

    private fun safeStartForeground(id: Int, notification: Notification) {
        runCatching { startForeground(id, notification) }
            .onFailure { Log.w(TAG, "startForeground 失败", it) }
    }

    private fun stopForegroundSafely(flag: Int) {
        runCatching { stopForeground(flag) }
            .onFailure { Log.w(TAG, "stopForeground 失败", it) }
    }

    private fun safeNotify(id: Int, notification: Notification) {
        runCatching {
            getSystemService(NotificationManager::class.java).notify(id, notification)
        }.onFailure { Log.w(TAG, "发布通知失败", it) }
    }

    companion object {
        const val ACTION_START = "top.wkbin.taixu.action.AGENT_START"
        const val ACTION_STOP = "top.wkbin.taixu.action.AGENT_STOP"
        const val KEY_REPLY = "agent_reply"
        private const val CHANNEL_ID = "agent-execution"
        private const val NOTIFICATION_ID = 2001
        private const val TAG = "AgentForegroundService"

        fun start(context: Context) {
            val intent = Intent(context, AgentForegroundService::class.java).setAction(ACTION_START)
            context.startForegroundService(intent)
        }

        fun startFromReply(context: Context) {
            start(context)
        }
    }
}
