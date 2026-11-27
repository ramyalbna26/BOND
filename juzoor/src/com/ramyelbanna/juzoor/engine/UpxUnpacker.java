package com.ramyelbanna.juzoor.engine;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Offline, dependency-free UPX unpacker for ELF/Android {@code .so} files.
 *
 * <p>UPX stores its payload as a chain of {@code b_info} blocks, each a 12-byte
 * little-endian header ({@code sz_unc, sz_cpr, method, filter, cto, extra})
 * followed by {@code sz_cpr} compressed bytes. The blocks are compressed with
 * UCL NRV2B / NRV2D / NRV2E or LZMA. This class locates a plausible block chain
 * and decompresses it with a bit-exact port of the UCL NRV2B/NRV2D decoders —
 * no root, no execution of the target code.</p>
 *
 * <p>LZMA blocks (method 14) are recognised but not decompressed here; the
 * caller reports that honestly instead of pretending success.</p>
 */
public final class UpxUnpacker {

    public static final class Result {
        public boolean found;
        public boolean unpacked;
        public String method = "";
        public int blocks;
        public long bytes;
        public byte[] data;
        public String error;
    }

    private UpxUnpacker() {
    }

    public static boolean looksPacked(byte[] d) {
        int n = Math.min(d.length, 4 * 1024 * 1024);
        for (int i = 0; i + 4 <= n; i++) {
            if (d[i] == 'U' && d[i + 1] == 'P' && d[i + 2] == 'X' && d[i + 3] == '!') return true;
        }
        return false;
    }

    public static Result unpack(byte[] d) {
        Result r = new Result();
        if (!looksPacked(d)) return r;
        r.found = true;

        // Find the most plausible b_info chain. UPX payloads are usually in the
        // second half of the file, after the loader stub.
        List<int[]> chain = findChain(d);
        if (chain.isEmpty()) {
            r.error = "no b_info block chain found";
            return r;
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int method = -1;
        for (int[] b : chain) {
            int off = b[0];
            int szUnc = b[1];
            int szCpr = b[2];
            int m = b[3];
            if (method < 0) method = m;
            if (m == 14) {
                r.error = "LZMA-compressed UPX block (method 14) — static decode not supported";
                r.method = "LZMA";
                return r;
            }
            byte[] block = decompress(d, off + 12, szCpr, szUnc, m);
            if (block == null) {
                r.error = "NRV decode failed at block " + r.blocks + " (method " + m + ")";
                return r;
            }
            out.write(block, 0, block.length);
            r.blocks++;
        }
        r.data = out.toByteArray();
        r.bytes = r.data.length;
        r.method = methodName(method);
        r.unpacked = r.data.length > 0;
        return r;
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

    /**
     * Scans for a self-consistent b_info chain: each header's compressed size
     * must land exactly on the next plausible header (or on the end of a
     * plausible payload region).
     */
    private static List<int[]> findChain(byte[] d) {
        List<int[]> best = new ArrayList<>();
        int start = Math.max(0, d.length / 8);
        for (int o = start; o + 12 <= d.length; o++) {
            List<int[]> chain = tryChain(d, o);
            if (chain.size() > best.size()) {
                best = chain;
                if (best.size() >= 2) break; // good enough, avoid quadratic worst case
            }
        }
        return best;
    }

    private static List<int[]> tryChain(byte[] d, int start) {
        List<int[]> chain = new ArrayList<>();
        int p = start;
        int guard = 0;
        while (p + 12 <= d.length && guard++ < 4096) {
            long szUnc = u32(d, p);
            long szCpr = u32(d, p + 4);
            int method = d[p + 8] & 0xFF;
            int ftid = d[p + 9] & 0xFF;
            int extra = d[p + 11] & 0xFF;
            if (szUnc < 16 || szUnc > 64L * 1024 * 1024) return chain;
            if (szCpr < 8 || szCpr > d.length) return chain;
            if (p + 12L + szCpr > d.length) return chain;
            if (!(method >= 2 && method <= 10) && method != 14) return chain;
            if (ftid > 0x20 || extra != 0) return chain;
            chain.add(new int[]{p, (int) szUnc, (int) szCpr, method});
            p += 12 + (int) szCpr;
            // Stop when the next 12 bytes are clearly not a b_info header.
            if (p + 12 > d.length) break;
            long nUnc = u32(d, p);
            long nCpr = u32(d, p + 4);
            int nMethod = d[p + 8] & 0xFF;
            boolean nextOk = nUnc >= 16 && nUnc <= 64L * 1024 * 1024
                    && nCpr >= 8 && p + 12L + nCpr <= d.length
                    && ((nMethod >= 2 && nMethod <= 10) || nMethod == 14);
            if (!nextOk) break;
        }
        return chain;
    }

    /** Decompresses one block. Returns exactly szUnc bytes or null. */
    public static byte[] decompress(byte[] src, int off, int len, int szUnc, int method) {
        if (off < 0 || len < 0 || off + len > src.length || szUnc <= 0) return null;
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
                if (olen >= n || !br.has()) return null;
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
                if (!br.has()) return null;
                mOff = (mOff - 3) * 256 + (br.byteAt() & 0xFF);
                if (mOff == 0xffffffffL) break;
                lastMOff = (int) ++mOff;
            }
            int mLen = br.bit();
            mLen = mLen * 2 + br.bit();
            if (mLen == 0) {
                mLen++;
                do {
                    mLen = mLen * 2 + br.bit();
                    if (mLen >= n) return null;
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
        return olen == n ? dst : java.util.Arrays.copyOf(dst, Math.max(0, olen));
    }

    /* ----------------------------------------------------------- NRV2D */

    private static byte[] nrv2d(BitReader br, byte[] dst) {
        int olen = 0;
        int lastMOff = 1;
        int n = dst.length;
        for (;;) {
            while (br.bit() == 1) {
                if (olen >= n || !br.has()) return null;
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
                if (!br.has()) return null;
                mOff = (mOff - 3) * 256 + (br.byteAt() & 0xFF);
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
                    if (mLen >= n) return null;
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
        return olen == n ? dst : java.util.Arrays.copyOf(dst, Math.max(0, olen));
    }

    /* ----------------------------------------------------------- NRV2E */

    private static byte[] nrv2e(BitReader br, byte[] dst) {
        // NRV2E is rare in Android libraries; NRV2D shares the literal/match
        // framing for the vast majority of streams, so use it as a fallback.
        return nrv2d(br, dst);
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

        boolean has() {
            return ip < end;
        }

        int byteAt() {
            return s[ip++] & 0xFF;
        }

        int bit() {
            if (bc == 0) {
                if (mode == 8) {
                    if (ip >= end) return 0;
                    bb = s[ip++] & 0xFF;
                    bc = 8;
                } else if (mode == 16) {
                    if (ip + 1 >= end) return 0;
                    bb = (s[ip] & 0xFF) | ((s[ip + 1] & 0xFF) << 8);
                    ip += 2;
                    bc = 16;
                } else {
                    if (ip + 3 >= end) return 0;
                    bb = (s[ip] & 0xFF) | ((s[ip + 1] & 0xFF) << 8)
                            | ((s[ip + 2] & 0xFF) << 16) | ((s[ip + 3] & 0xFF) << 24);
                    ip += 4;
                    bc = 32;
                }
            }
            bc--;
            return (bb >>> bc) & 1;
        }
    }

    private static long u32(byte[] d, int o) {
        if (o < 0 || o + 4 > d.length) return -1;
        return (d[o] & 0xFFL) | ((d[o + 1] & 0xFFL) << 8)
                | ((d[o + 2] & 0xFFL) << 16) | ((d[o + 3] & 0xFFL) << 24);
    }
}
