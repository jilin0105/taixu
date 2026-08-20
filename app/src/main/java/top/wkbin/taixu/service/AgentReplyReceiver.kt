package top.wkbin.taixu.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import top.wkbin.taixu.harness.HarnessLoop
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** 处理通知栏【回复】输入框：把用户的下一条指令交给 Agent 继续执行。 */
@AndroidEntryPoint
class AgentReplyReceiver : BroadcastReceiver() {

    @Inject lateinit var harnessLoop: HarnessLoop

    override fun onReceive(context: Context, intent: Intent) {
        val reply = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(AgentForegroundService.KEY_REPLY)
            ?.toString()
            ?.trim()
        if (reply.isNullOrBlank()) return
        // 先拉起前台服务（保持后台存活），再投递指令给 Agent。
        AgentForegroundService.startFromReply(context)
        harnessLoop.send(reply)
    }
}
