#!/usr/bin/env bash
# Builds launcher/build/tvhome.apk using only the Android SDK command line tools.
# No Gradle, no dependency downloads: the APK contains our Java code and nothing else.
#
# Needs: a JDK (9 or newer), and an Android SDK with a platform (API 28 or newer) and build-tools.
#   brew install openjdk android-commandlinetools
#   sdkmanager "platforms;android-34" "build-tools;34.0.0"
# Then: ANDROID_HOME=/path/to/sdk launcher/build.sh
set -euo pipefail
here="$(dirname "${BASH_SOURCE[0]}")"
cd "$here"

: "${ANDROID_HOME:?Set ANDROID_HOME to your Android SDK directory}"
latest() { ls "$1" | sort -V | tail -n 1; }

PLATFORM="$(latest "$ANDROID_HOME/platforms")"
TOOLS="$ANDROID_HOME/build-tools/$(latest "$ANDROID_HOME/build-tools")"
JAR="$ANDROID_HOME/platforms/$PLATFORM/android.jar"
[ -f "$JAR" ] || { echo "no android.jar under $ANDROID_HOME/platforms" >&2; exit 1; }
echo "platform: $PLATFORM   build-tools: $(basename "$TOOLS")"

rm -rf build
mkdir -p build/classes build/dex

# 1a. Wallpapers: shrink every image in ../wallpapers to at most 1920 wide JPEG (sips is built into
#     macOS) in a temp folder, then bundle them as APK assets. No storage permission needed.
MAX_WIDTH=1920
MAX_TOTAL_MB=50
WP_SRC="${WALLPAPER_DIR:-../../wallpapers}"
WP_OUT="build/assets/wallpapers"
mkdir -p "$WP_OUT"
n=0
if [ -d "$WP_SRC" ]; then
  while IFS= read -r img; do
    n=$((n + 1))
    out="$(printf '%s/w%02d.jpg' "$WP_OUT" "$n")"
    width="$(sips -g pixelWidth "$img" | awk '/pixelWidth/ {print $2}')"
    if [ "${width:-0}" -gt "$MAX_WIDTH" ]; then
      sips --resampleWidth "$MAX_WIDTH" -s format jpeg -s formatOptions 80 "$img" --out "$out" >/dev/null
    else
      sips -s format jpeg -s formatOptions 80 "$img" --out "$out" >/dev/null
    fi
  done < <(find "$WP_SRC" -maxdepth 1 -type f \( -iname '*.jpg' -o -iname '*.jpeg' -o -iname '*.png' \) | sort)
fi
total_kb="$(du -sk "$WP_OUT" | awk '{print $1}')"
echo "wallpapers: $n bundled, $((total_kb / 1024)) MB"
if [ "$total_kb" -gt $((MAX_TOTAL_MB * 1024)) ]; then
  echo "bundled wallpapers exceed ${MAX_TOTAL_MB} MB; remove some or lower the quality in build.sh" >&2
  exit 1
fi

[ -f assets/tick.wav ] && cp assets/tick.wav build/assets/tick.wav

# 1b. Package the manifest and assets (the app has no other resources).
"$TOOLS/aapt2" link -o build/base.apk -I "$JAR" --manifest AndroidManifest.xml -A build/assets -0 wav \
  --min-sdk-version 26 --target-sdk-version 28 --version-code 1 --version-name 1.0

# 2. Compile the Java sources against the SDK.
javac --release 8 -Xlint:-options -classpath "$JAR" -d build/classes \
  $(find src -name '*.java')

# 3. Convert to Android bytecode and add it to the APK.
"$TOOLS/d8" --release --min-api 26 --lib "$JAR" --output build/dex \
  $(find build/classes -name '*.class')
cp build/base.apk build/unsigned.apk
(cd build/dex && zip -q ../unsigned.apk classes.dex)

# 4. Sign with a key that is generated here, once, and never leaves this folder.
KEYDIR=".signing"
if [ ! -f "$KEYDIR/tvhome.keystore" ]; then
  mkdir -p "$KEYDIR"
  chmod 700 "$KEYDIR"
  openssl rand -hex 16 > "$KEYDIR/pass"
  chmod 600 "$KEYDIR/pass"
  keytool -genkeypair -keystore "$KEYDIR/tvhome.keystore" -alias tvhome \
    -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=tvhome" \
    -storepass:file "$KEYDIR/pass" -keypass:file "$KEYDIR/pass" >/dev/null
fi
"$TOOLS/zipalign" -f 4 build/unsigned.apk build/aligned.apk
"$TOOLS/apksigner" sign --ks "$KEYDIR/tvhome.keystore" --ks-pass "file:$KEYDIR/pass" \
  --out build/tvhome.apk build/aligned.apk

echo "built: launcher/build/tvhome.apk"
