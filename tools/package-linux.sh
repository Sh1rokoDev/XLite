#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
VERSION="${XLITE_VERSION:-$(sed -n 's/^xliteVersion=//p' "$ROOT/gradle.properties" | tr -d '\r')}"
if [[ ! "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo "Invalid XLite version: $VERSION" >&2
    exit 1
fi

if [[ $# -gt 1 || ( $# -eq 1 && "$1" != "--skip-build" ) ]]; then
    echo "Usage: $0 [--skip-build]" >&2
    exit 1
fi
if [[ $# -eq 0 ]]; then
    bash ./gradlew :desktopApp:createDistributable "-PxliteVersion=$VERSION" --console=plain
fi

APP_DIR="${XLITE_APP_DIR:-$ROOT/desktopApp/build/compose/binaries/main/app/XLite}"
WORK_DIR="$ROOT/desktopApp/build/appimage"
TOOLS="$ROOT/desktopApp/build/appimage-tools"
OUTPUT="$ROOT/dist/XLite-$VERSION-linux-x64.AppImage"

if [[ "$(uname -m)" != "x86_64" || ! -x "$APP_DIR/bin/XLite" ]]; then
    echo "Linux x64 app image not found or launcher missing: $APP_DIR/bin/XLite" >&2
    exit 1
fi
IMAGE_VERSION="$(sed -n 's/.*<app-version>\([^<]*\)<\/app-version>.*/\1/p' "$APP_DIR/lib/app/.jpackage.xml")"
if [[ "$IMAGE_VERSION" != "$VERSION" ]]; then
    echo "Linux app-image version $IMAGE_VERSION differs from requested $VERSION; rebuild it first." >&2
    exit 1
fi

mkdir -p "$ROOT/dist" "$TOOLS" "$WORK_DIR"
# Lock downloads to reviewed checksums. Fail rather than use changed binaries.
fetch_tool() {
    local name="$1" url="$2" sha="$3"
    if [[ ! -f "$TOOLS/$name" ]]; then
        curl --fail --location --retry 3 "$url" -o "$TOOLS/$name.part"
        echo "$sha  $TOOLS/$name.part" | sha256sum --check
        mv "$TOOLS/$name.part" "$TOOLS/$name"
    fi
    echo "$sha  $TOOLS/$name" | sha256sum --check
    chmod +x "$TOOLS/$name"
}
fetch_tool appimagetool-1.9.1-x86_64.AppImage \
    https://github.com/AppImage/appimagetool/releases/download/1.9.1/appimagetool-x86_64.AppImage \
    ed4ce84f0d9caff66f50bcca6ff6f35aae54ce8135408b3fa33abfc3cb384eb0
# Reuse the static type-2 runtime embedded in the verified, versioned tool.
# This avoids a mutable continuous runtime download and works fully offline
# once the tool is cached. The AppImage offset marks the end of the ELF runtime.
RUNTIME_SIZE="$("$TOOLS/appimagetool-1.9.1-x86_64.AppImage" --appimage-offset)"
[[ "$RUNTIME_SIZE" =~ ^[0-9]+$ && "$RUNTIME_SIZE" -gt 0 ]]
head -c "$RUNTIME_SIZE" "$TOOLS/appimagetool-1.9.1-x86_64.AppImage" > "$TOOLS/runtime-x86_64"

# Create a fresh staging directory; never modify the Gradle app image.
STAGE="$(mktemp -d "$WORK_DIR/.stage-XXXXXX")"
trap 'rm -rf -- "$STAGE"' EXIT
mkdir -p "$STAGE/usr"
cp -a "$APP_DIR" "$STAGE/usr/XLite"
cp "$ROOT/tools/appimage/AppRun" "$STAGE/AppRun"
cp "$ROOT/tools/appimage/xlite.desktop" "$STAGE/xlite.desktop"
cp "$ROOT/desktopApp/src/main/resources/xlite.png" "$STAGE/xlite.png"
ln -s xlite.png "$STAGE/.DirIcon"
chmod +x "$STAGE/AppRun"
# --appimage-extract-and-run lets the build tool work in WSL/CI without FUSE.
ARCH=x86_64 "$TOOLS/appimagetool-1.9.1-x86_64.AppImage" --appimage-extract-and-run \
    --runtime-file "$TOOLS/runtime-x86_64" "$STAGE" "$OUTPUT"
chmod +x "$OUTPUT"
echo "Linux single-file application: $OUTPUT"
