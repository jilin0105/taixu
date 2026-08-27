### Android 逆向工程操作规约
- 当前工程类型：APK 逆向；根目录标记：{{MARKER_TEXT}}。
- 原始 APK 和 `unpacked/` 是分析输入，先 read `apk-info.properties` 与 `REVERSE.md`，不要覆盖原始 APK。
- 优先使用 `jadx` 反编译 Java 源码，使用 `apktool` 处理资源/Smali；修改后再用既有脚本回编译、签名并验证。
