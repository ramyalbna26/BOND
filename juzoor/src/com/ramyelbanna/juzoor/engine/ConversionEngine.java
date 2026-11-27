package com.ramyelbanna.juzoor.engine;

import com.ramyelbanna.juzoor.core.LogManager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The Juzoor conversion pipeline.
 *
 * <pre>
 *   unpack  →  manifest  →  resources  →  copy tree  →  smali
 *           →  DEX strings  →  native (.so)  →  libraries  →  project  →  zip
 * </pre>
 *
 * Every stage is cancellation aware and reports progress through {@link LogSink}.
 */
public class ConversionEngine {

    private final File apk;
    private final File workDir;
    private final ConversionOptions options;
    private final LogSink log;
    private final ConversionResult result = new ConversionResult();

    public ConversionEngine(File apk, File workDir, ConversionOptions options, LogSink log) {
        this.apk = apk;
        this.workDir = workDir;
        this.options = options == null ? new ConversionOptions() : options;
        this.log = log;
    }

    public ConversionResult run() {
        long started = System.currentTimeMillis();
        result.apkFile = apk;
        File project = new File(workDir, "project");
        try {
            if (apk == null || !apk.isFile()) throw new IOException("Input file not found");
            log.log(LogManager.INFO, "Conversion started: " + apk.getName() + " (" + human(apk.length()) + ")");
            ZipUtil.deleteRecursive(project);
            if (!project.mkdirs()) throw new IOException("Cannot create work directory");

            File main = new File(project, "app/src/main");
            File resDir = new File(main, "res");
            File analysis = new File(project, "analysis");
            analysis.mkdirs();

            /* ---------------------------------------------------------- unpack */
            log.progress(3, "Unpacking archive");
            SplitBundle.Plan plan = SplitBundle.prepare(apk, workDir, log);
            checkCancel();
            result.isBundle = plan.bundle;
            result.bundleKind = plan.kind;
            result.splitCount = plan.splitCount;
            File merged = plan.merged;
            log.log(LogManager.INFO, "Archive unpacked: " + ZipUtil.countFiles(merged) + " entries"
                    + (plan.bundle ? " (" + plan.describe() + ")" : ""));

            /* -------------------------------------------------------- manifest */
            File manifest = new File(plan.baseDir == null ? merged : plan.baseDir, "AndroidManifest.xml");
            if (!manifest.isFile()) manifest = new File(merged, "AndroidManifest.xml");
            String decodedManifest = null;
            if (manifest.isFile()) {
                log.progress(10, "Decoding AndroidManifest.xml");
                log.file("AndroidManifest.xml", 10);
                try {
                    decodedManifest = BinaryXml.decode(ZipUtil.readFile(manifest));
                    ZipUtil.writeFile(new File(project, "AndroidManifest.decoded.xml"), decodedManifest);
                    readIdentity(decodedManifest);
                    log.log(LogManager.INFO, "Manifest decoded: package=" + result.packageName
                            + " version=" + result.versionName + " (" + result.versionCode + ")");
                } catch (Throwable t) {
                    result.warnings.add("Manifest decode failed: " + t);
                    log.log(LogManager.ERROR, "Manifest decode failed: " + t);
                }
            } else {
                result.warnings.add("No AndroidManifest.xml inside the archive");
                log.log(LogManager.WARN, "No AndroidManifest.xml inside the archive");
            }
            checkCancel();

            /* ------------------------------------------------------- resources */
            ResourceTable baseTable = null;
            if (options.decodeResources && !plan.arscFiles.isEmpty()) {
                log.progress(18, "Decoding resource table");
                int total = 0;
                for (int i = 0; i < plan.arscFiles.size(); i++) {
                    checkCancel();
                    File arsc = plan.arscFiles.get(i);
                    boolean base = i == 0;
                    log.file("resources.arsc" + (base ? "" : " #" + (i + 1)), 18 + i * 2);
                    try {
                        ResourceTable table = new ResourceTable(ZipUtil.readFile(arsc));
                        table.parse();
                        if (base) {
                            baseTable = table;
                            table.writeValues(resDir, log);
                            total += table.getEntryCount();
                        } else {
                            File tmp = new File(workDir, "splitres" + i);
                            ZipUtil.deleteRecursive(tmp);
                            tmp.mkdirs();
                            table.writeValues(tmp, null);
                            total += ResMerger.merge(tmp, resDir, log);
                            ZipUtil.deleteRecursive(tmp);
                        }
                    } catch (Throwable t) {
                        result.warnings.add("Resource table " + (i + 1) + " failed: " + t);
                        log.log(LogManager.WARN, "Resource table " + (i + 1) + " failed: " + t);
                    }
                }
                result.resourceCount = total;
                log.log(LogManager.INFO, "Resources decoded: " + total + " entries");
            } else if (plan.arscFiles.isEmpty()) {
                log.log(LogManager.WARN, "No resources.arsc inside the archive");
            }
            String securityManifest = decodedManifest;
            if (decodedManifest != null) {
                decodedManifest = ManifestFixer.fix(decodedManifest, baseTable);
                try {
                    ZipUtil.writeFile(new File(project, "AndroidManifest.decoded.xml"), decodedManifest);
                } catch (IOException ignored) {
                }
            }
            checkCancel();

            /* ------------------------------------------------------ copy tree */
            log.progress(30, "Copying resources and assets");
            if (options.copyAssets) {
                copyResources(new File(merged, "res"), resDir, baseTable);
                copyTree(new File(merged, "assets"), new File(main, "assets"), "assets");
                copyTree(new File(merged, "lib"), new File(main, "jniLibs"), "lib");
                copyTree(new File(merged, "META-INF"), new File(project, "META-INF"), "META-INF");
                copyTree(new File(merged, "kotlin"), new File(main, "kotlin"), "kotlin");
                log.log(LogManager.DEBUG, "Raw resources, assets and native libraries copied");
            }
            checkCancel();

            /* ------------------------------------------------------- framework */
            log.progress(32, "Detecting application framework");
            try {
                AppFramework.Report fw = AppFramework.detect(merged, analysis, log);
                result.framework = fw.kind;
                result.frameworkLabel = fw.label;
                result.frameworkHint = fw.hint;
                result.frameworkAssets = fw.assetFiles;
                if (!AppFramework.NATIVE.equals(fw.kind)) {
                    log.file(fw.label + " assets", 32);
                }
            } catch (Throwable t) {
                result.warnings.add("Framework detection failed: " + t);
                log.log(LogManager.WARN, "Framework detection failed: " + t);
            }
            checkCancel();

            /* ----------------------------------------------------------- smali */
            List<DexStrings.Report> dexReports = new ArrayList<>();
            List<File> dexFiles = new ArrayList<>();
            for (SplitBundle.Part part : plan.parts) dexFiles.addAll(part.dexFiles);
            result.dexCount = dexFiles.size();
            log.log(LogManager.INFO, "Found " + dexFiles.size() + " dex file(s) across " + plan.parts.size() + " APK(s)");

            if (options.disassembleDex) {
                File smaliRoot = new File(main, "smali");
                int index = 0;
                for (SplitBundle.Part part : plan.parts) {
                    for (File dex : part.dexFiles) {
                        checkCancel();
                        int pct = 35 + (int) (index * 32.0 / Math.max(1, dexFiles.size()));
                        log.progress(pct, "Disassembling " + dex.getName());
                        log.file(part.label() + "/" + dex.getName() + " → Smali", pct);
                        File out = index == 0 ? smaliRoot : new File(main, "smali_classes" + (index + 1));
                        try {
                            DexDisassembler dis = new DexDisassembler(ZipUtil.readFile(dex), out, log);
                            dis.disassemble();
                            result.classCount += dis.getClassCount();
                            log.log(LogManager.DEBUG, part.label() + "/" + dex.getName() + ": "
                                    + dis.getClassCount() + " classes");
                        } catch (Throwable t) {
                            result.warnings.add("Failed to disassemble " + dex.getName() + ": " + t);
                            log.log(LogManager.ERROR, "Failed to disassemble " + dex.getName() + ": " + t);
                        }
                        index++;
                    }
                }
            }
            checkCancel();

            /* ------------------------------------------------------ dex strings */
            Set<String> descriptors = new LinkedHashSet<>();
            if (options.decryptStrings || options.analyzeLibraries) {
                log.progress(70, "Recovering DEX string tables");
                List<File> smaliRoots = new ArrayList<>();
                File[] mainChildren = main.listFiles();
                if (mainChildren != null) {
                    for (File child : mainChildren) {
                        if (child.isDirectory() && child.getName().startsWith("smali")) smaliRoots.add(child);
                    }
                }
                for (File dex : dexFiles) {
                    checkCancel();
                    log.file(dex.getName() + " → strings", 70);
                    try {
                        byte[] data = ZipUtil.readFile(dex);
                        List<String> all = DexStrings.readStrings(data);
                        for (String s : all) {
                            if (s.length() > 2 && s.charAt(0) == 'L' && s.endsWith(";")) descriptors.add(s);
                        }
                        if (options.decryptStrings) {
                            DexStrings.Report rep = DexStrings.analyze(dex, analysis, smaliRoots, log);
                            dexReports.add(rep);
                            result.stringTotal += rep.total;
                            result.stringEncrypted += rep.encoded + rep.obfuscated;
                            result.stringRecovered += rep.recovered;
                        }
                    } catch (Throwable t) {
                        result.warnings.add("String analysis failed for " + dex.getName() + ": " + t);
                    }
                }
            }
            checkCancel();

            /* ---------------------------------------------------------- native */
            List<NativeAnalyzer.Report> nativeReports = new ArrayList<>();
            List<File> soFiles = new ArrayList<>();
            collect(soFiles, new File(main, "jniLibs"));
            collect(soFiles, new File(main, "assets"));
            result.nativeLibCount = soFiles.size();
            if (options.decryptNative && !soFiles.isEmpty()) {
                log.progress(76, "Analysing " + soFiles.size() + " native libraries");
                for (File so : soFiles) {
                    checkCancel();
                    log.file("lib/" + so.getParentFile().getName() + "/" + so.getName(), 76);
                    try {
                        NativeAnalyzer.Report rep = NativeAnalyzer.analyze(so, analysis, log);
                        nativeReports.add(rep);
                        result.nativeLibs.add(rep.name + " [" + rep.abi + "]");
                        for (String p : rep.packers) {
                            if (!result.packers.contains(p)) result.packers.add(p);
                        }
                        if (rep.decrypted) result.nativeDecryptedCount++;
                    } catch (Throwable t) {
                        result.warnings.add("Native analysis failed for " + so.getName() + ": " + t);
                        log.log(LogManager.WARN, "Native analysis failed for " + so.getName() + ": " + t);
                    }
                }
                result.packerCount = result.packers.size();
            }
            checkCancel();

            /* ------------------------------------------------------- libraries */
            List<LibrarySupport.Library> libs = new ArrayList<>();
            if (options.analyzeLibraries) {
                log.progress(80, "Detecting libraries");
                libs = LibrarySupport.detect(new ArrayList<>(descriptors));
                result.libraryCount = libs.size();
                for (LibrarySupport.Library l : libs) result.libraries.add(l.name);
                try {
                    ZipUtil.writeFile(new File(analysis, "libraries.json"), LibrarySupport.toJson(libs));
                } catch (IOException ignored) {
                }
                log.log(LogManager.INFO, "Libraries detected: " + libs.size());
            }

            /* -------------------------------------------------------- security */
            if (options.securityScan) {
                log.progress(82, "Running security analysis");
                try {
                    SecurityAnalyzer.Report sec = SecurityAnalyzer.analyze(merged, securityManifest, dexFiles,
                            nativeReports, libs, analysis, log);
                    sec.packageName = result.packageName;
                    result.security = sec;
                    result.securityScore = sec.riskScore;
                    result.securityFindings = sec.total();
                    result.trackerCount = sec.trackers.size();
                    result.secretCount = sec.secrets.size();
                    log.log(LogManager.INFO, "Security: risk " + sec.riskScore + "/100 (" + sec.riskLabel()
                            + "), " + sec.total() + " findings, " + sec.trackers.size() + " trackers");
                } catch (Throwable t) {
                    result.warnings.add("Security analysis failed: " + t);
                    log.log(LogManager.WARN, "Security analysis failed: " + t);
                }
            }
            checkCancel();

            /* --------------------------------------------------------- project */
            if (options.generateProject) {
                log.progress(86, "Generating Gradle project");
                log.file("app/build.gradle", 86);
                ProjectWriter.write(project, result, options, libs, decodedManifest, log);
            }

            /* -------------------------------------------------- source stubs */
            if (options.sourceStubs && options.generateProject) {
                log.progress(87, "Recovering Java / Kotlin source stubs");
                try {
                    SourceWriter.Stats st = SourceWriter.write(dexFiles, new File(main, "recovered/java"),
                            new File(main, "recovered/kotlin"), options.kotlinStubs, log);
                    result.javaFileCount = st.javaFiles;
                    result.kotlinFileCount = st.kotlinFiles;
                } catch (Throwable t) {
                    result.warnings.add("Source stubs failed: " + t);
                    log.log(LogManager.WARN, "Source stubs failed: " + t);
                }
            }
            checkCancel();

            result.fileCount = ZipUtil.countFiles(project);
            result.durationMs = System.currentTimeMillis() - started;
            if (options.writeReport) {
                log.progress(89, "Writing analysis report");
                log.file("REPORT.md", 89);
                try {
                    ReportWriter.write(project, result, nativeReports, dexReports, libs, log);
                } catch (Throwable t) {
                    log.log(LogManager.WARN, "Report failed: " + t);
                }
            }

            /* ---------------------------------------------------- project tree */
            if (options.projectTree) {
                try {
                    log.file("PROJECT_TREE.txt", 90);
                    result.treeFile = ProjectTree.write(project, log);
                    result.fileCount = ZipUtil.countFiles(project);
                    ProjectTree.writeIndex(project, result, dexReports, nativeReports, log);
                } catch (Throwable t) {
                    log.log(LogManager.WARN, "Project tree failed: " + t);
                }
            }

            /* ------------------------------------------- finalize metrics (2nd) */
            // The report and tree just added files, so recompute the count and
            // duration and refresh README/REPORT so every document agrees.
            result.fileCount = ZipUtil.countFiles(project);
            result.durationMs = System.currentTimeMillis() - started;
            if (options.writeReport) {
                try {
                    ReportWriter.write(project, result, nativeReports, dexReports, libs, log);
                } catch (Throwable ignored) {
                }
            }
            try {
                ProjectWriter.refresh(project, result);
            } catch (Throwable ignored) {
            }

            /* ------------------------------------------------------------- zip */
            log.progress(92, "Packing ZIP archive");
            log.file("source ZIP", 92);
            String clean = apk.getName().replaceAll("(?i)\\.(apk\\+|apk|apkx|aab|apks|apkm|xapk|zip)$", "");
            File zip = new File(workDir, (clean.isEmpty() ? "project" : clean) + "-source.zip");
            ZipUtil.zipDirectory(project, zip);
            result.zipFile = zip;
            result.outputDir = project;
            result.fileCount = ZipUtil.countFiles(project);
            result.bytes = zip.length();
            result.success = true;
            result.durationMs = System.currentTimeMillis() - started;
            log.progress(100, "Done");
            log.log(LogManager.INFO, "Source code generated: " + zip.getAbsolutePath());
            log.log(LogManager.SUCCESS, "Conversion completed successfully in " + (result.durationMs / 1000) + "s");
            return result;

        } catch (ZipUtil.CancelledException e) {
            result.error = "Cancelled";
            result.durationMs = System.currentTimeMillis() - started;
            log.log(LogManager.WARN, "Conversion cancelled by user");
            return result;
        } catch (Throwable t) {
            result.error = String.valueOf(t);
            result.durationMs = System.currentTimeMillis() - started;
            log.log(LogManager.ERROR, "Conversion failed: " + t);
            return result;
        }
    }

    /* ------------------------------------------------------------- helpers */

    private static void collect(List<File> out, File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) collect(out, f);
            else if (f.getName().toLowerCase(Locale.US).endsWith(".so")) out.add(f);
        }
    }

    private void readIdentity(String xml) {
        try {
            int p = xml.indexOf("package=\"");
            if (p >= 0) {
                int s = p + 9;
                int e = xml.indexOf('"', s);
                if (e > s) result.packageName = xml.substring(s, e);
            }
            result.versionName = attr(xml, "android:versionName");
            result.minSdk = intAttr(xml, "android:minSdkVersion", 26);
            result.targetSdk = intAttr(xml, "android:targetSdkVersion", 34);
            String vc = attr(xml, "android:versionCode");
            if (vc != null) {
                try {
                    result.versionCode = Long.parseLong(vc.trim());
                } catch (Exception ignored) {
                }
            }
            String label = attr(xml, "android:label");
            if (label != null && !label.startsWith("@")) result.appLabel = label;
        } catch (Exception ignored) {
        }
    }

    private static String attr(String xml, String name) {
        int p = xml.indexOf(name + "=\"");
        if (p < 0) return null;
        int s = p + name.length() + 2;
        int e = xml.indexOf('"', s);
        return e > s ? xml.substring(s, e) : null;
    }

    private static int intAttr(String xml, String name, int fallback) {
        String value = attr(xml, name);
        if (value == null) return fallback;
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    /**
     * Copies resources while turning binary Android XML back into editable
     * text.  Raw {@code values*} folders are skipped because the resource table
     * decoder already wrote the authoritative values there.
     */
    private void copyResources(File from, File to, ResourceTable table) {
        if (from == null || !from.isDirectory()) return;
        copyResourcesTree(from, from, to, table);
    }

    private void copyResourcesTree(File sourceRoot, File from, File to, ResourceTable table) {
        File[] files = from.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (log.isCancelled()) return;
            if (f.getName().startsWith("values")) continue;
            String rel = sourceRoot.toURI().relativize(f.toURI()).getPath();
            String mapped = table == null ? null : table.filePathFor("res/" + rel);
            if (!safeRelative(mapped)) mapped = rel;
            if (!safeRelative(mapped)) {
                result.warnings.add("Unsafe resource path skipped: " + rel);
                continue;
            }
            File dest = new File(to, mapped);
            if (f.isDirectory()) {
                dest.mkdirs();
                copyResourcesTree(sourceRoot, f, to, table);
                continue;
            }
            File parent = dest.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            String lower = dest.getName().toLowerCase(Locale.US);
            try {
                byte[] data = ZipUtil.readFile(f);
                if (lower.endsWith(".xml") && data.length >= 4
                        && (data[0] & 0xFF) == 0x03 && (data[1] & 0xFF) == 0x00) {
                    String xml = ManifestFixer.fix(BinaryXml.decode(data), table);
                    ZipUtil.writeFile(dest, xml);
                    log.file("res/" + mapped + " → XML", -1);
                } else {
                    ZipUtil.writeFile(dest, data);
                    log.file("res/" + mapped, -1);
                }
            } catch (Throwable t) {
                try {
                    ZipUtil.copy(f, dest);
                } catch (IOException ignored) {
                }
                result.warnings.add("Could not decode resource " + mapped + ": " + t.getMessage());
                log.log(LogManager.WARN, "Resource kept as-is: " + mapped + " (" + t.getMessage() + ")");
            }
        }
    }

    /** Rejects absolute/parent-relative paths from an untrusted archive. */
    private static boolean safeRelative(String path) {
        if (path == null || path.isEmpty()) return false;
        if (path.startsWith("/") || path.startsWith("\\")) return false;
        if (path.contains("..")) return false;
        return path.indexOf('\\') < 0;
    }

    private void copyTree(File from, File to, String prefix) {
        if (from == null || !from.isDirectory()) return;
        File[] files = from.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (log.isCancelled()) return;
            File dest = new File(to, f.getName());
            if (f.isDirectory()) {
                dest.mkdirs();
                copyTree(f, dest, prefix + "/" + f.getName());
            } else {
                log.file(prefix + "/" + f.getName(), -1);
                try {
                    if (!dest.exists() || dest.length() != f.length()) {
                        File parent = dest.getParentFile();
                        if (parent != null && !parent.exists()) parent.mkdirs();
                        ZipUtil.copy(f, dest);
                    }
                } catch (IOException e) {
                    result.warnings.add("Could not copy " + f.getName() + ": " + e.getMessage());
                    log.log(LogManager.WARN, "Could not copy " + f.getName() + ": " + e.getMessage());
                }
            }
        }
    }

    private void checkCancel() throws ZipUtil.CancelledException {
        while (log.isPaused()) {
            if (log.isCancelled()) throw new ZipUtil.CancelledException();
            try {
                Thread.sleep(100L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ZipUtil.CancelledException();
            }
        }
        if (log.isCancelled()) throw new ZipUtil.CancelledException();
    }

    public static String human(long j) {
        if (j < 1024) return j + " B";
        double d = j / 1024.0d;
        if (d < 1024.0d) return String.format(Locale.US, "%.1f KB", d);
        double d2 = d / 1024.0d;
        return d2 < 1024.0d ? String.format(Locale.US, "%.1f MB", d2)
                : String.format(Locale.US, "%.2f GB", d2 / 1024.0d);
    }
}
