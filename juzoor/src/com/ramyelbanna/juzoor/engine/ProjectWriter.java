package com.ramyelbanna.juzoor.engine;

import com.ramyelbanna.juzoor.core.LogManager;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * Emits a complete, openable Android Studio project around the recovered
 * artefacts: Gradle scripts, wrapper properties, manifest, package tree,
 * ProGuard rules, README and a machine readable metadata file.
 */
public final class ProjectWriter {

    private ProjectWriter() {
    }

    public static void write(File root, ConversionResult r, ConversionOptions o,
                             List<LibrarySupport.Library> libs, String decodedManifest,
                             LogSink log) throws IOException {
        String pkg = empty(r.packageName) ? "com.recovered.app" : r.packageName;
        String name = projectName(r);
        String versionName = empty(r.versionName) ? "1.0" : r.versionName;
        long versionCode = r.versionCode <= 0 ? 1 : r.versionCode;

        write(new File(root, "settings.gradle"), settings(name));
        write(new File(root, "build.gradle"), rootBuild());
        write(new File(root, "gradle.properties"), gradleProps());
        write(new File(root, "gradle/wrapper/gradle-wrapper.properties"), wrapper());
        write(new File(root, "app/build.gradle"), appBuild(pkg, versionName, versionCode, r.minSdk, r.targetSdk, libs));
        write(new File(root, "app/proguard-rules.pro"), proguard());
        write(new File(root, "app/src/main/AndroidManifest.xml"), manifest(pkg, decodedManifest));
        write(new File(root, ".gitignore"), gitignore());
        write(new File(root, "README.md"), readme(r, pkg, name));
        write(new File(root, "juzoor.json"), meta(r, pkg));

        // Package tree so the project is immediately navigable.
        if (o.javaTree) {
            File pkgDir = new File(root, "app/src/main/java/" + pkg.replace('.', '/'));
            pkgDir.mkdirs();
            write(new File(pkgDir, "package-info.java"),
                    "/** Recovered by Juzoor. Add the decompiled Java sources here. */\npackage " + pkg + ";\n");
            write(new File(root, "app/src/main/recovered/README.md"),
                    "# Recovered source (compiled with the project)\n\n"
                            + "`java/` contains real method bodies decompiled from DEX by "
                            + ConversionResult.JAVA_ENGINE + ".\n"
                            + "`kotlin/` contains editable Kotlin API mirrors generated from the same\n"
                            + "class table; JVM bytecode does not retain the original Kotlin source, so\n"
                            + "the executable bodies live in the Java tree. `recovered/java` is part of\n"
                            + "the Gradle source set; some classes may still need extra dependency jars\n"
                            + "that the original APK did not ship. The project builds from the decoded\n"
                            + "manifest, resources and detected library coordinates.\n");
        }
        log.log(LogManager.DEBUG, "Gradle project written (" + name + ", " + libs.size() + " dependencies)");
    }

    /** Rewrites README.md + juzoor.json after the final file count is known. */
    public static void refresh(File root, ConversionResult r) {
        String pkg = empty(r.packageName) ? "com.recovered.app" : r.packageName;
        try {
            write(new File(root, "README.md"), readme(r, pkg, projectName(r)));
            write(new File(root, "juzoor.json"), meta(r, pkg));
        } catch (IOException ignored) {
        }
    }

    private static String projectName(ConversionResult r) {
        String label = r.appLabel == null ? "" : r.appLabel.replaceAll("[^A-Za-z0-9 ]", "").trim();
        if (label.isEmpty()) {
            String p = r.packageName == null ? "" : r.packageName;
            int dot = p.lastIndexOf('.');
            label = dot >= 0 && dot + 1 < p.length() ? p.substring(dot + 1) : "RecoveredApp";
            label = label.replaceAll("[^A-Za-z0-9]", "");
        }
        if (label.isEmpty()) label = "RecoveredApp";
        return label;
    }

    private static boolean empty(String s) {
        return s == null || s.isEmpty();
    }

    private static void write(File f, String content) throws IOException {
        File parent = f.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        ZipUtil.writeFile(f, content);
    }

    private static String settings(String name) {
        return "rootProject.name = \"" + name + "\"\ninclude ':app'\n";
    }

    private static String rootBuild() {
        return "// Recovered by Juzoor " + ConversionResult.GENERATOR_VERSION + "\n"
                + "buildscript {\n"
                + "    repositories {\n        google()\n        mavenCentral()\n    }\n"
                + "    dependencies {\n        classpath 'com.android.tools.build:gradle:8.5.2'\n    }\n}\n\n"
                + "allprojects {\n    repositories {\n        google()\n        mavenCentral()\n        maven { url 'https://jitpack.io' }\n    }\n}\n\n"
                + "tasks.register('clean', Delete) {\n    delete rootProject.layout.buildDirectory\n}\n";
    }

    private static String gradleProps() {
        return "org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8\n"
                + "android.useAndroidX=true\n"
                + "android.nonTransitiveRClass=false\n"
                + "android.enableJetifier=true\n"
                + "kotlin.code.style=official\n";
    }

    private static String wrapper() {
        return "distributionBase=GRADLE_USER_HOME\n"
                + "distributionPath=wrapper/dists\n"
                + "distributionUrl=https\\://services.gradle.org/distributions/gradle-8.7-bin.zip\n"
                + "networkTimeout=10000\n"
                + "validateDistributionUrl=true\n"
                + "zipStoreBase=GRADLE_USER_HOME\n"
                + "zipStorePath=wrapper/dists\n";
    }

    private static String appBuild(String pkg, String versionName, long versionCode,
                                   int minSdk, int targetSdk, List<LibrarySupport.Library> libs) {
        StringBuilder deps = new StringBuilder();
        deps.append("    implementation 'androidx.appcompat:appcompat:1.7.0'\n");
        deps.append("    implementation 'com.google.android.material:material:1.12.0'\n");
        for (String c : LibrarySupport.gradleDependencies(libs)) {
            if (c.startsWith("androidx.appcompat:") || c.startsWith("com.google.android.material:")) continue;
            deps.append("    implementation '").append(c).append("'\n");
        }
        boolean firebase = false;
        for (LibrarySupport.Library l : libs) {
            if (l.note.startsWith("annotationProcessor ")) {
                deps.append("    ").append(l.note).append('\n');
            }
            if (l.coordinate.startsWith("com.google.firebase:firebase-bom:")) firebase = true;
        }
        if (firebase) {
            // The BOM only pins versions; an actual Firebase module is needed
            // for the recovered google-services classes to resolve.
            deps.append("    implementation 'com.google.firebase:firebase-analytics'\n");
        }
        return "// Recovered by Juzoor " + ConversionResult.GENERATOR_VERSION + "\n"
                + "plugins { id 'com.android.application' }\n\n"
                + "android {\n"
                + "    namespace '" + pkg + "'\n"
                + "    compileSdk 34\n\n"
                + "    defaultConfig {\n"
                + "        applicationId '" + pkg + "'\n"
                + "        minSdk " + Math.max(21, minSdk) + "\n"
                + "        targetSdk " + Math.min(34, Math.max(21, targetSdk)) + "\n"
                + "        versionCode " + versionCode + "\n"
                + "        versionName '" + versionName + "'\n"
                + "    }\n\n"
                + "    buildTypes {\n"
                + "        release {\n"
                + "            minifyEnabled false\n"
                + "            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'\n"
                + "        }\n"
                + "    }\n\n"
                + "    compileOptions {\n"
                + "        sourceCompatibility JavaVersion.VERSION_17\n"
                + "        targetCompatibility JavaVersion.VERSION_17\n"
                + "    }\n\n"
                + "    packaging {\n"
                + "        jniLibs { useLegacyPackaging = false }\n"
                + "    }\n\n"
                + "    sourceSets {\n"
                + "        main {\n"
                + "            manifest.srcFile 'src/main/AndroidManifest.xml'\n"
                + "            java.srcDirs = ['src/main/java', 'src/main/recovered/java']\n"
                + "            res.srcDirs = ['src/main/res']\n"
                + "            assets.srcDirs = ['src/main/assets']\n"
                + "            jniLibs.srcDirs = ['src/main/jniLibs']\n"
                + "        }\n"
                + "    }\n"
                + "}\n\n"
                + "dependencies {\n" + deps + "}\n";
    }

    private static String proguard() {
        return "-dontwarn **\n"
                + "-keep class ** { *; }\n"
                + "-keepclassmembers class * { native <methods>; }\n"
                + "-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod\n";
    }

    private static String manifest(String pkg, String decoded) {
        if (decoded != null && decoded.contains("<manifest")) {
            // The decoded manifest is already valid XML; keep it verbatim so no
            // component, permission or intent filter is lost. The package
            // attribute is owned by the Gradle namespace now.
            String cleaned = decoded.replaceFirst("(?s)(<manifest\\b[^>]*?)\\s+package=\"[^\"]*\"", "$1");
            // The Gradle DSL owns the SDK levels; a manifest <uses-sdk> only
            // produces an AGP warning.  AGP also warns about an explicit
            // extractNativeLibs="false"; the packaging block below owns it.
            cleaned = cleaned.replaceAll("(?s)\\s*<uses-sdk[^>]*/>", "");
            return cleaned.replaceAll("\\s+android:extractNativeLibs=\"false\"", "");
        }
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    package=\"" + pkg + "\">\n"
                + "    <application android:label=\"Recovered\" android:allowBackup=\"true\" />\n"
                + "</manifest>\n";
    }

    private static String gitignore() {
        return "*.iml\n.gradle\n/local.properties\n/.idea\n.DS_Store\n/build\n/app/build\n/captures\n.externalNativeBuild\n.cxx\n";
    }

    private static String readme(ConversionResult r, String pkg, String name) {
        StringBuilder b = new StringBuilder();
        b.append("# ").append(name).append(" — recovered source\n\n");
        b.append("Generated by **Juzoor ").append(ConversionResult.GENERATOR_VERSION)
                .append("** (APK → Source Code).\n\n");
        b.append("| Property | Value |\n|---|---|\n");
        b.append("| Package | `").append(pkg).append("` |\n");
        b.append("| Version | ").append(r.versionName).append(" (").append(r.versionCode).append(") |\n");
        b.append("| Classes | ").append(r.classCount).append(" |\n");
        b.append("| Resources | ").append(r.resourceCount).append(" |\n");
        b.append("| DEX files | ").append(r.dexCount).append(" |\n");
        b.append("| Framework | ").append(r.frameworkLabel)
                .append(r.frameworkAssets > 0 ? " (" + r.frameworkAssets + " assets)" : "").append(" |\n");
        if (r.dartStringCount > 0 || AppFramework.FLUTTER.equals(r.framework)) {
            b.append("| Dart strings | ").append(r.dartStringCount).append(" |\n");
        }
        b.append("| Native libs | ").append(r.nativeLibCount).append(" |\n");
        b.append("| Libraries | ").append(r.libraryCount).append(" |\n");
        b.append("| Java sources | ").append(r.javaFileCount)
                .append(" (").append(ConversionResult.JAVA_ENGINE).append(") |\n");
        b.append("| Kotlin mirrors | ").append(r.kotlinFileCount).append(" |\n");
        b.append("| Files | ").append(r.fileCount).append(" |\n");
        if (r.isBundle) {
            b.append("| Bundle | ").append(r.bundleKind).append(" (").append(r.splitCount + 1).append(" APKs merged) |\n");
        }
        b.append('\n');
        if (!r.packers.isEmpty()) {
            b.append("> ⚠️ Protector detected: **").append(String.join(", ", r.packers))
                    .append("**. See `REPORT.md` for what was recovered.\n\n");
        }
        b.append("## Layout\n\n");
        b.append("- `app/src/main/AndroidManifest.xml` — fully decoded manifest\n");
        b.append("- `app/src/main/res/` — decoded `values*/` plus every raw resource\n");
        b.append("- `app/src/main/assets/` — original assets\n");
        b.append("- `app/src/main/jniLibs/<abi>/` — native libraries\n");
        b.append("- `app/src/main/smali*/` — disassembled Dalvik bytecode\n");
        b.append("- `app/src/main/recovered/java/` — real decompiled Java sources\n");
        b.append("- `app/src/main/recovered/kotlin/` — Kotlin API mirrors (signatures)\n");
        if (AppFramework.FLUTTER.equals(r.framework)) {
            b.append("- `dart_strings/` — strings from the Dart snapshot in `libapp.so`\n");
        }
        b.append("- `analysis/` — ELF reports, string tables, carved payloads, libraries\n");
        b.append("- `REPORT.md` — complete human readable analysis\n\n");
        b.append("## Building\n\n");
        b.append("1. Open this folder in Android Studio (Gradle 8.7 / AGP 8.5.2).\n");
        b.append("2. The decoded manifest and resources are already in the Gradle layout.\n");
        b.append("3. Press Run, or run `gradle assembleDebug` from this folder.\n\n");
        b.append("`app/src/main/recovered/java` is part of the Gradle source set so the real\n");
        b.append("Jadx method bodies are compiled with the project. Some recovered classes\n");
        b.append("may still need extra dependency jars that an APK does not contain; the\n");
        b.append("detected libraries are listed above and in `analysis/libraries.json`. The\n");
        b.append("Kotlin tree is an editable API mirror next to the full `smali*/` tree.\n\n");
        b.append("---\n\n© 2027 Ramy El-Banna — Juzoor™\n");
        return b.toString();
    }

    private static String meta(ConversionResult r, String pkg) {
        return String.format(Locale.US,
                "{\n  \"generator\": \"Juzoor\",\n  \"generatorVersion\": \"" + ConversionResult.GENERATOR_VERSION + "\",\n"
                        + "  \"package\": \"%s\",\n  \"versionName\": \"%s\",\n  \"versionCode\": %d,\n"
                        + "  \"classes\": %d,\n  \"resources\": %d,\n  \"files\": %d,\n  \"dexFiles\": %d,\n"
                        + "  \"nativeLibraries\": %d,\n  \"libraries\": %d,\n  \"packers\": %d,\n"
                        + "  \"strings\": {\"total\": %d, \"readable\": %d, \"encoded\": %d,"
                        + " \"decoded\": %d, \"recovered\": %d},\n"
                        + "  \"bundle\": %s,\n  \"bundleKind\": \"%s\",\n  \"splits\": %d,\n"
                        + "  \"framework\": \"%s\",\n  \"frameworkAssets\": %d,\n"
                        + "  \"javaEngine\": \"%s\",\n  \"javaSources\": %d,\n  \"kotlinMirrors\": %d,\n"
                        + "  \"dartStrings\": %d,\n  \"durationMs\": %d\n}\n",
                pkg, r.versionName, r.versionCode, r.classCount, r.resourceCount, r.fileCount,
                r.dexCount, r.nativeLibCount, r.libraryCount, r.packers.size(),
                r.stringTotal, r.stringReadable, r.stringEncrypted, r.stringDecoded,
                r.stringRecovered, r.isBundle, r.bundleKind, r.splitCount,
                r.framework == null ? "native" : r.framework, r.frameworkAssets,
                ConversionResult.JAVA_ENGINE, r.javaFileCount, r.kotlinFileCount,
                r.dartStringCount, r.durationMs);
    }
}
