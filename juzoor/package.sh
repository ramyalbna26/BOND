#!/usr/bin/env bash
# Build the signed Juzoor release APK and a clean, self-contained source ZIP.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$ROOT/.." && pwd)"
DIST="$ROOT/dist"
STAGE="$(mktemp -d "${TMPDIR:-/tmp}/juzoor-source.XXXXXX")"
VERSION="2.3.0"

if [ "${SKIP_BUILD:-0}" != "1" ]; then
  (cd "$REPO" && ./gradlew :juzoor:assembleRelease)
fi

APK="$ROOT/build/outputs/apk/release/juzoor-release.apk"
[ -f "$APK" ] || { echo "release APK not found: $APK" >&2; exit 1; }

mkdir -p "$DIST"
cp "$APK" "$DIST/Juzoor-$VERSION.apk"

mkdir -p "$STAGE"
cp "$REPO/build.gradle.kts" "$REPO/gradle.properties" "$REPO/gradlew" "$STAGE/"
cp "$ROOT/README.md" "$STAGE/README.md"
cp -a "$REPO/gradle" "$STAGE/gradle"
cp -a "$ROOT" "$STAGE/juzoor"
rm -rf "$STAGE/juzoor/build" "$STAGE/juzoor/dist" "$STAGE/juzoor/.gradle" "$STAGE/juzoor/.idea"
find "$STAGE" -name '*.iml' -delete
# Never stage signing material, even if a developer keeps a keystore inside
# the module directory. This restores the guarantee of the old zip -x flags.
rm -f "$STAGE/release.keystore" "$STAGE/release.properties" \
      "$STAGE/juzoor/release.keystore" "$STAGE/juzoor/release.properties"
if find "$STAGE" \( -name 'release.keystore' -o -name 'release.properties' \) -print -quit | grep -q .; then
  echo "Refusing to package: signing material is still present" >&2
  exit 1
fi

cat > "$STAGE/settings.gradle.kts" <<'EOF'
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Juzoor"
include(":juzoor")
EOF

# Never ship the release keystore or its plaintext credentials. The Gradle
# signing config is optional, so the source archive still builds unsigned.
rm -f "$DIST/Juzoor-$VERSION-source.zip"
(cd "$STAGE" && jar cfM "$DIST/Juzoor-$VERSION-source.zip" .)

rm -rf "$STAGE"
echo "APK:  $DIST/Juzoor-$VERSION.apk"
echo "ZIP:  $DIST/Juzoor-$VERSION-source.zip"
