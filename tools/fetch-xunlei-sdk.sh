#!/usr/bin/env bash
set -euo pipefail

VERSION="${1:-1.0.2}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DIST="$ROOT/third_party/xunlei/dist"
M2="$ROOT/third_party/xunlei/m2"
DESKTOP_URL="https://down.sandai.net/opensdk/download/xunlei-dlsdk-java-desktop.zip"
ANDROID_URL="https://down.sandai.net/opensdk/download/xunlei-dlsdk-java-android.aar"
DESKTOP_ZIP_HASH="566b8c17043ff1fffe6f0d863edcd7f4359ccbfb358de350a90771b44d8b4d5a"
ANDROID_AAR_HASH="22a1728c59e359c3e77732c0c516b406151916a72125c5cd8686a8e907044e6f"

check_hash() {
    local file="$1" expected="$2" actual
    actual="$(sha256sum "$file" | awk '{print $1}')"
    if [[ "$actual" != "$expected" ]]; then
        echo "SHA-256 mismatch for $file" >&2
        echo "  expected: $expected" >&2
        echo "  actual:   $actual" >&2
        exit 1
    fi
}

write_pom() {
    local artifact="$1" packaging="$2" dir="$M2/com/xunlei/open/$1/$VERSION"
    mkdir -p "$dir"
    cat > "$dir/$artifact-$VERSION.pom" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.xunlei.open</groupId>
  <artifactId>$artifact</artifactId>
  <version>$VERSION</version>
  <packaging>$packaging</packaging>
</project>
EOF
}

mkdir -p "$DIST" "$M2"
ZIP="$DIST/xunlei-dlsdk-java-desktop-$VERSION.zip"
AAR="$DIST/xunlei-dlsdk-java-android-$VERSION.aar"

if [[ ! -f "$ZIP" ]]; then
    echo "Downloading desktop SDK..."
    curl -fL --retry 2 -o "$ZIP" "$DESKTOP_URL"
fi
check_hash "$ZIP" "$DESKTOP_ZIP_HASH"

if [[ ! -f "$AAR" ]]; then
    echo "Downloading Android SDK..."
    curl -fL --retry 2 -o "$AAR" "$ANDROID_URL"
fi
check_hash "$AAR" "$ANDROID_AAR_HASH"

TMP="$DIST/extract-$VERSION"
rm -rf "$TMP"
mkdir -p "$TMP"
unzip -q "$ZIP" -d "$TMP"
LIBS="$TMP/xunlei-dlsdk-java-desktop/libs"

copy_art() {
    local source="$1" artifact="$2" packaging="$3"
    local dir="$M2/com/xunlei/open/$artifact/$VERSION"
    mkdir -p "$dir"
    cp -f "$source" "$dir/$artifact-$VERSION.$packaging"
    write_pom "$artifact" "$packaging"
}

copy_art "$LIBS/xl-dl-desktop-$VERSION.jar" "dlsdk-desktop" "jar"
copy_art "$LIBS/xl-dl-native-windows-x64-$VERSION.jar" "dlsdk-native-windows-x64" "jar"
copy_art "$LIBS/xl-dl-native-linux-x64-$VERSION.jar" "dlsdk-native-linux-x64" "jar"
copy_art "$AAR" "dlsdk-android" "aar"
rm -rf "$TMP"

check_hash "$M2/com/xunlei/open/dlsdk-desktop/$VERSION/dlsdk-desktop-$VERSION.jar" \
    "9f13d08ed7edbbb256ca1513981992b4bde475d2fda109c74e0f658d8ac15615"
check_hash "$M2/com/xunlei/open/dlsdk-native-windows-x64/$VERSION/dlsdk-native-windows-x64-$VERSION.jar" \
    "edbbc1deafc572e78fbe06e7e6ddab200fe9b5d81067c587cdabee7467a19a8e"
check_hash "$M2/com/xunlei/open/dlsdk-native-linux-x64/$VERSION/dlsdk-native-linux-x64-$VERSION.jar" \
    "a11e27d77cbd5513c82bb60ee9a67856a51096c181ca19b251c8aae1132e15af"
check_hash "$M2/com/xunlei/open/dlsdk-android/$VERSION/dlsdk-android-$VERSION.aar" "$ANDROID_AAR_HASH"

echo "Xunlei SDK $VERSION is ready in $M2"
