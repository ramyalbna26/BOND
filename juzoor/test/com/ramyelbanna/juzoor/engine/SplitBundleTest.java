package com.ramyelbanna.juzoor.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class SplitBundleTest {

    @Test
    public void countsEveryApkEntryInContainer() throws Exception {
        File zip = File.createTempFile("juzoor-bundle", ".xapk");
        ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zip));
        for (String name : new String[]{"base.apk", "config.arm64_v8a.apk", "assets/icon.png", "config.xxhdpi.apk"}) {
            out.putNextEntry(new ZipEntry(name));
            out.write(name.getBytes("UTF-8"));
            out.closeEntry();
        }
        out.close();
        assertEquals(3, SplitBundle.countApks(zip));
        zip.delete();
    }

    @Test
    public void aabIsRecognisedAndBadged() {
        assertTrue(SplitBundle.isSupported("release.aab"));
        assertEquals("AAB", SplitBundle.badge("release.aab"));
        assertFalse(SplitBundle.isSupported("notes.txt"));
    }
}
