package com.ramyelbanna.juzoor.engine;

import com.ramyelbanna.juzoor.core.LogManager;

import org.w3c.dom.Document;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;

import jadx.api.JadxArgs;
import jadx.api.JadxDecompiler;
import jadx.api.JavaClass;
import jadx.api.impl.NoOpCodeCache;
import jadx.api.plugins.JadxPlugin;
import jadx.api.plugins.loader.JadxPluginLoader;
import jadx.api.security.IJadxSecurity;
import jadx.api.usage.impl.EmptyUsageInfoCache;
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
 * <p>When a DEX is too large for the current heap, {@link DexBatcher} rewrites
 * it into smaller valid DEX files and each batch is decompiled separately.  A
 * batch that still runs out of memory is split further, down to a single class group.
 * Class names are kept stable across batches, so files written by an earlier
 * attempt are reused rather than duplicated.</p>
 *
 * <p>The DEX input plugin is registered explicitly instead of relying on
 * {@code META-INF/services}.  Android packaging does not guarantee that a
 * desktop service-loader file survives, so this makes the engine deterministic
 * on-device.</p>
 */
public final class SourceDecompiler {

    public static final String ENGINE = ConversionResult.JAVA_ENGINE;

    private static final String[] TEMP_DIRS = {"jadx-tmp", "jadx-cache", "jadx-config"};
    private static final String CHUNK_DIR = "jadx-chunks";

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
        return decompile(dexFiles, javaRoot, workDir, log, 0);
    }

    /**
     * @param maxClassesPerBatch positive to force batching (used by tests),
     *                           zero to pick a safe size from the heap limit
     */
    static Stats decompile(List<File> dexFiles, File javaRoot, File workDir, LogSink log,
                           int maxClassesPerBatch) {
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
            boolean complete;
            if (maxClassesPerBatch > 0 || shouldBatch(dex)) {
                int target = maxClassesPerBatch > 0 ? maxClassesPerBatch : batchTarget();
                complete = decompileBatched(dex, javaRoot, workDir, log, stats, target);
            } else {
                complete = decompileWhole(dex, javaRoot, workDir, log, stats);
            }
            if (!complete && (log == null || !log.isCancelled())) {
                stats.failedDexes.add(dex);
                if (log != null) log.log(LogManager.WARN,
                        "Java source is incomplete for " + dex.getName() + "; stub fallback will fill the gaps");
            }
            // Release the per-DEX class graph before loading the next split.
            System.gc();
        }
        return stats;
    }

    private static boolean decompileWhole(File dex, File javaRoot, File workDir, LogSink log,
                                          Stats stats) {
        int[] before = snapshot(stats);
        try {
            int produced = decompileOne(dex, dex.getName(), javaRoot, workDir, log, stats);
            if (produced > 0) {
                if (log != null) log.log(LogManager.INFO,
                        "Java sources from " + dex.getName() + ": " + produced);
                return true;
            }
            if (log != null) log.log(LogManager.WARN,
                    "Jadx returned no classes for " + dex.getName());
            restore(stats, before);
            return false;
        } catch (OutOfMemoryError oom) {
            restore(stats, before);
            if (log != null) log.log(LogManager.WARN,
                    "Low memory while decompiling " + dex.getName() + "; retrying in bounded batches");
            System.gc();
            return decompileBatched(dex, javaRoot, workDir, log, stats, batchTarget());
        } catch (Throwable t) {
            restore(stats, before);
            if (log != null) log.log(LogManager.WARN,
                    "Jadx failed for " + dex.getName() + ": " + t);
            return false;
        }
    }

    private static boolean decompileBatched(File dex, File javaRoot, File workDir, LogSink log,
                                            Stats stats, int targetClasses) {
        DexBatcher.Plan plan;
        try {
            plan = DexBatcher.plan(dex, targetClasses);
        } catch (OutOfMemoryError oom) {
            if (log != null) log.log(LogManager.ERROR,
                    "Not enough memory to split " + dex.getName() + "; smaller stub tree will be used");
            return false;
        } catch (Throwable t) {
            if (log != null) log.log(LogManager.WARN,
                    "Could not split " + dex.getName() + ": " + t);
            return false;
        }
        if (plan.batches.isEmpty()) return false;

        File chunkDir = new File(workDir, CHUNK_DIR);
        chunkDir.mkdirs();
        Deque<DexBatcher.Batch> queue = new ArrayDeque<>(plan.batches);
        int batchNo = 0;
        boolean complete = true;
        while (!queue.isEmpty()) {
            if (log != null && log.isCancelled()) return true;
            DexBatcher.Batch batch = queue.removeFirst();
            if (batch.isEmpty()) continue;
            int[] before = snapshot(stats);
            File temp = new File(chunkDir, "batch-" + (batchNo++) + ".dex");
            try {
                plan.write(batch, temp);
                String label = plan.sourceName() + " batch " + batchNo;
                int produced = decompileOne(temp, label, javaRoot, workDir, log, stats);
                if (produced == 0) {
                    complete = false;
                    if (log != null) log.log(LogManager.WARN,
                            "No Java source produced for " + label + " (" + batch.size() + " classes)");
                }
            } catch (OutOfMemoryError oom) {
                restore(stats, before);
                if (batch.canSplit()) {
                    if (log != null) log.log(LogManager.WARN,
                            "Splitting a " + batch.size() + "-class batch after running out of memory");
                    queue.addFirst(batch.secondHalf());
                    queue.addFirst(batch.firstHalf());
                } else {
                    complete = false;
                    if (log != null) log.log(LogManager.ERROR,
                            "Not enough memory for class in " + plan.sourceName() + "; stub fallback will be used");
                }
            } catch (Throwable t) {
                restore(stats, before);
                complete = false;
                if (log != null) log.log(LogManager.WARN,
                        "Batch " + batchNo + " of " + plan.sourceName() + " failed: " + t);
            } finally {
                temp.delete();
            }
            System.gc();
        }
        return complete;
    }

    /**
     * A full Jadx run needs roughly 55 times the DEX size in heap.  When that
     * does not fit comfortably, batching avoids an OutOfMemoryError instead of
     * waiting for one and then recovering.
     */
    private static boolean shouldBatch(File dex) {
        long max = Runtime.getRuntime().maxMemory();
        if (max <= 0) return true;
        long estimate = dex.length() * 55L;
        return estimate > max * 3L / 4L;
    }

    private static int batchTarget() {
        long mb = Runtime.getRuntime().maxMemory() / (1024L * 1024L);
        return (int) Math.max(64L, Math.min(256L, mb * 2L));
    }

    private static int[] snapshot(Stats stats) {
        return new int[]{stats.classes, stats.javaFiles, stats.skipped};
    }

    private static void restore(Stats stats, int[] values) {
        stats.classes = values[0];
        stats.javaFiles = values[1];
        stats.skipped = values[2];
    }

    /**
     * Decompiles one DEX file and returns the number of classes that have a
     * source file (newly written or already present from an earlier attempt).
     */
    private static int decompileOne(File dex, String label, File javaRoot, File workDir, LogSink log,
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
        // Jadx normally keeps every generated class in an in-memory code
        // cache.  On a phone that cache alone can exhaust the heap while
        // hundreds of classes are streamed to disk.  Juzoor writes each class
        // immediately, so the cache is intentionally disabled.
        args.setCodeCache(NoOpCodeCache.INSTANCE);
        args.setUsageInfoCache(new EmptyUsageInfoCache());
        args.setShowInconsistentCode(false);
        args.setDeobfuscationOn(false);
        // Keep one source file per DEX class.  Inlining anonymous classes makes
        // the output depend on which other classes are present in the batch;
        // stable names are required for batching and for the stub fallback.
        args.setInlineAnonymousClasses(false);
        args.setMoveInnerClasses(true);
        args.setRenameFlags(EnumSet.of(JadxArgs.RenameEnum.VALID, JadxArgs.RenameEnum.PRINTABLE));
        args.setPluginLoader(new SinglePluginLoader());
        args.setSecurity(new OfflineSecurity());
        args.setFilesGetter(new OfflineFiles(temp, cache, config));

        JadxDecompiler decompiler = new JadxDecompiler(args);
        int produced = 0;
        try {
            decompiler.load();
            if (log != null) {
                log.log(LogManager.INFO, "Jadx loaded " + label + ": "
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
                    produced++;
                    continue;
                }
                try {
                    ZipUtil.writeFile(out, header + code);
                    stats.classes++;
                    stats.javaFiles++;
                    produced++;
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
        return produced;
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
