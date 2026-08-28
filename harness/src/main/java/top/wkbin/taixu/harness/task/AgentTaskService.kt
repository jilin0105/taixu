package top.wkbin.taixu.harness.task

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import top.wkbin.taixu.core.common.logging.AppLogger
import javax.inject.Inject

@AndroidEntryPoint
class AgentTaskService : Service() {

    @Inject lateinit var logger: AppLogger
    @Inject lateinit var stateMachine: AgentStateMachine

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("TaiXu Agent is running"))
        logger.i("[AgentTaskService] Service created and foreground started.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            ACTION_START -> {
                val taskId = intent.getStringExtra(EXTRA_TASK_ID)
                if (taskId != null) {
                    stateMachine.startTask(taskId, serviceScope)
                }
            }
            ACTION_STOP -> {
                val taskId = intent.getStringExtra(EXTRA_TASK_ID)
                if (taskId != null) {
                    stateMachine.cancelTask(taskId, serviceScope)
                } else {
                    // No specific task: cancel all and shut down
                    stateMachine.cancelAll(serviceScope)
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        logger.i("[AgentTaskService] Service destroyed.")
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Agent Tasks",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TaiXu Agent")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_START = "top.wkbin.taixu.action.START_AGENT_TASK"
        const val ACTION_STOP = "top.wkbin.taixu.action.STOP_AGENT_TASK"
        const val EXTRA_TASK_ID = "extra_task_id"
        private const val CHANNEL_ID = "taixu_agent_tasks"
        private const val NOTIFICATION_ID = 8801
    }
}

