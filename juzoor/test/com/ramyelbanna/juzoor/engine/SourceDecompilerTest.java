package com.ramyelbanna.juzoor.engine;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SourceDecompilerTest {

    @Test
    public void jadxProducesRealJavaBodies() throws Exception {
        File root = Files.createTempDirectory("juzoor-jadx-").toFile();
        try {
            File dex = new File(root, "classes.dex");
            copyResource("/sample.dex", dex);
            File java = new File(root, "java");
            File work = new File(root, "work");
            SilentSink log = new SilentSink();

            SourceDecompiler.Stats stats = SourceDecompiler.decompile(
                    Collections.singletonList(dex), java, work, log);

            assertTrue("Jadx should not fall back for the fixture", stats.failedDexes.isEmpty());
            assertTrue("Expected at least one Java file", stats.javaFiles > 0);
            File sample = new File(java, "com/test/Sample.java");
            assertTrue("Sample.java was not written", sample.isFile());
            String code = new String(Files.readAllBytes(sample.toPath()), Charset.forName("UTF-8"));
            assertTrue(code.contains("package com.test;"));
            assertTrue(code.contains("public String hello(String"));
            assertTrue("Real body should contain the recovered return expression", code.contains("this.greeting"));
            assertTrue("Nested class should survive decompilation", code.contains("class Inner"));
            assertFalse("A real decompilation must not be a throwing stub",
                    code.contains("UnsupportedOperationException"));
            assertTrue(code.contains("decompiled with Jadx"));
        } finally {
            delete(root);
        }
    }

    @Test
    public void batchedDecompileKeepsEveryClassAndInnerClass() throws Exception {
        File root = Files.createTempDirectory("juzoor-jadx-batch-").toFile();
        try {
            File dex = new File(root, "multi.dex");
            copyResource("/multi.dex", dex);
            File java = new File(root, "java");
            File work = new File(root, "work");

            // Force four small DEX batches even though the fixture would fit
            // in one run; this exercises the low-memory path used on phones.
            SourceDecompiler.Stats stats = SourceDecompiler.decompile(
                    Collections.singletonList(dex), java, work, new SilentSink(), 2);

            assertTrue("Batching must not fall back to stubs", stats.failedDexes.isEmpty());
            assertEquals("Every top-level class must be written exactly once", 6, stats.javaFiles);
            for (String name : new String[]{"Alpha", "Beta", "Gamma", "Delta", "Epsilon", "Zeta"}) {
                assertTrue("Missing " + name + ".java", new File(java, "demo/" + name + ".java").isFile());
            }
            String alpha = new String(Files.readAllBytes(
                    new File(java, "demo/Alpha.java").toPath()), Charset.forName("UTF-8"));
            assertTrue("Batched output must keep real method bodies", alpha.contains("return \"alpha\""));
            String zeta = new String(Files.readAllBytes(
                    new File(java, "demo/Zeta.java").toPath()), Charset.forName("UTF-8"));
            assertTrue("Inner classes must stay inside their outer class", zeta.contains("class Nested"));
            assertTrue("Nested inner classes must stay with the same outer class",
                    zeta.contains("class Deeper"));
            assertFalse("A split batch must never create an inner class file",
                    new File(java, "demo/Zeta$Nested.java").isFile());
            assertFalse("A split batch must never create a nested-inner file",
                    new File(java, "demo/Zeta$Nested$Deeper.java").isFile());
        } finally {
            delete(root);
        }
    }

    @Test
    public void corruptDexFallsBackInsteadOfCrashing() throws Exception {
        File root = Files.createTempDirectory("juzoor-jadx-bad-").toFile();
        try {
            File dex = new File(root, "bad.dex");
            try (OutputStream out = new FileOutputStream(dex)) {
                out.write("not a dex".getBytes("UTF-8"));
            }
            SourceDecompiler.Stats stats = SourceDecompiler.decompile(
                    Collections.singletonList(dex), new File(root, "java"), new File(root, "work"),
                    new SilentSink());
            assertFalse("The failed DEX should be reported to the caller", stats.failedDexes.isEmpty());
            assertTrue(stats.javaFiles == 0);
        } finally {
            delete(root);
        }
    }

    private static void copyResource(String name, File target) throws Exception {
        try (InputStream in = SourceDecompilerTest.class.getResourceAsStream(name)) {
            assertTrue("Missing test resource " + name, in != null);
            try (OutputStream out = new FileOutputStream(target)) {
                byte[] buffer = new byte[8192];
                int n;
                while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
            }
        }
    }

    private static void delete(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) delete(child);
        }
        file.delete();
    }

    private static final class SilentSink implements LogSink {
        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public void log(String level, String message) {
        }

        @Override
        public void progress(int percent, String message) {
        }
    }
}
