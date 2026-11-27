package com.ramyelbanna.juzoor.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;

public class AppFrameworkTest {

    @Test
    public void detectsFlutterAndCountsAssets() throws Exception {
        File merged = tempDir();
        write(new File(merged, "lib/arm64-v8a/libflutter.so"), "ELF");
        write(new File(merged, "assets/flutter_assets/AssetManifest.json"), "{}");
        write(new File(merged, "assets/flutter_assets/fonts/Roboto.ttf"), "font");
        AppFramework.Report r = AppFramework.detect(merged, tempDir(), null);
        assertEquals(AppFramework.FLUTTER, r.kind);
        assertEquals("Flutter", r.label);
        assertEquals(2, r.flutterAssets);
        assertTrue(r.evidence.toString().contains("libflutter.so"));
    }

    @Test
    public void detectsUnityFromDataFolder() throws Exception {
        File merged = tempDir();
        write(new File(merged, "assets/bin/Data/level0"), "unity");
        write(new File(merged, "lib/armeabi-v7a/libunity.so"), "ELF");
        AppFramework.Report r = AppFramework.detect(merged, tempDir(), null);
        assertEquals(AppFramework.UNITY, r.kind);
        assertEquals(1, r.unityAssets);
    }

    @Test
    public void plainAndroidIsNative() throws Exception {
        File merged = tempDir();
        write(new File(merged, "classes.dex"), "dex");
        AppFramework.Report r = AppFramework.detect(merged, tempDir(), null);
        assertEquals(AppFramework.NATIVE, r.kind);
    }

    private static File tempDir() throws Exception {
        File dir = File.createTempFile("juzoor-fw", "");
        assertTrue(dir.delete());
        assertTrue(dir.mkdirs());
        dir.deleteOnExit();
        return dir;
    }

    private static void write(File file, String text) throws Exception {
        File parent = file.getParentFile();
        if (parent != null) parent.mkdirs();
        FileOutputStream out = new FileOutputStream(file);
        out.write(text.getBytes("UTF-8"));
        out.close();
    }
}
