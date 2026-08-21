package top.wkbin.taixu

import android.app.Application
import top.wkbin.taixu.core.common.logging.CrashReporter
import top.wkbin.taixu.harness.HarnessLoop
import top.wkbin.taixu.core.datastore.SettingsDataStore
import top.wkbin.taixu.service.AgentForegroundService
import dagger.hilt.android.HiltAndroidApp
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
    @Inject lateinit var harnessLoop: HarnessLoop
    @Inject lateinit var settingsDataStore: SettingsDataStore

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        crashReporter.install()
        // Agent 开始执行时拉起前台服务，保证后台存活 + 通知进度；结束后由服务发带回复框的通知。
        appScope.launch {
            harnessLoop.running.collectLatest { running ->
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
