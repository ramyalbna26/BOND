package com.ramyelbanna.juzoor.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class DartStringsTest {

    @Test
    public void extractsLengthPrefixedAndUtf16DartStrings() throws Exception {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        data.write("_kDartVmSnapshotData".getBytes(StandardCharsets.US_ASCII));
        uleb(data, "Hello from Dart AOT".length());
        data.write("Hello from Dart AOT".getBytes(StandardCharsets.UTF_8));
        uleb(data, "Dart UTF16 text".length());
        writeUtf16(data, "Dart UTF16 text");
        data.write(0);
        data.write("plain flutter asset label".getBytes(StandardCharsets.US_ASCII));

        File so = tempFile(".so");
        write(so, data.toByteArray());
        File out = tempDir();
        DartStrings.Report r = DartStrings.analyze(so, out, tempDir(), null);

        assertTrue(r.markers > 0);
        assertTrue(r.total > 0);
        assertTrue("UTF-16 snapshot string missing", r.utf16Strings > 0);
        String text = new String(Files.readAllBytes(r.file.toPath()), StandardCharsets.UTF_8);
        assertTrue(text.contains("Hello from Dart AOT"));
        assertTrue(text.contains("Dart UTF16 text"));
        assertTrue(text.contains("plain flutter asset label"));
        assertTrue(r.jsonFile.isFile());
        assertTrue(DartStrings.toJson(r).contains("\"total\": " + r.total));
    }

    @Test
    public void mapsElfSnapshotSymbolToFileRegion() throws Exception {
        ByteArrayOutputStream snapshot = new ByteArrayOutputStream();
        uleb(snapshot, "snapshot symbol string".length());
        snapshot.write("snapshot symbol string".getBytes(StandardCharsets.UTF_8));
        File so = tempFile(".so");
        write(so, elfWithSnapshot(snapshot.toByteArray()));
        File out = tempDir();
        DartStrings.Report r = DartStrings.analyze(so, out, tempDir(), null);

        assertEquals(1, r.regions.size());
        assertEquals("_kDartVmSnapshotData", r.regions.get(0).symbol);
        assertEquals(0x700L, r.regions.get(0).offset);
        assertTrue(r.snapshotMarkers > 0);
        String text = new String(Files.readAllBytes(r.file.toPath()), StandardCharsets.UTF_8);
        assertTrue(text.contains("snapshot symbol string"));
    }

    @Test
    public void strippedLibappStillYieldsReadableStrings() throws Exception {
        File dir = new File(tempDir(), "arm64-v8a");
        assertTrue(dir.mkdirs());
        File so = new File(dir, "libapp.so");
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        uleb(data, "stripped Dart payload".length());
        data.write("stripped Dart payload".getBytes(StandardCharsets.UTF_8));
        write(so, data.toByteArray());

        DartStrings.Report r = DartStrings.analyze(so, tempDir(), null, null);
        assertTrue(r.total > 0);
        assertTrue(r.file.isFile());
        assertEquals("arm64-v8a-libapp.so.dart.txt", r.file.getName());
        assertTrue(new String(Files.readAllBytes(r.file.toPath()), StandardCharsets.UTF_8)
                .contains("stripped Dart payload"));
    }

    @Test
    public void libraryWithoutSnapshotMarkersProducesNoFile() throws Exception {
        File so = tempFile(".so");
        write(so, "ordinary native library".getBytes(StandardCharsets.US_ASCII));
        File out = tempDir();
        DartStrings.Report r = DartStrings.analyze(so, out, null, null);
        assertEquals(0, r.total);
        assertEquals(0, r.regions.size());
    }

    /**
     * Builds a minimal ELF64 with one .data section and a
     * _kDartVmSnapshotData symbol pointing at it.
     */
    private static byte[] elfWithSnapshot(byte[] data) {
        int shoff = 0x100;
        int shnum = 5;
        int shstr = 0x400;
        int symtab = 0x500;
        int strtab = 0x600;
        int body = 0x700;
        byte[] file = new byte[body + data.length];

        file[0] = 0x7F;
        file[1] = 'E';
        file[2] = 'L';
        file[3] = 'F';
        file[4] = 2;       // ELFCLASS64
        file[5] = 1;       // little endian
        file[6] = 1;       // EV_CURRENT
        u16(file, 16, 2);  // ET_EXEC
        u16(file, 18, 183);// AArch64
        u32(file, 20, 1);
        u64(file, 40, shoff);
        u16(file, 52, 64);
        u16(file, 54, 0);
        u16(file, 56, 0);
        u16(file, 58, 64);
        u16(file, 60, shnum);
        u16(file, 62, 1);

        byte[] shstrData = "\0.shstrtab\0.symtab\0.strtab\0.data\0".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(shstrData, 0, file, shstr, shstrData.length);
        String other = "someOtherSymbol";
        byte[] strData = ("\0" + other + "\0_kDartVmSnapshotData\0").getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(strData, 0, file, strtab, strData.length);
        System.arraycopy(data, 0, file, body, data.length);

        // null symbol + _kDartVmSnapshotData
        u32(file, symtab + 24, 1 + other.length() + 1);
        file[symtab + 24 + 4] = 0x11; // STB_GLOBAL | STT_OBJECT
        u16(file, symtab + 24 + 6, 4); // .data section index
        u64(file, symtab + 24 + 8, 0x1000);
        u64(file, symtab + 24 + 16, data.length);

        section(file, shoff, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        section(file, shoff + 64, 1, 3, 0, 0, shstr, shstrData.length, 0, 0, 1, 0);
        section(file, shoff + 128, 11, 2, 0, 0, symtab, 48, 3, 1, 8, 24);
        section(file, shoff + 192, 19, 3, 0, 0, strtab, strData.length, 0, 0, 1, 0);
        section(file, shoff + 256, 27, 1, 3, 0x1000, body, data.length, 0, 0, 16, 0);
        return file;
    }

    private static void section(byte[] b, int at, int name, int type, long flags,
                                long addr, long offset, long size, long link, long info,
                                long align, long entsize) {
        u32(b, at, name);
        u32(b, at + 4, type);
        u64(b, at + 8, flags);
        u64(b, at + 16, addr);
        u64(b, at + 24, offset);
        u64(b, at + 32, size);
        u32(b, at + 40, (int) link);
        u32(b, at + 44, (int) info);
        u64(b, at + 48, align);
        u64(b, at + 56, entsize);
    }

    private static void uleb(ByteArrayOutputStream out, int value) {
        while (value > 0x7F) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value);
    }

    private static void writeUtf16(ByteArrayOutputStream out, String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            out.write(c & 0xFF);
            out.write((c >>> 8) & 0xFF);
        }
    }

    private static void u16(byte[] b, int off, int value) {
        b[off] = (byte) value;
        b[off + 1] = (byte) (value >>> 8);
    }

    private static void u32(byte[] b, int off, int value) {
        b[off] = (byte) value;
        b[off + 1] = (byte) (value >>> 8);
        b[off + 2] = (byte) (value >>> 16);
        b[off + 3] = (byte) (value >>> 24);
    }

    private static void u64(byte[] b, int off, long value) {
        for (int i = 0; i < 8; i++) b[off + i] = (byte) (value >>> (8 * i));
    }

    private static File tempFile(String suffix) throws Exception {
        File f = File.createTempFile("juzoor-dart", suffix);
        f.deleteOnExit();
        return f;
    }

    private static File tempDir() throws Exception {
        File dir = File.createTempFile("juzoor-dart-out", "");
        assertTrue(dir.delete());
        assertTrue(dir.mkdirs());
        dir.deleteOnExit();
        return dir;
    }

    private static void write(File file, byte[] data) throws Exception {
        FileOutputStream out = new FileOutputStream(file);
        out.write(data);
        out.close();
    }
}
