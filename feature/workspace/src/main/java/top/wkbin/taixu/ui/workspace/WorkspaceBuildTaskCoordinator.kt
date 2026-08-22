package top.wkbin.taixu.ui.workspace

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.wkbin.taixu.core.tools.ToolNotificationNotifier
import top.wkbin.taixu.runtime.WorkspaceProject
import top.wkbin.taixu.runtime.BackgroundTaskRegistry
import top.wkbin.taixu.runtime.build.BuildRunProgress
import top.wkbin.taixu.runtime.build.WorkspaceBuildRunner

data class WorkspaceBuildTaskState(
    val project: WorkspaceProject,
    val progress: BuildRunProgress,
)

/** Keeps a workspace build alive while the workspace destination is recreated. */
@Singleton
class WorkspaceBuildTaskCoordinator @Inject constructor(
    private val runner: WorkspaceBuildRunner,
    private val notifier: ToolNotificationNotifier,
    private val backgroundTaskRegistry: BackgroundTaskRegistry,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<WorkspaceBuildTaskState?>(null)
    val state: StateFlow<WorkspaceBuildTaskState?> = _state.asStateFlow()
    private var job: Job? = null

    @Synchronized
    fun start(project: WorkspaceProject): Boolean {
        if (job?.isActive == true || _state.value?.progress?.isRunning == true) return false
        val initial = BuildRunProgress(step = "准备编译环境...")
        _state.value = WorkspaceBuildTaskState(project, initial)
        backgroundTaskRegistry.start(BUILD_TASK_ID)
        notifier.showBuildProgress(project.name, initial.step)
        job = scope.launch {
            try {
                runner.runProject(project).collect { progress ->
                    _state.value = WorkspaceBuildTaskState(project, progress)
                    if (progress.isRunning) {
                        notifier.showBuildProgress(project.name, progress.step)
                    } else {
                        if (progress.isSuccess == true) {
                            notifier.showBuildSuccess(project.name, progress.apkPath)
                        } else {
                            notifier.showBuildFailed(project.name, progress.message ?: "未知错误")
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val failed = BuildRunProgress(
                    step = "构建异常中断",
                    isRunning = false,
                    isSuccess = false,
                    message = error.message ?: "构建过程遇到异常",
                )
                _state.value = WorkspaceBuildTaskState(project, failed)
                notifier.showBuildFailed(project.name, failed.message ?: "未知异常")
            } finally {
                backgroundTaskRegistry.finish(BUILD_TASK_ID)
                job = null
            }
        }
        return true
    }

    @Synchronized
    fun cancel() {
        val current = _state.value ?: return
        job?.cancel()
        job = null
        val stopped = BuildRunProgress(
            step = "已手动停止编译",
            isRunning = false,
            isSuccess = false,
            message = "编译任务已被用户手动终止",
            logOutput = current.progress.logOutput,
        )
        _state.value = current.copy(progress = stopped)
        notifier.showBuildFailed(current.project.name, stopped.message ?: "编译已停止")
    }

    fun dismiss() {
        if (_state.value?.progress?.isRunning != true) _state.value = null
    }

    fun launchPackageInstaller(apkPath: String) {
        runner.launchPackageInstaller(java.io.File(apkPath))
    }

    private companion object {
        const val BUILD_TASK_ID = "workspace-build"
    }
}
