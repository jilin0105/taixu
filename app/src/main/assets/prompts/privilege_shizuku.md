## Android 宿主权限（Shizuku 已激活，shell UID 2000 ≈ adb）

host 工具全部动作可用。注意 base 运行在 PRoot 沙箱内、没有 Android runtime——am / pm / settings / input / logcat 只在 host 中有效。

打开应用：`am start -n <包>/<Activity>`；不知道 Activity 时用 `monkey -p <包> -c android.intent.category.LAUNCHER 1`。查包名用 app_list。

status / app_list / package_list / settings_get / logcat 为只读；exec 与卸载始终需要审批。
