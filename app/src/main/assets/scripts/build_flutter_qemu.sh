#!/bin/sh
# Flutter x86_64 host compatibility build; Android output remains arm64-v8a.
set -e

PROJECT_PATH="${1:-.}"
COMPAT_ROOT=/opt/taixu/compat/x86_64
FLUTTER_HOME="$COMPAT_ROOT/flutter"
JAVA_HOME="$COMPAT_ROOT/jdk-17"
ANDROID_HOME="$COMPAT_ROOT/android-sdk"

[ "$(uname -m)" = "x86_64" ] || { echo "==> [TaiXu QEMU Flutter] ❌ 当前并非 x86_64 QEMU Guest"; exit 126; }
for binary in "$JAVA_HOME/bin/java" "$FLUTTER_HOME/bin/cache/dart-sdk/bin/dart" "$ANDROID_HOME/build-tools/35.0.0/aapt2"; do
    [ -x "$binary" ] || { echo "==> [TaiXu QEMU Flutter] ❌ 缺少 $binary"; exit 127; }
    machine=$(od -An -tu2 -j18 -N2 "$binary" 2>/dev/null | tr -d '[:space:]')
    [ "$machine" = "62" ] || { echo "==> [TaiXu QEMU Flutter] ❌ 主机工具不是 x86_64 ELF: $binary"; exit 126; }
done

export JAVA_HOME ANDROID_HOME
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export FLUTTER_HOME
export PUB_CACHE="$COMPAT_ROOT/cache/flutter-pub"
export GRADLE_USER_HOME="$COMPAT_ROOT/cache/gradle"
export PUB_HOSTED_URL=https://pub.flutter-io.cn
export FLUTTER_STORAGE_BASE_URL=https://storage.flutter-io.cn
export ORG_GRADLE_PROJECT_android_aapt2FromMavenOverride="$ANDROID_HOME/build-tools/35.0.0/aapt2"
export PATH="$FLUTTER_HOME/bin:$JAVA_HOME/bin:$COMPAT_ROOT/gradle-8.14.2/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/build-tools/35.0.0:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
mkdir -p "$PUB_CACHE" "$GRADLE_USER_HOME"

cd "$PROJECT_PATH"
mkdir -p android
LOCAL_PROPERTIES=android/local.properties
TMP_PROPERTIES="${LOCAL_PROPERTIES}.taixu-qemu.tmp"
if [ -f "$LOCAL_PROPERTIES" ]; then
    sed -e '/^[[:space:]]*sdk\.dir[[:space:]]*=/d' -e '/^[[:space:]]*ndk\.dir[[:space:]]*=/d' -e '/^[[:space:]]*flutter\.sdk[[:space:]]*=/d' "$LOCAL_PROPERTIES" > "$TMP_PROPERTIES"
else
    : > "$TMP_PROPERTIES"
fi
printf 'sdk.dir=%s\nflutter.sdk=%s\n' "$ANDROID_HOME" "$FLUTTER_HOME" >> "$TMP_PROPERTIES"
mv -f "$TMP_PROPERTIES" "$LOCAL_PROPERTIES"

echo "==> [TaiXu QEMU Flutter] 拉取 Dart 依赖"
if [ "${TAIXU_OFFLINE:-0}" = "1" ]; then
    "$FLUTTER_HOME/bin/flutter" pub get --offline
else
    "$FLUTTER_HOME/bin/flutter" pub get
fi
echo "==> [TaiXu QEMU Flutter] 构建 Android ARM64 APK"
if [ "${TAIXU_OFFLINE:-0}" = "1" ]; then
    exec "$FLUTTER_HOME/bin/flutter" build apk --debug --target-platform android-arm64 --offline
else
    exec "$FLUTTER_HOME/bin/flutter" build apk --debug --target-platform android-arm64
fi
