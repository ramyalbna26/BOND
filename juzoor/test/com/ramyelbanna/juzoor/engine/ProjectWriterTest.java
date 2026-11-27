package com.ramyelbanna.juzoor.engine;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class ProjectWriterTest {

    @Test
    public void generatedGradleIncludesRecoveredSourcesAndDetectedLibraries() throws Exception {
        File root = tempDir();
        ConversionResult result = new ConversionResult();
        result.packageName = "com.example.rebuilt";
        result.versionName = "1.2.3";
        result.versionCode = 7;
        result.minSdk = 26;
        result.targetSdk = 34;

        List<LibrarySupport.Library> libs = new ArrayList<>();
        libs.add(new LibrarySupport.Library("Firebase",
                "com.google.firebase:firebase-bom:33.1.0", "apply google-services"));
        libs.add(new LibrarySupport.Library("jsoup", "org.jsoup:jsoup:1.17.2", ""));
        libs.add(new LibrarySupport.Library("Material Components",
                "com.google.android.material:material:1.12.0", ""));

        ProjectWriter.write(root, result, new ConversionOptions(), libs, null,
                new ConversionEngineSmokeTest.SilentSink());

        String gradle = read(new File(root, "app/build.gradle"));
        assertTrue("recovered Java must be a Gradle source directory",
                gradle.contains("src/main/recovered/java"));
        assertTrue(gradle.contains("com.google.firebase:firebase-bom:33.1.0"));
        assertTrue(gradle.contains("com.google.firebase:firebase-analytics"));
        assertTrue(gradle.contains("org.jsoup:jsoup:1.17.2"));
        assertTrue(gradle.contains("com.google.android.material:material:1.12.0"));

        String readme = read(new File(root, "README.md"));
        assertTrue(readme.contains("app/src/main/recovered/java"));
        String recoveredReadme = read(new File(root,
                "app/src/main/recovered/README.md"));
        assertTrue(recoveredReadme.startsWith("# Recovered source (compiled with the project)"));
        assertTrue(recoveredReadme.contains("part of"));
    }

    private static String read(File file) throws Exception {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static File tempDir() throws Exception {
        File dir = File.createTempFile("juzoor-project", "");
        assertTrue(dir.delete());
        assertTrue(dir.mkdirs());
        dir.deleteOnExit();
        return dir;
    }
}
