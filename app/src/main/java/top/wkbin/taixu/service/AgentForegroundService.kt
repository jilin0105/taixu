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
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import top.wkbin.taixu.R
import top.wkbin.taixu.core.database.HarnessSessionRepository
import top.wkbin.taixu.core.model.SessionRunState
import top.wkbin.taixu.harness.HarnessLoop
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.time.Duration.Companion.milliseconds

/**
 * Agent 后台执行前台服务：
 * 当任意一个或多个 Agent 正在运行时启动保活服务。
 * 为每个并行运行的 Agent 会话分发专属的系统通知（标题含会话名与当前执行动作），
 * 支持独立点击【停止】以及执行完毕后的【回复】续跑。
 */
@AndroidEntryPoint
class AgentForegroundService : Service() {

    @Inject lateinit var harnessLoop: HarnessLoop
    @Inject lateinit var sessionDao: HarnessSessionRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var collecting = false
    private var processLock: PowerManager.WakeLock? = null
    private val activeNotifSessionIds = mutableSetOf<String>()
    /** 记录每个会话开始运行的时间戳，用于通知中显示已运行时长。 */
    private val sessionStartTimes = mutableMapOf<String, Long>()
    /** 定时刷新通知的 Job，运行期间每 30 秒更新一次，降低被系统判定为闲置服务的概率。 */
    private var notificationRefreshJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        runCatching {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.taixu_agent_notification_channel),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "用于展示 AI 深度思考与任务进度的灵动岛/原子胶囊"
                enableVibration(false)
                setSound(null, null)
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(channel)
        }.onFailure { Log.w(TAG, "创建通知渠道失败", it) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val targetSessionId = intent?.getStringExtra(EXTRA_SESSION_ID)
        when (intent?.action) {
            ACTION_STOP -> {
                runCatching {
                    if (!targetSessionId.isNullOrBlank()) {
                        harnessLoop.cancel(targetSessionId)
                    } else {
                        harnessLoop.cancel()
                    }
                }.onFailure { Log.w(TAG, "取消 Agent 失败", it) }
                return START_NOT_STICKY
            }
            else -> {
                safeStartForeground(PRIMARY_NOTIFICATION_ID, placeholderNotification(getString(R.string.taixu_agent_ready)))
                acquireProcessLock()
                if (!collecting) {
                    collecting = true
                    serviceScope.launch {
                        combine(
                            harnessLoop.sessionRunStates,
                            harnessLoop.sessionStatuses,
                        ) { runStates, statuses ->
                            runStates to statuses
                        }.collectLatest { (runStates, statuses) ->
                            val runningEntries = runStates.filter { it.value == SessionRunState.RUNNING }
                            if (runningEntries.isNotEmpty()) {
                                acquireProcessLock()
                                val now = System.currentTimeMillis()
                                runningEntries.forEach { (sessionId, _) ->
                                    activeNotifSessionIds.add(sessionId)
                                    sessionStartTimes.putIfAbsent(sessionId, now)
                                    val notifId = sessionNotificationId(sessionId)
                                    val sessionTitle = sessionDao.findById(sessionId)?.title ?: getString(R.string.taixu_agent_default_title)
                                    val statusText = statuses[sessionId]?.takeIf { it.isNotBlank() } ?: getString(R.string.taixu_agent_thinking)
                                    latestSessionStatuses[sessionId] = statusText
                                    val startTime = sessionStartTimes[sessionId] ?: now
                                    val elapsedSeconds = (now - startTime) / 1000L
                                    val notif = sessionNotification(sessionId, sessionTitle, statusText, elapsedSeconds)
                                    safeNotify(notifId, notif)
                                }
                                startNotificationRefresh()
                            } else {
                                stopNotificationRefresh()
                                val previouslyRunning = activeNotifSessionIds.toList()
                                activeNotifSessionIds.clear()
                                sessionStartTimes.clear()
                                previouslyRunning.forEach { sessionId ->
                                    val notifId = sessionNotificationId(sessionId)
                                    val sessionTitle = sessionDao.findById(sessionId)?.title ?: getString(R.string.taixu_agent_default_title)
                                    safeNotify(notifId, completedNotification(sessionId, sessionTitle))
                                }
                                releaseProcessLock()
                                stopForegroundSafely(STOP_FOREGROUND_DETACH)
                                stopSelf()
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
        stopNotificationRefresh()
        releaseProcessLock()
        serviceScope.cancel()
        super.onDestroy()
    }

    private val latestSessionStatuses = mutableMapOf<String, String>()

    private fun startNotificationRefresh() {
        if (notificationRefreshJob?.isActive == true) return
        notificationRefreshJob = serviceScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(NOTIFICATION_REFRESH_INTERVAL_MS.milliseconds)
                val snapshot = activeNotifSessionIds.toList()
                if (snapshot.isEmpty()) break
                val now = System.currentTimeMillis()
                snapshot.forEach { sessionId ->
                    val startTime = sessionStartTimes[sessionId] ?: continue
                    val elapsedSeconds = (now - startTime) / 1000L
                    val notifId = sessionNotificationId(sessionId)
                    val sessionTitle = runCatching { sessionDao.findById(sessionId)?.title }
                        .getOrNull() ?: getString(R.string.taixu_agent_default_title)
                    val currentStatus = latestSessionStatuses[sessionId] ?: getString(R.string.taixu_agent_thinking)
                    val notif = sessionNotification(
                        sessionId = sessionId,
                        title = sessionTitle,
                        status = currentStatus,
                        elapsedSeconds = elapsedSeconds,
                    )
                    safeNotify(notifId, notif)
                }
            }
        }
    }

    private fun stopNotificationRefresh() {
        notificationRefreshJob?.cancel()
        notificationRefreshJob = null
        latestSessionStatuses.clear()
    }

    private fun acquireProcessLock() {
        if (processLock?.isHeld == true) return
        runCatching {
            val powerManager = getSystemService(PowerManager::class.java)
            processLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
                .also { it.acquire(LOCK_TIMEOUT_MS) }
            Log.i(TAG, "Acquired partial wake lock for agent execution")
        }.onFailure { Log.w(TAG, "获取进程锁失败", it) }
    }

    private fun releaseProcessLock() {
        val lock = processLock ?: return
        runCatching { if (lock.isHeld) lock.release() }
            .onFailure { Log.w(TAG, "释放进程锁失败", it) }
        processLock = null
    }

    private fun sessionNotificationId(sessionId: String): Int {
        val primary = activeNotifSessionIds.firstOrNull()
        return if (primary == null || primary == sessionId) {
            PRIMARY_NOTIFICATION_ID
        } else {
            PRIMARY_NOTIFICATION_ID + (sessionId.hashCode().absoluteValue % 9000) + 1
        }
    }

    private fun placeholderNotification(status: String): Notification {
        val stopPending = PendingIntent.getService(
            this,
            PRIMARY_NOTIFICATION_ID,
            Intent(this, AgentForegroundService::class.java)
                .setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return LiveCapsuleHelper.buildRunningCapsuleNotification(
            context = this,
            channelId = CHANNEL_ID,
            sessionId = "taixu_primary_session",
            sessionTitle = getString(R.string.taixu_agent_default_title),
            rawStatus = status,
            elapsedSeconds = 0L,
            stopPendingIntent = stopPending,
        )
    }

    private fun sessionNotification(
        sessionId: String,
        title: String,
        status: String,
        elapsedSeconds: Long = 0L,
    ): Notification {
        val stopPending = PendingIntent.getService(
            this,
            sessionNotificationId(sessionId),
            Intent(this, AgentForegroundService::class.java)
                .setAction(ACTION_STOP)
                .putExtra(EXTRA_SESSION_ID, sessionId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return LiveCapsuleHelper.buildRunningCapsuleNotification(
            context = this,
            channelId = CHANNEL_ID,
            sessionId = sessionId,
            sessionTitle = title,
            rawStatus = status,
            elapsedSeconds = elapsedSeconds,
            stopPendingIntent = stopPending,
        )
    }

    private fun completedNotification(sessionId: String, title: String): Notification {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val replyPending = PendingIntent.getBroadcast(
            this,
            sessionNotificationId(sessionId),
            Intent(this, AgentReplyReceiver::class.java)
                .putExtra(EXTRA_SESSION_ID, sessionId),
            flags,
        )
        val remoteInput = RemoteInput.Builder(KEY_REPLY).setLabel(getString(R.string.taixu_notification_reply_to, title)).build()
        val replyAction = NotificationCompat.Action.Builder(
            R.drawable.taixu_logo,
            getString(R.string.taixu_notification_reply),
            replyPending,
        ).addRemoteInput(remoteInput).build()

        return LiveCapsuleHelper.buildCompletedCapsuleNotification(
            context = this,
            channelId = CHANNEL_ID,
            sessionId = sessionId,
            sessionTitle = title,
            replyAction = replyAction,
        )
    }

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
        const val EXTRA_SESSION_ID = "extra_session_id"
        const val KEY_REPLY = "agent_reply"
        private const val CHANNEL_ID = "taixu-agent-capsule-v4"
        private const val PRIMARY_NOTIFICATION_ID = 2001
        private const val TAG = "AgentForegroundService"
        private const val WAKE_LOCK_TAG = "taixu:agent-execution"
        private const val LOCK_TIMEOUT_MS = 4 * 60 * 60 * 1000L
        /** 灵动岛 / 实时胶囊定时刷新间隔：2 秒。运行期间高频平滑更新状态与运行时长。 */
        private const val NOTIFICATION_REFRESH_INTERVAL_MS = 2_000L

        fun start(context: Context, sessionId: String? = null) {
            val intent = Intent(context, AgentForegroundService::class.java)
                .setAction(ACTION_START)
            sessionId?.let { intent.putExtra(EXTRA_SESSION_ID, it) }
            context.startForegroundService(intent)
        }

        fun startFromReply(context: Context, sessionId: String? = null) {
            start(context, sessionId)
        }
    }
}
