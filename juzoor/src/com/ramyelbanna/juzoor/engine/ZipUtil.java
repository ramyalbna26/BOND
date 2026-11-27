package com.ramyelbanna.juzoor.engine;

import com.ramyelbanna.juzoor.core.LogManager;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/* loaded from: /workspace/apk_extract/classes.dex */
public final class ZipUtil {
    private ZipUtil() {
    }

    public static int extract(File file, File file2, LogSink logSink) throws IOException {
        if (!file2.exists() && !file2.mkdirs()) {
            throw new IOException("Cannot create " + String.valueOf(file2));
        }
        String str = file2.getCanonicalPath() + File.separator;
        ZipInputStream zipInputStream = new ZipInputStream(new BufferedInputStream(new FileInputStream(file), 65536));
        try {
            byte[] bArr = new byte[65536];
            int i = 0;
            while (true) {
                ZipEntry nextEntry = zipInputStream.getNextEntry();
                if (nextEntry != null) {
                    if (logSink != null && logSink.isCancelled()) {
                        throw new CancelledException();
                    }
                    String name = nextEntry.getName();
                    if (name != null && !name.isEmpty()) {
                        File file3 = new File(file2, name);
                        String canonicalPath = file3.getCanonicalPath();
                        if (canonicalPath.startsWith(str) || canonicalPath.equals(file2.getCanonicalPath())) {
                            if (nextEntry.isDirectory()) {
                                file3.mkdirs();
                            } else {
                                File parentFile = file3.getParentFile();
                                if (parentFile != null && !parentFile.exists()) {
                                    parentFile.mkdirs();
                                }
                                BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(file3), 65536);
                                while (true) {
                                    try {
                                        int read = zipInputStream.read(bArr);
                                        if (read <= 0) {
                                            break;
                                        }
                                        bufferedOutputStream.write(bArr, 0, read);
                                    } finally {
                                    }
                                }
                                bufferedOutputStream.close();
                                i++;
                            }
                        } else if (logSink != null) {
                            logSink.log(LogManager.WARN, "Blocked unsafe zip entry: " + name);
                        }
                    }
                } else {
                    zipInputStream.close();
                    return i;
                }
            }
        } catch (Throwable th) {
            try {
                zipInputStream.close();
            } catch (Throwable th2) {
                th.addSuppressed(th2);
            }
            throw th;
        }
    }

    public static void zipDirectory(File file, File file2) throws IOException {
        File parentFile = file2.getParentFile();
        if (parentFile != null && !parentFile.exists()) {
            parentFile.mkdirs();
        }
        ZipOutputStream zipOutputStream = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(file2), 65536));
        try {
            zipOutputStream.setLevel(6);
            zipRecursive(file, file, zipOutputStream);
            zipOutputStream.close();
        } catch (Throwable th) {
            try {
                zipOutputStream.close();
            } catch (Throwable th2) {
                th.addSuppressed(th2);
            }
            throw th;
        }
    }

    private static void zipRecursive(File file, File file2, ZipOutputStream zipOutputStream) throws IOException {
        File[] listFiles = file2.listFiles();
        if (listFiles == null) {
            return;
        }
        byte[] bArr = new byte[65536];
        for (File file3 : listFiles) {
            String path = file.toURI().relativize(file3.toURI()).getPath();
            if (file3.isDirectory()) {
                if (!path.endsWith("/")) {
                    path = path + "/";
                }
                ZipEntry zipEntry = new ZipEntry(path);
                zipEntry.setTime(file3.lastModified());
                zipOutputStream.putNextEntry(zipEntry);
                zipOutputStream.closeEntry();
                zipRecursive(file, file3, zipOutputStream);
            } else {
                ZipEntry zipEntry2 = new ZipEntry(path);
                zipEntry2.setTime(file3.lastModified());
                zipOutputStream.putNextEntry(zipEntry2);
                BufferedInputStream bufferedInputStream = new BufferedInputStream(new FileInputStream(file3), 65536);
                while (true) {
                    try {
                        int read = bufferedInputStream.read(bArr);
                        if (read <= 0) {
                            break;
                        } else {
                            zipOutputStream.write(bArr, 0, read);
                        }
                    } catch (Throwable th) {
                        try {
                            bufferedInputStream.close();
                        } catch (Throwable th2) {
                            th.addSuppressed(th2);
                        }
                        throw th;
                    }
                }
                bufferedInputStream.close();
                zipOutputStream.closeEntry();
            }
        }
    }

    public static long directorySize(File file) {
        File[] listFiles = file.listFiles();
        long j = 0;
        if (listFiles == null) {
            return 0L;
        }
        for (File file2 : listFiles) {
            j += file2.isDirectory() ? directorySize(file2) : file2.length();
        }
        return j;
    }

    public static int countFiles(File file) {
        File[] listFiles = file.listFiles();
        if (listFiles == null) {
            return 0;
        }
        int i = 0;
        for (File file2 : listFiles) {
            i += file2.isDirectory() ? countFiles(file2) : 1;
        }
        return i;
    }

    public static void deleteRecursive(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        File[] listFiles = file.listFiles();
        if (listFiles != null) {
            for (File file2 : listFiles) {
                deleteRecursive(file2);
            }
        }
        file.delete();
    }

    public static void copy(File file, File file2) throws IOException {
        BufferedInputStream bufferedInputStream = new BufferedInputStream(new FileInputStream(file), 65536);
        try {
            BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(file2), 65536);
            try {
                byte[] bArr = new byte[65536];
                while (true) {
                    int read = bufferedInputStream.read(bArr);
                    if (read <= 0) {
                        return;
                    } else {
                        bufferedOutputStream.write(bArr, 0, read);
                    }
                }
            } finally {
                bufferedOutputStream.close();
            }
        } finally {
            bufferedInputStream.close();
        }
    }

    public static byte[] readFile(File file) throws IOException {
        long length = file.length();
        if (length > 2147483639) {
            throw new IOException("File too large: " + file.getName());
        }
        int i = (int) length;
        byte[] bArr = new byte[i];
        BufferedInputStream bufferedInputStream = new BufferedInputStream(new FileInputStream(file), 65536);
        int i2 = 0;
        while (i2 < i) {
            try {
                int read = bufferedInputStream.read(bArr, i2, i - i2);
                if (read < 0) {
                    break;
                }
                i2 += read;
            } finally {
                bufferedInputStream.close();
            }
        }
        if (i2 == i) {
            return bArr;
        }
        throw new IOException("Short read on " + file.getName());
    }

    public static void writeFile(File file, String str) throws IOException {
        File parentFile = file.getParentFile();
        if (parentFile != null && !parentFile.exists()) {
            parentFile.mkdirs();
        }
        BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(file), 65536);
        try {
            bufferedOutputStream.write(str.getBytes("UTF-8"));
        } finally {
            bufferedOutputStream.close();
        }
    }

    public static void writeFile(File file, byte[] data) throws IOException {
        File parentFile = file.getParentFile();
        if (parentFile != null && !parentFile.exists()) {
            parentFile.mkdirs();
        }
        BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(file), 65536);
        try {
            out.write(data);
        } finally {
            out.close();
        }
    }

    public static class CancelledException extends IOException {
        public CancelledException() {
            super("Cancelled");
        }
    }
}
