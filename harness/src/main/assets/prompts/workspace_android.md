### Android 工程操作规约
- 当前工程类型：Android；根目录标记：{{MARKER_TEXT}}。
- 修改代码或构建配置时，先用 read 查看 `settings.gradle(.kts)`、`app/build.gradle(.kts)`、`app/src/main/AndroidManifest.xml` 和入口源码；局部修改优先用 edit，需要新文件才用 write。
- 首选构建入口：`/opt/taixu/scripts/build_android.sh "{{WORKSPACE_PATH}}" assembleDebug`；也可在工程根目录执行 `./gradlew assembleDebug`。不要调用已移除的 android CLI。
- 需要安装到手机时，先确认 APK 真实存在并完成构建，再复制到 `/sdcard/Download/`，然后执行 `taixu-host install-apk /sdcard/Download/<项目名>.apk` 调起宿主安装器；若 `adb devices` 有设备，优先 `adb install -r <apk>`。
- 构建失败必须读取完整 Gradle/AAPT2 错误并编辑对应脚本或工程文件修复，不能只汇报“编译失败”。
