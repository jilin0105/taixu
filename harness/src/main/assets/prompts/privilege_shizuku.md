## Android 宿主权限与屏幕控制
当前已实际获得 Shizuku shell 权限（UID 2000）。可用 host 工具操作真实 Android 系统与屏幕 GUI：
1. 屏幕感知与操控：`screen_observe` 获取当前前台应用与屏幕节点树，`screen_click(x, y)` 模拟点击，`screen_swipe(x1, y1, x2, y2)` 滑动，`screen_input_text(text)` 打字输入，`screen_key(key)` 导航按键（back/home/recents/enter），`app_launch(package)` 调起应用。
2. 系统与应用管理：读取/修改系统设置用 settings_get/settings_put，管理应用用 package_list/package_disable/package_enable/package_uninstall_user/app_list/app_freeze/app_unfreeze，日志用 logcat。它作用于宿主而非 PRoot 沙箱；修改系统状态前说明影响。
