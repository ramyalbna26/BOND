# جذور — Juzoor 4.3

**أصل البذور — أوفلاين 100%. بلا إنترنت. بلا روت.**
**Origin of the Seeds — 100% offline. No internet. No root.**

Juzoor turns an Android package into a navigable project: decoded manifest,
decoded resources, Smali, **real Java source**, Kotlin API mirrors, native
analysis, recovered strings, security findings and a buildable Gradle project —
all on the device.

APK, APK+, XAPK, APKS and APKM are expanded locally. AAB is recognised and the
app explains that it must first be turned into an APK set with `bundletool`,
because an AAB carries a protobuf manifest/resource table that cannot be
expanded to a buildable APK on-device.

## What is new in 4.3

* **Bytecode-aware string recovery** — the Smali tree is mined for
  `const-string` values next to `Base64.decode`, `StringBuilder.reverse`,
  `MessageDigest`, `Cipher` and `xor-int` instructions. Those hints recover
  readable-looking ciphertext that the old printable-text shortcut kept as
  plain text, and common XOR keys (`0x3D`, `0x55`, `0xFF`, …) are tried before
  the exhaustive fallback. Reports now show extracted, readable, encoded,
  decoded and recovered totals.
* **Flutter / Dart strings** — when a Flutter app is detected, Juzoor reads
  the `_kDartVmSnapshotData` / `_kDartIsolateSnapshotData` ELF symbols from
  `libapp.so` (and `libflutter.so`), decodes the Dart ULEB128 length-prefixed
  one-byte and UTF-16 strings, and writes them to a separate `dart_strings/`
  folder plus `analysis/dart_strings/` metadata. The result screen tells the
  user that the original program is Dart.
* **Live search** — the one live list has a small search bar for file paths,
  log text and levels. Filtering uses the same bounded 300-row snapshot, so
  searching never loads extra history into memory.
* **Build-ready Gradle** — generated projects now include
  `app/src/main/recovered/java` in `sourceSets`, and detected Firebase,
  Material, jsoup and other library coordinates are emitted with their
  dependencies. Firebase also gets an analytics module next to its BOM.
* **Everything from 4.2** — bounded RecyclerView live canvas, Jadx Java
  recovery, DEX batching, paginated project tree, security warning and the
  complete offline analysis pipeline.

## What is new in 4.2

* **One live list, one scroll bar** — the live decompilation screen now uses a
  single `RecyclerView` with a `ListAdapter`/`DiffUtil` adapter for logs, file
  names and paths. Only changed rows are rebound, and the list
  recycles its row views instead of drawing thousands of canvas nodes.
* **300 rows in memory** — `LiveCanvasFeed` keeps the newest 300 rows and
  coalesces repeated updates for the same file. Older log rows stay in the
  persistent Logcat, and extracted files remain in the project output. Pending
  UI events are bounded too, so a callback burst cannot grow the heap.
* **Stable rows** — file paths use a monospace face with middle ellipsis and a
  single line; log text uses sans-serif with end ellipsis. Every row has a
  fixed height, so long paths can never resize or reflow the page.
* **Smart auto-scroll** — scrolling up pauses following immediately; reaching
  the bottom (or tapping the hint) resumes it. The progress header and bar sit
  outside the list and never scroll away.
* **Gold list styling** — status icons (✓ success, ✗ error, ⚠ warning), level
  colours and the gold surface are applied to the unified list.
* **Everything from 4.0** — Jadx Java recovery, bounded DEX batching,
  process-isolated conversion, paginated project tree, generated Gradle
  project, full DEX strings and UPX NRV2B/D/E support.

## What is new in 4.0

* **Real Java decompilation** — the bundled open-source **Jadx 1.5.1** engine
  turns every DEX class into editable `.java` files with real method bodies,
  not throwing skeletons. The DEX input plugin is registered explicitly, so it
  works from an APK without desktop service-loader files.
* **Memory-bounded tree** — `ProjectTreePager` reads folder names, keeps only
  one 100-row page and lists the next page on demand. Opening a folder with
  18,000+ entries no longer creates 18,000 rows.
* **Low-memory decompilation** — when a DEX is too large for the current
  heap, `DexBatcher` rewrites it into smaller valid DEX files (complete outer +
  inner class groups) and decompiles them one at a time. A batch that still
  runs out of memory is split again down to a single class group, so a big APK does
  not take the whole conversion down with it.
* **No source cache in RAM** — Jadx normally keeps every generated class in
  memory. Juzoor streams each `.java` file straight to disk, keeps only the
  newest 300-row live feed, and releases string/resource analysis data before
  decompilation starts.
* **Safe fallback** — if one class cannot be decompiled even alone, only that
  DEX falls back to the previous stubs; the rest of the Java tree stays real.
  A corrupt DEX never crashes the conversion.
* **Kotlin API mirrors** — Kotlin files keep real package, class, field and
  method signatures next to the Java tree. JVM bytecode does not retain the
  original Kotlin source, so executable bodies live in `.java`; this is stated
  in the generated `recovered/README.md`.
* **Everything from 3.0** — full DEX string table with raw-byte Base64/hex/
  ROT13/XOR decoding, UPX NRV2B/NRV2D/NRV2E block decoding, Flutter/Unity
  framework alerts, global regex search, APK diff, syntax-highlighted editor,
  assets viewer, cross-platform preview, gold UI, dark mode, font scaling
  100–150%, live canvas, coloured logcat and the legal security warning.

## Build

Requirements: JDK 17, Android SDK build-tools 35.0.0, platform android-35.

```bash
# Release wrapper: Gradle R8 build + signature verification
ANDROID_SDK_ROOT=/path/to/sdk ./build.sh

# Gradle module (R8 release, v1/v2/v3 flags, lint, unit tests)
cd .. && ./gradlew :juzoor:testDebugUnitTest :juzoor:assembleRelease
```

The release wrapper writes `../build/apk/Juzoor-v4.3.0.apk`. The Gradle release
writes `build/outputs/apk/release/juzoor-release.apk`. The live list uses
AndroidX RecyclerView, so the release is built through Gradle.

`package.sh` copies the Gradle APK to `dist/` and creates a clean source ZIP.

## Layout

```
src/com/ramyelbanna/juzoor/        activities, service, queue, guard, editor
src/com/ramyelbanna/juzoor/engine/ Jadx, DEX, AXML, ARSC, ELF, SAST, project writer
src/com/ramyelbanna/juzoor/core/   preferences, logging, signing key
res/                               gold premium UI, English + Arabic copies
libs/                              apksig-lite + bundled Jadx runtime jars
test/resources/sample.dex          decompiler regression fixture
```

## Security

* No `INTERNET` permission.
* APK output is signed locally; the Rebuild & Sign screen supports v1/v2/v3.
* `SecurityGuard` detects a connected debugger, Frida/Xposed/Riru/Zygisk
  mappings and emulator fingerprints. A red/black legal warning is shown,
  the incident is written to the log directory, and the process is closed.
* Release builds are processed by R8 with a narrow keep list (the embedded
  Jadx API is kept so the decompiler keeps working after shrinking).

Debug builds (`applicationId` ending in `.debug`) skip the guard so the
developer build can run on an emulator.

## Limitations

* Kotlin decompilation is API-level only. Jadx outputs Java; the Kotlin tree is
  an editable signature mirror, not reconstructed Kotlin source.
* Dart snapshot extraction is a static string recovery pass. It does not
  rebuild the original Dart source or re-link `libapp.so`; the extracted text is
  for analysis and asset/string recovery.
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

Third-party components and licences are listed in `THIRD_PARTY_NOTICES.md`.

## Copyright

© 2027 رامي البنا — جميع الحقوق محفوظة.
© 2027 Ramy El-Banna — all rights reserved.
