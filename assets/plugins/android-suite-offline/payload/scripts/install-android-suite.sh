#!/bin/sh
set -eu

PAYLOAD="${TAIXU_PLUGIN_PAYLOAD:?missing TAIXU_PLUGIN_PAYLOAD}"
ARCHIVES="$PAYLOAD/archives"
CHECKSUMS="$PAYLOAD/checksums/SHA256SUMS"
TOOL_DIR="${TAIXU_TOOL_DIR:?missing TAIXU_TOOL_DIR}"
ANDROID_HOME="/opt/android-sdk"
TOOLCHAIN_ROOT="/opt/taixu/toolchains/android"
JDK_HOME="$TOOLCHAIN_ROOT/jdk"
NDK_HOME="$TOOLCHAIN_ROOT/ndk"
GRADLE_VERSION="8.14.2"
BUILD_TOOLS_VERSION="35.0.0"

need() { test -s "$1" || { echo "missing offline resource: $1" >&2; exit 2; }; }

mkdir -p "$TOOL_DIR/bin" "$ANDROID_HOME" "$TOOLCHAIN_ROOT" /opt/taixu/bin /opt/taixu/locks
need "$CHECKSUMS"

# Validate every bundled archive before changing the runtime.
(cd "$PAYLOAD" && sha256sum -c checksums/SHA256SUMS)

# JDK 17 ARM64.
need "$ARCHIVES/jdk-17-aarch64-linux.tar.gz"
rm -rf "$JDK_HOME.staging"
mkdir -p "$JDK_HOME.staging"
tar -xzf "$ARCHIVES/jdk-17-aarch64-linux.tar.gz" -C "$JDK_HOME.staging"
JDK_BIN=$(find "$JDK_HOME.staging" -type f -path '*/bin/java' -print -quit)
need "$JDK_BIN"
JDK_SOURCE=$(dirname "$(dirname "$JDK_BIN")")
rm -rf "$JDK_HOME"
mv "$JDK_SOURCE" "$JDK_HOME"
rm -rf "$JDK_HOME.staging"
"$JDK_HOME/bin/java" -version >/dev/null 2>&1

# ZIP is not a required system package for this offline plugin. Prefer native
# unzip when available; otherwise use the JDK jar tool installed above.
extract_zip() {
    archive="$1"
    destination="$2"
    mkdir -p "$destination"
    if command -v unzip >/dev/null 2>&1; then
        unzip -q -o "$archive" -d "$destination"
    elif [ -x "$JDK_HOME/bin/jar" ]; then
        (cd "$destination" && "$JDK_HOME/bin/jar" xf "$archive")
    else
        echo "missing ZIP extractor: unzip and JDK jar are unavailable" >&2
        exit 6
    fi
}

# Gradle.
need "$ARCHIVES/gradle-8.14.2-bin.zip"
rm -rf "/opt/gradle-$GRADLE_VERSION.staging"
mkdir -p "/opt/gradle-$GRADLE_VERSION.staging"
extract_zip "$ARCHIVES/gradle-$GRADLE_VERSION-bin.zip" "/opt/gradle-$GRADLE_VERSION.staging"
GRADLE_SOURCE=$(find "/opt/gradle-$GRADLE_VERSION.staging" -type f -name "gradle-launcher-$GRADLE_VERSION.jar" -print -quit | xargs -r dirname | xargs -r dirname)
need "$GRADLE_SOURCE/bin/gradle"
rm -rf "/opt/gradle-$GRADLE_VERSION"
mv "$GRADLE_SOURCE" "/opt/gradle-$GRADLE_VERSION"
rm -rf "/opt/gradle-$GRADLE_VERSION.staging"

# Android Platform 34.
need "$ARCHIVES/platform-34-ext7_r03.zip"
rm -rf /tmp/taixu-android-platform
mkdir -p /tmp/taixu-android-platform
extract_zip "$ARCHIVES/platform-34-ext7_r03.zip" /tmp/taixu-android-platform
PLATFORM_SOURCE=$(find /tmp/taixu-android-platform -type f -name android.jar -print -quit | xargs -r dirname)
need "$PLATFORM_SOURCE/android.jar"
rm -rf "$ANDROID_HOME/platforms/android-34"
mkdir -p "$ANDROID_HOME/platforms"
mv "$PLATFORM_SOURCE" "$ANDROID_HOME/platforms/android-34"
rm -rf /tmp/taixu-android-platform

# Java Build-Tools 35 and ARM64 native SDK tools.
need "$ARCHIVES/build-tools_r35_linux.zip"
rm -rf /tmp/taixu-build-tools
mkdir -p /tmp/taixu-build-tools
extract_zip "$ARCHIVES/build-tools_r35_linux.zip" /tmp/taixu-build-tools
BUILD_SOURCE=$(find /tmp/taixu-build-tools -type f -name source.properties -print -quit | xargs -r dirname)
need "$BUILD_SOURCE/lib/d8.jar"
rm -rf "$ANDROID_HOME/build-tools/$BUILD_TOOLS_VERSION"
mkdir -p "$ANDROID_HOME/build-tools"
mv "$BUILD_SOURCE" "$ANDROID_HOME/build-tools/$BUILD_TOOLS_VERSION"
rm -rf /tmp/taixu-build-tools

# The Google archive may contain x86 host ELF helpers. Keep Java/JAR assets,
# then remove every non-AArch64 ELF before installing the ARM64 replacements.
find "$ANDROID_HOME/build-tools/$BUILD_TOOLS_VERSION" -type f |
    while IFS= read -r file_path; do
        if file "$file_path" 2>/dev/null | grep -q 'ELF' &&
            ! file "$file_path" 2>/dev/null | grep -Eiq 'aarch64|arm64'; then
            rm -f "$file_path"
        fi
    done

need "$ARCHIVES/android-sdk-tools-static-aarch64.zip"
rm -rf /tmp/taixu-arm64-tools
mkdir -p /tmp/taixu-arm64-tools
extract_zip "$ARCHIVES/android-sdk-tools-static-aarch64.zip" /tmp/taixu-arm64-tools
AAPT2=$(find /tmp/taixu-arm64-tools -type f -name aapt2 -print -quit)
need "$AAPT2"
file "$AAPT2" 2>/dev/null | grep -Eiq 'aarch64|arm64' || {
    echo "AAPT2 is not ARM64: $AAPT2" >&2
    exit 3
}
cp -a "$(dirname "$AAPT2")/." "$ANDROID_HOME/build-tools/$BUILD_TOOLS_VERSION/"
rm -rf /tmp/taixu-arm64-tools

# ARM64 NDK. Find llvm tools dynamically; do not assume an x86 directory name.
need "$ARCHIVES/android-ndk-r29-aarch64.tar.xz"
rm -rf "$NDK_HOME.staging"
mkdir -p "$NDK_HOME.staging"
tar -xJf "$ARCHIVES/android-ndk-r29-aarch64.tar.xz" -C "$NDK_HOME.staging"
NDK_SOURCE=$(find "$NDK_HOME.staging" -type f -name source.properties -print -quit | xargs -r dirname)
NDK_CLANG=$(find "$NDK_SOURCE/toolchains/llvm/prebuilt" -type f -name clang -print -quit)
NDK_STRIP=$(find "$NDK_SOURCE/toolchains/llvm/prebuilt" -type f -name llvm-strip -print -quit)
need "$NDK_SOURCE/source.properties"
need "$NDK_CLANG"
need "$NDK_STRIP"
file "$NDK_CLANG" "$NDK_STRIP" 2>/dev/null | grep -Eiq 'aarch64|arm64' || {
    echo "NDK tools are not ARM64" >&2
    exit 4
}
rm -rf "$NDK_HOME"
mv "$NDK_SOURCE" "$NDK_HOME"
rm -rf "$NDK_HOME.staging"

# Linux AArch64 CMake and Ninja.
need "$ARCHIVES/cmake-linux-aarch64.tar.gz"
rm -rf /tmp/taixu-cmake
mkdir -p /tmp/taixu-cmake
tar -xzf "$ARCHIVES/cmake-linux-aarch64.tar.gz" -C /tmp/taixu-cmake
CMAKE_BIN=$(find /tmp/taixu-cmake -type f -path '*/bin/cmake' -print -quit)
need "$CMAKE_BIN"
cp -a "$(dirname "$(dirname "$CMAKE_BIN")")" "$TOOL_DIR/cmake"
ln -sfn "$TOOL_DIR/cmake/bin/cmake" "$TOOL_DIR/bin/cmake"
rm -rf /tmp/taixu-cmake

need "$ARCHIVES/ninja-linux-aarch64.zip"
extract_zip "$ARCHIVES/ninja-linux-aarch64.zip" "$TOOL_DIR/bin"
chmod +x "$TOOL_DIR/bin/ninja"

# ADB from the Debian/Termux aarch64 package. Extract it without apt/network.
need "$ARCHIVES/android-tools_aarch64.deb"
if [ -s "$ARCHIVES/android-tools_aarch64.deb" ]; then
    rm -rf /tmp/taixu-adb
    mkdir -p /tmp/taixu-adb
    if command -v dpkg-deb >/dev/null 2>&1; then
        dpkg-deb -x "$ARCHIVES/android-tools_aarch64.deb" /tmp/taixu-adb
    elif command -v ar >/dev/null 2>&1; then
        (cd /tmp/taixu-adb && ar x "$ARCHIVES/android-tools_aarch64.deb" && tar -xf data.tar.* 2>/dev/null)
    fi
    ADB_SOURCE=$(find /tmp/taixu-adb -type f -name adb -print -quit)
    need "$ADB_SOURCE"
    file "$ADB_SOURCE" 2>/dev/null | grep -Eiq 'aarch64|arm64' || { echo "ADB is not ARM64" >&2; exit 5; }
    cp "$ADB_SOURCE" "$TOOL_DIR/bin/adb"
    chmod +x "$TOOL_DIR/bin/adb"
    rm -rf /tmp/taixu-adb
fi

need "$ARCHIVES/flutter-linux-arm64-android-only-slim.tar.gz"
if [ -s "$ARCHIVES/flutter-linux-arm64-android-only-slim.tar.gz" ]; then
    rm -rf /tmp/taixu-flutter
    mkdir -p /tmp/taixu-flutter
    tar -xzf "$ARCHIVES/flutter-linux-arm64-android-only-slim.tar.gz" -C /tmp/taixu-flutter
    FLUTTER_SOURCE=$(find /tmp/taixu-flutter -type f -path '*/bin/flutter' -print -quit | xargs -r dirname | xargs -r dirname)
    need "$FLUTTER_SOURCE/bin/flutter"
    rm -rf /opt/flutter
    mv "$FLUTTER_SOURCE" /opt/flutter
    rm -rf /tmp/taixu-flutter
    ln -sfn /opt/flutter/bin/flutter "$TOOL_DIR/bin/flutter"
    ln -sfn /opt/flutter/bin/dart "$TOOL_DIR/bin/dart"
fi

ln -sfn "$JDK_HOME/bin/java" "$TOOL_DIR/bin/java"
ln -sfn "$JDK_HOME/bin/javac" "$TOOL_DIR/bin/javac"
ln -sfn "/opt/gradle-$GRADLE_VERSION/bin/gradle" "$TOOL_DIR/bin/gradle"
for command in java javac gradle cmake ninja flutter dart; do
    if [ -e "$TOOL_DIR/bin/$command" ]; then ln -sfn "$TOOL_DIR/bin/$command" "/opt/taixu/bin/$command"; fi
done

mkdir -p /root/.gradle /root/.gradle/init.d
if [ -f "$PAYLOAD/config/gradle.properties" ]; then cp "$PAYLOAD/config/gradle.properties" /root/.gradle/gradle.properties; fi
if [ -f "$PAYLOAD/config/taixu-android-ndk.gradle" ]; then cp "$PAYLOAD/config/taixu-android-ndk.gradle" /root/.gradle/init.d/taixu-android-ndk.gradle; fi
printf '%s\n' 'android.builder.sdkDownload=false' >> /root/.gradle/gradle.properties

/bin/sh "$PAYLOAD/scripts/verify-android-suite.sh"
