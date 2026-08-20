package top.wkbin.taixu.core.model

import kotlinx.serialization.Serializable

@Serializable
data class AgentSkill(
    val id: String,
    val name: String,
    val description: String,
    val systemPrompt: String,
    val triggerCommand: String? = null,
    val iconName: String = "Code",
    val isEnabled: Boolean = true,
    val isBuiltin: Boolean = true,
    val category: String = "通用",
)

object BuiltinSkills {
    val presets: List<AgentSkill> = listOf(
        AgentSkill(
            id = "linux_ops",
            name = "Linux 沙箱运维专精",
            description = "深入理解 PRoot 特性、Debian dpkg setuid 残留修复、前台常驻进程与网络诊断",
            systemPrompt = """
                【Linux 沙箱运维专精指导】：
                1. PRoot 环境中没有真实 root 权限，避免执行破坏性内核命令（如 mount、sysctl、chown）。
                2. dpkg 安装/升级时若提示 unable to securely remove .dpkg-tmp，先清理临时文件并使用 chmod u-s 降低 setuid 属性后再重试。
                3. 无 systemd 支持，需常驻的后台服务应推荐使用 nohup 或前台并排运行，并向用户说明。
            """.trimIndent(),
            triggerCommand = "/ops",
            iconName = "Terminal",
            isEnabled = true,
            isBuiltin = true,
            category = "系统运维",
        ),
        AgentSkill(
            id = "code_refactor",
            name = "代码重构与审查",
            description = "精细化代码审查、遵循架构模式、利用 edit 工具进行小步无损重构",
            systemPrompt = """
                【代码重构与审查指导】：
                1. 优先使用 edit 进行局部精准修改，避免无意义的大范围整文件重写。
                2. edit 时 oldText 必须精确匹配且上下文唯一；修改前后必须核对语法一致性。
                3. 遵循现存项目的代码规范（命名、注释、架构分层），避免引入不必要的新依赖。
            """.trimIndent(),
            triggerCommand = "/refactor",
            iconName = "Check",
            isEnabled = true,
            isBuiltin = true,
            category = "编程开发",
        ),
        AgentSkill(
            id = "git_workflow",
            name = "Git 敏捷工作流",
            description = "自动化 Git 状态分析、分支管理、原子提交信息规范生成与冲突诊断",
            systemPrompt = """
                【Git 敏捷工作流指导】：
                1. 执行 git 提交前务必通过 base 运行 git status 和 git diff 确认改动范围。
                2. 提交信息（commit message）遵循规范：feat / fix / refactor / docs / chore 等。
                3. 遇到冲突时，先通过 read 读取带冲突标记的文件，分析原因后再行修复。
            """.trimIndent(),
            triggerCommand = "/git",
            iconName = "Code",
            isEnabled = true,
            isBuiltin = true,
            category = "版本控制",
        ),
        AgentSkill(
            id = "build_debugger",
            name = "全栈构建与排错",
            description = "快速定位 Python venv、Node.js/npm、C/C++ makefile 等构建报错与依赖解析",
            systemPrompt = """
                【全栈构建与排错指导】：
                1. Python 项目优先推荐创建和激活 venv 虚拟环境，避免污染全局 python 库。
                2. Node.js/npm 安装依赖时若遇网络或编译错误，先检查 package.json 与 node-gyp 依赖。
                3. C/C++ 编译优先使用 gcc/g++ 或 cmake，并注意 Android/ARM64 平台的架构兼容性。
            """.trimIndent(),
            triggerCommand = "/debug",
            iconName = "Alert",
            isEnabled = true,
            isBuiltin = true,
            category = "编程开发",
        ),
    )
}
