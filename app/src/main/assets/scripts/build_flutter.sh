#!/bin/sh
# ==============================================================================
# TaiXu (LinuxAIRuntime) - Flutter Project One-Key Build Engine
# Usage: build_flutter.sh <project_path> [target]
# ==============================================================================
set -e

PROJECT_PATH="${1:-.}"
TARGET="${2:-apk --debug}"

echo "==> [TaiXu Build Engine] 启动 Flutter 项目跨端编译..."
echo "==> [TaiXu Build] 项目路径: $PROJECT_PATH"

# 1. 注入 Flutter 与 Gradle PATH
export PATH="/opt/flutter/bin:/opt/gradle-8.7/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:$PATH"
export PUB_HOSTED_URL="https://pub.flutter-io.cn"
export FLUTTER_STORAGE_BASE_URL="https://storage.flutter-io.cn"

# 2. 自愈软链接
if [ -d /opt/flutter/bin ] && [ ! -f /usr/local/bin/flutter ]; then
    ln -sf /opt/flutter/bin/flutter /usr/local/bin/flutter 2>/dev/null || true
    ln -sf /opt/flutter/bin/dart /usr/local/bin/dart 2>/dev/null || true
fi

cd "$PROJECT_PATH"

echo "==> [TaiXu Build] 正在拉取 Flutter 依赖 (flutter pub get)..."
flutter pub get

echo "==> [TaiXu Build] 正在执行 Flutter 打包编译 (flutter build $TARGET)..."
exec flutter build $TARGET