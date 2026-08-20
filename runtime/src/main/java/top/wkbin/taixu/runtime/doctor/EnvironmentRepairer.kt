package top.wkbin.taixu.runtime.doctor

import top.wkbin.taixu.core.model.RepairProgress
import top.wkbin.taixu.core.model.RuntimeState
import top.wkbin.taixu.runtime.LinuxRuntime
import top.wkbin.taixu.runtime.shell.CommandResult
import top.wkbin.taixu.runtime.shell.ShellCommand
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

@Singleton
class EnvironmentRepairer @Inject constructor(
    private val linuxRuntime: LinuxRuntime,
    private val environmentDoctor: EnvironmentDoctor,
) {
    fun repair(): Flow<RepairProgress> = flow {
        val totalSteps = 5
        val logs = mutableListOf<String>()

        fun addLog(message: String) {
            logs.add(message)
            if (logs.size > 200) {
                logs.removeAt(0)
            }
        }

        if (linuxRuntime.state.value !is RuntimeState.Ready) {
            emit(
                RepairProgress(
                    stepTitle = "Linux 沙箱未就绪",
                    stepIndex = 0,
                    totalSteps = totalSteps,
                    progress = 0.0f,
                    logs = listOf("错误: Linux 沙箱未初始化或处于异常状态，无法执行自愈修复"),
                    isCompleted = false,
                    isFailed = true,
                    errorMessage = "Linux 沙箱未就绪，请先初始化沙箱",
                ),
            )
            return@flow
        }

        try {
            // ==========================================
            // Step 1: 修复 DNS 与证书
            // ==========================================
            emit(
                RepairProgress(
                    stepTitle = "正在配置高可用 DNS 与 SSL 根证书...",
                    stepIndex = 1,
                    totalSteps = totalSteps,
                    progress = 0.15f,
                    logs = logs.toList(),
                ),
            )
            addLog("[Step 1/5] 写入公共 DNS (114.114.114.114, 223.5.5.5, 8.8.8.8)")
            val dnsCmd = "mkdir -p /etc && printf 'nameserver 114.114.114.114\\nnameserver 223.5.5.5\\nnameserver 8.8.8.8\\n' > /etc/resolv.conf"
            val dnsRes = executeCommand(dnsCmd, logs)
            if (!dnsRes.isSuccess) {
                addLog("警告: 写入 /etc/resolv.conf 失败: ${dnsRes.stderr}")
            }

            // ==========================================
            // Step 2: 清理残留锁并切换国内镜像加速源
            // ==========================================
            emit(
                RepairProgress(
                    stepTitle = "正在配置清华大学国内镜像加速源...",
                    stepIndex = 2,
                    totalSteps = totalSteps,
                    progress = 0.35f,
                    logs = logs.toList(),
                ),
            )
            addLog("[Step 2/5] 清理 dpkg/apt 事务锁并替换国内源")
            val mirrorScript = """
                rm -f /var/lib/apt/lists/lock /var/cache/apt/archives/lock /var/lib/dpkg/lock* 2>/dev/null || true
                if [ -f /etc/os-release ]; then
                    . /etc/os-release
                    if [ "${'$'}ID" = "ubuntu" ]; then
                        sed -i 's|http://ports.ubuntu.com/ubuntu-ports|https://mirrors.tuna.tsinghua.edu.cn/ubuntu-ports|g' /etc/apt/sources.list /etc/apt/sources.list.d/*.sources /etc/apt/sources.list.d/*.list 2>/dev/null || true
                        sed -i 's|http://archive.ubuntu.com/ubuntu|https://mirrors.tuna.tsinghua.edu.cn/ubuntu|g' /etc/apt/sources.list /etc/apt/sources.list.d/*.sources /etc/apt/sources.list.d/*.list 2>/dev/null || true
                        sed -i 's|http://security.ubuntu.com/ubuntu|https://mirrors.tuna.tsinghua.edu.cn/ubuntu|g' /etc/apt/sources.list /etc/apt/sources.list.d/*.sources /etc/apt/sources.list.d/*.list 2>/dev/null || true
                    elif [ "${'$'}ID" = "debian" ] || [ "${'$'}ID_LIKE" = "debian" ]; then
                        sed -i 's|http://deb.debian.org/debian|https://mirrors.tuna.tsinghua.edu.cn/debian|g' /etc/apt/sources.list /etc/apt/sources.list.d/*.sources /etc/apt/sources.list.d/*.list 2>/dev/null || true
                        sed -i 's|http://security.debian.org/debian-security|https://mirrors.tuna.tsinghua.edu.cn/debian-security|g' /etc/apt/sources.list /etc/apt/sources.list.d/*.sources /etc/apt/sources.list.d/*.list 2>/dev/null || true
                    fi
                fi
            """.trimIndent()
            executeCommand(mirrorScript, logs)

            // ==========================================
            // Step 3: 更新 APT 软件包索引
            // ==========================================
            emit(
                RepairProgress(
                    stepTitle = "正在更新 APT 软件包索引...",
                    stepIndex = 3,
                    totalSteps = totalSteps,
                    progress = 0.55f,
                    logs = logs.toList(),
                ),
            )
            addLog("[Step 3/5] 执行 apt-get update 刷新索引")
            val updateRes = executeCommand("DEBIAN_FRONTEND=noninteractive apt-get update -y", logs, timeoutMs = 120_000L)
            if (!updateRes.isSuccess) {
                addLog("提示: apt-get update 产生部分非致命提示")
            }

            // ==========================================
            // Step 4: 安装核心工具链 (curl, git, tar, xz-utils)
            // ==========================================
            emit(
                RepairProgress(
                    stepTitle = "正在安装核心工具链 (Git / Curl / Tar / XZ)...",
                    stepIndex = 4,
                    totalSteps = totalSteps,
                    progress = 0.75f,
                    logs = logs.toList(),
                ),
            )
            addLog("[Step 4/5] 安装 ca-certificates, curl, git, tar, xz-utils, procps")
            val installToolsCmd = "DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends ca-certificates curl git tar xz-utils procps"
            val installToolsRes = executeCommand(installToolsCmd, logs, timeoutMs = 180_000L)
            if (!installToolsRes.isSuccess) {
                addLog("警告: 基础工具链安装异常: ${installToolsRes.stderr.ifBlank { installToolsRes.stdout }}")
            }

            // ==========================================
            // Step 5: 配置并准备 Node.js 与国内包镜像
            // ==========================================
            emit(
                RepairProgress(
                    stepTitle = "正在就绪 Node.js 运行时与 NPM/PIP 国内镜像...",
                    stepIndex = 5,
                    totalSteps = totalSteps,
                    progress = 0.90f,
                    logs = logs.toList(),
                ),
            )
            addLog("[Step 5/5] 检查 Node.js 并配置 npm/pip 国内镜像")
            val setupNodeAndMirrors = """
                if ! which node >/dev/null 2>&1; then
                    DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends nodejs npm || true
                fi
                which npm >/dev/null 2>&1 && npm config set registry https://registry.npmmirror.com || true
                mkdir -p ${'$'}HOME/.pip
                printf '[global]\nindex-url = https://pypi.tuna.tsinghua.edu.cn/simple\n' > ${'$'}HOME/.pip/pip.conf 2>/dev/null || true
            """.trimIndent()
            executeCommand(setupNodeAndMirrors, logs, timeoutMs = 120_000L)

            // 触发重新体检
            addLog("自愈修复已全部执行完成，正在刷新环境体检报告...")
            environmentDoctor.check()

            emit(
                RepairProgress(
                    stepTitle = "环境自愈与加速配置完成！",
                    stepIndex = 5,
                    totalSteps = totalSteps,
                    progress = 1.0f,
                    logs = logs.toList(),
                    isCompleted = true,
                    isFailed = false,
                ),
            )
        } catch (cancellation: CancellationException) {
            addLog("修复流程被用户取消")
            emit(
                RepairProgress(
                    stepTitle = "修复已取消",
                    stepIndex = 0,
                    totalSteps = totalSteps,
                    progress = 0.0f,
                    logs = logs.toList(),
                    isCompleted = false,
                    isFailed = true,
                    errorMessage = "修复任务已被手动取消",
                ),
            )
            throw cancellation
        } catch (throwable: Throwable) {
            addLog("修复异常: ${throwable.message}")
            emit(
                RepairProgress(
                    stepTitle = "自愈修复失败",
                    stepIndex = 0,
                    totalSteps = totalSteps,
                    progress = 0.0f,
                    logs = logs.toList(),
                    isCompleted = false,
                    isFailed = true,
                    errorMessage = throwable.message ?: "环境自愈修复发生未知异常",
                ),
            )
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun executeCommand(
        command: String,
        logs: MutableList<String>,
        timeoutMs: Long = 60_000L,
    ): CommandResult {
        val result = runCatching {
            linuxRuntime.execute(ShellCommand(commandLine = command, timeoutMs = timeoutMs))
        }.getOrElse {
            CommandResult(
                exitCode = 1,
                stdout = "",
                stderr = it.message ?: "执行命令出错",
                durationMs = 0L,
            )
        }

        result.stdout.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .take(15)
            .forEach { logs.add(it) }

        if (!result.isSuccess) {
            result.stderr.lineSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .take(10)
                .forEach { logs.add("ERR: $it") }
        }

        return result
    }
}
