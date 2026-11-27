package com.ramyelbanna.juzoor.engine;

import com.ramyelbanna.juzoor.core.LogManager;
import com.ramyelbanna.juzoor.engine.ZipUtil;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/* loaded from: /workspace/apk_extract/classes.dex */
public final class SplitBundle {
    private static final String[] BUNDLE_EXTS = {".apk+", ".apks", ".apkm", ".xapk", ".apkx"};

    private SplitBundle() {
    }

    public static class Part {
        public File apk;
        public boolean base;
        public File dir;
        public String name = "base";
        public final List<File> dexFiles = new ArrayList();

        public String label() {
            return this.base ? "base" : this.name;
        }
    }

    public static class Plan {
        public File baseDir;
        public boolean bundle;
        public File merged;
        public int splitCount;
        public long totalBytes;
        public String kind = "APK";
        public final List<Part> parts = new ArrayList();
        public final List<File> arscFiles = new ArrayList();

        public String describe() {
            if (!this.bundle) {
                return "APK";
            }
            return this.kind + " · " + (this.splitCount + 1) + " APKs";
        }
    }

    public static boolean isBundleName(String str) {
        if (str == null) {
            return false;
        }
        String lowerCase = str.toLowerCase(Locale.US);
        for (String str2 : BUNDLE_EXTS) {
            if (lowerCase.endsWith(str2)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isSupported(String str) {
        if (str == null) {
            return false;
        }
        String lowerCase = str.toLowerCase(Locale.US);
        return lowerCase.endsWith(".apk") || lowerCase.endsWith(".aab") || isBundleName(lowerCase);
    }

    public static String badge(String str) {
        if (str == null) {
            return "APK";
        }
        String lowerCase = str.toLowerCase(Locale.US);
        return lowerCase.endsWith(".apk+") ? "APK+" : lowerCase.endsWith(".apks") ? "APKS" : lowerCase.endsWith(".apkm") ? "APKM" : lowerCase.endsWith(".xapk") ? "XAPK" : lowerCase.endsWith(".apkx") ? "APKX" : lowerCase.endsWith(".aab") ? "AAB" : "APK";
    }

    public static int countApks(File file) {
        if (file != null && file.isFile()) {
            ZipInputStream zipInputStream = null;
            try {
                ZipInputStream zipInputStream2 = new ZipInputStream(new BufferedInputStream(new FileInputStream(file), 8192));
                int i = 0;
                try {
                    do {
                        try {
                            ZipEntry nextEntry = zipInputStream2.getNextEntry();
                            if (nextEntry == null) break;
                            if (nextEntry.getName().toLowerCase(Locale.US).endsWith(".apk")) {
                                i++;
                            }
                        } catch (Throwable unused) {
                            zipInputStream = zipInputStream2;
                            if (zipInputStream != null) {
                                try {
                                    zipInputStream.close();
                                } catch (IOException unused2) {
                                }
                            }
                            return 0;
                        }
                    } while (i <= 512);
                    zipInputStream2.close();
                } catch (IOException unused3) {
                }
                return i;
            } catch (Throwable unused4) {
            }
        }
        return 0;
    }

    public static Plan prepare(File file, File file2, LogSink logSink) throws IOException {
        if (file != null && file.getName().toLowerCase(Locale.US).endsWith(".aab")) {
            throw new IOException("AAB bundles are not expanded on-device. Build an APK set with bundletool, then open the .apks file.");
        }
        File file3;
        int i;
        Plan plan = new Plan();
        plan.totalBytes = file.length();
        File file4 = new File(file2, "stage");
        ZipUtil.deleteRecursive(file4);
        if (!file4.mkdirs()) {
            throw new IOException("Cannot create staging directory");
        }
        boolean isBundleName = isBundleName(file.getName());
        if (!isBundleName) {
            isBundleName = looksLikeContainer(file);
        }
        if (!isBundleName) {
            Part part = new Part();
            part.apk = file;
            part.base = true;
            part.dir = new File(file4, "base");
            if (!part.dir.mkdirs()) {
                throw new IOException("Cannot create staging directory");
            }
            ZipUtil.extract(file, part.dir, logSink);
            collectDex(part);
            plan.parts.add(part);
            plan.baseDir = part.dir;
            plan.merged = part.dir;
            plan.kind = badge(file.getName());
            File file5 = new File(part.dir, "resources.arsc");
            if (file5.isFile()) {
                plan.arscFiles.add(file5);
            }
            return plan;
        }
        plan.bundle = true;
        plan.kind = badge(file.getName());
        if (logSink != null) {
            logSink.log(LogManager.INFO, "Bundle detected (" + plan.kind + ") — expanding splits");
        }
        File file6 = new File(file4, "container");
        if (!file6.mkdirs()) {
            throw new IOException("Cannot create staging directory");
        }
        ZipUtil.extract(file, file6, logSink);
        ArrayList arrayList = new ArrayList();
        findApks(file6, arrayList, 0);
        if (arrayList.isEmpty()) {
            if (logSink != null) {
                logSink.log(LogManager.WARN, "Bundle contains no APK payloads — treating as plain archive");
            }
            plan.bundle = false;
            plan.baseDir = file6;
            plan.merged = file6;
            Part part2 = new Part();
            part2.apk = file;
            part2.dir = file6;
            part2.base = true;
            collectDex(part2);
            plan.parts.add(part2);
            return plan;
        }
        Iterator it = arrayList.iterator();
        while (true) {
            if (!it.hasNext()) {
                file3 = null;
                break;
            }
            file3 = (File) it.next();
            if (file3.getName().equalsIgnoreCase("base.apk")) {
                break;
            }
        }
        if (file3 == null) {
            Iterator it2 = arrayList.iterator();
            while (true) {
                if (!it2.hasNext()) {
                    break;
                }
                File file7 = (File) it2.next();
                if (hasEntry(file7, "resources.arsc")) {
                    file3 = file7;
                    break;
                }
            }
        }
        if (file3 == null) {
            file3 = (File) arrayList.get(0);
        }
        java.util.Collections.sort(arrayList, new java.util.Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });
        Iterator it3 = arrayList.iterator();
        int i2 = 0;
        while (it3.hasNext()) {
            File file8 = (File) it3.next();
            if (logSink != null && logSink.isCancelled()) {
                throw new ZipUtil.CancelledException();
            }
            Part part3 = new Part();
            part3.apk = file8;
            part3.base = file8 == file3;
            part3.name = splitName(file8, file6);
            int i3 = i2 + 1;
            Iterator it4 = it3;
            part3.dir = new File(file4, "part" + i2 + "_" + sanitize(part3.name));
            if (!part3.dir.mkdirs()) {
                throw new IOException("Cannot create split directory");
            }
            try {
                ZipUtil.extract(file8, part3.dir, logSink);
                collectDex(part3);
                plan.parts.add(part3);
                if (part3.base) {
                    plan.baseDir = part3.dir;
                }
                if (logSink != null) {
                    logSink.log(LogManager.DEBUG, "Split " + part3.label() + ": " + part3.dexFiles.size() + " dex, " + ZipUtil.countFiles(part3.dir) + " files");
                }
            } catch (IOException e) {
                if (logSink != null) {
                    logSink.log(LogManager.WARN, "Split unreadable, skipped: " + file8.getName() + " (" + e.getMessage() + ")");
                }
            }
            it3 = it4;
            i2 = i3;
        }
        if (plan.baseDir != null || plan.parts.isEmpty()) {
            i = 0;
        } else {
            i = 0;
            plan.baseDir = plan.parts.get(0).dir;
        }
        plan.splitCount = Math.max(i, plan.parts.size() - 1);
        File file9 = new File(file4, "merged");
        if (!file9.mkdirs()) {
            throw new IOException("Cannot create merged directory");
        }
        for (Part part4 : plan.parts) {
            if (logSink != null && logSink.isCancelled()) {
                throw new ZipUtil.CancelledException();
            }
            overlay(part4.dir, file9);
        }
        plan.merged = file9;
        Iterator<Part> it5 = plan.parts.iterator();
        while (it5.hasNext()) {
            File file10 = new File(it5.next().dir, "resources.arsc");
            if (file10.isFile()) {
                plan.arscFiles.add(file10);
            }
        }
        if (logSink != null) {
            logSink.log(LogManager.INFO, "Bundle merged: " + plan.parts.size() + " APKs (" + plan.splitCount + " splits), " + ZipUtil.countFiles(file9) + " files");
        }
        return plan;
    }

    private static void findApks(File file, List<File> list, int i) {
        File[] listFiles;
        if (i <= 6 && (listFiles = file.listFiles()) != null) {
            for (File file2 : listFiles) {
                if (file2.isDirectory()) {
                    findApks(file2, list, i + 1);
                } else if (file2.getName().toLowerCase(Locale.US).endsWith(".apk")) {
                    list.add(file2);
                }
            }
        }
    }

    private static boolean looksLikeContainer(File file) {
        return file.getName().toLowerCase(Locale.US).endsWith(".zip") && countApks(file) > 0;
    }

    private static boolean hasEntry(File file, String str) {
        ZipFile zipFile = null;
        try {
            ZipFile zipFile2 = new ZipFile(file);
            try {
                boolean z = zipFile2.getEntry(str) != null;
                try {
                    zipFile2.close();
                } catch (IOException unused) {
                }
                return z;
            } catch (Throwable unused2) {
                zipFile = zipFile2;
                if (zipFile != null) {
                    try {
                        zipFile.close();
                    } catch (IOException unused3) {
                    }
                }
                return false;
            }
        } catch (Throwable unused4) {
        }
        return false;
    }

    private static void collectDex(Part part) {
        File[] listFiles = part.dir.listFiles();
        if (listFiles == null) {
            return;
        }
        for (File file : listFiles) {
            String name = file.getName();
            if (file.isFile() && name.startsWith("classes") && name.endsWith(".dex")) {
                part.dexFiles.add(file);
            }
        }
        Collections.sort(part.dexFiles);
    }

    private static String splitName(File file, File file2) {
        String name = file.getName();
        if (name.toLowerCase(Locale.US).endsWith(".apk")) {
            name = name.substring(0, name.length() - 4);
        }
        if (name.equalsIgnoreCase("base")) {
            return "base";
        }
        String replaceAll = name.replaceAll("(?i)^split[_-]?", "").replaceAll("(?i)^config[._-]?", "");
        return replaceAll.isEmpty() ? file.getAbsolutePath().substring(file2.getAbsolutePath().length()).replace('/', '_').replaceAll("(?i)\\.apk$", "") : replaceAll;
    }

    private static String sanitize(String str) {
        String replaceAll = str.replaceAll("[^A-Za-z0-9._-]", "_");
        return replaceAll.isEmpty() ? "part" : replaceAll;
    }

    private static void overlay(File file, File file2) throws IOException {
        File[] listFiles = file.listFiles();
        if (listFiles == null) {
            return;
        }
        for (File file3 : listFiles) {
            File file4 = new File(file2, file3.getName());
            if (file3.isDirectory()) {
                if (!file4.exists() && !file4.mkdirs()) {
                    throw new IOException("Cannot create " + String.valueOf(file4));
                }
                overlay(file3, file4);
            } else {
                File parentFile = file4.getParentFile();
                if (parentFile != null && !parentFile.exists() && !parentFile.mkdirs()) {
                    throw new IOException("Cannot create " + String.valueOf(parentFile));
                }
                ZipUtil.copy(file3, file4);
            }
        }
    }
}
