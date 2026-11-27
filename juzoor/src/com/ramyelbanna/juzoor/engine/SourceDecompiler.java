package com.ramyelbanna.juzoor.engine;

import com.ramyelbanna.juzoor.core.LogManager;

import org.w3c.dom.Document;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jadx.api.JadxArgs;
import jadx.api.JadxDecompiler;
import jadx.api.JavaClass;
import jadx.api.plugins.JadxPlugin;
import jadx.api.plugins.loader.JadxPluginLoader;
import jadx.api.security.IJadxSecurity;
import jadx.core.plugins.files.IJadxFilesGetter;
import jadx.plugins.input.dex.DexInputPlugin;

/**
 * Real Java source recovery for the DEX class tables.
 *
 * <p>Jadx is used as an embedded, offline decompiler.  One DEX is loaded at a
 * time, its classes are decompiled to {@code .java} files and the decompiler is
 * closed before the next DEX is opened.  That keeps peak memory proportional
 * to the largest single DEX instead of the whole application, which matters on
 * phones with a large heap but no desktop-class memory.</p>
 *
 * <p>The DEX input plugin is registered explicitly instead of relying on
 * {@code META-INF/services}.  Android packaging does not guarantee that a
 * desktop service-loader file survives, so this makes the engine deterministic
 * on-device.</p>
 */
public final class SourceDecompiler {

    public static final String ENGINE = ConversionResult.JAVA_ENGINE;

    private static final String[] TEMP_DIRS = {"jadx-tmp", "jadx-cache", "jadx-config"};

    public static final class Stats {
        public int classes;
        public int javaFiles;
        public int skipped;
        public final List<File> failedDexes = new ArrayList<>();

        public String line() {
            return javaFiles + " .java (" + classes + " classes, " + skipped + " skipped)";
        }
    }

    private SourceDecompiler() {
    }

    public static Stats decompile(List<File> dexFiles, File javaRoot, File workDir, LogSink log) {
        Stats stats = new Stats();
        if (javaRoot == null || dexFiles == null || dexFiles.isEmpty()) return stats;
        if (workDir == null) workDir = javaRoot;
        if (!javaRoot.exists()) javaRoot.mkdirs();

        int index = 0;
        for (File dex : dexFiles) {
            if (log != null && log.isCancelled()) break;
            index++;
            int percent = 87 + (int) (index * 3.0 / Math.max(1, dexFiles.size()));
            if (log != null) {
                log.progress(percent, "Decompiling " + dex.getName());
                log.file(dex.getName() + " → Java", percent);
            }
            try {
                int written = decompileOne(dex, javaRoot, workDir, log, stats);
                if (written == 0) {
                    stats.failedDexes.add(dex);
                    if (log != null) log.log(LogManager.WARN,
                            "Jadx returned no classes for " + dex.getName() + "; stub fallback will be used");
                } else if (log != null) {
                    log.log(LogManager.INFO, "Java sources from " + dex.getName() + ": " + written);
                }
            } catch (OutOfMemoryError oom) {
                stats.failedDexes.add(dex);
                if (log != null) log.log(LogManager.ERROR,
                        "Not enough memory to decompile " + dex.getName() + "; smaller stub tree will be used");
            } catch (Throwable t) {
                stats.failedDexes.add(dex);
                if (log != null) log.log(LogManager.WARN,
                        "Jadx failed for " + dex.getName() + ": " + t);
            }
            // Release the per-DEX class graph before loading the next split.
            System.gc();
        }
        return stats;
    }

    private static int decompileOne(File dex, File javaRoot, File workDir, LogSink log,
                                    Stats stats) throws IOException {
        File temp = new File(workDir, TEMP_DIRS[0]);
        File cache = new File(workDir, TEMP_DIRS[1]);
        File config = new File(workDir, TEMP_DIRS[2]);
        temp.mkdirs();
        cache.mkdirs();
        config.mkdirs();

        JadxArgs args = new JadxArgs();
        args.setInputFile(dex);
        args.setOutDir(javaRoot);
        args.setThreadsCount(1);
        args.setSkipResources(true);
        args.setSkipFilesSave(true);
        args.setShowInconsistentCode(false);
        args.setDeobfuscationOn(false);
        args.setPluginLoader(new SinglePluginLoader());
        args.setSecurity(new OfflineSecurity());
        args.setFilesGetter(new OfflineFiles(temp, cache, config));

        JadxDecompiler decompiler = new JadxDecompiler(args);
        int written = 0;
        try {
            decompiler.load();
            if (log != null) {
                log.log(LogManager.INFO, "Jadx loaded " + dex.getName() + ": "
                        + decompiler.getClasses().size() + " classes");
            }
            String header = "// Recovered by Juzoor " + ConversionResult.GENERATOR_VERSION
                    + " · decompiled with Jadx " + JadxDecompiler.getVersion() + "\n";
            int total = decompiler.getClasses().size();
            int done = 0;
            for (JavaClass cls : decompiler.getClasses()) {
                if (log != null && log.isCancelled()) break;
                if (log != null && log.isPaused()) {
                    if (!waitWhilePaused(log)) break;
                }
                done++;
                String full = cls.getFullName();
                if (full == null || full.isEmpty()) {
                    stats.skipped++;
                    continue;
                }
                String rel = full.replace('.', '/') + ".java";
                if (!safeRelative(rel)) {
                    stats.skipped++;
                    continue;
                }
                String code;
                try {
                    code = cls.getCode();
                } catch (Throwable t) {
                    stats.skipped++;
                    if (log != null) log.log(LogManager.DEBUG,
                            "Jadx skipped " + full + ": " + t);
                    continue;
                }
                if (code == null || code.isEmpty()) {
                    stats.skipped++;
                    continue;
                }
                File out = new File(javaRoot, rel);
                if (out.isFile() && out.length() > 0) {
                    // A class can appear in more than one split DEX.  Keep the
                    // first decompilation instead of paying for it twice.
                    stats.classes++;
                    continue;
                }
                try {
                    ZipUtil.writeFile(out, header + code);
                    stats.classes++;
                    stats.javaFiles++;
                    written++;
                } catch (IOException e) {
                    stats.skipped++;
                    if (log != null) log.log(LogManager.WARN, "Could not write " + rel + ": " + e.getMessage());
                }
                if (log != null && (done == 1 || done % 25 == 0 || done == total)) {
                    log.file(rel, 87 + (int) (done * 3.0 / Math.max(1, total)));
                }
            }
        } finally {
            try {
                decompiler.close();
            } catch (Throwable ignored) {
            }
        }
        return written;
    }

    private static boolean waitWhilePaused(LogSink log) {
        while (log.isPaused()) {
            if (log.isCancelled()) return false;
            try {
                Thread.sleep(100L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return !log.isCancelled();
    }

    private static boolean safeRelative(String path) {
        return path != null && !path.isEmpty()
                && !path.startsWith("/") && !path.startsWith("\\")
                && !path.contains("..") && path.indexOf('\\') < 0;
    }

    /** Loads only the DEX input backend; no desktop plugin discovery is needed. */
    private static final class SinglePluginLoader implements JadxPluginLoader {
        @Override
        public List<JadxPlugin> load() {
            return Collections.<JadxPlugin>singletonList(new DexInputPlugin());
        }

        @Override
        public void close() {
        }
    }

    /**
     * XML parsing is not used because resources are skipped.  Implementing the
     * interface avoids pulling {@code javax.xml.parsers}, which is absent on
     * Android, into a live code path.
     */
    private static final class OfflineSecurity implements IJadxSecurity {
        @Override
        public String verifyAppPackage(String appPackage) {
            return appPackage;
        }

        @Override
        public Document parseXml(InputStream in) {
            throw new UnsupportedOperationException("Juzoor skips jadx resource XML");
        }
    }

    /** Keeps every temporary and cache path inside the app's work directory. */
    private static final class OfflineFiles implements IJadxFilesGetter {
        private final Path temp;
        private final Path cache;
        private final Path config;

        OfflineFiles(File temp, File cache, File config) {
            this.temp = temp.getAbsoluteFile().toPath();
            this.cache = cache.getAbsoluteFile().toPath();
            this.config = config.getAbsoluteFile().toPath();
        }

        @Override
        public Path getConfigDir() {
            return config;
        }

        @Override
        public Path getCacheDir() {
            return cache;
        }

        @Override
        public Path getTempDir() {
            return temp;
        }
    }
}
