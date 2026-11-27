package com.ramyelbanna.juzoor.engine;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Everything a finished conversion reports back to the UI. */
public class ConversionResult {
    /** Single source of truth for every generated report and README. */
    public static final String GENERATOR_VERSION = "2.3.0";

    public File apkFile;
    public long bytes;
    public int classCount;
    public long durationMs;
    public String error;
    public int fileCount;
    public File outputDir;
    public boolean success;
    public File zipFile;
    public String packageName = "";
    public String appLabel = "";
    public String versionName = "";
    public long versionCode = 0;
    public int minSdk = 26;
    public int targetSdk = 34;
    public int dexCount = 0;
    public int resourceCount = 0;
    public boolean isBundle = false;
    public String bundleKind = "APK";
    public int splitCount = 0;

    /* --- Juzoor 2.0 --- */
    public int nativeLibCount = 0;
    public int nativeDecryptedCount = 0;
    public int packerCount = 0;
    public int stringTotal = 0;
    public int stringEncrypted = 0;
    public int stringRecovered = 0;
    public int libraryCount = 0;
    public File reportFile;
    public File reportJson;
    public final List<String> packers = new ArrayList<>();
    public final List<String> libraries = new ArrayList<>();
    public final List<String> nativeLibs = new ArrayList<>();
    public final List<String> warnings = new ArrayList<>();

    /* --- Juzoor 2.3 --- */
    /** Full security report (null when the scan is disabled). */
    public SecurityAnalyzer.Report security;
    public int securityScore = -1;
    public int securityFindings = 0;
    public int trackerCount = 0;
    public int secretCount = 0;
    public int javaFileCount = 0;
    public int kotlinFileCount = 0;
    public File treeFile;

    /* --- Juzoor 2.3: cross-platform runtimes --- */
    /** native / flutter / unity / react_native / xamarin / cocos / godot / cordova. */
    public String framework = AppFramework.NATIVE;
    public String frameworkLabel = "Native Android";
    public String frameworkHint = "";
    public int frameworkAssets = 0;

    public String summary() {
        return "classes=" + classCount + " res=" + resourceCount + " dex=" + dexCount
                + " so=" + nativeLibCount + " libs=" + libraryCount;
    }

    /** Compact one line security verdict used by the result screen. */
    public String securityLine() {
        if (security == null) return "";
        return security.riskLabel() + " · " + security.total() + " findings · "
                + security.trackers.size() + " trackers";
    }
}
