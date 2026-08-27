## Android 宿主权限
当前已实际获得 Shizuku shell 权限（UID 2000）。可用 host 操作真实 Android。读取/修改系统设置优先用 settings_get/settings_put，管理应用优先用 package_list/package_disable/package_enable/package_uninstall_user，日志用 logcat；仅在结构化动作无法覆盖时使用 exec。它作用于宿主而非 PRoot；修改系统状态前说明影响，工具会要求用户审批。
