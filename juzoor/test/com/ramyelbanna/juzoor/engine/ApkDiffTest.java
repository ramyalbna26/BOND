package com.ramyelbanna.juzoor.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ApkDiffTest {

    @Test
    public void reportsAddedRemovedAndModifiedEntries() throws Exception {
        File older = zip("old.apk", new String[]{"classes.dex", "res/a.xml", "lib/x.so"},
                new String[]{"dex-v1", "a", "so"});
        File newer = zip("new.apk", new String[]{"classes.dex", "res/b.xml", "lib/x.so"},
                new String[]{"dex-v2", "b", "so"});
        ApkDiff.Report report = ApkDiff.diff(older, newer, null);
        assertEquals(1, report.added.size());
        assertEquals("res/b.xml", report.added.get(0).name);
        assertEquals(1, report.removed.size());
        assertEquals("res/a.xml", report.removed.get(0).name);
        assertEquals(1, report.modified.size());
        assertEquals("classes.dex", report.modified.get(0).name);
        assertEquals(1, report.unchanged);
        assertTrue(ApkDiff.toText(report).contains("Modified : 1"));
    }

    private static File zip(String name, String[] entries, String[] values) throws Exception {
        File f = File.createTempFile(name, ".apk");
        ZipOutputStream out = new ZipOutputStream(new FileOutputStream(f));
        for (int i = 0; i < entries.length; i++) {
            out.putNextEntry(new ZipEntry(entries[i]));
            out.write(values[i].getBytes("UTF-8"));
            out.closeEntry();
        }
        out.close();
        f.deleteOnExit();
        return f;
    }
}
