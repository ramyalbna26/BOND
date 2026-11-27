# Third-party notices

Juzoor bundles the following open-source components as jars in `libs/` so the
decompiler and signer work fully offline. The full Apache 2.0 text is in
`licenses/Apache-2.0.txt`.

| Component | Version | Licence | Project |
|---|---|---|---|
| Jadx core / input API / DEX input | 1.5.1 | Apache 2.0 | https://github.com/skylot/jadx |
| smali / baksmali / dexlib2 / util | 3.0.8 | BSD 3-Clause | https://github.com/google/smali |
| Guava | 31.1-android | Apache 2.0 | https://github.com/google/guava |
| Gson | 2.11.0 | Apache 2.0 | https://github.com/google/gson |
| SLF4J API | 2.0.16 | MIT | https://www.slf4j.org/ |
| JSR-305 | 3.0.2 | BSD 3-Clause | https://github.com/google/jsr305 |
| apksig-lite | bundled | Apache 2.0 | https://android.googlesource.com/platform/tools/apksig |
| AndroidX RecyclerView | 1.3.2 | Apache 2.0 | https://developer.android.com/jetpack/androidx/releases/recyclerview |

The Jadx and smali jars are unmodified upstream artifacts. Juzoor only calls
their public APIs from `SourceDecompiler`.
