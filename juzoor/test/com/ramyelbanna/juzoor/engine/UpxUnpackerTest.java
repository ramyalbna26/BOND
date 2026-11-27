package com.ramyelbanna.juzoor.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Real UCL NRV stream fixtures. Each compressed block is the first b_info
 * payload of a UPX 4.2.4 packed x86-64 ELF; the expected hash is the original
 * 736-byte ELF header block. This catches a decoder that compiles but gets the
 * bit framing wrong (the 2.3 code did exactly that).
 */
public class UpxUnpackerTest {

    private static final String EXPECTED_SHA256 =
            "58166d7a9e083cf5cd7b5e0ab7b78e21c6012c42065b16a13e256cb3f3d24a23";

    private static final String NRV2B =
            "+/sh/39FTEYCAQEAAwA+AA2gMxEPW7IHG0AXOK5cIxM4bFn33QAMBSQAIgAGDwQnB0t2IaegAghnNyGv7DzgAgccAFPYW3IBNwCYvctO2CMRBwAQNwWHS76Q7xcHM3AxNuzkN2Q3BtdVR3UHR5aQL7CGATfw7ITczttIBwtJICEnkL0J+zBRADcH328c7AghZwJvqNCHt5DuB/AghwGwH5IAUuV0ZATfd7AJ4cCKAYdQN7hFSV5CvrIHpEoBK43Cnvk3UacAOWWz7z8D/L8HIAAAgGELbwIAAAAAAABA/w==";
    private static final String NRV2D =
            "9vsh/39FTEYCAQEAAwA+ABqgMxEf+6U9bEAvOK5cRyc4AAwKTm2tfSQAIgAGHgRPD6AC2y3tQgjPb+AvJRdyag8cAAEn9hS7bgGYIxEPABAh77RrbwUuoA4zwdCWfHAxNm8GriFf7ORVR3UPsIYB5DsfLW/w20gPC0kzsW8nICFPMFEA3weOGAJJBM8CLaSdw9+o0A7wwQ8Vl0EAD1LldGQEvzZRPADAigEPUG9CvtoduEWSD6RKAcUG5l1WblFOAdQWvBt+Bvx/DySMLeQgAN+oJEmS/w==";
    private static final String NRV2E =
            "u/sg/39FTEYCAQEAAwA+ABugMxEf39327UAvOK5cEyc4AAwKJAByaGvvIgAGHgRPD6AC9mZjEwjPb+AvD1syIaccAAFu9mAfsQGYIxEPABBvCXnLmwUuoA4zDSa25HAxNm8Gr1WWPLGRR3UPsIYBQL7z1G/w20gOC0k8B3v2ICFPMFEA3wcEbYhhhc8C38WEtOWo0A7wAA9V8BdyAFLldGQEv3tyG3nAigFvULhFktuBfLUPpEoBBG92FBucUU4BBAdLTu3g/H8PIACSJAlr3wAAKkn/";

    @Test
    public void decodesNrv2bNrv2dAndNrv2e() throws Exception {
        assertBlock(NRV2B, 223);
        assertBlock(NRV2D, 214);
        assertBlock(NRV2E, 213);
    }

    @Test
    public void findsTrailerAndUnpacksChain() throws Exception {
        byte[] compressed = Base64.getDecoder().decode(NRV2E);
        byte[] upx = container(compressed, 8);
        assertTrue(UpxUnpacker.looksPacked(upx));
        UpxUnpacker.Result r = UpxUnpacker.unpack(upx);
        assertTrue(r.found);
        assertTrue(r.error == null ? "unpacked" : r.error, r.unpacked);
        assertEquals(1, r.blocks);
        assertEquals(736, r.bytes);
        assertTrue("an unfiltered block must not report a filter", r.filters.isEmpty());
        assertEquals(EXPECTED_SHA256, sha256(r.data));
    }

    @Test
    public void ignoresBareUpxMagicWithoutTrailer() {
        byte[] d = new byte[2048];
        byte[] magic = "UPX!".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        System.arraycopy(magic, 0, d, 1800, magic.length);
        assertFalse(UpxUnpacker.looksPacked(d));
        assertFalse(UpxUnpacker.unpack(d).found);
    }

    private void assertBlock(String base64, int expectedCompressed) throws Exception {
        byte[] compressed = Base64.getDecoder().decode(base64);
        assertEquals(expectedCompressed, compressed.length);
        byte[] out = UpxUnpacker.decompress(compressed, 0, compressed.length, 736, method(base64));
        assertTrue("decoder returned null", out != null);
        assertEquals(736, out.length);
        assertEquals(EXPECTED_SHA256, sha256(out));
    }

    private static int method(String base64) {
        if (base64.equals(NRV2B)) return 2;
        if (base64.equals(NRV2D)) return 5;
        return 8;
    }

    /** l_info + p_info + one b_info block + the 4-byte overlay pointer. */
    private static byte[] container(byte[] compressed, int method) throws Exception {
        int lInfo = 20;
        int pInfo = lInfo + 12;
        int bInfo = pInfo + 12;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // l_info: checksum, magic, lsize, version, format
        le32(out, 0);
        out.write('U');
        out.write('P');
        out.write('X');
        out.write('!');
        le16(out, 0x0E16);
        out.write(7);
        out.write(8);
        // p_info: progid, filesize, blocksize
        le32(out, 0);
        le32(out, 4096);
        le32(out, 4096);
        // b_info: sz_unc, sz_cpr, method, filter, cto, extra
        le32(out, 736);
        le32(out, compressed.length);
        out.write(method);
        out.write(0);
        out.write(0);
        out.write(0);
        out.write(compressed);
        le32(out, pInfo);
        assertEquals(bInfo, pInfo + 12);
        return out.toByteArray();
    }

    private static void le16(ByteArrayOutputStream out, int v) {
        out.write(v & 0xFF);
        out.write((v >> 8) & 0xFF);
    }

    private static void le32(ByteArrayOutputStream out, int v) {
        out.write(v & 0xFF);
        out.write((v >> 8) & 0xFF);
        out.write((v >> 16) & 0xFF);
        out.write((v >> 24) & 0xFF);
    }

    private static String sha256(byte[] data) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
        StringBuilder b = new StringBuilder();
        for (byte value : digest) b.append(String.format("%02x", value));
        return b.toString();
    }
}
