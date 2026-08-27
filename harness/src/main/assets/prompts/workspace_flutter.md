### Flutter 工程操作规约
- 当前工程类型：Flutter；根目录标记：{{MARKER_TEXT}}。
- 修改前先 read `pubspec.yaml`、`lib/` 和 `android/` 的 Gradle 配置；Dart 代码用 edit/write 修改。
- 依赖优先执行 `flutter pub get`；构建入口：`/opt/taixu/scripts/build_flutter.sh "{{WORKSPACE_PATH}}" "apk --debug"`，或 `flutter build apk --debug`。
- 安装到手机时，确认 `build/app/outputs/flutter-apk/*.apk` 完整后复制到 `/sdcard/Download/`，再执行 `taixu-host install-apk <apk路径>`；检测到 ADB 后可执行 `adb install -r <apk>`。
- 遇到 Android Gradle/AAPT2 错误，检查 `android/gradle.properties`、Android 核心环境和 ARM64 AAPT2，不要反复全量下载 Flutter SDK。
