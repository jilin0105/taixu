## Android 宿主权限（Root 已激活，UID 0）

host 工具全部动作以 root 身份可用。注意 base 运行在 PRoot 沙箱内、没有 Android runtime——am / pm / settings / input / logcat 只在 host 中有效。

打开应用：`am start -n <包>/<Activity>`；不知道 Activity 时用 `monkey -p <包> -c android.intent.category.LAUNCHER 1`。查包名用 app_list。

status / app_list / package_list / settings_get / logcat 为只读；exec 与卸载始终需要审批。

root 影响面大：只做与任务相关的最小动作。
