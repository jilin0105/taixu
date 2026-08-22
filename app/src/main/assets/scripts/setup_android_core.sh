#!/bin/sh
# ==============================================================================
# TaiXu (LinuxAIRuntime) - Android Core Development Environment Setup
# ==============================================================================
set -e

echo "==> [TaiXu] 正在初始化 Android 核心基础环境..."

mkdir -p /opt /usr/local/bin /usr/bin ${TAIXU_TOOL_DIR:-/opt/taixu/tools}/bin /tmp 2>/dev/null || true

# 1. 深度自愈与初始化 Java 根证书信任库 (cacerts)
echo "==> [TaiXu] 正在配置 Java SSL 根证书库..."
(dpkg-reconfigure -f noninteractive ca-certificates-java 2>/dev/null || \
 update-ca-certificates -f 2>/dev/null || true)

JAVA_BIN=$(which java 2>/dev/null || ls /usr/lib/jvm/*/bin/java 2>/dev/null | head -n 1 || true)
if [ -n "$JAVA_BIN" ]; then
    J_HOME=$(dirname $(dirname $(readlink -f "$JAVA_BIN" 2>/dev/null || echo "$JAVA_BIN")))
    mkdir -p "$J_HOME/lib/security" "$J_HOME/conf/security" /etc/ssl/certs/java 2>/dev/null || true
    if [ -s /etc/ssl/certs/java/cacerts ]; then
        ln -sf /etc/ssl/certs/java/cacerts "$J_HOME/lib/security/cacerts" 2>/dev/null || true
    fi
fi

# 2. 部署官方独立 Gradle 8.7 (带完整运行时依赖，杜绝缺少 LoggerFactory)
if [ ! -f /opt/gradle-8.7/lib/gradle-launcher-8.7.jar ]; then
    echo "==> [TaiXu] 正在从国内加速镜像拉取 Gradle 8.7..."
    rm -rf /tmp/gradle-8.7.zip /opt/gradle-8.7 2>/dev/null || true
    (curl -fsSL -m 180 https://mirrors.cloud.tencent.com/gradle/gradle-8.7-bin.zip -o /tmp/gradle-8.7.zip 2>/dev/null || \
     curl -fsSL -m 180 https://mirrors.huaweicloud.com/gradle/gradle-8.7-bin.zip -o /tmp/gradle-8.7.zip 2>/dev/null || true)

    if [ -f /tmp/gradle-8.7.zip ]; then
        echo "==> [TaiXu] 正在解压 Gradle 8.7 到 /opt/..."
        (unzip -qo /tmp/gradle-8.7.zip -d /opt/ 2>/dev/null || \
         python3 -c 'import zipfile; zipfile.ZipFile("/tmp/gradle-8.7.zip").extractall("/opt/")' 2>/dev/null || \
         busybox unzip /tmp/gradle-8.7.zip -d /opt/ 2>/dev/null || true)
        rm -f /tmp/gradle-8.7.zip
    fi
fi

# 3. 建立全局可执行软链接
if [ -d /opt/gradle-8.7/bin ]; then
    chmod +x /opt/gradle-8.7/bin/gradle 2>/dev/null || true
    ln -sf /opt/gradle-8.7/bin/gradle /usr/local/bin/gradle 2>/dev/null || true
    ln -sf /opt/gradle-8.7/bin/gradle /usr/bin/gradle 2>/dev/null || true
    echo "==> [TaiXu] Gradle 8.7 软链接配置就绪"
fi

# 4. 部署 Google Android CLI 工具
if [ -f /opt/taixu/tools/android ]; then
    cp -f /opt/taixu/tools/android /usr/local/bin/android 2>/dev/null || true
    chmod +x /usr/local/bin/android 2>/dev/null || true
fi

echo "==> [TaiXu] ✅ Android 核心基础环境配置完成！"