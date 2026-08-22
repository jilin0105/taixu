#!/bin/sh
# ==============================================================================
# TaiXu (LinuxAIRuntime) - Flutter SDK Environment Setup
# ==============================================================================
set -e

echo "==> [TaiXu] 正在初始化 Flutter 跨端开发环境..."

# Flutter's artifact and pub mirrors avoid routing the large SDK cache through
# GitHub/pub.dev when the Android suite is installed in mainland China.
export PUB_HOSTED_URL="${PUB_HOSTED_URL:-https://pub.flutter-io.cn}"
export FLUTTER_STORAGE_BASE_URL="${FLUTTER_STORAGE_BASE_URL:-https://storage.flutter-io.cn}"

mkdir -p /opt/flutter /usr/local/bin /usr/bin 2>/dev/null || true

if [ ! -f "${ANDROID_HOME:-/opt/android-sdk}/platforms/android-34/android.jar" ]; then
    echo "!! [TaiXu] Flutter APK 构建依赖 Android 核心基础环境 (Platform 34)，请先安装 android-core"
fi

# 1. 国内镜像拉取 Flutter SDK (ARM64)
if [ ! -f /opt/flutter/bin/flutter ]; then
    echo "==> [TaiXu] 正在从国内镜像克隆 Flutter SDK (stable 分支)..."
    rm -rf /opt/flutter 2>/dev/null || true
    FLUTTER_SDK_READY=0
    FLUTTER_CLONE_LOG="/tmp/taixu-flutter-clone.log"
    for FLUTTER_URL in \
        "https://mirrors.tuna.tsinghua.edu.cn/git/flutter-sdk.git" \
        "https://gitee.com/mirrors/Flutter.git" \
        "https://gh-proxy.com/https://github.com/flutter/flutter.git" \
        "https://github.com/flutter/flutter.git"; do
        attempt=1
        while [ "$attempt" -le 2 ]; do
            echo "==> [TaiXu] 尝试 Flutter 镜像 ($attempt/2): $FLUTTER_URL"
            rm -rf /opt/flutter 2>/dev/null || true
            # Reduce pack memory/CPU pressure in PRoot and retry transient
            # early-EOF/TLS failures. The fallback command handles mirrors
            # that do not support Git partial clone.
            if git -c http.version=HTTP/1.1 -c http.postBuffer=524288000 \
                -c core.compression=0 clone -b stable --single-branch --no-tags \
                --filter=blob:none --depth 1 "$FLUTTER_URL" /opt/flutter >"$FLUTTER_CLONE_LOG" 2>&1 &&
                [ -f /opt/flutter/bin/flutter ]; then
                FLUTTER_SDK_READY=1
                break 2
            fi
            if git -c http.version=HTTP/1.1 -c http.postBuffer=524288000 \
                -c core.compression=0 clone -b stable --single-branch --no-tags \
                --depth 1 "$FLUTTER_URL" /opt/flutter >"$FLUTTER_CLONE_LOG" 2>&1 &&
                [ -f /opt/flutter/bin/flutter ]; then
                FLUTTER_SDK_READY=1
                break 2
            fi
            echo "!! [TaiXu] Flutter 镜像失败: $(tail -n 2 "$FLUTTER_CLONE_LOG" 2>/dev/null | tr '\n' ' ')"
            attempt=$((attempt + 1))
        done
        [ "$FLUTTER_SDK_READY" -eq 1 ] && break
    done
    rm -f "$FLUTTER_CLONE_LOG"
    [ "$FLUTTER_SDK_READY" -eq 1 ] || {
        echo "!! [TaiXu] Flutter SDK 拉取失败，所有镜像均不可用"
        exit 1
    }
fi

# 2. 建立全局软链接并授权
if [ -f /opt/flutter/bin/flutter ]; then
    chmod +x /opt/flutter/bin/flutter /opt/flutter/bin/dart 2>/dev/null || true
    ln -sf /opt/flutter/bin/flutter /usr/local/bin/flutter 2>/dev/null || true
    ln -sf /opt/flutter/bin/flutter /usr/bin/flutter 2>/dev/null || true
    ln -sf /opt/flutter/bin/dart /usr/local/bin/dart 2>/dev/null || true
    ln -sf /opt/flutter/bin/dart /usr/bin/dart 2>/dev/null || true
    echo "==> [TaiXu] Flutter & Dart 软链接配置就绪"
    /opt/flutter/bin/flutter config --no-analytics >/dev/null 2>&1 || true
    PUB_HOSTED_URL="$PUB_HOSTED_URL" FLUTTER_STORAGE_BASE_URL="$FLUTTER_STORAGE_BASE_URL" \
        /opt/flutter/bin/flutter precache --android >/dev/null 2>&1 || true
fi

if [ ! -f /opt/flutter/bin/flutter ]; then
    echo "!! [TaiXu] Flutter SDK 未就位，安装失败"
    exit 1
fi
echo "==> [TaiXu] ✅ Flutter 跨端开发环境配置完成！"
