package com.ramyelbanna.juzoor.engine;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Offline, dependency-free UPX payload decoder for ELF/Android {@code .so}
 * files.
 *
 * <p>UPX stores its payload as a chain of {@code b_info} blocks, each a 12-byte
 * little-endian header ({@code sz_unc, sz_cpr, method, filter, cto, extra})
 * followed by {@code sz_cpr} compressed bytes. The block chain itself starts
 * after the {@code l_info}/{@code p_info} trailer, whose file offset is stored
 * in the final four bytes of a UPX file. The older code scanned every byte of
 * the file for a plausible header, which was both slow and prone to false
 * positives; this version parses the documented trailer first and only falls
 * back to a bounded scan when the trailer is missing.</p>
 *
 * <p>UCL NRV2B, NRV2D and NRV2E blocks are decompressed with bit-exact ports of
 * the reference decoders. LZMA blocks are recognised but not decoded here; the
 * caller reports that honestly instead of pretending success. Decompressed
 * blocks are the packed payload, not a rebuilt executable: the UPX loader also
 * stores relocation/filter metadata that cannot be applied without executing
 * the target. Juzoor uses the payload for string and embedded-file recovery,
 * and says so in the generated report.</p>
 */
public final class UpxUnpacker {

    private static final long MAX_PAYLOAD = 256L * 1024L * 1024L;
    private static final int MAX_BLOCKS = 4096;

    public static final class Result {
        public boolean found;
        public boolean unpacked;
        public String method = "";
        public int blocks;
        public long bytes;
        public byte[] data;
        public String error;
        /** File offset of the first b_info header, when a UPX trailer was found. */
        public long overlay = -1;
        /** UPX filter id of the first filtered block, or -1. */
        public int filter = -1;
        /** Filter/CTO pairs (e.g. {@code 0x49/0x60}) for the report. */
        public final List<String> filters = new ArrayList<>();
    }

    private static final class Block {
        int offset;
        int uncompressed;
        int compressed;
        int method;
        int filter;
        int cto;
    }

    private UpxUnpacker() {
    }

    /**
     * True only when a real UPX l_info/p_info trailer or a self-consistent
     * b_info chain is present. This avoids the false positive that a random
     * "UPX!" string inside a large PDF/rendering library used to cause.
     */
    public static boolean looksPacked(byte[] d) {
        return locate(d) >= 0;
    }

    public static Result unpack(byte[] d) {
        Result r = new Result();
        int start = locate(d);
        if (start < 0) return r;
        r.found = true;
        r.overlay = start;

        List<Block> chain = readChain(d, start);
        if (chain.isEmpty()) {
            // A damaged trailer can still leave a decodable block chain; look
            // in the last 8 MB of the file but never scan a huge library
            // byte-for-byte with an unbounded candidate list.
            int from = Math.max(0, d.length - 8 * 1024 * 1024);
            for (int p = from; p + 12 <= d.length; p++) {
                if ((d[p + 8] & 0xFF) < 2 || (d[p + 8] & 0xFF) > 14) continue;
                List<Block> candidate = readChain(d, p);
                if (candidate.size() > 1) {
                    chain = candidate;
                    break;
                }
            }
            if (chain.isEmpty()) {
                r.error = "no valid b_info block chain found";
                return r;
            }
        }

        long total = 0;
        int firstFilter = -1;
        for (Block b : chain) {
            total += b.uncompressed;
            if (total > MAX_PAYLOAD) {
                r.error = "uncompressed payload exceeds " + (MAX_PAYLOAD / (1024 * 1024)) + " MB";
                return r;
            }
            // Filter 0 means "no filter" in UPX; only real filter ids belong
            // in the report. Finding 0x00/0x00 next to 0x49/0x60 was noise.
            if (b.filter > 0) {
                if (firstFilter < 0) firstFilter = b.filter;
                String f = String.format(java.util.Locale.US, "0x%02X/0x%02X", b.filter, b.cto);
                if (!r.filters.contains(f)) r.filters.add(f);
            }
        }
        long heapLimit = Math.max(16L * 1024L * 1024L, Runtime.getRuntime().maxMemory() / 3L);
        if (total > heapLimit) {
            r.error = "uncompressed payload needs " + total + " bytes; refusing to risk OutOfMemory";
            return r;
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream((int) total);
        String method = "";
        for (Block b : chain) {
            int m = b.method;
            if (m == 14) {
                r.error = "LZMA-compressed UPX block (method 14) — static decode is not supported";
                r.method = "LZMA";
                return r;
            }
            byte[] block = decompress(d, b.offset + 12, b.compressed, b.uncompressed, m);
            if (block == null || block.length != b.uncompressed) {
                r.error = "NRV decode failed at block " + r.blocks + " (method " + m + ")";
                return r;
            }
            out.write(block, 0, block.length);
            r.blocks++;
            String name = methodName(m);
            if (method.isEmpty()) method = name;
            else if (!method.contains(name)) method = method + "/" + name;
        }
        r.data = out.toByteArray();
        r.bytes = r.data.length;
        r.method = method;
        r.filter = firstFilter;
        r.unpacked = r.data.length > 0;
        return r;
    }

    /**
     * Returns the offset of the first b_info header, or -1. Modern UPX writes
     * {@code l_info, p_info, b_info...} at {@code overlay_offset}; the last
     * 4 bytes of the file contain that offset. Some old builds also leave an
     * extra "UPX!" marker in the loader, so the tail is checked too.
     */
    private static int locate(byte[] d) {
        if (d == null || d.length < 28) return -1;
        int overlay = (int) Math.min(Integer.MAX_VALUE, u32(d, d.length - 4));
        if (validTrailer(d, overlay)) return overlay + 12;

        int from = Math.max(0, d.length - 64 * 1024);
        for (int i = from; i + 4 <= d.length; i++) {
            if (d[i] != 'U' || d[i + 1] != 'P' || d[i + 2] != 'X' || d[i + 3] != '!') continue;
            // l_magic is the second dword of l_info, so l_info starts at i-4.
            int candidate = i - 4;
            if (candidate < 0) continue;
            int ov = candidate + 12;
            if (validTrailer(d, ov)) return ov + 12;
        }
        return -1;
    }

    private static boolean validTrailer(byte[] d, int overlay) {
        if (overlay < 12 || overlay + 12 > d.length) return false;
        int magic = overlay - 8;
        if (d[magic] != 'U' || d[magic + 1] != 'P' || d[magic + 2] != 'X' || d[magic + 3] != '!') {
            return false;
        }
        // p_info: progid, filesize, blocksize. Only the size fields matter here.
        long fileSize = u32(d, overlay + 4);
        long blockSize = u32(d, overlay + 8);
        if (fileSize < 16 || fileSize > (1L << 31)) return false;
        if (blockSize == 0 || blockSize > fileSize) return false;
        int start = overlay + 12;
        if (start + 12 > d.length) return false;
        return !readChain(d, start).isEmpty();
    }

    private static List<Block> readChain(byte[] d, int start) {
        List<Block> chain = new ArrayList<>();
        int p = start;
        while (p + 12 <= d.length && chain.size() < MAX_BLOCKS) {
            long u = u32(d, p);
            long c = u32(d, p + 4);
            int method = d[p + 8] & 0xFF;
            int filter = d[p + 9] & 0xFF;
            int cto = d[p + 10] & 0xFF;
            int extra = d[p + 11] & 0xFF;
            if (u <= 0 || u > MAX_PAYLOAD) break;
            if (c < 4 || c > d.length) break;
            if (p + 12L + c > d.length) break;
            if (method < 2 || method > 14) break;
            if (extra != 0) break;
            Block b = new Block();
            b.offset = p;
            b.uncompressed = (int) u;
            b.compressed = (int) c;
            b.method = method;
            b.filter = filter;
            b.cto = cto;
            chain.add(b);
            p += 12 + b.compressed;
        }
        return chain;
    }

    private static String methodName(int m) {
        switch (m) {
            case 2: return "NRV2B/LE32";
            case 3: return "NRV2B/8";
            case 4: return "NRV2B/LE16";
            case 5: return "NRV2D/LE32";
            case 6: return "NRV2D/8";
            case 7: return "NRV2D/LE16";
            case 8: return "NRV2E/LE32";
            case 9: return "NRV2E/8";
            case 10: return "NRV2E/LE16";
            case 14: return "LZMA";
            default: return "method " + m;
        }
    }

    /** Decompresses one block. Returns exactly szUnc bytes or null. */
    public static byte[] decompress(byte[] src, int off, int len, int szUnc, int method) {
        if (off < 0 || len <= 0 || off + len > src.length || szUnc <= 0) return null;
        BitReader br = new BitReader(src, off, len, method);
        byte[] out = new byte[szUnc];
        try {
            if (method >= 2 && method <= 4) return nrv2b(br, out);
            if (method >= 5 && method <= 7) return nrv2d(br, out);
            if (method >= 8 && method <= 10) return nrv2e(br, out);
        } catch (Throwable t) {
            return null;
        }
        return null;
    }

    /* ----------------------------------------------------------- NRV2B */

    private static byte[] nrv2b(BitReader br, byte[] dst) {
        int olen = 0;
        int lastMOff = 1;
        int n = dst.length;
        for (;;) {
            while (br.bit() == 1) {
                dst[olen++] = (byte) br.byteAt();
            }
            long mOff = 1;
            do {
                mOff = mOff * 2 + br.bit();
                if (mOff > 0xffffffL + 3) return null;
            } while (br.bit() == 0);
            if (mOff == 2) {
                mOff = lastMOff;
            } else {
                mOff = (mOff - 3) * 256 + br.byteAt();
                if (mOff == 0xffffffffL) break;
                lastMOff = (int) ++mOff;
            }
            int mLen = br.bit();
            mLen = mLen * 2 + br.bit();
            if (mLen == 0) {
                mLen++;
                do {
                    mLen = mLen * 2 + br.bit();
                } while (br.bit() == 0);
                mLen += 2;
            }
            if (mOff > 0xd00) mLen++;
            if (mOff > olen || olen + mLen + 1 > n) return null;
            int mPos = (int) (olen - mOff);
            dst[olen++] = dst[mPos++];
            do {
                dst[olen++] = dst[mPos++];
            } while (--mLen > 0);
        }
        return olen == n ? dst : null;
    }

    /* ----------------------------------------------------------- NRV2D */

    private static byte[] nrv2d(BitReader br, byte[] dst) {
        int olen = 0;
        int lastMOff = 1;
        int n = dst.length;
        for (;;) {
            while (br.bit() == 1) {
                dst[olen++] = (byte) br.byteAt();
            }
            long mOff = 1;
            for (;;) {
                mOff = mOff * 2 + br.bit();
                if (mOff > 0xffffffL + 3) return null;
                if (br.bit() == 1) break;
                mOff = (mOff - 1) * 2 + br.bit();
            }
            int mLen;
            if (mOff == 2) {
                mOff = lastMOff;
                mLen = br.bit();
            } else {
                mOff = (mOff - 3) * 256 + br.byteAt();
                if (mOff == 0xffffffffL) break;
                mLen = (int) ((mOff ^ 0xffffffffL) & 1);
                mOff >>= 1;
                lastMOff = (int) ++mOff;
            }
            mLen = mLen * 2 + br.bit();
            if (mLen == 0) {
                mLen++;
                do {
                    mLen = mLen * 2 + br.bit();
                } while (br.bit() == 0);
                mLen += 2;
            }
            if (mOff > 0x500) mLen++;
            if (mOff > olen || olen + mLen + 1 > n) return null;
            int mPos = (int) (olen - mOff);
            dst[olen++] = dst[mPos++];
            do {
                dst[olen++] = dst[mPos++];
            } while (--mLen > 0);
        }
        return olen == n ? dst : null;
    }

    /* ----------------------------------------------------------- NRV2E */

    private static byte[] nrv2e(BitReader br, byte[] dst) {
        int olen = 0;
        int lastMOff = 1;
        int n = dst.length;
        for (;;) {
            while (br.bit() == 1) {
                dst[olen++] = (byte) br.byteAt();
            }
            long mOff = 1;
            for (;;) {
                mOff = mOff * 2 + br.bit();
                if (mOff > 0xffffffL + 3) return null;
                if (br.bit() == 1) break;
                mOff = (mOff - 1) * 2 + br.bit();
            }
            int mLen;
            if (mOff == 2) {
                mOff = lastMOff;
                mLen = br.bit();
            } else {
                mOff = (mOff - 3) * 256 + br.byteAt();
                if (mOff == 0xffffffffL) break;
                mLen = (int) ((mOff ^ 0xffffffffL) & 1);
                mOff >>= 1;
                lastMOff = (int) ++mOff;
            }
            if (mLen != 0) {
                mLen = 1 + br.bit();
            } else if (br.bit() == 1) {
                mLen = 3 + br.bit();
            } else {
                mLen++;
                do {
                    mLen = mLen * 2 + br.bit();
                } while (br.bit() == 0);
                mLen += 3;
            }
            if (mOff > 0x500) mLen++;
            if (mOff > olen || olen + mLen + 1 > n) return null;
            int mPos = (int) (olen - mOff);
            dst[olen++] = dst[mPos++];
            do {
                dst[olen++] = dst[mPos++];
            } while (--mLen > 0);
        }
        return olen == n ? dst : null;
    }

    /* ------------------------------------------------------ bit reader */

    private static final class BitReader {
        final byte[] s;
        final int end;
        final int mode; // 8, 16, 32
        int ip;
        int bb;
        int bc;

        BitReader(byte[] s, int off, int len, int method) {
            this.s = s;
            this.ip = off;
            this.end = off + len;
            this.mode = (method == 3 || method == 6 || method == 9) ? 8
                    : (method == 4 || method == 7 || method == 10) ? 16 : 32;
        }

        int byteAt() {
            if (ip >= end) throw new IllegalStateException("compressed input overrun");
            return s[ip++] & 0xFF;
        }

        int bit() {
            if (bc == 0) fill();
            bc--;
            return (bb >>> bc) & 1;
        }

        private void fill() {
            if (ip >= end) throw new IllegalStateException("compressed input overrun");
            if (mode == 8) {
                bb = s[ip++] & 0xFF;
                bc = 8;
            } else if (mode == 16) {
                int a = s[ip++] & 0xFF;
                int b = ip < end ? s[ip++] & 0xFF : 0;
                bb = a | (b << 8);
                bc = 16;
            } else {
                int a = s[ip++] & 0xFF;
                int b = ip < end ? s[ip++] & 0xFF : 0;
                int c = ip < end ? s[ip++] & 0xFF : 0;
                int d = ip < end ? s[ip++] & 0xFF : 0;
                bb = a | (b << 8) | (c << 16) | (d << 24);
                bc = 32;
            }
        }
    }

    private static long u32(byte[] d, int o) {
        if (o < 0 || o + 4 > d.length) return -1;
        return (d[o] & 0xFFL) | ((d[o + 1] & 0xFFL) << 8)
                | ((d[o + 2] & 0xFFL) << 16) | ((d[o + 3] & 0xFFL) << 24);
    }
}
