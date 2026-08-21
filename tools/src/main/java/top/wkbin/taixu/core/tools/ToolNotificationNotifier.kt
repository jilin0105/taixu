package top.wkbin.taixu.core.tools

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 插件与工具安装系统通知栏控制器
 * 实时同步并发安装进度、完成与失败状态到 Android 系统通知栏。
 */
@SuppressLint("MissingPermission")
@Singleton
class ToolNotificationNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val notificationManager = NotificationManagerCompat.from(context)
    private val channelId = "taixu_tool_install"

    init {
        createChannel()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            channelId,
            "插件与工具安装进度",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "展示太墟 PRoot 沙箱内 AI 工具与插件的安装与更新进度"
            setShowBadge(false)
        }
        val systemManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        systemManager?.createNotificationChannel(channel)
    }

    fun showProgress(toolId: String, toolName: String, message: String, progress: Float?) {
        val notificationId = toolNotificationId(toolId)
        val pendingIntent = getLaunchIntent()

        val progressPercent = ((progress ?: 0f) * 100).toInt().coerceIn(0, 100)
        val isIndeterminate = progress == null

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(context.applicationInfo.icon.takeIf { it != 0 } ?: android.R.drawable.stat_sys_download)
            .setContentTitle("正在安装 $toolName")
            .setContentText(message)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progressPercent, isIndeterminate)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        try {
            notificationManager.notify(notificationId, builder.build())
        } catch (_: SecurityException) {
            // Android 13+ 通知权限未授权时忽略
        }
    }

    fun showSuccess(toolId: String, toolName: String, version: String?) {
        val notificationId = toolNotificationId(toolId)
        val pendingIntent = getLaunchIntent()

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(context.applicationInfo.icon.takeIf { it != 0 } ?: android.R.drawable.stat_sys_download_done)
            .setContentTitle("$toolName 安装完成")
            .setContentText("版本 ${version ?: "已就绪"}，可在控制台或工具中心直接使用")
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        try {
            notificationManager.notify(notificationId, builder.build())
        } catch (_: SecurityException) {
        }
    }

    fun showFailed(toolId: String, toolName: String, error: String) {
        val notificationId = toolNotificationId(toolId)
        val pendingIntent = getLaunchIntent()

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(context.applicationInfo.icon.takeIf { it != 0 } ?: android.R.drawable.stat_notify_error)
            .setContentTitle("$toolName 安装失败")
            .setContentText(error.take(120))
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        try {
            notificationManager.notify(notificationId, builder.build())
        } catch (_: SecurityException) {
        }
    }

    fun cancel(toolId: String) {
        try {
            notificationManager.cancel(toolNotificationId(toolId))
        } catch (_: Exception) {
        }
    }

    private fun toolNotificationId(toolId: String): Int {
        return 20000 + (toolId.hashCode() and 0x7FFF)
    }

    private fun getLaunchIntent(): PendingIntent? {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
