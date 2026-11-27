package com.ramyelbanna.juzoor.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonParser;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
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
        entry(zip, "classes.dex", resource("/sample.dex"));
        entry(zip, "assets/flutter_assets/AssetManifest.json", "{}");
        entry(zip, "lib/arm64-v8a/libflutter.so", "not-a-real-elf");
        entry(zip, "lib/arm64-v8a/libapp.so", dartSnapshot());
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
        JsonParser.parseString(read(new File(result.outputDir, "analysis/report.json")));
        JsonParser.parseString(read(new File(result.outputDir, "juzoor.json")));
        assertTrue(result.zipFile.isFile());
        assertEquals(AppFramework.FLUTTER, result.framework);
        assertEquals("Flutter", result.frameworkLabel);
        assertEquals(1, result.frameworkAssets);
        assertTrue("Dart strings should be extracted from libapp.so", result.dartStringCount > 0);
        assertEquals(1, result.dartLibraryCount);
        File dart = new File(result.outputDir, "dart_strings/arm64-v8a-libapp.so.dart.txt");
        assertTrue("Dart string file missing", dart.isFile());
        assertTrue(read(dart).contains("Dart smoke string"));
        assertTrue(result.fileCount > 0);
        assertTrue("Smoke APK should produce real Java", result.javaFileCount > 0);
        File sample = new File(result.outputDir, "app/src/main/recovered/java/com/test/Sample.java");
        assertTrue("Decompiled Sample.java missing", sample.isFile());
        String code = new String(Files.readAllBytes(sample.toPath()), Charset.forName("UTF-8"));
        assertTrue(code.contains("this.greeting"));
        assertTrue(code.contains("class Inner"));
        File kotlin = new File(result.outputDir, "app/src/main/recovered/kotlin/com/test/Sample.kt");
        assertTrue("Kotlin API mirror missing", kotlin.isFile());
        String kt = new String(Files.readAllBytes(kotlin.toPath()), Charset.forName("UTF-8"));
        assertTrue("Kotlin signatures should use Kotlin types", kt.contains("fun hello(") && kt.contains(": String"));
        assertTrue("Kotlin primitive types must not leak Java names", !kt.contains(": int"));
        assertTrue("Boxed fields must stay nullable", kt.contains("var boxed: Int? = null"));
        assertFalse("A non-null Kotlin type cannot default to null", kt.contains("var boxed: Int = null"));
        assertTrue("Arrays of boxed types need a top-level nullable marker",
                kt.contains("var boxedArray: Array<Int?>? = null"));
        assertTrue("Arrays of generic types need a top-level nullable marker",
                kt.contains("var classArray: Array<Class<*>?>? = null"));
        assertFalse("Inner nullable types must not leave the array non-nullable",
                kt.contains("var boxedArray: Array<Int?> = null"));
    }

    private static byte[] dartSnapshot() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write("_kDartIsolateSnapshotData".getBytes("UTF-8"));
        String text = "Dart smoke string";
        int value = text.length();
        while (value > 0x7F) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value);
        out.write(text.getBytes("UTF-8"));
        return out.toByteArray();
    }

    private static String read(File file) throws Exception {
        return new String(Files.readAllBytes(file.toPath()), Charset.forName("UTF-8"));
    }

    private static byte[] resource(String name) throws Exception {
        try (InputStream in = ConversionEngineSmokeTest.class.getResourceAsStream(name)) {
            assertTrue("Missing test resource " + name, in != null);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
            return out.toByteArray();
        }
    }

    private static void entry(ZipOutputStream zip, String name, String data) throws Exception {
        entry(zip, name, data.getBytes("UTF-8"));
    }

    private static void entry(ZipOutputStream zip, String name, byte[] data) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(data);
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
