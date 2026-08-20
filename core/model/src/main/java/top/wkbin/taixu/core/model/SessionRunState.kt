package top.wkbin.taixu.core.model

/**
 * 智能体 Agent 会话运行状态
 */
enum class SessionRunState {
    IDLE,        // 空闲 / 就绪
    RUNNING,     // 执行中 (橙色 🟠)
    COMPLETED,   // 已完成 (绿色 🟢)
    FAILED,      // 失败 (红色 🔴)
}
