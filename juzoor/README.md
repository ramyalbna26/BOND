# جذور — Juzoor 2.2

**أصل البذور — أوفلاين 100%. بلا إنترنت. بلا روت.**
**Origin of the Seeds — 100% offline. No internet. No root.**

Juzoor turns an Android package into a navigable project: decoded manifest,
decoded resources, Smali, Java/Kotlin skeletons, native-library analysis,
recovered strings, security findings and a buildable Gradle project — all on
the device.

APK, APK+, XAPK, APKS and APKM are expanded locally. AAB is recognised and the
app explains that it must first be turned into an APK set with `bundletool`,
because an AAB carries a protobuf manifest/resource table that cannot be
expanded to a buildable APK on-device.

## Build

Requirements: JDK 17, Android SDK build-tools 34.0.0, platform android-34.

```bash
# Reproducible raw build (aapt2 + javac + d8 + apksigner)
ANDROID_SDK_ROOT=/path/to/sdk ./build.sh

# Gradle module (R8 release, v1/v2/v3 flags, lint)
cd .. && ./gradlew :juzoor:assembleRelease
```

The raw build writes `../build/apk/Juzoor-v2.2.0.apk`. The Gradle release writes
`build/outputs/apk/release/juzoor-release.apk`.

`package.sh` copies the Gradle APK to `dist/` and creates a clean source ZIP.

## Layout

```
src/com/ramyelbanna/juzoor/        activities, service, queue, guard
src/com/ramyelbanna/juzoor/engine/ DEX, AXML, ARSC, ELF, SAST, project writer
src/com/ramyelbanna/juzoor/core/   preferences, logging, signing key
res/                               gold premium UI, English + Arabic copies
libs/apksig-lite.jar               local APK signer used by Rebuild & Sign
```

## Security

* No `INTERNET` permission.
* APK output is signed locally; the Rebuild & Sign screen supports v1/v2/v3.
* `SecurityGuard` detects a connected debugger, Frida/Xposed/Riru/Zygisk
  mappings and emulator fingerprints. A red/black legal warning is shown,
  the incident is written to the log directory, and the process is closed.
* Release builds are processed by R8 with a narrow keep list.

Debug builds (`applicationId` ending in `.debug`) skip the guard so the
developer build can run on an emulator.

## Copyright

© 2027 رامي البنا — جميع الحقوق محفوظة.
© 2027 Ramy El-Banna — all rights reserved.
