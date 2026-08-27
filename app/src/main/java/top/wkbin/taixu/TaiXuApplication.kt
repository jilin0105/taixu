package top.wkbin.taixu

import android.app.Application
import top.wkbin.taixu.core.common.logging.CrashReporter
import top.wkbin.taixu.harness.HarnessLoop
import top.wkbin.taixu.core.datastore.SettingsDataStore
import top.wkbin.taixu.core.database.AgentSkillRepository
import top.wkbin.taixu.core.database.McpServerRepository
import top.wkbin.taixu.service.AgentForegroundService
import top.wkbin.taixu.runtime.privilege.PrivilegeManager
import dagger.hilt.android.HiltAndroidApp
import dagger.Lazy
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@HiltAndroidApp
class TaiXuApplication : Application() {
    @Inject lateinit var crashReporter: CrashReporter

    // 启动性能：HarnessLoop / Room 仓储的构造图很重（DAO、DataStore、Agent 引擎全家桶），
    // eager 注入会拖慢第一帧。改为 dagger.Lazy，把实际构建推迟到首个 IO 协程内。
    @Inject lateinit var harnessLoopLazy: Lazy<HarnessLoop>
    @Inject lateinit var settingsDataStore: SettingsDataStore
    @Inject lateinit var agentSkillRepositoryLazy: Lazy<AgentSkillRepository>
    @Inject lateinit var mcpServerRepositoryLazy: Lazy<McpServerRepository>
    @Inject lateinit var privilegeManager: PrivilegeManager

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        crashReporter.install()
        appScope.launch(Dispatchers.IO) {
            // 上一次未捕获崩溃会先落在应用私有目录；下次启动后复制到公共下载目录，
            // 方便测试用户直接从 Download/TaiXu/crash-reports 取出并反馈。
            runCatching { crashReporter.exportPendingReports() }
            runCatching { privilegeManager.reconcilePersistedMode() }
            agentSkillRepositoryLazy.get().ensureInitialized()
            mcpServerRepositoryLazy.get().ensureInitialized()
            settingsDataStore.incrementLaunchCount()
            // 进程被杀后重启时，批量恢复所有被中断的 Agent 会话：
            // 将"运行中"假死状态还原为 IDLE / WAITING_APPROVAL，并提示用户发送消息即可继续，
            // 避免用户切回应用时看到空白对话或永久"运行中"状态。
            val harnessLoop = harnessLoopLazy.get()
            runCatching {
                val recovered = harnessLoop.recoverAllInterruptedSessions()
                if (recovered > 0) {
                    android.util.Log.i("TaiXuApp", "已恢复 $recovered 个被中断的 Agent 会话")
                }
            }.onFailure {
                android.util.Log.w("TaiXuApp", "恢复中断会话失败", it)
            }
        }
        // Agent 开始执行时拉起前台服务，保证后台存活 + 通知进度；结束后由服务发带回复框的通知。
        appScope.launch {
            harnessLoopLazy.get().running.collectLatest { running ->
                if (running) {
                    runCatching { AgentForegroundService.start(this@TaiXuApplication) }
                }
            }
        }
    }

    override fun onTerminate() {
        appScope.cancel()
        super.onTerminate()
    }
}
