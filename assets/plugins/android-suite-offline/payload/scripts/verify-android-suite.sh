#!/bin/sh
set -eu

test -x /opt/taixu/bin/java
test -x /opt/taixu/bin/javac
test -x /opt/taixu/bin/gradle
test -x /opt/taixu/bin/cmake
test -x /opt/taixu/bin/ninja
test -f /opt/android-sdk/platforms/android-34/android.jar
test -f /opt/android-sdk/build-tools/35.0.0/lib/d8.jar
test -x /opt/android-sdk/build-tools/35.0.0/aapt2
file /opt/android-sdk/build-tools/35.0.0/aapt2 2>/dev/null | grep -Eiq 'aarch64|arm64'
test -x /opt/taixu/toolchains/android/ndk/toolchains/llvm/prebuilt/*/bin/llvm-strip
test -x /opt/taixu/bin/adb
/opt/taixu/bin/java -version >/dev/null 2>&1
/opt/taixu/bin/gradle --version >/dev/null 2>&1
/opt/taixu/bin/cmake --version >/dev/null 2>&1
/opt/taixu/bin/ninja --version >/dev/null 2>&1
test -x /opt/taixu/bin/flutter
/opt/taixu/bin/flutter --version >/dev/null 2>&1 || true
# Android-only policy: web/desktop/iOS artifacts are intentionally not required.
test -d /opt/flutter/bin/cache/artifacts/engine/android-arm64-release
test -d /opt/flutter/bin/cache/artifacts/engine/android-arm64-profile
echo "TaiXu Android ARM64 offline suite is ready"
