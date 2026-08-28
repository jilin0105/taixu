package top.wkbin.taixu.harness.task

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.wkbin.taixu.core.common.logging.AppLogger
import top.wkbin.taixu.core.database.task.AgentTaskDao
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AgentStateMachine @Inject constructor(
    private val taskDao: AgentTaskDao,
    private val logger: AppLogger,
) {
    private val activeJobs = ConcurrentHashMap<String, Job>()

    fun startTask(taskId: String, scope: CoroutineScope) {
        if (activeJobs.containsKey(taskId)) return

        val job = scope.launch {
            try {
                taskDao.updateTaskStatus(taskId, TaskStatus.RUNNING, null, System.currentTimeMillis())
                logger.i("[AgentStateMachine] Task $taskId started.")

                // TODO: Wire HarnessLoop session dispatch here

                taskDao.updateTaskStatus(taskId, TaskStatus.COMPLETED, null, System.currentTimeMillis())
                logger.i("[AgentStateMachine] Task $taskId completed.")
            } catch (e: Exception) {
                // Use NonCancellable to ensure DB update even if the coroutine is cancelled
                withContext(NonCancellable) {
                    taskDao.updateTaskStatus(taskId, TaskStatus.ERROR, e.message, System.currentTimeMillis())
                }
                logger.e("[AgentStateMachine] Task $taskId failed", e)
            } finally {
                activeJobs.remove(taskId)
            }
        }
        activeJobs[taskId] = job
    }

    fun cancelTask(taskId: String, scope: CoroutineScope) {
        activeJobs[taskId]?.cancel()
        activeJobs.remove(taskId)
        // Write SUSPENDED status via NonCancellable so it persists after cancellation
        scope.launch {
            withContext(NonCancellable) {
                taskDao.updateTaskStatus(taskId, TaskStatus.SUSPENDED, null, System.currentTimeMillis())
            }
        }
    }

    fun cancelAll(scope: CoroutineScope) {
        val ids = activeJobs.keys.toList()
        ids.forEach { taskId ->
            activeJobs[taskId]?.cancel()
            activeJobs.remove(taskId)
            scope.launch {
                withContext(NonCancellable) {
                    taskDao.updateTaskStatus(taskId, TaskStatus.SUSPENDED, null, System.currentTimeMillis())
                }
            }
        }
    }

    object TaskStatus {
        const val IDLE = "IDLE"
        const val RUNNING = "RUNNING"
        const val SUSPENDED = "SUSPENDED"
        const val ERROR = "ERROR"
        const val COMPLETED = "COMPLETED"
    }
}
