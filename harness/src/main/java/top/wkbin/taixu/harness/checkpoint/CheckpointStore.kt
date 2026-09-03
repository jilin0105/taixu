package top.wkbin.taixu.harness.checkpoint

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 文件快照的按会话存储与重放规划。不触碰 git：
 * - 每个用户轮次 [beginTurn] 开启一个 checkpoint，仅记录该轮被 write/edit 触碰文件在触碰前的轮初内容；
 * - 同一轮同一路径去重（只保留第一次触碰的轮初内容）；
 * - [beginTurn] 切换时关闭上一轮 checkpoint，并保留最近 [MAX_KEPT] 轮；
 * - [planCodeRewind] 生成从目标轮起逐 path 取最早快照的恢复方案，供 RewindController 落盘。
 */
@Singleton
class CheckpointStore @Inject constructor() {

    private val sessions = ConcurrentHashMap<String, SessionState>()

    private class SessionState {
        val checkpoints = mutableListOf<Checkpoint>()
        var active: MutableMap<String, FileSnap>? = null
        var activeTurn = 0
        var activePrompt = ""
        var open = false
    }

    /** 开启一个新的用户轮次 checkpoint，并关闭上一轮（若有）。 */
    @Synchronized
    fun beginTurn(sessionId: String, prompt: String) {
        val state = sessions.getOrPut(sessionId) { SessionState() }
        closeTurn(state)
        state.activeTurn = state.checkpoints.size
        state.activePrompt = prompt.ifBlank { "（空白输入）" }
        state.active = LinkedHashMap()
        state.open = true
    }

    /** 记录某路径在该轮"触碰前"的内容；无活动轮或路径已记录时忽略。 */
    @Synchronized
    fun capture(sessionId: String, path: String, before: String?): Boolean {
        val state = sessions[sessionId] ?: return false
        val active = state.active ?: return false
        if (path in active) return false
        active[path] = FileSnap(path, before)
        return true
    }

    /** 强制关闭当前活动轮（无触碰则丢弃空轮）。 */
    @Synchronized
    fun endTurn(sessionId: String) {
        sessions[sessionId]?.let(::closeTurn)
    }

    /** 会话删除/重建时清理。 */
    @Synchronized
    fun dropSession(sessionId: String) {
        sessions.remove(sessionId)
    }

    @Synchronized
    fun checkpoints(sessionId: String): List<CheckpointMeta> =
        sessions[sessionId]?.checkpoints?.map {
            CheckpointMeta(it.turn, it.time, it.prompt, it.files.map { snap -> snap.path })
        } ?: emptyList()

    /**
     * 规划"代码回滚"：撤回到 [turn]，即撤销该轮及之后的所有写改动。
     * 对每路径取 [turn] 起最早的轮初内容；路径本身是 [turn] 前创建的、
     * 之后仅被改动，则该最早快照即 [turn] 的轮初内容。
     */
    @Synchronized
    fun planCodeRewind(sessionId: String, turn: Int): List<FileSnap> {
        val state = sessions[sessionId] ?: return emptyList()
        val open = state.active
        val totalTurns = state.checkpoints.size + if (open != null) 1 else 0
        if (turn < 0 || turn >= totalTurns) return emptyList()
        val merged = LinkedHashMap<String, FileSnap>()
        for (index in turn until state.checkpoints.size) {
            for (snap in state.checkpoints[index].files) {
                merged.putIfAbsent(snap.path, snap)
            }
        }
        // 当前（尚未关闭）的轮次也纳入回滚范围
        if (open != null && turn <= state.checkpoints.size) {
            for (snap in open.values) merged.putIfAbsent(snap.path, snap)
        }
        return merged.values.toList()
    }

    private fun closeTurn(state: SessionState) {
        val active = state.active ?: return
        if (active.isNotEmpty()) {
            state.checkpoints.add(
                Checkpoint(
                    turn = state.activeTurn,
                    time = System.currentTimeMillis(),
                    prompt = state.activePrompt,
                    files = active.values.toList(),
                ),
            )
            while (state.checkpoints.size > MAX_KEPT) state.checkpoints.removeAt(0)
        }
        state.active = null
        state.open = false
    }

    companion object {
        const val MAX_KEPT = 100
    }
}