package top.wkbin.taixu.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import top.wkbin.taixu.MainActivity
import top.wkbin.taixu.R

/**
 * 跨厂商灵动岛 / 实时胶囊 / 流体云 / 原子岛 / 实况窗 核心适配器
 *
 * 深度集成各大厂商前台实时通知协议：
 * - 小米澎湃 OS (Xiaomi HyperOS 1/2 & MIUI 14+)：焦点通知 (Focus Notification / 实时胶囊)
 * - OPPO / 一加 / Realme (ColorOS / OxygenOS 14+)：流体云 (Fluid Cloud)
 * - vivo / iQOO (OriginOS 4/5)：原子通知 / 原子岛 (Atomic Island)
 * - 荣耀 (MagicOS 8.0+)：灵动胶囊 (Smart Capsule)
 * - 华为 (HarmonyOS 4.0+)：实况窗 (Live View)
 * - 原生 Android / Samsung One UI：Ongoing Live Progress Activity
 */
object LiveCapsuleHelper {

    /**
     * 构建支持各大厂商灵动岛胶囊的前台服务进行中通知
     */
    fun buildRunningCapsuleNotification(
        context: Context,
        channelId: String,
        sessionId: String,
        sessionTitle: String,
        rawStatus: String,
        elapsedSeconds: Long,
        stopPendingIntent: PendingIntent,
    ): Notification {
        val formattedStatus = formatCapsuleStatus(rawStatus, elapsedSeconds)
        val shortTitle = "太墟 · $sessionTitle"
        val contentText = if (elapsedSeconds > 0) {
            "${formatDuration(elapsedSeconds)} · $rawStatus"
        } else {
            rawStatus
        }

        val clickIntent = PendingIntent.getActivity(
            context,
            sessionId.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(AgentForegroundService.EXTRA_SESSION_ID, sessionId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val bigTextStyle = NotificationCompat.BigTextStyle()
            .setBigContentTitle(shortTitle)
            .bigText(contentText)
            .setSummaryText(formattedStatus)

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.taixu_logo)
            .setContentTitle(shortTitle)
            .setContentText(contentText)
            .setSubText(formattedStatus)
            .setStyle(bigTextStyle)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setContentIntent(clickIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setProgress(0, 0, true) // 不确定进度条（触发胶囊呼吸/旋转环动效）
            .addAction(
                NotificationCompat.Action(
                    R.drawable.taixu_logo,
                    context.getString(R.string.taixu_notification_stop),
                    stopPendingIntent,
                ),
            )

        injectManufacturerCapsuleExtras(
            builder = builder,
            title = shortTitle,
            statusText = formattedStatus,
            detailedContent = contentText,
            isOngoing = true,
        )

        return builder.build()
    }

    /**
     * 构建任务完成时的收起/提示通知
     */
    fun buildCompletedCapsuleNotification(
        context: Context,
        channelId: String,
        sessionId: String,
        sessionTitle: String,
        replyAction: NotificationCompat.Action?,
    ): Notification {
        val completedTitle = context.getString(R.string.taixu_agent_task_completed, sessionTitle)
        val completedText = context.getString(R.string.taixu_agent_next_task_hint)

        val clickIntent = PendingIntent.getActivity(
            context,
            sessionId.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(AgentForegroundService.EXTRA_SESSION_ID, sessionId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val bigTextStyle = NotificationCompat.BigTextStyle()
            .setBigContentTitle(completedTitle)
            .bigText(completedText)
            .setSummaryText("✅ 任务完成")

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.taixu_logo)
            .setContentTitle(completedTitle)
            .setContentText(completedText)
            .setStyle(bigTextStyle)
            .setAutoCancel(true)
            .setOngoing(false)
            .setContentIntent(clickIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        if (replyAction != null) {
            builder.addAction(replyAction)
        }

        injectManufacturerCapsuleExtras(
            builder = builder,
            title = completedTitle,
            statusText = "✅ 完成",
            detailedContent = completedText,
            isOngoing = false,
        )

        return builder.build()
    }

    /**
     * 格式化灵动岛状态摘要（紧凑短文本）
     */
    fun formatCapsuleStatus(status: String, elapsedSeconds: Long? = null): String {
        val trimmed = status.trim()
        val base = when {
            trimmed.contains("思考", ignoreCase = true) || trimmed.contains("think", ignoreCase = true) -> "🧠 深度思考"
            trimmed.contains("bash", ignoreCase = true) || trimmed.contains("exec", ignoreCase = true) || trimmed.contains("命令", ignoreCase = true) -> "⚡ 执行命令"
            trimmed.contains("write", ignoreCase = true) || trimmed.contains("file", ignoreCase = true) || trimmed.contains("写入", ignoreCase = true) -> "📁 写入产物"
            trimmed.contains("mcp", ignoreCase = true) || trimmed.contains("tool", ignoreCase = true) || trimmed.contains("工具", ignoreCase = true) -> "🔌 调用工具"
            trimmed.isNotBlank() -> trimmed.take(12)
            else -> "🧠 运行中"
        }
        return if (elapsedSeconds != null && elapsedSeconds > 0) {
            "$base (${elapsedSeconds}s)"
        } else {
            base
        }
    }

    /**
     * 格式化运行时间（秒/分/小时）
     */
    fun formatDuration(seconds: Long): String = when {
        seconds < 60 -> "${seconds}秒"
        seconds < 3600 -> "${seconds / 60}分${seconds % 60}秒"
        else -> "${seconds / 3600}小时${(seconds % 3600) / 60}分"
    }

    /**
     * 为 NotificationCompat.Builder 注入各大厂商专属的灵动岛/流体云/原子岛/实况窗元数据
     */
    private fun injectManufacturerCapsuleExtras(
        builder: NotificationCompat.Builder,
        title: String,
        statusText: String,
        detailedContent: String,
        isOngoing: Boolean,
    ) {
        val extras = Bundle()

        // 1. 小米澎湃 OS (Xiaomi HyperOS 1/2 & MIUI) 焦点通知 / 实时胶囊
        extras.putBoolean("miui.focus.notification", true)
        extras.putString("miui.focus.title", title)
        extras.putString("miui.focus.content", statusText)
        extras.putString("miui.focus.subcontent", detailedContent)
        extras.putInt("miui.focus.type", if (isOngoing) 1 else 0)
        extras.putBoolean("miui.focus.show_when", true)
        extras.putLong("miui.focus.time", System.currentTimeMillis())

        // 2. OPPO / 一加 / Realme (ColorOS / OxygenOS 14+) 流体云 (Fluid Cloud)
        extras.putBoolean("android.substName", true)
        extras.putString("oppo.notification.title", title)
        extras.putString("oppo.notification.content", statusText)
        extras.putBoolean("oppo.notification.capsule", isOngoing)
        extras.putBoolean("coloros.notification.capsule", isOngoing)

        // 3. vivo / iQOO (OriginOS 3/4/5) 原子通知 / 原子岛 (Atomic Island / 实时胶囊)
        extras.putBoolean("vivo.atomic.notification", true)
        extras.putBoolean("vivo.notification.capsule", isOngoing)
        extras.putBoolean("vivo.as.capsule", isOngoing)
        extras.putString("vivo.notification.type", "status_bar_island")
        extras.putString("vivo.summary.text", statusText)
        extras.putString("vivo.atomic.title", title)
        extras.putString("vivo.atomic.content", statusText)
        extras.putString("vivo.atomic.status", statusText)
        extras.putBoolean("vivo.atomic.ongoing", isOngoing)
        extras.putBoolean("vivo.live.activity", isOngoing)
        extras.putBoolean("vivo.statusbar.capsule", isOngoing)
        extras.putBoolean("vivo.island.enable", isOngoing)

        // 4. 荣耀 MagicOS (灵动胶囊) & 华为 HarmonyOS (实况窗)
        extras.putBoolean("honor.smart.capsule", isOngoing)
        extras.putBoolean("huawei.live.view", isOngoing)
        extras.putString("hw_live_view_title", title)
        extras.putString("hw_live_view_content", statusText)

        builder.addExtras(extras)
    }
}
