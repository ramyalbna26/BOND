# Juzoor 4.3 — Phase 1-3 status

## Phase 1 — Android priority
- [x] Lazy project tree with a bounded 100-row page, recycling rows, pinch zoom and pan
- [x] `ProjectTreePager` regression test over a 250-entry folder
- [x] Flutter / Unity / React Native / Xamarin / Cocos / Godot / Cordova detection
- [x] Full Flutter asset preservation + visible runtime alert
- [x] Every DEX string extracted + streaming ASCII / UTF-8 / UTF-16 scan
- [x] Raw-byte Base64 / hex / ROT13 / single-byte and repeating-key XOR decoding
- [x] ELF symbols, embedded DEX/ZIP carving, XOR recovery, packer fingerprints
- [x] UPX l_info/p_info parsing and NRV2B / NRV2D / NRV2E block decoding
- [x] AAB is recognised and gives explicit bundletool guidance

## Phase 2 — Real source and developer tools
- [x] Embedded Jadx 1.5.1 Java decompiler with real method bodies
- [x] One-DEX-at-a-time memory budget and per-DEX stub fallback
- [x] Memory-bounded DEX batching with adaptive class-group retry
- [x] No Jadx in-memory source cache; generated Java streams straight to disk
- [x] Coalesced live-canvas updates so file events cannot flood the heap
- [x] Explicit DEX plugin registration (no desktop service-loader dependency)
- [x] Kotlin API mirrors generated from the DEX class table
- [x] Global regex search across Java, XML, assets and .so
- [x] APK diff (added / removed / modified with size delta)
- [x] mapping.txt deobfuscation for Smali + Java/Kotlin
- [x] Syntax-highlighted offline code editor
- [x] Image and font assets viewer

## Phase 3 — Cross-platform
- [x] PE/EXE/DLL headers, sections, .NET detection and PyInstaller carving
- [x] ELF / DEB (ar+tar.gz) / RPM (header+cpio gzip)
- [x] Mach-O thin/fat headers, symbols and load commands
- [x] DMG koly trailer and partition plist
- [x] Settings preview section for Windows / Linux / macOS
- [ ] LZMA-compressed UPX blocks and RPM xz/zstd payloads (documented limitation)

## Update 4.3 — deeper decryption and Flutter/Dart
- [x] Smali hint mining: const-string next to Base64/reverse/MessageDigest/Cipher/xor-int
- [x] Common XOR keys (0x3D, 0x55, 0xFF, …) tried before the full 255-key fallback
- [x] Context-confirmed recovery for printable-looking ciphertext
- [x] Dart snapshot symbol lookup (`_kDartVmSnapshotData` / `_kDartIsolateSnapshotData`)
- [x] ULEB128 one-byte and UTF-16 Dart string extraction into `dart_strings/`
- [x] Flutter result notice and report section
- [x] Live canvas search over paths, logs and levels using the bounded 300-row feed
- [x] Generated Gradle project includes `recovered/java` and Firebase/jsoup dependencies
- [x] Metrics split into extracted / readable / encoded / decoded / recovered

## Update 4.2 — live feed and layout stability
- [x] One RecyclerView + ListAdapter/DiffUtil for logs, files and paths
- [x] `LiveCanvasFeed` keeps only the newest 300 rows and coalesces file updates
- [x] Bounded pending queue so conversion callbacks cannot flood the heap
- [x] Monospace middle-ellipsized paths and fixed row heights (no layout shift)
- [x] Smart auto-scroll: pauses when the user scrolls up, resumes at the bottom
- [x] Fixed progress header outside the list, gold status icons and level colours
- [x] Older log rows remain in Logcat when evicted from memory

## Build & deliver
- [x] Unit + integration tests, including a real DEX decompilation fixture
- [x] Raw and Gradle release builds
- [x] Rebuild, verify signature, re-zip source, deliver
