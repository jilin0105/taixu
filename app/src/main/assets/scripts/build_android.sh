#!/bin/sh
# ==============================================================================
# TaiXu (LinuxAIRuntime) - Android Project One-Key Build Engine
# Usage: build_android.sh <project_path> [task]
# ------------------------------------------------------------------------------
# 纯执行器：所有环境部署均由【Android & 移动全栈开发套件】插件装配完成。
# 本脚本只负责：加载环境 → 调度 Gradle 构建。不做任何修复/下载/自愈。
# ==============================================================================
set -e

PROJECT_PATH="${1:-.}"
TASK="${2:-assembleDebug}"
GRADLE_VER="8.14.2"

echo "==> [TaiXu Build Engine] 启动 Android 项目编译..."
echo "==> [TaiXu Build] 项目路径: $PROJECT_PATH"
echo "==> [TaiXu Build] 构建任务: $TASK"

# 1. 加载插件装配期固化的环境变量
if [ -f /etc/profile.d/taixu-android.sh ]; then
    . /etc/profile.d/taixu-android.sh
fi
# /etc/environment 由插件装配期写入，PRoot 非登录 shell 场景下兜底
if [ -f /etc/environment ]; then
    while IFS= read -r line; do
        case "$line" in
            *=*) key="${line%%=*}"
                 val="${line#*=}"
                 eval "current=\${$key}"
                 [ -z "$current" ] && export "$key=$val"
                 ;;
        esac
    done < /etc/environment
fi
export ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"
export ANDROID_SDK_ROOT="${ANDROID_HOME}"

# AGP's default Maven artifact is a Linux x86_64 executable.  When the
# android-core plugin installed the ARM64 tool bundle, pass the real aapt2
# executable explicitly so AGP never tries to start the incompatible daemon.
AAPT2_OVERRIDE="${TAIXU_AAPT2_PATH:-}"
AAPT2_MODE="${TAIXU_AAPT2_MODE:-native}"
for native_aapt2 in \
    "/opt/taixu/android-sdk-tools/35.0.2/build-tools/aapt2" \
    "/usr/local/bin/aapt2" \
    "/usr/bin/aapt2"; do
    if [ -x "$native_aapt2" ]; then
        AAPT2_NATIVE_CANDIDATE="$native_aapt2"
        break
    fi
done
if [ "${TAIXU_FORCE_QEMU_AAPT2:-0}" != "1" ] && [ -n "${AAPT2_NATIVE_CANDIDATE:-}" ]; then
    AAPT2_MODE="native"
    AAPT2_OVERRIDE="$AAPT2_NATIVE_CANDIDATE"
fi
if [ "$AAPT2_MODE" = "qemu" ]; then
    AAPT2_OVERRIDE="${TAIXU_AAPT2_PATH:-/opt/taixu/android-sdk-tools/qemu/aapt2}"
elif [ -z "$AAPT2_OVERRIDE" ] || [ ! -x "$AAPT2_OVERRIDE" ]; then
    for candidate in \
        "/opt/taixu/android-sdk-tools/35.0.2/build-tools/aapt2" \
        "/usr/local/bin/aapt2" \
        "/usr/bin/aapt2"; do
        if [ -x "$candidate" ]; then
            AAPT2_OVERRIDE="$candidate"
            break
        fi
    done
fi

# 非登录 shell 可能没有继承插件写入的 profile；变量为空时从标准 JDK
# 目录和 PATH 重新解析，避免环境变量漂移阻断构建。
if [ -z "${JAVA_HOME:-}" ] || [ ! -x "$JAVA_HOME/bin/java" ]; then
    JAVA_HOME=""
    for candidate in /usr/lib/jvm/java-17-openjdk-arm64 /usr/lib/jvm/java-17-openjdk-aarch64 /usr/lib/jvm/default-java; do
        if [ -x "$candidate/bin/java" ]; then
            JAVA_HOME="$candidate"
            break
        fi
    done
fi
if [ -z "${JAVA_HOME:-}" ]; then
    JAVA_BIN_FALLBACK=$(command -v java 2>/dev/null || true)
    if [ -n "$JAVA_BIN_FALLBACK" ]; then
        JAVA_REAL_FALLBACK=$(readlink -f "$JAVA_BIN_FALLBACK" 2>/dev/null || echo "$JAVA_BIN_FALLBACK")
        JAVA_HOME=$(dirname "$(dirname "$JAVA_REAL_FALLBACK")")
    fi
fi
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    export JAVA_HOME
    export PATH="$JAVA_HOME/bin:$PATH"
fi

# 固定本次构建使用的 Java，避免 PATH 中的旧软链接指向另一套 JDK。
JAVA_EXEC=""
if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    JAVA_EXEC="$JAVA_HOME/bin/java"
else
    JAVA_EXEC=$(command -v java 2>/dev/null || true)
    if [ -n "$JAVA_EXEC" ] && command -v readlink >/dev/null 2>&1; then
        JAVA_REAL=$(readlink -f "$JAVA_EXEC" 2>/dev/null || true)
        [ -n "$JAVA_REAL" ] && JAVA_EXEC="$JAVA_REAL"
    fi
fi
if [ -z "$JAVA_EXEC" ] || [ ! -x "$JAVA_EXEC" ]; then
    echo "==> [TaiXu Build] ❌ 未找到可执行 Java (JAVA_HOME=$JAVA_HOME)"
    exit 127
fi
echo "==> [TaiXu Build] Java 执行文件: $JAVA_EXEC"

echo "==> [TaiXu Build] JAVA_HOME: $JAVA_HOME"
echo "==> [TaiXu Build] ANDROID_HOME: $ANDROID_HOME"

# AGP may invoke llvm-strip for native libraries. The official Android NDK
# Linux package currently provides only a linux-x86_64 toolchain; on this
# ARM64 PRoot host it fails before the project code is compiled. Report this
# early so the error is not confused with Gradle file-system watching noise.
HOST_ARCH="$(uname -m 2>/dev/null || echo unknown)"
if [ "$HOST_ARCH" = "aarch64" ] || [ "$HOST_ARCH" = "arm64" ]; then
    NDK_STRIP=""
    for candidate in "$ANDROID_HOME"/ndk/*/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip; do
        if [ -x "$candidate" ]; then
            NDK_STRIP="$candidate"
            break
        fi
    done
    if [ -n "$NDK_STRIP" ]; then
        echo "==> [TaiXu Build] 检测到 ARM64 主机与 x86_64 NDK llvm-strip: $NDK_STRIP"
        echo "==> [TaiXu Build] 内置模板已配置 keepDebugSymbols；若是外部工程，请在 android.packagingOptions.jniLibs 中保留 *.so 符号"
    fi
fi

# 2. 绑定 SDK 到当前工程
echo "sdk.dir=$ANDROID_HOME" > "$PROJECT_PATH/local.properties"
echo "==> [TaiXu Build] 绑定 ANDROID_HOME: $ANDROID_HOME"

# 3. SSL 信任库参数
SSL_OPTS=""
if [ -s "$JAVA_HOME/lib/security/cacerts" ]; then
    CACERTS_PATH="$JAVA_HOME/lib/security/cacerts"
elif [ -s /etc/ssl/certs/java/cacerts ]; then
    CACERTS_PATH="/etc/ssl/certs/java/cacerts"
fi
if [ -n "$CACERTS_PATH" ]; then
    # 内置 cacerts 是 PKCS12；显式声明格式，避免精简 OpenJDK 默认按 JKS 解析。
    SSL_OPTS="-Djavax.net.ssl.trustStore=$CACERTS_PATH -Djavax.net.ssl.trustStoreType=PKCS12 -Djavax.net.ssl.trustStorePassword=changeit"
fi

export PATH="/opt/gradle-$GRADLE_VER/bin:${TAIXU_TOOL_DIR:-/opt/taixu/tools}/bin:$PATH"

cd "$PROJECT_PATH"

# 4. 调度 Gradle 构建
EXTRA_ARGS="--console=plain --info --stacktrace --no-daemon -Dorg.gradle.native=false"
if [ -n "$AAPT2_OVERRIDE" ] && [ -x "$AAPT2_OVERRIDE" ]; then
    EXTRA_ARGS="$EXTRA_ARGS -Pandroid.aapt2FromMavenOverride=$AAPT2_OVERRIDE"
    if [ "$AAPT2_MODE" = "qemu" ]; then
        echo "==> [TaiXu Build] QEMU AAPT2: $AAPT2_OVERRIDE"
        echo "==> [TaiXu Build] 校验 QEMU AAPT2 启动..."
        AAPT2_CHECK_LOG="${TMPDIR:-/tmp}/taixu-aapt2-build-check.log"
        if ! "$AAPT2_OVERRIDE" version >"$AAPT2_CHECK_LOG" 2>&1; then
            sed -n '1,12p' "$AAPT2_CHECK_LOG" 2>/dev/null || true
            rm -f "$AAPT2_CHECK_LOG"
            echo "==> [TaiXu Build] ❌ QEMU AAPT2 无法启动。请检查 qemu-x86_64-static、x86_64 loader/运行库及 /opt/taixu bind mount"
            exit 126
        fi
        rm -f "$AAPT2_CHECK_LOG"
    else
        echo "==> [TaiXu Build] ARM64 AAPT2: $AAPT2_OVERRIDE"
    fi
elif [ "$AAPT2_MODE" = "qemu" ]; then
    echo "==> [TaiXu Build] ❌ 强制 QEMU 模式已启用，但 qemu-aapt2 包装器不存在"
    echo "==> [TaiXu Build] 请重新装配 Android 核心环境，确认 APK 内置 QEMU 已同步到当前 RootFS"
    exit 126
else
    echo "==> [TaiXu Build] 警告：未找到 ARM64 aapt2，将由 AGP 选择默认工具"
fi
JAVA_RUNTIME_OPTS="-Djava.security.egd=file:/dev/urandom"
[ -n "$SSL_OPTS" ] && JAVA_RUNTIME_OPTS="$JAVA_RUNTIME_OPTS $SSL_OPTS"
export GRADLE_OPTS="${GRADLE_OPTS:-} $JAVA_RUNTIME_OPTS"

if [ -d /opt/gradle-$GRADLE_VER/lib ]; then
    echo "==> [TaiXu Build] 调度官方独立完整版 Gradle $GRADLE_VER 执行构建..."
    exec "$JAVA_EXEC" -Xmx1024m \
        -Dorg.gradle.appname=gradle \
        -Dorg.gradle.installation.dir=/opt/gradle-$GRADLE_VER \
        $JAVA_RUNTIME_OPTS \
        -classpath "/opt/gradle-$GRADLE_VER/lib/*" \
        org.gradle.launcher.GradleMain $TASK $EXTRA_ARGS
elif [ -d /opt/gradle-8.7/lib ]; then
    echo "==> [TaiXu Build] 调度官方独立完整版 Gradle 8.7 执行构建..."
    exec "$JAVA_EXEC" -Xmx1024m \
        -Dorg.gradle.appname=gradle \
        -Dorg.gradle.installation.dir=/opt/gradle-8.7 \
        $JAVA_RUNTIME_OPTS \
        -classpath "/opt/gradle-8.7/lib/*" \
        org.gradle.launcher.GradleMain $TASK $EXTRA_ARGS
elif [ -x /opt/gradle-$GRADLE_VER/bin/gradle ]; then
    echo "==> [TaiXu Build] 调度 /opt/gradle-$GRADLE_VER/bin/gradle 执行构建..."
    exec /opt/gradle-$GRADLE_VER/bin/gradle $TASK $EXTRA_ARGS
elif [ -f ./gradlew ] && [ -f ./gradle/wrapper/gradle-wrapper.jar ]; then
    echo "==> [TaiXu Build] 调度项目本地 Gradle Wrapper 执行构建..."
    chmod +x ./gradlew
    exec ./gradlew $TASK $EXTRA_ARGS
elif command -v gradle >/dev/null 2>&1; then
    echo "==> [TaiXu Build] 调度系统 Gradle 执行构建..."
    exec gradle $TASK $EXTRA_ARGS
else
    echo "==> [TaiXu Build] ❌ 未找到有效的 Gradle 执行环境，请在插件中心装配【Android & 移动全栈开发套件】"
    exit 127
fi
