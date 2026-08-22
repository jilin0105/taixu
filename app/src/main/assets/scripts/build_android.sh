#!/bin/sh
# ==============================================================================
# TaiXu (LinuxAIRuntime) - Android Project One-Key Build Engine
# Usage: build_android.sh <project_path> [task]
# ==============================================================================
set -e

PROJECT_PATH="${1:-.}"
TASK="${2:-assembleDebug}"

echo "==> [TaiXu Build Engine] 启动 Android 项目编译..."
echo "==> [TaiXu Build] 项目路径: $PROJECT_PATH"
echo "==> [TaiXu Build] 构建任务: $TASK"

# 1. 智能推导 JAVA_HOME 与 Java 可执行路径
JAVA_BIN=$(which java 2>/dev/null || ls /usr/lib/jvm/*/bin/java 2>/dev/null | head -n 1 || true)
if [ -n "$JAVA_BIN" ] && [ -x "$JAVA_BIN" ]; then
    export JAVA_HOME=$(dirname $(dirname $(readlink -f "$JAVA_BIN" 2>/dev/null || echo "$JAVA_BIN")))
    echo "==> [TaiXu Build] 识别 JAVA_HOME: $JAVA_HOME"
    export PATH="$JAVA_HOME/bin:$PATH"
    if [ ! -x /usr/bin/java ]; then
        ln -sf "$JAVA_BIN" /usr/local/bin/java 2>/dev/null || true
        ln -sf "$JAVA_BIN" /usr/bin/java 2>/dev/null || true
    fi
    JAVA_EXEC="$JAVA_BIN"
elif [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    export PATH="$JAVA_HOME/bin:$PATH"
    JAVA_EXEC="$JAVA_HOME/bin/java"
else
    JAVA_EXEC=$(which java 2>/dev/null || echo "java")
fi

# 2. 深度自愈 SSL 根证书与 cacerts 信任库
mkdir -p "$JAVA_HOME/lib/security" "$JAVA_HOME/conf/security" /etc/ssl/certs/java /etc/java-17-openjdk/security 2>/dev/null || true

# 检查系统 cacerts，若缺失则触发自愈
CACERTS_PATH=""
if [ -s /etc/ssl/certs/java/cacerts ]; then
    CACERTS_PATH="/etc/ssl/certs/java/cacerts"
elif [ -s "$JAVA_HOME/lib/security/cacerts" ]; then
    CACERTS_PATH="$JAVA_HOME/lib/security/cacerts"
fi

if [ -z "$CACERTS_PATH" ]; then
    echo "==> [TaiXu Build] 正在自愈重建 Java SSL 根证书信任库..."
    (dpkg-reconfigure -f noninteractive ca-certificates-java 2>/dev/null || \
     update-ca-certificates -f 2>/dev/null || true)
    if [ -s /etc/ssl/certs/java/cacerts ]; then
        CACERTS_PATH="/etc/ssl/certs/java/cacerts"
    fi
fi

if [ -n "$CACERTS_PATH" ]; then
    ln -sf "$CACERTS_PATH" "$JAVA_HOME/lib/security/cacerts" 2>/dev/null || true
    ln -sf "$CACERTS_PATH" /etc/ssl/certs/java/cacerts 2>/dev/null || true
    SSL_OPTS="-Djavax.net.ssl.trustStore=$CACERTS_PATH -Djavax.net.ssl.trustStorePassword=changeit"
else
    SSL_OPTS=""
fi

# 3. 自动配置 Gradle 全局镜像加速 (通过 beforeSettings 避免 project 冲突)
mkdir -p /root/.gradle
cat << 'EOF' > /root/.gradle/init.gradle
gradle.beforeSettings { settings ->
    settings.pluginManagement.repositories {
        maven { url 'https://maven.aliyun.com/repository/google' }
        maven { url 'https://maven.aliyun.com/repository/public' }
        maven { url 'https://maven.aliyun.com/repository/gradle-plugin' }
    }
    settings.dependencyResolutionManagement.repositories {
        maven { url 'https://maven.aliyun.com/repository/google' }
        maven { url 'https://maven.aliyun.com/repository/public' }
    }
}
EOF

# 4. 自愈工程本地 settings.gradle.kts (如果缺少阿里云镜像则自动前置插入)
if [ -f "$PROJECT_PATH/settings.gradle.kts" ]; then
    if ! grep -q "maven.aliyun.com" "$PROJECT_PATH/settings.gradle.kts" 2>/dev/null; then
        sed -i 's|repositories {|repositories {\n        maven("https://maven.aliyun.com/repository/google")\n        maven("https://maven.aliyun.com/repository/public")\n        maven("https://maven.aliyun.com/repository/gradle-plugin")|g' "$PROJECT_PATH/settings.gradle.kts" 2>/dev/null || true
    fi
fi

# 5. 注入核心环境变量
export APP_HOME="/opt/gradle-8.7"
export GRADLE_HOME="/opt/gradle-8.7"
export PATH="/opt/gradle-8.7/bin:${TAIXU_TOOL_DIR:-/opt/taixu/tools}/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:$PATH"

# 6. 检查并自愈 Gradle 8.7 官方独立包
if [ ! -f /opt/gradle-8.7/lib/gradle-launcher-8.7.jar ]; then
    echo "==> [TaiXu Build] 正在自动部署官方完整版 Gradle 8.7..."
    mkdir -p /opt /tmp
    rm -rf /tmp/gradle-8.7.zip /opt/gradle-8.7 2>/dev/null || true
    (curl -fsSL -m 180 https://mirrors.cloud.tencent.com/gradle/gradle-8.7-bin.zip -o /tmp/gradle-8.7.zip 2>/dev/null || \
     curl -fsSL -m 180 https://mirrors.huaweicloud.com/gradle/gradle-8.7-bin.zip -o /tmp/gradle-8.7.zip 2>/dev/null || true)
    if [ -f /tmp/gradle-8.7.zip ]; then
        (unzip -qo /tmp/gradle-8.7.zip -d /opt/ 2>/dev/null || \
         python3 -c 'import zipfile; zipfile.ZipFile("/tmp/gradle-8.7.zip").extractall("/opt/")' 2>/dev/null || \
         busybox unzip /tmp/gradle-8.7.zip -d /opt/ 2>/dev/null || true)
        rm -f /tmp/gradle-8.7.zip
    fi
fi

# 7. 自愈全局软链接
if [ -d /opt/gradle-8.7/bin ]; then
    chmod +x /opt/gradle-8.7/bin/gradle 2>/dev/null || true
    ln -sf /opt/gradle-8.7/bin/gradle /usr/local/bin/gradle 2>/dev/null || true
    ln -sf /opt/gradle-8.7/bin/gradle /usr/bin/gradle 2>/dev/null || true
fi

cd "$PROJECT_PATH"

# 8. 智能选择 Gradle 构建执行器
EXTRA_ARGS="--stacktrace --no-daemon -Dorg.gradle.native=false -Djava.security.egd=file:/dev/urandom $SSL_OPTS"

if [ -d /opt/gradle-8.7/lib ]; then
    echo "==> [TaiXu Build] 调度官方独立完整版 Gradle 8.7 执行构建..."
    exec "$JAVA_EXEC" -Xmx1024m \
        -Dorg.gradle.appname=gradle \
        -Dorg.gradle.installation.dir=/opt/gradle-8.7 \
        -classpath "/opt/gradle-8.7/lib/*" \
        org.gradle.launcher.GradleMain $TASK $EXTRA_ARGS
elif [ -x /opt/gradle-8.7/bin/gradle ]; then
    echo "==> [TaiXu Build] 调度 /opt/gradle-8.7/bin/gradle 执行构建..."
    exec /opt/gradle-8.7/bin/gradle $TASK $EXTRA_ARGS
elif [ -f ./gradlew ] && [ -f ./gradle/wrapper/gradle-wrapper.jar ]; then
    echo "==> [TaiXu Build] 调度项目本地 Gradle Wrapper 执行构建..."
    chmod +x ./gradlew
    exec ./gradlew $TASK $EXTRA_ARGS
elif command -v gradle >/dev/null 2>&1; then
    echo "==> [TaiXu Build] 调度系统 Gradle 执行构建..."
    exec gradle $TASK $EXTRA_ARGS
else
    echo "==> [TaiXu Build] ❌ 未找到有效的 Gradle 执行环境，请在插件中心装配【Android & 移动全栈套件】"
    exit 127
fi