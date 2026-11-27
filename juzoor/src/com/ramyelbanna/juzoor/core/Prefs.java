package com.ramyelbanna.juzoor.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;

import java.io.File;

/** Typed access to every user preference. */
public final class Prefs {
    /** Product version shown by every screen and every generated report. */
    public static final String VERSION = "2.2.0";
    private static final String FILE = "juzoor";
    private static SharedPreferences sp;

    private Prefs() {
    }

    public static void init(Context context) {
        if (sp == null) sp = context.getApplicationContext().getSharedPreferences(FILE, 0);
    }

    private static boolean b(String key, boolean def) {
        return sp.getBoolean(key, def);
    }

    private static void set(String key, boolean v) {
        sp.edit().putBoolean(key, v).apply();
    }

    public static boolean dark() { return b("dark", true); }
    public static void setDark(boolean z) { set("dark", z); }

    public static String lang() { return sp.getString("lang", "ar"); }
    public static void setLang(String s) { sp.edit().putString("lang", s).apply(); }

    /** User selected font scale in the 1.00 – 1.50 range. */
    public static float fontScale() {
        float v = sp.getFloat("font_scale", 1.0f);
        if (v < 1.0f) return 1.0f;
        if (v > 1.5f) return 1.5f;
        return v;
    }

    public static void setFontScale(float v) {
        if (v < 1.0f) v = 1.0f;
        if (v > 1.5f) v = 1.5f;
        sp.edit().putFloat("font_scale", v).apply();
    }

    /** Restores every preference to its factory default. */
    public static void reset() {
        sp.edit().clear().apply();
    }

    public static String savePath() {
        return sp.getString("save_path",
                new File(Environment.getExternalStorageDirectory(), "Download").getAbsolutePath());
    }
    public static void setSavePath(String s) { sp.edit().putString("save_path", s).apply(); }

    /** SAF tree chosen for exports; empty means the legacy filesystem path. */
    public static String saveTreeUri() { return sp.getString("save_tree_uri", ""); }
    public static void setSaveTreeUri(String s) { sp.edit().putString("save_tree_uri", s == null ? "" : s).apply(); }

    public static boolean autoLog() { return b("auto_log", true); }
    public static void setAutoLog(boolean z) { set("auto_log", z); }

    public static boolean decodeResources() { return b("opt_res", true); }
    public static void setDecodeResources(boolean z) { set("opt_res", z); }

    public static boolean disassembleDex() { return b("opt_dex", true); }
    public static void setDisassembleDex(boolean z) { set("opt_dex", z); }

    public static boolean generateProject() { return b("opt_proj", true); }
    public static void setGenerateProject(boolean z) { set("opt_proj", z); }

    public static boolean copyAssets() { return b("opt_assets", true); }
    public static void setCopyAssets(boolean z) { set("opt_assets", z); }

    public static boolean notifications() { return b("notif", true); }
    public static void setNotifications(boolean z) { set("notif", z); }

    public static boolean keepWorkDir() { return b("keep_work", false); }
    public static void setKeepWorkDir(boolean z) { set("keep_work", z); }

    /* --- Juzoor 2.0 --- */
    public static boolean decryptNative() { return b("opt_native", true); }
    public static void setDecryptNative(boolean z) { set("opt_native", z); }

    public static boolean decryptStrings() { return b("opt_strings", true); }
    public static void setDecryptStrings(boolean z) { set("opt_strings", z); }

    public static boolean analyzeLibraries() { return b("opt_libs", true); }
    public static void setAnalyzeLibraries(boolean z) { set("opt_libs", z); }

    public static boolean writeReport() { return b("opt_report", true); }
    public static void setWriteReport(boolean z) { set("opt_report", z); }

    public static boolean javaTree() { return b("opt_java", true); }
    public static void setJavaTree(boolean z) { set("opt_java", z); }

    public static int logLevel() { return sp.getInt("log_level", 0); }
    public static void setLogLevel(int level) { sp.edit().putInt("log_level", level).apply(); }

    public static int conversions() { return sp.getInt("conversions", 0); }
    public static void bumpConversions() { sp.edit().putInt("conversions", conversions() + 1).apply(); }

    public static int errors() { return sp.getInt("errors", 0); }
    public static void bumpErrors() { sp.edit().putInt("errors", errors() + 1).apply(); }

    /* --- Juzoor 2.2: security, source and signing --- */
    public static boolean securityScan() { return b("opt_security", true); }
    public static void setSecurityScan(boolean z) { set("opt_security", z); }

    public static boolean kotlinStubs() { return b("opt_kotlin", true); }
    public static void setKotlinStubs(boolean z) { set("opt_kotlin", z); }

    public static boolean signV1() { return b("sign_v1", true); }
    public static void setSignV1(boolean z) { set("sign_v1", z); }

    public static boolean signV2() { return b("sign_v2", true); }
    public static void setSignV2(boolean z) { set("sign_v2", z); }

    public static boolean signV3() { return b("sign_v3", true); }
    public static void setSignV3(boolean z) { set("sign_v3", z); }

    public static boolean defaultKey() { return b("sign_default_key", true); }
    public static void setDefaultKey(boolean z) { set("sign_default_key", z); }

    public static String keystorePath() { return sp.getString("keystore_path", ""); }
    public static void setKeystorePath(String s) { sp.edit().putString("keystore_path", s).apply(); }

    public static String keystoreAlias() { return sp.getString("keystore_alias", "juzoor"); }
    public static void setKeystoreAlias(String s) { sp.edit().putString("keystore_alias", s).apply(); }

    public static String keystorePass() { return sp.getString("keystore_pass", "juzoor"); }
    public static void setKeystorePass(String s) { sp.edit().putString("keystore_pass", s).apply(); }

    public static String lastSecurityReport() { return sp.getString("last_security", ""); }
    public static void setLastSecurityReport(String s) { sp.edit().putString("last_security", s).apply(); }

    public static int securityScans() { return sp.getInt("security_scans", 0); }
    public static void bumpSecurityScans() { sp.edit().putInt("security_scans", securityScans() + 1).apply(); }
}
