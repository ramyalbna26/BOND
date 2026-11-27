package com.ramyelbanna.juzoor.engine;

/** Every switch that controls one conversion run. */
public class ConversionOptions {
    public boolean decodeResources = true;
    public boolean disassembleDex = true;
    public boolean generateProject = true;
    public boolean copyAssets = true;
    public boolean keepWorkDir = true;

    /* --- Juzoor 2.0 additions --- */
    /** Inspect and decrypt native libraries (.so). */
    public boolean decryptNative = true;
    /** Recover / de-obfuscate string tables inside every DEX. */
    public boolean decryptStrings = true;
    /** Detect third-party libraries and emit ready Gradle dependencies. */
    public boolean analyzeLibraries = true;
    /** Write REPORT.md + machine readable JSON reports. */
    public boolean writeReport = true;
    /** Emit a Java source tree placeholder with package folders. */
    public boolean javaTree = true;

    /* --- Juzoor 2.2 additions --- */
    /** Run the offline security analyser (trackers, secrets, manifest, crypto). */
    public boolean securityScan = true;
    /** Emit Java + Kotlin stub sources recovered from the DEX class tables. */
    public boolean sourceStubs = true;
    /** Also emit Kotlin skeletons next to the Java tree. */
    public boolean kotlinStubs = true;
    /** Write PROJECT_TREE.txt describing every generated file. */
    public boolean projectTree = true;

    public static ConversionOptions fromPrefs() {
        ConversionOptions o = new ConversionOptions();
        o.decodeResources = com.ramyelbanna.juzoor.core.Prefs.decodeResources();
        o.disassembleDex = com.ramyelbanna.juzoor.core.Prefs.disassembleDex();
        o.generateProject = com.ramyelbanna.juzoor.core.Prefs.generateProject();
        o.copyAssets = com.ramyelbanna.juzoor.core.Prefs.copyAssets();
        o.keepWorkDir = com.ramyelbanna.juzoor.core.Prefs.keepWorkDir();
        o.decryptNative = com.ramyelbanna.juzoor.core.Prefs.decryptNative();
        o.decryptStrings = com.ramyelbanna.juzoor.core.Prefs.decryptStrings();
        o.analyzeLibraries = com.ramyelbanna.juzoor.core.Prefs.analyzeLibraries();
        o.writeReport = com.ramyelbanna.juzoor.core.Prefs.writeReport();
        o.javaTree = com.ramyelbanna.juzoor.core.Prefs.javaTree();
        o.securityScan = com.ramyelbanna.juzoor.core.Prefs.securityScan();
        o.sourceStubs = com.ramyelbanna.juzoor.core.Prefs.javaTree();
        o.kotlinStubs = com.ramyelbanna.juzoor.core.Prefs.kotlinStubs();
        o.projectTree = true;
        return o;
    }
}
