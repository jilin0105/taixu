package top.wkbin.taixu.harness.hook

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import top.wkbin.taixu.core.common.logging.AppLogger
import top.wkbin.taixu.harness.task.AgentStateMachine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * GitHub 仓库轮询钩子：每 15 分钟拉取带 codex-run 标签的 issue，
 * 将其转化为 AgentTask 并交由 AgentStateMachine 执行。
 */
@Singleton
class GithubRepositoryHook @Inject constructor(
    private val stateMachine: AgentStateMachine,
    private val logger: AppLogger,
) {
    fun startPolling(repoOwner: String, repoName: String, scope: CoroutineScope) {
        scope.launch {
            logger.i("[GithubRepositoryHook] Starting to poll $repoOwner/$repoName")
            while (isActive) {
                runCatching { pollOnce(repoOwner, repoName, scope) }
                    .onFailure { logger.e("[GithubRepositoryHook] Poll failed", it) }
                delay(15 * 60 * 1000L) // poll interval: 15 min
            }
        }
    }

    private fun pollOnce(repoOwner: String, repoName: String, scope: CoroutineScope) {
        // TODO: use core:network OkHttp to call:
        //   GET https://api.github.com/repos/{owner}/{repo}/issues?labels=codex-run&state=open
        // Parse JSON response, for each issue:
        //   1. Create AgentTaskEntity via DAO with status = IDLE
        //   2. Call stateMachine.startTask(taskId, scope)
        logger.i("[GithubRepositoryHook] Poll for $repoOwner/$repoName — no new issues (stub)")
    }
}
