# Juzoor 2.2 — Phase 2 status

## Bugs
- [x] Fix NPE in SourceWriter (null class names, sanitize, per-class try/catch)
- [x] Fix DexDisassembler readString pointer bug + null-safe escape/typeName
- [x] Fix JuzoorFiles shareIntent (ClipData, grant, zip mime)
- [x] Fix report consistency (duration + fileCount before writers)
- [x] Fix binary resource decoder returning `false` for colour/dimen/bool values
- [x] Fix PROJECT_INDEX.md scanning the wrong smali root
- [x] Fix manifest/resource IDs and flag masks in generated Android projects
- [x] Map R8-flattened resource file names back to drawable/mipmap/xml paths
- [x] Recognise AAB and give explicit bundletool guidance instead of a broken project

## Missing features
- [x] Strengthen DexStrings (smali-driven key mining, multi-byte XOR, tolerant readability)
- [x] Static UPX unpacking in NativeAnalyzer
- [x] Full secrets (no truncation) + copy in SecurityActivity + report/json
- [x] PROJECT_TREE.txt + PROJECT_INDEX.md
- [x] SAST tracker -> exact Smali class linkage
- [x] Live decompilation canvas with zoom/pan, pause and per-file progress
- [x] Batch queue with two parallel workers and one progress bar per app
- [x] Font-size slider (100–150%) applied to every screen
- [x] Runtime anti-tampering guard + legal warning screen
- [x] Manifest / build.gradle editor with ZIP re-export

## Build & deliver
- [x] Rebuild, verify signature, re-zip source, deliver
