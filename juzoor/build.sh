#!/usr/bin/env bash
# Juzoor 4.2 — release wrapper
#
# The live decompilation screen uses AndroidX RecyclerView, so the app is built
# through the Gradle module.  This wrapper keeps the historical output path and
# verifies the resulting signature.
# Requirements: JDK 17, Android SDK build-tools 35.0.0 + platform android-35.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$ROOT/.." && pwd)"
VERSION="4.2.0"
SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$ROOT/../tools/sdk}}"
OUT="$ROOT/../build/apk"
APK="$ROOT/build/outputs/apk/release/juzoor-release.apk"
FINAL="$OUT/Juzoor-v$VERSION.apk"
APKSIGNER="$SDK/build-tools/35.0.0/apksigner"
KS="${KEYSTORE:-$ROOT/../build/juzoor.jks}"

if [ -d "$SDK" ]; then
  export ANDROID_SDK_ROOT="$SDK"
  export ANDROID_HOME="$SDK"
fi

echo "==> gradle release"
(cd "$REPO" && ./gradlew :juzoor:assembleRelease)

[ -f "$APK" ] || { echo "release APK not found: $APK" >&2; exit 1; }
mkdir -p "$OUT"
cp "$APK" "$FINAL"

if [ -x "$APKSIGNER" ] && "$APKSIGNER" verify "$FINAL" >/dev/null 2>&1; then
  echo "==> verified signature"
else
  if [ ! -x "$APKSIGNER" ]; then
    echo "warning: apksigner not found, unsigned copy left at $FINAL" >&2
    exit 0
  fi
  echo "==> signing with the local Juzoor key"
  if [ ! -f "$KS" ]; then
    keytool -genkeypair -keystore "$KS" -alias juzoor -keyalg RSA -keysize 2048 \
      -validity 10950 -storepass juzoor -keypass juzoor \
      -dname "CN=Ramy El-Banna, OU=Juzoor, O=Ramy El-Banna, L=Cairo, C=EG"
  fi
  UNSIGNED="$OUT/Juzoor-v$VERSION-unsigned.apk"
  cp "$FINAL" "$UNSIGNED"
  "$APKSIGNER" sign --ks "$KS" --ks-pass pass:juzoor --key-pass pass:juzoor \
    --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
    --out "$FINAL" "$UNSIGNED"
  rm -f "$UNSIGNED"
  "$APKSIGNER" verify "$FINAL"
fi

echo "==> done: $FINAL"
