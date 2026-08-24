# ARM64 资源下载清单

本插件定位为 **Android-only**：Flutter 只保留 Android 编译所需资源，不提供 Web、iOS、Windows、macOS 或 Linux Desktop 构建能力。iOS 本身也不能在 Linux ARM64 PRoot 中构建，因为需要 macOS/Xcode。

请只下载下列 AArch64/ARM64 资源，并按目标文件名保存到 `payload/archives/`。不要下载文件名包含 `x86`、`x86_64`、`amd64` 的变体。

| 目标文件名 | 下载地址 | 备注 |
|---|---|---|
| `jdk-17-aarch64-linux.tar.gz` | https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.20.1%2B1/OpenJDK17U-jdk_aarch64_linux_hotspot_17.0.20.1_1.tar.gz | Eclipse Temurin JDK 17 AArch64；SHA-256 `457b57af8f9c93ec39080bb8c764f559dc8c89a6da1a39d718a400b7890d3e41` |
| `gradle-8.14.2-bin.zip` | https://mirrors.cloud.tencent.com/gradle/gradle-8.14.2-bin.zip | Gradle 是 Java 归档，不是主机 ELF；SHA-256 `7197a12f450794931532469d4ff21a59ea2c1cd59a3ec3f89c035c3c420a6999` |
| `platform-34-ext7_r03.zip` | https://mirrors.cloud.tencent.com/AndroidSDK/platform-34-ext7_r03.zip | Android Platform 34，主要是 `android.jar` 等架构无关资源；SHA-1 `1f2e9478d6a7601425ceaa553311dc43191f103d` |
| `build-tools_r35_linux.zip` | https://mirrors.cloud.tencent.com/AndroidSDK/build-tools_r35_linux.zip | 只使用其中 JAR/资源；安装脚本会删除非 ARM64 ELF；SHA-1 `2cfaa0bbb2336e9ec18ed3ecea84fa2e2af607bc` |
| `android-sdk-tools-static-aarch64.zip` | https://ghfast.top/https://github.com/lzhiyong/android-sdk-tools/releases/download/35.0.2/android-sdk-tools-static-aarch64.zip | ARM64 `aapt2/aidl/zipalign`；SHA-256 `DB1CEA2C4454D5F9C5A802646B2D1CF560B4EE7BADBE23E51AB8E1881BB50FC2` |
| `android-ndk-r29-aarch64.tar.xz` | https://github.com/lzhiyong/termux-ndk/releases/download/android-ndk/android-ndk-r29-aarch64.tar.xz | Linux AArch64 NDK；SHA-256 `02e10e4ddfe8deaeb0bd0cf29d04c981ed5bc8a5d6b560ebb9e7661f472d684b` |
| `cmake-linux-aarch64.tar.gz` | https://github.com/Kitware/CMake/releases/download/v3.31.7/cmake-3.31.7-linux-aarch64.tar.gz | CMake Linux AArch64 |
| `ninja-linux-aarch64.zip` | https://github.com/ninja-build/ninja/releases/download/v1.12.1/ninja-linux-aarch64.zip | Ninja Linux AArch64 |
| `flutter-linux-arm64-android-only-slim.tar.gz` | https://github.com/MohamedAlkindi/flutter-native-arm64/releases/download/flutter-3.47.1-87-linux/flutter_v3.47.1_linux_arm64_android_web_sdk.tar.gz | 已从 ARM64 Flutter 包中移除 Android x86/x64、iOS、桌面、示例和源码目录，只保留 Android ARM/ARM64 与 Linux ARM64 编译所需缓存；最终约 848 MB |
| `android-tools_aarch64.deb` | https://packages.termux.dev/apt/termux-main/pool/main/a/android-tools/android-tools_36.0.1%2Breally35.0.2_aarch64.deb | Termux AArch64 `adb`；SHA-256 `82e48bf8038250fb0997b1f2cf5f780730104f2544a5532298c453d94cfe1537` |

## 放置位置

最终目录必须是：

```text
D:\work\taixu\assets\plugins\android-suite-offline\payload\archives\
```

PowerShell 示例：

```powershell
$archiveDir = 'D:\work\taixu\assets\plugins\android-suite-offline\payload\archives'
New-Item -ItemType Directory -Force $archiveDir | Out-Null
Move-Item 'D:\Downloads\OpenJDK17U-jdk_aarch64_linux_hotspot_17.0.20.1_1.tar.gz' "$archiveDir\jdk-17-aarch64-linux.tar.gz"
Move-Item 'D:\Downloads\flutter_v3.47.1_linux_arm64_android_web_sdk.tar.gz' "$archiveDir\flutter-source-arm64.tar.gz"
```

下载完成后不要解压，直接把原始归档放入 `payload/archives/`。`install-android-suite.sh` 会在 PRoot 内解压到 `/opt`。

## 下载后校验

```powershell
Get-FileHash "$archiveDir\jdk-17-aarch64-linux.tar.gz" -Algorithm SHA256
Get-FileHash "$archiveDir\android-ndk-r29-aarch64.tar.xz" -Algorithm SHA256
Get-FileHash "$archiveDir\android-sdk-tools-static-aarch64.zip" -Algorithm SHA256
Get-FileHash "$archiveDir\android-tools_aarch64.deb" -Algorithm SHA256
```

如果 GitHub 直连失败，可以只替换为 `ghfast.top` 代理，但文件名和 SHA-256 必须保持一致。不要用同名的 `x86_64`、`amd64` 或 `linux-x64` 资源替换。
