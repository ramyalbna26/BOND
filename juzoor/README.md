# جذور — Juzoor 3.0

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

## What is new in 3.0

* **Every DEX string, every byte** — the complete string table is extracted
  (the report shows 100%, not only the decoded subset) and a second
  `BufferedInputStream` pass recovers printable runs outside `string_ids`.
* **Raw-byte string decoding** — Base64, hex, ROT13, single-byte and
  repeating-key XOR are applied to the exact MUTF-8 bytes of each entry, not
  only to already-decoded Unicode. This is what makes non-ASCII XOR tables
  recoverable.
* **Real UPX block decoding** — the UPX `l_info`/`p_info` trailer is parsed
  instead of blindly scanning for `UPX!`, and NRV2B/NRV2D/NRV2E blocks are
  decompressed with ports of the reference UCL decoders. The recovered payload
  is written as `.payload.bin` and scanned for strings and embedded files.
* **Fewer false positives** — a random `UPX!` string inside a PDF library is no
  longer reported as a packed binary.
* **Paged project tree** — folders list 100 entries at a time with a "show
  more" row, keeping memory bounded on 18,000+ file projects.
* **Everything from 2.3** — lazy tree with zoom/pan, framework detection
  (Flutter/Unity/React Native/Xamarin/Cocos/Godot/Cordova), global regex
  search, APK diff, syntax-highlighted editor with `mapping.txt`, assets
  viewer, offline cross-platform analyser, gold UI, dark mode, font scaling
  100–150%, live canvas, coloured logcat and the legal security warning.

## Build

Requirements: JDK 17, Android SDK build-tools 34.0.0, platform android-34.

```bash
# Reproducible raw build (aapt2 + javac + d8 + apksigner)
ANDROID_SDK_ROOT=/path/to/sdk ./build.sh

# Gradle module (R8 release, v1/v2/v3 flags, lint, unit tests)
cd .. && ./gradlew :juzoor:testDebugUnitTest :juzoor:assembleRelease
```

The raw build writes `../build/apk/Juzoor-v3.0.0.apk`. The Gradle release writes
`build/outputs/apk/release/juzoor-release.apk`.

`package.sh` copies the Gradle APK to `dist/` and creates a clean source ZIP.

## Layout

```
src/com/ramyelbanna/juzoor/        activities, service, queue, guard, editor
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

## Limitations

* AAB is expanded only after conversion to an APK set with `bundletool`.
* UPX payloads are decompressed but not relinked: the loader's relocation and
  filter metadata is required to turn the blocks back into a runnable `.so`.
  Juzoor reports the filter and uses the payload for string/asset recovery.
* LZMA-compressed UPX blocks are recognised and reported, but not decompressed
  on-device.
* RPM payloads compressed with xz/zstd and DMG HFS/APFS volumes are inspected
  but not decompressed on-device; gzip payloads and DMG metadata are extracted.
* PyInstaller scripts are saved as `.pyc` (ready for a Python decompiler);
  Juzoor does not embed a Python bytecode decompiler.

## Copyright

© 2027 رامي البنا — جميع الحقوق محفوظة.
© 2027 Ramy El-Banna — all rights reserved.
