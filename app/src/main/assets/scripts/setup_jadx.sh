#!/bin/sh
# ==============================================================================
# TaiXu (LinuxAIRuntime) - JADX-CLI Reverse Engineering Suite Setup
# ==============================================================================
set -e

echo "==> [TaiXu] 正在初始化 JADX-CLI 逆向审计工具包..."

mkdir -p /opt/jadx /usr/local/bin /usr/bin /tmp 2>/dev/null || true

# 1. 下载 JADX 官方二进制包
if [ ! -x /opt/jadx/bin/jadx ]; then
    echo "==> [TaiXu] 正在拉取 JADX-CLI v1.5.0..."
    curl -fsSL -m 180 https://mirror.ghproxy.com/https://github.com/skylot/jadx/releases/download/v1.5.0/jadx-1.5.0.zip -o /tmp/jadx.zip 2>/dev/null || \
    curl -fsSL -m 180 https://github.com/skylot/jadx/releases/download/v1.5.0/jadx-1.5.0.zip -o /tmp/jadx.zip 2>/dev/null || true

    if [ -f /tmp/jadx.zip ]; then
        echo "==> [TaiXu] 正在解压 JADX 到 /opt/jadx/..."
        (unzip -qo /tmp/jadx.zip -d /opt/jadx/ 2>/dev/null || \
         python3 -c 'import zipfile; zipfile.ZipFile("/tmp/jadx.zip").extractall("/opt/jadx/")' 2>/dev/null || \
         busybox unzip /tmp/jadx.zip -d /opt/jadx/ 2>/dev/null || true)
        rm -f /tmp/jadx.zip
    fi
fi

# 2. 建立全局软链接
if [ -f /opt/jadx/bin/jadx ]; then
    chmod +x /opt/jadx/bin/jadx 2>/dev/null || true
    ln -sf /opt/jadx/bin/jadx /usr/local/bin/jadx 2>/dev/null || true
    ln -sf /opt/jadx/bin/jadx /usr/bin/jadx 2>/dev/null || true
    echo "==> [TaiXu] JADX-CLI 软链接配置就绪"
fi

echo "==> [TaiXu] ✅ JADX 逆向分析工具包配置完成！"