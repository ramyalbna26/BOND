# Juzoor 2.3 — Phase 1-3 status

## Phase 1 — Android priority
- [x] Lazy project tree (folders load on open, recycling rows, pinch zoom)
- [x] Flutter / Unity / React Native / Xamarin / Cocos / Godot / Cordova detection
- [x] Full Flutter asset preservation + visible runtime alert
- [x] Streaming ASCII / UTF-8 / UTF-16 / Base64 / Hex string recovery for .so
- [x] ELF symbols, embedded DEX/ZIP carving, XOR recovery, UPX unpacking
- [x] Large-file guard: no full-file allocation for 50 MB+ libraries
- [x] AAB is recognised and gives explicit bundletool guidance

## Phase 2 — Developer tools
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
- [ ] RPM xz/zstd payload and DMG filesystem extraction (documented limitation)

## Build & deliver
- [x] Unit + integration tests
- [x] Raw and Gradle release builds
- [x] Rebuild, verify signature, re-zip source, deliver
