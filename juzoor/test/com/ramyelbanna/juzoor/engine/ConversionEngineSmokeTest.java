package com.ramyelbanna.juzoor.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Runs the complete conversion pipeline on a tiny APK-shaped archive.  This is
 * intentionally an integration test: it catches broken wiring between the
 * unpacker, framework detector, native/string stages, report writer and ZIP
 * exporter that unit tests around individual classes would miss.
 */
public class ConversionEngineSmokeTest {

    @Test
    public void convertsArchiveEndToEnd() throws Exception {
        File apk = File.createTempFile("smoke", ".apk");
        ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(apk));
        entry(zip, "AndroidManifest.xml", "<manifest package=\"com.example.smoke\">"
                + "<application android:label=\"Smoke\"/></manifest>");
        entry(zip, "assets/flutter_assets/AssetManifest.json", "{}");
        entry(zip, "lib/arm64-v8a/libflutter.so", "not-a-real-elf");
        entry(zip, "res/layout/main.xml", "<layout/>");
        zip.close();
        File work = File.createTempFile("smoke-work", "");
        assertTrue(work.delete());
        assertTrue(work.mkdirs());
        ConversionOptions options = new ConversionOptions();
        ConversionEngine engine = new ConversionEngine(apk, work, options, new SilentSink());
        ConversionResult result = engine.run();
        assertTrue(result.error == null ? "converted" : result.error, result.success);
        assertTrue(result.outputDir.isDirectory());
        assertTrue(new File(result.outputDir, "REPORT.md").isFile());
        assertTrue(new File(result.outputDir, "PROJECT_TREE.txt").isFile());
        assertTrue(result.zipFile.isFile());
        assertEquals(AppFramework.FLUTTER, result.framework);
        assertEquals("Flutter", result.frameworkLabel);
        assertEquals(1, result.frameworkAssets);
        assertTrue(result.fileCount > 0);
    }

    private static void entry(ZipOutputStream zip, String name, String data) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(data.getBytes("UTF-8"));
        zip.closeEntry();
    }

    static final class SilentSink implements LogSink {
        @Override
        public void log(String level, String message) {
        }

        @Override
        public void file(String path, int percent) {
        }

        @Override
        public void progress(int percent, String stage) {
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isPaused() {
            return false;
        }
    }
}
