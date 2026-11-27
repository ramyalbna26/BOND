package com.ramyelbanna.juzoor.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;

public class GlobalSearchTest {

    @Test
    public void findsTextAndRegexHitsAcrossFolders() throws Exception {
        File root = tempDir();
        write(new File(root, "app/src/main/assets/words.txt"), "alpha\nSecretToken=abc123\n");
        GlobalSearch.Request request = new GlobalSearch.Request();
        request.query = "secret\\w+=abc\\d+";
        request.regex = true;
        GlobalSearch.Report report = GlobalSearch.search(root, request, null);
        assertEquals(1, report.hits.size());
        assertEquals("app/src/main/assets/words.txt", report.hits.get(0).path);
        assertEquals(2, report.hits.get(0).line);
    }

    @Test
    public void searchesNativeBinaryWithoutLoadingItAsText() throws Exception {
        File root = tempDir();
        File so = new File(root, "lib/arm64-v8a/libfake.so");
        byte[] prefix = new byte[4096];
        byte[] marker = "native_secret_123".getBytes("US-ASCII");
        System.arraycopy(marker, 0, prefix, 1024, marker.length);
        write(so, prefix);
        GlobalSearch.Request request = new GlobalSearch.Request();
        request.query = "native_secret";
        GlobalSearch.Report report = GlobalSearch.search(root, request, null);
        assertEquals(1, report.hits.size());
        assertTrue(report.hits.get(0).binary);
        assertEquals(1024L, report.hits.get(0).offset);
    }

    @Test
    public void invalidRegexIsReported() throws Exception {
        GlobalSearch.Request request = new GlobalSearch.Request();
        request.query = "[";
        request.regex = true;
        GlobalSearch.Report report = GlobalSearch.search(tempDir(), request, null);
        assertTrue(report.error.startsWith("Invalid regular expression"));
    }

    private static File tempDir() throws Exception {
        File dir = File.createTempFile("juzoor-search", "");
        assertTrue(dir.delete());
        assertTrue(dir.mkdirs());
        dir.deleteOnExit();
        return dir;
    }

    private static void write(File file, String text) throws Exception {
        write(file, text.getBytes("UTF-8"));
    }

    private static void write(File file, byte[] data) throws Exception {
        File parent = file.getParentFile();
        if (parent != null) parent.mkdirs();
        FileOutputStream out = new FileOutputStream(file);
        out.write(data);
        out.close();
    }
}
