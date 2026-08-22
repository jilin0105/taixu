#!/bin/sh
# ==============================================================================
# TaiXu (LinuxAIRuntime) - Flutter Project One-Key Build Engine
# Usage: build_flutter.sh <project_path> [target]
# ==============================================================================
set -e

PROJECT_PATH="${1:-.}"
TARGET="${2:-apk --debug}"
GRADLE_VER="8.9"

echo "==> [TaiXu Build Engine] 启动 Flutter 项目跨端编译..."
echo "==> [TaiXu Build] 项目路径: $PROJECT_PATH"

# 1. 注入 Flutter 与 Gradle PATH (优先加载插件装配期固化的环境变量)
if [ -f /etc/profile.d/taixu-android.sh ]; then
    . /etc/profile.d/taixu-android.sh
fi
export PATH="/opt/flutter/bin:/opt/gradle-8.9/bin:/opt/gradle-8.7/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:$PATH"
export ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PUB_HOSTED_URL="https://pub.flutter-io.cn"
export FLUTTER_STORAGE_BASE_URL="https://storage.flutter-io.cn"
export PUB_CACHE="${PUB_CACHE:-/opt/taixu/cache/flutter-pub}"

# 2. 自愈软链接
if [ -d /opt/flutter/bin ] && [ ! -f /usr/local/bin/flutter ]; then
    ln -sf /opt/flutter/bin/flutter /usr/local/bin/flutter 2>/dev/null || true
    ln -sf /opt/flutter/bin/dart /usr/local/bin/dart 2>/dev/null || true
fi

cd "$PROJECT_PATH"

if ! command -v flutter >/dev/null 2>&1; then
    echo "==> [TaiXu Build] ❌ 未找到 Flutter SDK，请安装 Flutter 跨平台开发套件"
    exit 127
fi
if [ ! -f "${ANDROID_HOME}/platforms/android-34/android.jar" ]; then
    echo "==> [TaiXu Build] ❌ 缺少 Android SDK Platform 34，请同时安装 Android 核心基础环境"
    exit 126
fi
if [ ! -x "${ANDROID_HOME}/build-tools/34.0.0/aapt2" ]; then
    echo "==> [TaiXu Build] ❌ 缺少 Android Build-Tools 34，请重新装配 Android 核心基础环境"
    exit 126
fi

mkdir -p "$PUB_CACHE" android
if [ -f /opt/flutter/bin/flutter ]; then
    printf 'sdk.dir=%s\nflutter.sdk=/opt/flutter\n' "$ANDROID_HOME" > android/local.properties
fi
if [ "${TAIXU_AAPT2_MODE:-qemu}" = "qemu" ]; then
    AAPT2_PATH="${TAIXU_AAPT2_PATH:-/opt/taixu/android-sdk-tools/qemu/aapt2}"
    if [ ! -x "$AAPT2_PATH" ]; then
        echo "==> [TaiXu Build] ❌ Flutter Android 构建需要 QEMU AAPT2 包装器: $AAPT2_PATH"
        exit 126
    fi
    echo "==> [TaiXu Build] 校验 QEMU AAPT2 启动..."
    if ! "$AAPT2_PATH" version; then
        echo "==> [TaiXu Build] ❌ QEMU AAPT2 无法启动。请检查 qemu-x86_64-static 与 x86_64 运行库"
        exit 126
    fi
    # Flutter invokes Gradle internally, so pass the same override through the
    # standard Gradle project-property environment channel.
    export ORG_GRADLE_PROJECT_android_aapt2FromMavenOverride="$AAPT2_PATH"
fi

export GRADLE_OPTS="${GRADLE_OPTS:-} -Dorg.gradle.jvmargs=-Xmx1024m"

echo "==> [TaiXu Build] 正在拉取 Flutter 依赖 (flutter pub get)..."
flutter pub get

echo "==> [TaiXu Build] 正在执行 Flutter 打包编译 (flutter build $TARGET)..."
exec flutter build $TARGET
