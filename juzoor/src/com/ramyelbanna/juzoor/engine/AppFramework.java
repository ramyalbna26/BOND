package com.ramyelbanna.juzoor.engine;

import com.ramyelbanna.juzoor.core.LogManager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Detects the cross-platform runtime that produced an application (Flutter,
 * Unity, React Native, Xamarin, Cocos, Godot or Cordova) from the files that
 * were already unpacked by the conversion engine.
 *
 * <p>The detection is deliberately marker based: it never downloads a
 * framework definition and it works on obfuscated apps because the engine
 * libraries and asset folders keep their names.  Every marker found is written
 * to {@code analysis/framework.json} so the user can audit the verdict.</p>
 */
public final class AppFramework {

    public static final String NATIVE = "native";
    public static final String FLUTTER = "flutter";
    public static final String UNITY = "unity";
    public static final String REACT_NATIVE = "react_native";
    public static final String XAMARIN = "xamarin";
    public static final String COCOS = "cocos";
    public static final String GODOT = "godot";
    public static final String CORDOVA = "cordova";

    public static final class Report {
        public String kind = NATIVE;
        public String label = "Native Android";
        public String hint = "Plain Android application — no cross-platform runtime detected.";
        public final List<String> evidence = new ArrayList<>();
        public int assetFiles;
        public int flutterAssets;
        public int unityAssets;
        public int scriptAssets;
        public File jsonFile;

        public String summary() {
            return label + (assetFiles > 0 ? " · " + assetFiles + " framework asset(s)" : "");
        }
    }

    private AppFramework() {
    }

    /** Reads the unpacked archive and returns a local, offline verdict. */
    public static Report detect(File mergedDir, File analysisDir, LogSink log) {
        Report r = new Report();
        if (mergedDir == null || !mergedDir.isDirectory()) return r;

        boolean flutter = hasLib(mergedDir, "libflutter.so", "libapp.so")
                || dirExists(mergedDir, "assets/flutter_assets")
                || fileExists(mergedDir, "assets/flutter_assets/AssetManifest.json")
                || fileExists(mergedDir, "assets/flutter_assets/AssetManifest.bin");
        boolean unity = hasLib(mergedDir, "libunity.so", "libil2cpp.so")
                || dirExists(mergedDir, "assets/bin/Data");
        boolean reactNative = hasLib(mergedDir, "libhermes.so", "libhermes-executor.so",
                "libreactnativejni.so", "libreanimated.so")
                || fileExists(mergedDir, "assets/index.android.bundle")
                || dirExists(mergedDir, "assets/node_modules");
        boolean xamarin = hasLib(mergedDir, "libmonodroid.so", "libmonosgen-2.0.so")
                || dirExists(mergedDir, "assemblies");
        boolean cocos = hasLib(mergedDir, "libcocos2dcpp.so", "libcocos.so")
                || fileExists(mergedDir, "assets/src/project.json");
        boolean godot = hasLib(mergedDir, "libgodot_android.so")
                || fileExists(mergedDir, "assets/.gdignore");
        boolean cordova = fileExists(mergedDir, "assets/www/cordova.js")
                || fileExists(mergedDir, "assets/www/cordova_plugins.js");

        if (flutter) {
            r.kind = FLUTTER;
            r.label = "Flutter";
            r.hint = "Dart AOT app. Everything under assets/flutter_assets is recovered: images, fonts, strings and the asset manifest.";
            addEvidence(r, "libflutter.so / libapp.so", hasLib(mergedDir, "libflutter.so", "libapp.so"));
            addEvidence(r, "assets/flutter_assets/", dirExists(mergedDir, "assets/flutter_assets"));
            r.flutterAssets = countTree(new File(mergedDir, "assets/flutter_assets"));
            r.assetFiles = r.flutterAssets;
        } else if (unity) {
            r.kind = UNITY;
            r.label = "Unity";
            r.hint = "Unity IL2CPP player. Native binaries, assets/bin/Data and StreamingAssets are preserved for reconstruction.";
            addEvidence(r, "libunity.so / libil2cpp.so", hasLib(mergedDir, "libunity.so", "libil2cpp.so"));
            addEvidence(r, "assets/bin/Data/", dirExists(mergedDir, "assets/bin/Data"));
            r.unityAssets = countTree(new File(mergedDir, "assets/bin/Data"));
            r.assetFiles = r.unityAssets;
        } else if (reactNative) {
            r.kind = REACT_NATIVE;
            r.label = "React Native";
            r.hint = "Hermes / JSC bundle detected. assets/index.android.bundle holds the JavaScript bundle and is copied unchanged.";
            addEvidence(r, "libhermes / libreactnativejni", hasLib(mergedDir, "libhermes.so",
                    "libhermes-executor.so", "libreactnativejni.so"));
            addEvidence(r, "assets/index.android.bundle", fileExists(mergedDir, "assets/index.android.bundle"));
            r.scriptAssets = countTree(new File(mergedDir, "assets"));
            r.assetFiles = r.scriptAssets;
        } else if (xamarin) {
            r.kind = XAMARIN;
            r.label = "Xamarin / .NET";
            r.hint = "Mono runtime detected. Managed assemblies are preserved next to the native libraries.";
            addEvidence(r, "libmonodroid / libmonosgen", hasLib(mergedDir, "libmonodroid.so", "libmonosgen-2.0.so"));
            r.assetFiles = countTree(new File(mergedDir, "assemblies"));
        } else if (cocos) {
            r.kind = COCOS;
            r.label = "Cocos2d-x";
            r.hint = "Cocos player detected. Scripts, project.json and assets are recovered unchanged.";
            addEvidence(r, "libcocos2dcpp.so", hasLib(mergedDir, "libcocos2dcpp.so", "libcocos.so"));
            r.assetFiles = countTree(new File(mergedDir, "assets"));
        } else if (godot) {
            r.kind = GODOT;
            r.label = "Godot";
            r.hint = "Godot Android template detected. The PCK payload under assets is preserved.";
            addEvidence(r, "libgodot_android.so", hasLib(mergedDir, "libgodot_android.so"));
            r.assetFiles = countTree(new File(mergedDir, "assets"));
        } else if (cordova) {
            r.kind = CORDOVA;
            r.label = "Cordova / WebView";
            r.hint = "Cordova web application detected. assets/www is the editable HTML/JS/CSS source tree.";
            addEvidence(r, "assets/www/cordova.js", fileExists(mergedDir, "assets/www/cordova.js"));
            r.assetFiles = countTree(new File(mergedDir, "assets/www"));
        } else {
            r.evidence.add("Android package with no Flutter/Unity/React Native markers");
            r.assetFiles = countTree(new File(mergedDir, "assets"));
        }

        File analysis = analysisDir == null ? null : new File(analysisDir, "framework.json");
        if (analysis != null) {
            try {
                ZipUtil.writeFile(analysis, toJson(r));
                r.jsonFile = analysis;
            } catch (IOException e) {
                if (log != null) log.log(LogManager.WARN, "Framework report failed: " + e);
            }
        }
        if (log != null) {
            if (NATIVE.equals(r.kind)) {
                log.log(LogManager.INFO, "Application framework: " + r.label);
            } else {
                log.log(LogManager.SUCCESS, "Detected " + r.label + " application — " + r.assetFiles
                        + " framework asset(s) recovered");
            }
        }
        return r;
    }

    /** Maps a report kind to a short localized-independent badge. */
    public static String badge(String kind) {
        if (FLUTTER.equals(kind)) return "FLUTTER";
        if (UNITY.equals(kind)) return "UNITY";
        if (REACT_NATIVE.equals(kind)) return "REACT NATIVE";
        if (XAMARIN.equals(kind)) return "XAMARIN";
        if (COCOS.equals(kind)) return "COCOS";
        if (GODOT.equals(kind)) return "GODOT";
        if (CORDOVA.equals(kind)) return "CORDOVA";
        return "NATIVE";
    }

    private static void addEvidence(Report r, String label, boolean present) {
        if (present) r.evidence.add(label);
    }

    private static boolean hasLib(File merged, String... names) {
        File lib = new File(merged, "lib");
        File[] abis = lib.listFiles();
        if (abis == null) return false;
        for (File abi : abis) {
            if (abi == null || !abi.isDirectory()) continue;
            for (String name : names) {
                if (new File(abi, name).isFile()) return true;
            }
        }
        return false;
    }

    private static boolean dirExists(File root, String rel) {
        File f = new File(root, rel);
        return f.isDirectory();
    }

    private static boolean fileExists(File root, String rel) {
        return new File(root, rel).isFile();
    }

    /** Counts files without loading anything; capped for pathological trees. */
    private static int countTree(File root) {
        if (root == null || !root.isDirectory()) return 0;
        return countTree(root, 0, new int[]{200000});
    }

    private static int countTree(File dir, int depth, int[] budget) {
        if (depth > 40 || budget[0] <= 0) return 0;
        File[] children = dir.listFiles();
        if (children == null) return 0;
        int n = 0;
        for (File f : children) {
            if (budget[0]-- <= 0) break;
            if (f.isDirectory()) n += countTree(f, depth + 1, budget);
            else n++;
        }
        return n;
    }

    public static String toJson(Report r) {
        StringBuilder b = new StringBuilder();
        b.append("{\n  \"kind\": \"").append(esc(r.kind)).append("\",\n");
        b.append("  \"label\": \"").append(esc(r.label)).append("\",\n");
        b.append("  \"hint\": \"").append(esc(r.hint)).append("\",\n");
        b.append("  \"assetFiles\": ").append(r.assetFiles).append(",\n");
        b.append("  \"flutterAssets\": ").append(r.flutterAssets).append(",\n");
        b.append("  \"unityAssets\": ").append(r.unityAssets).append(",\n");
        b.append("  \"scriptAssets\": ").append(r.scriptAssets).append(",\n");
        b.append("  \"evidence\": [");
        for (int i = 0; i < r.evidence.size(); i++) {
            if (i > 0) b.append(", ");
            b.append('"').append(esc(r.evidence.get(i))).append('"');
        }
        return b.append("]\n}\n").toString();
    }

    static String localizedKind(String kind) {
        return kind == null ? NATIVE : kind.toLowerCase(Locale.US);
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "");
    }
}
