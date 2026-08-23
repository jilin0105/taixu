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

# 1. 下载 Flutter SDK 压缩包（HTTP Range 分片 + 断点续传）
if [ ! -f /opt/flutter/bin/flutter ]; then
    echo "==> [TaiXu] 正在获取 Flutter stable SDK 发布信息..."
    FLUTTER_META="/tmp/taixu-flutter-releases.json"
    FLUTTER_META_URL="https://storage.googleapis.com/flutter_infra_release/releases/releases_linux.json"
    rm -f "$FLUTTER_META"
    curl -fsSL --retry 4 --retry-all-errors --connect-timeout 20 --max-time 120 \
        "$FLUTTER_META_URL" -o "$FLUTTER_META" || {
        echo "!! [TaiXu] Flutter 发布信息获取失败"
        exit 1
    }
    command -v jq >/dev/null 2>&1 || {
        echo "!! [TaiXu] 缺少 jq，无法解析 Flutter 发布索引"
        exit 1
    }
    FLUTTER_ARCHIVE_REL=$(jq -r '[.releases[] | select(.channel == "stable" and .dart_sdk_arch == "x64")][0].archive // empty' "$FLUTTER_META")
    FLUTTER_SHA256=$(jq -r '[.releases[] | select(.channel == "stable" and .dart_sdk_arch == "x64")][0].sha256 // empty' "$FLUTTER_META")
    rm -f "$FLUTTER_META"
    [ -n "$FLUTTER_ARCHIVE_REL" ] && [ "$FLUTTER_SHA256" != "" ] || {
        echo "!! [TaiXu] 未找到可用的 Flutter stable Linux SDK"
        exit 1
    }

    FLUTTER_ARCHIVE_NAME=$(basename "$FLUTTER_ARCHIVE_REL")
    FLUTTER_ARCHIVE="/tmp/$FLUTTER_ARCHIVE_NAME"

    # 下载一个 Range 分片并在已有内容后继续，网络中断时可复用已完成字节。
    download_flutter_chunk() {
        chunk_url="$1"
        chunk_start="$2"
        chunk_end="$3"
        chunk_file="$4"
        chunk_size=$((chunk_end - chunk_start + 1))
        existing=0
        [ -f "$chunk_file" ] && existing=$(wc -c < "$chunk_file" 2>/dev/null || echo 0)
        if [ "$existing" -eq "$chunk_size" ]; then
            return 0
        fi
        [ "$existing" -gt "$chunk_size" ] && { rm -f "$chunk_file"; existing=0; }
        request_start=$((chunk_start + existing))
        chunk_part="${chunk_file}.part"
        rm -f "$chunk_part"
        curl -fL --retry 5 --retry-all-errors --connect-timeout 20 --max-time 1800 \
            -r "${request_start}-${chunk_end}" "$chunk_url" -o "$chunk_part"
        cat "$chunk_part" >> "$chunk_file"
        rm -f "$chunk_part"
        [ "$(wc -c < "$chunk_file")" -eq "$chunk_size" ]
    }

    download_flutter_archive() {
        archive_url="$1"
        echo "==> [TaiXu] 尝试 Flutter SDK 压缩包: $archive_url"
        header_file="${FLUTTER_ARCHIVE}.headers"
        curl -fsSL --retry 3 --retry-all-errors --connect-timeout 20 --max-time 60 \
            -r 0-0 -D "$header_file" -o /dev/null "$archive_url" || return 1
        total_size=$(awk 'tolower($1) == "content-range:" {split($3,a,"/"); print a[2]; exit} tolower($1) == "content-length:" {gsub("\r", "", $2); print $2; exit}' "$header_file")
        rm -f "$header_file"
        case "$total_size" in ''|*[!0-9]*) return 1 ;; esac
        [ "$total_size" -gt 1048576 ] || return 1

        # If the origin ignores Range and returns the full archive, use a
        # single resumable transfer instead of concatenating invalid chunks.
        range_probe="${FLUTTER_ARCHIVE}.range-probe"
        range_headers="${range_probe}.headers"
        curl -fsSL --retry 2 --retry-all-errors --connect-timeout 20 --max-time 60 \
            -r 0-0 -D "$range_headers" -o "$range_probe" "$archive_url" || true
        if ! grep -q " 206 " "$range_headers" 2>/dev/null; then
            rm -f "$range_probe" "$range_headers"
            curl -fL --retry 8 --retry-all-errors --connect-timeout 20 --max-time 3600 \
                -C - "$archive_url" -o "$FLUTTER_ARCHIVE" || return 1
        else
            rm -f "$range_probe" "$range_headers"
            chunk_dir="${FLUTTER_ARCHIVE}.chunks"
            mkdir -p "$chunk_dir"
            chunk_count=4
            chunk_pids=""
            chunk_failed=0
            i=0
            while [ "$i" -lt "$chunk_count" ]; do
                start=$((i * total_size / chunk_count))
                end=$(((i + 1) * total_size / chunk_count - 1))
                [ "$i" -eq $((chunk_count - 1)) ] && end=$((total_size - 1))
                download_flutter_chunk "$archive_url" "$start" "$end" "$chunk_dir/part-$i" &
                chunk_pids="$chunk_pids $!"
                i=$((i + 1))
            done
            for pid in $chunk_pids; do
                wait "$pid" || chunk_failed=1
            done
            if [ "$chunk_failed" -ne 0 ]; then
                rm -rf "$chunk_dir"
                return 1
            fi
            rm -f "$FLUTTER_ARCHIVE"
            i=0
            while [ "$i" -lt "$chunk_count" ]; do
                cat "$chunk_dir/part-$i" >> "$FLUTTER_ARCHIVE"
                i=$((i + 1))
            done
            rm -rf "$chunk_dir"
        fi
        [ "$(wc -c < "$FLUTTER_ARCHIVE")" -eq "$total_size" ] || return 1
        if command -v sha256sum >/dev/null 2>&1; then
            echo "$FLUTTER_SHA256  $FLUTTER_ARCHIVE" | sha256sum -c - >/dev/null 2>&1 || return 1
        fi
        return 0
    }

    FLUTTER_SDK_READY=0
    rm -f "$FLUTTER_ARCHIVE"
    for FLUTTER_BASE in \
        "${FLUTTER_STORAGE_BASE_URL%/}/flutter_infra_release/releases" \
        "https://storage.googleapis.com/flutter_infra_release/releases"; do
        if download_flutter_archive "$FLUTTER_BASE/$FLUTTER_ARCHIVE_REL"; then
            FLUTTER_SDK_READY=1
            break
        fi
        echo "!! [TaiXu] Flutter SDK 压缩包下载或校验失败，切换线路"
        rm -f "$FLUTTER_ARCHIVE"
        rm -rf "${FLUTTER_ARCHIVE}.chunks"
    done
    [ "$FLUTTER_SDK_READY" -eq 1 ] || {
        echo "!! [TaiXu] Flutter SDK 下载失败，未执行 Git 全量克隆"
        exit 1
    }

    echo "==> [TaiXu] 正在校验并解压 Flutter SDK..."
    FLUTTER_STAGING="/tmp/taixu-flutter-staging"
    rm -rf "$FLUTTER_STAGING"
    mkdir -p "$FLUTTER_STAGING"
    tar -xJf "$FLUTTER_ARCHIVE" -C "$FLUTTER_STAGING"
    [ -f "$FLUTTER_STAGING/flutter/bin/flutter" ] || {
        echo "!! [TaiXu] Flutter SDK 压缩包结构无效"
        exit 1
    }
    rm -rf /opt/flutter
    mv "$FLUTTER_STAGING/flutter" /opt/flutter
    rm -rf "$FLUTTER_STAGING" "$FLUTTER_ARCHIVE"
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
