package com.ramyelbanna.juzoor.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Regression tests for the 3.0 extraction engine. The fixture is a minimal but
 * structurally valid DEX with a string table only; it covers plain text,
 * Base64, hex, ASCII-range XOR and high-byte XOR entries.
 */
public class DexStringsTest {

    @Test
    public void extractsEveryStringAndDecodesLayers() throws Exception {
        byte[] xorAscii = xor("secret message", 0x7F);
        byte[] xorHigh = xor("raw xor payload", 0xAA);
        byte[] xorCommon = xor("common xor key", 0x3D);
        byte[] xorMinedOnly = xor("mined key payload", 0x2B);
        File dex = dex(new byte[][]{
                ascii("com.example.Hello"),
                ascii("SGVsbG8gV29ybGQh"),
                ascii("48656c6c6f2c2068657821"),
                xorAscii,
                xorHigh,
                xorCommon,
                xorMinedOnly,
                "مرحبا بالعالم".getBytes(StandardCharsets.UTF_8)
        });
        File out = tempDir();
        DexStrings.Report r = DexStrings.analyze(dex, out, null);

        assertEquals(8, r.total);
        assertEquals("every DEX string must be extracted, not only decoded ones",
                8, DexStrings.readStrings(bytes(dex)).size());
        String decoded = new String(java.nio.file.Files.readAllBytes(r.decodedFile.toPath()),
                StandardCharsets.UTF_8);
        assertTrue(decoded.contains("Hello World!"));
        assertTrue(decoded.contains("Hello, hex!"));
        assertTrue(decoded.contains("secret message"));
        assertTrue(decoded.contains("raw xor payload"));
        assertTrue(decoded.contains("common xor key"));
        assertTrue(decoded.contains("mined key payload"));
        assertTrue(r.recovered >= 6);
        assertTrue(r.rawStrings > 0);
        assertTrue(r.rawFile.isFile());
        assertTrue(r.stringsFile.isFile());
    }

    @Test
    public void smaliDecryptorHintRecoversReversedReadableText() throws Exception {
        File dex = dex(new byte[][]{ascii("!dlroW olleH")});
        File roots = tempDir();
        File smali = new File(roots, "com/example/Reveal.smali");
        smali.getParentFile().mkdirs();
        FileOutputStream out = new FileOutputStream(smali);
        out.write((".class public Lcom/example/Reveal;\n"
                + ".method public static reveal()Ljava/lang/String;\n"
                + "    const-string v0, \"!dlroW olleH\"\n"
                + "    new-instance v1, Ljava/lang/StringBuilder;\n"
                + "    invoke-direct {v1, v0}, Ljava/lang/StringBuilder;-><init>(Ljava/lang/String;)V\n"
                + "    invoke-virtual {v1}, Ljava/lang/StringBuilder;->reverse()Ljava/lang/StringBuilder;\n"
                + "    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;\n"
                + "    move-result-object v0\n"
                + "    return-object v0\n"
                + ".end method\n").getBytes(StandardCharsets.UTF_8));
        out.close();

        File analysis = tempDir();
        DexStrings.Report r = DexStrings.analyze(dex, analysis,
                java.util.Collections.singletonList(roots), null);

        assertEquals(1, r.total);
        assertTrue("bytecode sites should be mined", r.decryptorSites > 0);
        assertTrue("string should have a Smali hint", r.hints > 0);
        assertEquals(1, r.recovered);
        String decoded = new String(java.nio.file.Files.readAllBytes(r.decodedFile.toPath()),
                StandardCharsets.UTF_8);
        assertTrue(decoded.contains("Hello World!"));
    }

    @Test
    public void readStringsKeepsEveryEntry() throws Exception {
        assertEquals(3, DexStrings.readStrings(dexBytes(new byte[][]{
                ascii("SGVsbG8h"), ascii("48656c6c6f21"), ascii("plain")
        })).size());
    }

    /* ------------------------------------------------------------ fixture */

    private static File dex(byte[][] entries) throws Exception {
        File f = File.createTempFile("juzoor-test", ".dex");
        FileOutputStream out = new FileOutputStream(f);
        out.write(dexBytes(entries));
        out.close();
        f.deleteOnExit();
        return f;
    }

    private static byte[] dexBytes(byte[][] entries) throws Exception {
        int n = entries.length;
        int dataOff = 0x70 + n * 4;
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        int[] offsets = new int[n];
        for (int i = 0; i < n; i++) {
            offsets[i] = dataOff + data.size();
            uleb(data, entries[i].length);
            data.write(entries[i]);
            data.write(0);
        }
        byte[] dex = new byte[dataOff + data.size()];
        dex[0] = 'd';
        dex[1] = 'e';
        dex[2] = 'x';
        dex[3] = '\n';
        dex[4] = '0';
        dex[5] = '3';
        dex[6] = '5';
        dex[7] = 0;
        u32(dex, 0x38, n);
        u32(dex, 0x3C, 0x70);
        for (int i = 0; i < n; i++) u32(dex, 0x70 + i * 4, offsets[i]);
        byte[] payload = data.toByteArray();
        System.arraycopy(payload, 0, dex, dataOff, payload.length);
        return dex;
    }

    private static byte[] bytes(File f) throws Exception {
        return java.nio.file.Files.readAllBytes(f.toPath());
    }

    private static byte[] ascii(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] xor(String s, int key) {
        byte[] out = s.getBytes(StandardCharsets.US_ASCII);
        for (int i = 0; i < out.length; i++) out[i] ^= (byte) key;
        return out;
    }

    private static void uleb(ByteArrayOutputStream out, int value) {
        while (value > 0x7F) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value);
    }

    private static void u32(byte[] b, int off, int value) {
        b[off] = (byte) value;
        b[off + 1] = (byte) (value >> 8);
        b[off + 2] = (byte) (value >> 16);
        b[off + 3] = (byte) (value >> 24);
    }

    private static File tempDir() throws Exception {
        File dir = File.createTempFile("juzoor-dex", "");
        assertTrue(dir.delete());
        assertTrue(dir.mkdirs());
        dir.deleteOnExit();
        return dir;
    }
}
