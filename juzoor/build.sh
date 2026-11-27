#!/usr/bin/env bash
# Juzoor 2.3 — reproducible build script
# Requirements: JDK 17, Android SDK build-tools 34.0.0 + platform android-34
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
SDK="${ANDROID_SDK_ROOT:-$ROOT/../tools/sdk}"
BT="$SDK/build-tools/34.0.0"
JAR="$SDK/platforms/android-34/android.jar"
OUT="$ROOT/../build"
KS="${KEYSTORE:-$OUT/juzoor.jks}"

AAPT2="$BT/aapt2"; D8="$BT/d8"; ZIPALIGN="$BT/zipalign"; APKSIGNER="$BT/apksigner"

rm -rf "$OUT/gen" "$OUT/classes" "$OUT/dex"
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex" "$OUT/apk"

echo "==> aapt2 compile"
"$AAPT2" compile --dir "$ROOT/res" -o "$OUT/res.zip"

echo "==> aapt2 link"
# aapt2 still needs the legacy package attribute; Gradle uses the namespace.
MANIFEST="$OUT/AndroidManifest.xml"
sed '0,/<manifest/{s/<manifest/<manifest package="com.ramyelbanna.juzoor"/}' \
  "$ROOT/AndroidManifest.xml" > "$MANIFEST"
"$AAPT2" link -o "$OUT/apk/base.apk" -I "$JAR" \
  --manifest "$MANIFEST" -R "$OUT/res.zip" \
  --java "$OUT/gen" --auto-add-overlay

echo "==> javac"
SRCS=$(find "$ROOT/src" -name '*.java' ! -name 'R.java')
LIBS=$(find "$ROOT/libs" -name "*.jar" | tr '\n' ':')
javac -encoding UTF-8 -source 8 -target 8 -nowarn -Xlint:none -bootclasspath "$JAR" -cp "$JAR:$LIBS" \
  -d "$OUT/classes" $SRCS "$OUT/gen/com/ramyelbanna/juzoor/R.java"

echo "==> d8"
"$D8" --lib "$JAR" --min-api 26 --release --output "$OUT/dex" \
  $(find "$OUT/classes" -name '*.class') $(find "$ROOT/libs" -name '*.jar')

echo "==> package"
cp "$OUT/apk/base.apk" "$OUT/apk/unsigned.apk"
(cd "$OUT/dex" && jar uf "$OUT/apk/unsigned.apk" classes.dex)
"$ZIPALIGN" -f -p 4 "$OUT/apk/unsigned.apk" "$OUT/apk/aligned.apk"

echo "==> sign"
if [ ! -f "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -alias juzoor -keyalg RSA -keysize 2048 \
    -validity 10950 -storepass juzoor -keypass juzoor \
    -dname "CN=Ramy El-Banna, OU=Juzoor, O=Ramy El-Banna, L=Cairo, C=EG"
fi
"$APKSIGNER" sign --ks "$KS" --ks-pass pass:juzoor --key-pass pass:juzoor \
  --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
  --out "$OUT/apk/Juzoor-v2.3.0.apk" "$OUT/apk/aligned.apk"
"$APKSIGNER" verify "$OUT/apk/Juzoor-v2.3.0.apk"

echo "==> done: $OUT/apk/Juzoor-v2.3.0.apk"
