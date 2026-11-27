package com.ramyelbanna.juzoor.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;

public class CrossPlatformTest {

    @Test
    public void readsPeHeadersAndArchitecture() throws Exception {
        byte[] pe = pe();
        File file = file("sample.exe", pe);
        CrossPlatform.Report r = CrossPlatform.analyze(file, tempDir(), null);
        assertEquals(CrossPlatform.PE, r.format);
        assertEquals(64, r.bits);
        assertEquals("x86_64", r.arch);
        assertTrue(r.notes.toString().contains("Windows console"));
    }

    @Test
    public void readsLittleEndianMachO() throws Exception {
        byte[] m = new byte[32];
        m[0] = (byte) 0xCF; m[1] = (byte) 0xFA; m[2] = (byte) 0xED; m[3] = (byte) 0xFE;
        le32(m, 4, 0x0100000C);
        le32(m, 12, 2);
        le32(m, 16, 0);
        CrossPlatform.Report r = CrossPlatform.analyze(file("tool", m), tempDir(), null);
        assertEquals(CrossPlatform.MACHO, r.format);
        assertEquals("ARM64", r.arch);
        assertEquals(64, r.bits);
    }

    @Test
    public void readsDmgKolyTrailer() throws Exception {
        byte[] d = new byte[2048];
        int t = d.length - 512;
        d[t] = 'k'; d[t + 1] = 'o'; d[t + 2] = 'l'; d[t + 3] = 'y';
        be32(d, t + 24, 0);
        be64(d, t + 32, 187);
        be64(d, t + 492, 42);
        CrossPlatform.Report r = CrossPlatform.analyze(file("image.dmg", d), tempDir(), null);
        assertEquals(CrossPlatform.DMG, r.format);
        assertTrue(r.notes.toString().contains("UDIF image"));
    }

    @Test
    public void extractsDebControlAndData() throws Exception {
        byte[] control = tar("control", ("Package: demo\nVersion: 1.0\nArchitecture: all\n").getBytes(StandardCharsets.UTF_8));
        byte[] data = tar("usr/bin/demo", "#!/bin/sh\necho hi\n".getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream ar = new ByteArrayOutputStream();
        ar.write("!<arch>\n".getBytes(StandardCharsets.US_ASCII));
        member(ar, "debian-binary", "2.0\n".getBytes(StandardCharsets.US_ASCII));
        member(ar, "control.tar.gz", gzip(control));
        member(ar, "data.tar.gz", gzip(data));
        CrossPlatform.Report r = CrossPlatform.analyze(file("demo.deb", ar.toByteArray()), tempDir(), null);
        assertEquals(CrossPlatform.DEB, r.format);
        assertTrue(r.notes.toString().contains("control Package: demo"));
        assertTrue(r.contents.toString().contains("usr/bin/demo"));
        File extracted = new File(r.extractedDir, "root/usr/bin/demo");
        assertTrue(extracted.isFile());
    }

    @Test
    public void extractsPyInstallerScriptAsPyc() throws Exception {
        byte[] payload = new byte[]{1, 2, 3, 4, 5, 6, 7};
        String name = "app.py";
        int entryLen = 18 + name.length() + 1; // PyInstaller names are NUL terminated in practice
        byte[] toc = new byte[entryLen];
        be32(toc, 0, entryLen);
        be32(toc, 4, entryLen);
        be32(toc, 8, payload.length);
        be32(toc, 12, payload.length);
        toc[16] = 0;
        toc[17] = 's';
        System.arraycopy((name + "\u0000").getBytes(StandardCharsets.ISO_8859_1), 0, toc, 18, name.length() + 1);

        byte[] base = pe();
        int start = base.length;
        int pkgLen = toc.length + payload.length + 88;
        byte[] cookie = new byte[88];
        System.arraycopy(new byte[]{'M', 'E', 'I', 0x0C, 0x0B, 0x0A, 0x0B, 0x0E}, 0, cookie, 0, 8);
        be32(cookie, 8, pkgLen);
        be32(cookie, 12, 0);
        be32(cookie, 16, toc.length);
        be32(cookie, 20, 311);
        byte[] all = new byte[start + toc.length + payload.length + 88];
        System.arraycopy(base, 0, all, 0, start);
        System.arraycopy(toc, 0, all, start, toc.length);
        System.arraycopy(payload, 0, all, start + toc.length, payload.length);
        System.arraycopy(cookie, 0, all, start + toc.length + payload.length, 88);
        CrossPlatform.Report r = CrossPlatform.analyze(file("packed.exe", all), tempDir(), null);
        assertTrue(r.pyinstaller);
        assertEquals(1, r.pyinstallerEntries);
        File pyc = new File(r.extractedDir, "app.py.pyc");
        assertTrue(pyc.isFile());
        byte[] header = new byte[16];
        java.io.FileInputStream in = new java.io.FileInputStream(pyc);
        in.read(header);
        in.close();
        assertEquals((byte) 0xA7, header[0]);
        assertEquals((byte) 0x0D, header[1]);
        assertEquals(7, header[12] | (header[13] << 8) | (header[14] << 16) | (header[15] << 24));
    }


    @Test
    public void readsLittleEndian32MachO() throws Exception {
        byte[] m = new byte[28];
        m[0] = (byte) 0xCE; m[1] = (byte) 0xFA; m[2] = (byte) 0xED; m[3] = (byte) 0xFE;
        le32(m, 4, 7);
        le32(m, 12, 2);
        le32(m, 16, 0);
        CrossPlatform.Report r = CrossPlatform.analyze(file("tool32", m), tempDir(), null);
        assertEquals(CrossPlatform.MACHO, r.format);
        assertEquals("x86", r.arch);
        assertEquals(32, r.bits);
    }

    @Test
    public void extractsRpmGzipCpioPayload() throws Exception {
        ByteArrayOutputStream cpio = new ByteArrayOutputStream();
        cpioEntry(cpio, "usr/bin/demo", "#!/bin/sh\necho rpm\n".getBytes(StandardCharsets.UTF_8));
        cpioEntry(cpio, "TRAILER!!!", new byte[0]);
        byte[] payload = gzip(cpio.toByteArray());

        String[] tags = {"demo", "1.0", "1", "x86_64", "gzip"};
        int[] tagIds = {1000, 1001, 1002, 1022, 1125};
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        int[] offsets = new int[tags.length];
        for (int i = 0; i < tags.length; i++) {
            offsets[i] = data.size();
            data.write(tags[i].getBytes(StandardCharsets.UTF_8));
            data.write(0);
        }
        int headerSize = 16 + 16 * tags.length + data.size();
        ByteArrayOutputStream rpm = new ByteArrayOutputStream();
        rpm.write(new byte[96]); // lead
        // Signature header record (real RPMs always have one).  Its tag 1000 is
        // an int size, not the package NAME, so it must be skipped.
        rpm.write(new byte[]{(byte) 0x8E, (byte) 0xAD, (byte) 0xE8, 0x01});
        rpm.write(new byte[4]);
        be32To(rpm, 1);
        be32To(rpm, 4);
        be32To(rpm, 1000);
        be32To(rpm, 4);
        be32To(rpm, 0);
        be32To(rpm, 1);
        be32To(rpm, 12345);
        while (rpm.size() % 8 != 0) rpm.write(0);
        // Main header record with the package tags.
        rpm.write(new byte[]{(byte) 0x8E, (byte) 0xAD, (byte) 0xE8, 0x01});
        rpm.write(new byte[4]);
        be32To(rpm, tags.length);
        be32To(rpm, data.size());
        for (int i = 0; i < tags.length; i++) {
            be32To(rpm, tagIds[i]);
            be32To(rpm, 6);
            be32To(rpm, offsets[i]);
            be32To(rpm, 1);
        }
        rpm.write(data.toByteArray());
        while (rpm.size() % 8 != 0) rpm.write(0);
        rpm.write(payload);
        byte[] bytes = rpm.toByteArray();
        bytes[0] = (byte) 0xED; bytes[1] = (byte) 0xAB; bytes[2] = (byte) 0xEE; bytes[3] = (byte) 0xDB;
        CrossPlatform.Report r = CrossPlatform.analyze(file("demo.rpm", bytes), tempDir(), null);
        assertEquals(CrossPlatform.RPM, r.format);
        assertTrue(r.notes.toString().contains("package demo 1.0-1"));
        assertTrue(r.notes.toString().contains("cpio:"));
        assertTrue(new File(r.extractedDir, "root/usr/bin/demo").isFile());
    }

    /* ------------------------------------------------------------- fixtures */

    private static void cpioEntry(ByteArrayOutputStream out, String name, byte[] data) throws Exception {
        byte[] n = (name + "\u0000").getBytes(StandardCharsets.UTF_8);
        String header = "070701"
                + String.format("%08x", 1) + String.format("%08x", 0100644)
                + String.format("%08x", 0) + String.format("%08x", 0)
                + String.format("%08x", 1) + String.format("%08x", 0)
                + String.format("%08x", data.length) + String.format("%08x", 0)
                + String.format("%08x", 0) + String.format("%08x", 0)
                + String.format("%08x", 0) + String.format("%08x", n.length)
                + String.format("%08x", 0);
        out.write(header.getBytes(StandardCharsets.US_ASCII));
        out.write(n);
        while (out.size() % 4 != 0) out.write(0);
        out.write(data);
        while (out.size() % 4 != 0) out.write(0);
    }

    private static void be32To(ByteArrayOutputStream out, int v) {
        out.write((v >> 24) & 0xFF);
        out.write((v >> 16) & 0xFF);
        out.write((v >> 8) & 0xFF);
        out.write(v & 0xFF);
    }

    private static byte[] pe() {
        byte[] b = new byte[0x400];
        b[0] = 'M'; b[1] = 'Z';
        le32(b, 0x3C, 0x80);
        b[0x80] = 'P'; b[0x81] = 'E'; b[0x82] = 0; b[0x83] = 0;
        le16(b, 0x84, 0x8664);
        le16(b, 0x86, 1);
        le16(b, 0x94, 0xF0);
        int opt = 0x98;
        le16(b, opt, 0x20B);
        le16(b, opt + 68, 3);
        int sec = opt + 0xF0;
        System.arraycopy(".text".getBytes(StandardCharsets.US_ASCII), 0, b, sec, 5);
        le32(b, sec + 16, 0x200);
        le32(b, sec + 20, 0x200);
        le32(b, sec + 36, 0x60000020);
        return b;
    }

    private static byte[] tar(String name, byte[] data) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] header = new byte[512];
        System.arraycopy(name.getBytes(StandardCharsets.UTF_8), 0, header, 0, Math.min(name.length(), 100));
        octal(header, 100, 8, 0644);
        octal(header, 108, 8, 0);
        octal(header, 116, 8, 0);
        octal(header, 124, 12, data.length);
        octal(header, 136, 12, 0);
        header[156] = '0';
        System.arraycopy("ustar".getBytes(StandardCharsets.US_ASCII), 0, header, 257, 5);
        out.write(header);
        out.write(data);
        int pad = (512 - (data.length % 512)) % 512;
        out.write(new byte[pad]);
        out.write(new byte[1024]);
        return out.toByteArray();
    }

    private static void member(ByteArrayOutputStream out, String name, byte[] data) throws Exception {
        byte[] h = new byte[60];
        System.arraycopy(name.getBytes(StandardCharsets.US_ASCII), 0, h, 0, name.length());
        System.arraycopy("0".getBytes(StandardCharsets.US_ASCII), 0, h, 16, 1);
        System.arraycopy("0".getBytes(StandardCharsets.US_ASCII), 0, h, 28, 1);
        System.arraycopy("0".getBytes(StandardCharsets.US_ASCII), 0, h, 34, 1);
        System.arraycopy("100644".getBytes(StandardCharsets.US_ASCII), 0, h, 40, 6);
        String size = String.valueOf(data.length);
        System.arraycopy(size.getBytes(StandardCharsets.US_ASCII), 0, h, 48, size.length());
        h[58] = '`'; h[59] = '\n';
        out.write(h);
        out.write(data);
        if ((data.length & 1) == 1) out.write('\n');
    }

    private static byte[] gzip(byte[] data) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        GZIPOutputStream gz = new GZIPOutputStream(out);
        gz.write(data);
        gz.close();
        return out.toByteArray();
    }

    private static void octal(byte[] b, int off, int len, long value) {
        String s = Long.toOctalString(value);
        while (s.length() < len - 1) s = "0" + s;
        byte[] bytes = s.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(bytes, 0, b, off, Math.min(bytes.length, len - 1));
        b[off + len - 1] = 0;
    }

    private static void le16(byte[] b, int off, int v) {
        b[off] = (byte) (v & 0xFF);
        b[off + 1] = (byte) ((v >> 8) & 0xFF);
    }

    private static void le32(byte[] b, int off, int v) {
        b[off] = (byte) (v & 0xFF);
        b[off + 1] = (byte) ((v >> 8) & 0xFF);
        b[off + 2] = (byte) ((v >> 16) & 0xFF);
        b[off + 3] = (byte) ((v >> 24) & 0xFF);
    }

    private static void be32(byte[] b, int off, int v) {
        b[off] = (byte) ((v >> 24) & 0xFF);
        b[off + 1] = (byte) ((v >> 16) & 0xFF);
        b[off + 2] = (byte) ((v >> 8) & 0xFF);
        b[off + 3] = (byte) (v & 0xFF);
    }

    private static void be64(byte[] b, int off, long v) {
        for (int i = 0; i < 8; i++) b[off + i] = (byte) ((v >> (8 * (7 - i))) & 0xFF);
    }

    private static File file(String name, byte[] data) throws Exception {
        File f = new File(tempDir(), name);
        FileOutputStream out = new FileOutputStream(f);
        out.write(data);
        out.close();
        f.deleteOnExit();
        return f;
    }

    private static File tempDir() throws Exception {
        File dir = File.createTempFile("juzoor-cross", "");
        assertTrue(dir.delete());
        assertTrue(dir.mkdirs());
        dir.deleteOnExit();
        return dir;
    }
}
