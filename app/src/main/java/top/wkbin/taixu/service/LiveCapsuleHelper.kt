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
            sessionId = sessionId,
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
            sessionId = sessionId,
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
     * 为 NotificationCompat.Builder 注入各大厂商官方灵动岛/流体云/原子岛/实况窗规范的元数据与 JSON 数据包
     */
    private fun injectManufacturerCapsuleExtras(
        builder: NotificationCompat.Builder,
        sessionId: String,
        title: String,
        statusText: String,
        detailedContent: String,
        isOngoing: Boolean,
    ) {
        val extras = Bundle()

        // 1. 小米澎湃 OS (Xiaomi HyperOS 1/2 & MIUI) 焦点通知 / 超级岛官方规范 (miui.focus.param)
        runCatching {
            val hyperOsIsland = org.json.JSONObject().apply {
                put("islandProperty", 1)
                put("smallIslandArea", org.json.JSONObject().apply {
                    put("type", 1)
                    put("text", statusText)
                })
                put("bigIslandArea", org.json.JSONObject().apply {
                    put("imageTextInfoLeft", org.json.JSONObject().apply {
                        put("type", 1)
                        put("text", title)
                    })
                    put("imageTextInfoRight", org.json.JSONObject().apply {
                        put("type", 1)
                        put("text", statusText)
                    })
                })
            }
            val hyperOsParam = org.json.JSONObject().apply {
                put("param_v2", org.json.JSONObject().apply {
                    put("business", "countdown")
                    put("updatable", isOngoing)
                    put("orderId", sessionId)
                    put("param_island", hyperOsIsland)
                })
            }
            extras.putString("miui.focus.param", hyperOsParam.toString())
        }
        extras.putBoolean("miui.focus.notification", true)
        extras.putString("miui.focus.title", title)
        extras.putString("miui.focus.content", statusText)
        extras.putString("miui.focus.subcontent", detailedContent)
        extras.putInt("miui.focus.type", if (isOngoing) 1 else 0)
        extras.putBoolean("miui.focus.show_when", true)
        extras.putLong("miui.focus.time", System.currentTimeMillis())

        // 2. vivo / iQOO (OriginOS 3/4/5) 原子通知 / 原子岛官方规范 (notification.superx.* & vivo.atomic.param)
        runCatching {
            val operationCode = if (isOngoing) 0 else 2
            extras.putInt("notification.superx.operation", operationCode)
            extras.putString("notification.superx.scene", "countdown")
            extras.putString("notification.superx.template", "capsule")
            extras.putString("notification.superx.title", title)
            extras.putString("notification.superx.content", statusText)

            val liveMessage = org.json.JSONObject().apply {
                put("operation", operationCode)
                put("title", title)
                put("content", statusText)
                put("subTitle", "太墟 AI")
                put("capsule", org.json.JSONObject().apply {
                    put("text", statusText)
                    put("color", "#2C7FEB")
                })
            }
            extras.putString("notification.superx.liveMessage", liveMessage.toString())
            extras.putString("notification.superx.capsule", liveMessage.toString())

            val vivoParam = org.json.JSONObject().apply {
                put("operation", if (isOngoing) 0 else 1)
                put("scene", "countdown")
                put("templateType", 1)
                put("showNotify", true)
                put("title", title)
                put("content", statusText)
                put("capsuleData", org.json.JSONObject().apply {
                    put("bgColor", "#2C7FEB")
                    put("statusText", statusText)
                    put("isOngoing", isOngoing)
                })
            }
            extras.putString("vivo.atomic.param", vivoParam.toString())
        }
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

        // 3. OPPO / 一加 / Realme (ColorOS / OxygenOS 14+) 流体云 / 潘塔纳尔规范 (androidOppoIntelligentIntent)
        runCatching {
            val oppoIntent = org.json.JSONObject().apply {
                put("intent", "ai_agent_live")
                put("status", statusText)
                put("title", title)
                put("capsule", org.json.JSONObject().apply {
                    put("icon", "taixu_logo")
                    put("text", statusText)
                })
            }
            extras.putString("androidOppoIntelligentIntent", oppoIntent.toString())
            extras.putString("oppo.intelligent.intent", oppoIntent.toString())
        }
        extras.putBoolean("android.substName", true)
        extras.putString("oppo.notification.title", title)
        extras.putString("oppo.notification.content", statusText)
        extras.putBoolean("oppo.notification.capsule", isOngoing)
        extras.putBoolean("coloros.notification.capsule", isOngoing)

        // 4. 华为 (HarmonyOS / EMUI) 实况窗规范 (hw_live_view_data) & 荣耀 MagicOS (灵动胶囊)
        runCatching {
            val huaweiLiveView = org.json.JSONObject().apply {
                put("capsule", org.json.JSONObject().apply {
                    put("title", title)
                    put("status", statusText)
                    put("icon", "taixu_logo")
                })
                put("card", org.json.JSONObject().apply {
                    put("title", title)
                    put("content", detailedContent)
                })
            }
            extras.putString("hw_live_view_data", huaweiLiveView.toString())
        }
        extras.putBoolean("honor.smart.capsule", isOngoing)
        extras.putBoolean("huawei.live.view", isOngoing)
        extras.putString("hw_live_view_title", title)
        extras.putString("hw_live_view_content", statusText)

        builder.addExtras(extras)
    }
}
