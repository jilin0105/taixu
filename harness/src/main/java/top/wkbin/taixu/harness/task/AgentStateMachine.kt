package top.wkbin.taixu.harness.task

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
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
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val task = taskDao.getTaskById(taskId)
                    ?: error("Task $taskId does not exist")
                taskDao.updateTaskStatus(taskId, TaskStatus.RUNNING, null, System.currentTimeMillis())
                logger.i("[AgentStateMachine] Task $taskId started.")

                // This legacy background-task surface is not connected to HarnessLoop. Failing
                // explicitly prevents an accepted task from being reported as completed without
                // ever executing its description.
                error("Background task execution is not configured: ${task.title}")
            } catch (cancelled: CancellationException) {
                withContext(NonCancellable) {
                    taskDao.updateTaskStatus(taskId, TaskStatus.SUSPENDED, null, System.currentTimeMillis())
                }
                throw cancelled
            } catch (e: Exception) {
                // Use NonCancellable to ensure DB update even if the coroutine is cancelled
                withContext(NonCancellable) {
                    taskDao.updateTaskStatus(taskId, TaskStatus.ERROR, e.message, System.currentTimeMillis())
                }
                logger.e("[AgentStateMachine] Task $taskId failed", e)
            } finally {
                activeJobs.remove(taskId, currentCoroutineContext()[Job])
            }
        }
        if (activeJobs.putIfAbsent(taskId, job) == null) {
            job.start()
        } else {
            job.cancel()
        }
    }

    fun cancelTask(taskId: String, scope: CoroutineScope) {
        val job = activeJobs[taskId]
        if (job != null) {
            job.cancel()
            return
        }
        scope.launch {
            withContext(NonCancellable) {
                taskDao.updateTaskStatus(taskId, TaskStatus.SUSPENDED, null, System.currentTimeMillis())
            }
        }
    }

    fun cancelAll(scope: CoroutineScope) {
        val ids = activeJobs.keys.toList()
        ids.forEach { taskId -> cancelTask(taskId, scope) }
    }

    object TaskStatus {
        const val IDLE = "IDLE"
        const val RUNNING = "RUNNING"
        const val SUSPENDED = "SUSPENDED"
        const val ERROR = "ERROR"
        const val COMPLETED = "COMPLETED"
    }
}
