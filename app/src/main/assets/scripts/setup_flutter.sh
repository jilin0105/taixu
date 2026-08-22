#!/bin/sh
# ==============================================================================
# TaiXu (LinuxAIRuntime) - Flutter SDK Environment Setup
# ==============================================================================
set -e

echo "==> [TaiXu] 正在初始化 Flutter 跨端开发环境..."

mkdir -p /opt/flutter /usr/local/bin /usr/bin 2>/dev/null || true

# 1. 国内镜像拉取 Flutter SDK (ARM64)
if [ ! -f /opt/flutter/bin/flutter ]; then
    echo "==> [TaiXu] 正在从国内清华/Gitee镜像克隆 Flutter SDK (stable 分支)..."
    cd /opt && (git clone -b stable --depth 1 https://mirrors.tuna.tsinghua.edu.cn/git/flutter-sdk.git flutter 2>/dev/null || \
                git clone -b stable --depth 1 https://gitee.com/mirrors/Flutter.git flutter 2>/dev/null || true)
fi

# 2. 建立全局软链接并授权
if [ -f /opt/flutter/bin/flutter ]; then
    chmod +x /opt/flutter/bin/flutter /opt/flutter/bin/dart 2>/dev/null || true
    ln -sf /opt/flutter/bin/flutter /usr/local/bin/flutter 2>/dev/null || true
    ln -sf /opt/flutter/bin/flutter /usr/bin/flutter 2>/dev/null || true
    ln -sf /opt/flutter/bin/dart /usr/local/bin/dart 2>/dev/null || true
    ln -sf /opt/flutter/bin/dart /usr/bin/dart 2>/dev/null || true
    echo "==> [TaiXu] Flutter & Dart 软链接配置就绪"
fi

echo "==> [TaiXu] ✅ Flutter 跨端开发环境配置完成！"