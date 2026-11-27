package com.ramyelbanna.juzoor.engine;

import java.nio.charset.StandardCharsets;

/* loaded from: /workspace/apk_extract/classes.dex */
public class StringPool {
    private final String[] cache;
    private final int count;
    private final byte[] data;
    private final int[] offsets;
    private final int stringsStart;
    private final boolean utf8;

    public StringPool(byte[] bArr, int i) {
        this.data = bArr;
        int u16 = u16(i);
        if (u16 != 1) {
            throw new IllegalArgumentException("Not a string pool: 0x" + Integer.toHexString(u16));
        }
        int u162 = u16(i + 2);
        int i32 = i32(i + 8);
        int i322 = i32(i + 16);
        int i323 = i32(i + 20);
        this.count = i32;
        this.utf8 = (i322 & 256) != 0;
        this.stringsStart = i323 + i;
        this.offsets = new int[i32];
        this.cache = new String[i32];
        int i2 = i + u162;
        for (int i3 = 0; i3 < i32; i3++) {
            this.offsets[i3] = i32((i3 * 4) + i2);
        }
    }

    public int size() {
        return this.count;
    }

    public String get(int i) {
        if (i < 0 || i >= this.count) {
            return "";
        }
        String str = this.cache[i];
        if (str != null) {
            return str;
        }
        String decode = decode(i);
        this.cache[i] = decode;
        return decode;
    }

    private String decode(int i) {
        int i2 = this.stringsStart + this.offsets[i];
        if (this.utf8) {
            int[] readLen8 = readLen8(readLen8(i2)[1]);
            return decodeMutf8(readLen8[1], readLen8[0]);
        }
        int u16 = u16(i2);
        int i3 = i2 + 2;
        if ((32768 & u16) != 0) {
            u16 = ((u16 & 32767) << 16) | u16(i3);
            i3 += 2;
        }
        StringBuilder sb = new StringBuilder(u16);
        for (int i4 = 0; i4 < u16; i4++) {
            sb.append((char) u16(i3));
            i3 += 2;
        }
        return sb.toString();
    }

    private int[] readLen8(int i) {
        byte[] bArr = this.data;
        int i2 = bArr[i] & 255;
        if ((i2 & 128) != 0) {
            return new int[]{(bArr[i + 1] & 255) | ((i2 & 127) << 8), i + 2};
        }
        return new int[]{i2, i + 1};
    }

    private String decodeMutf8(int off, int len) {
        StringBuilder sb = new StringBuilder(len);
        byte[] d = this.data;
        int end = Math.min(off + len, d.length);
        int i = off;
        while (i < end) {
            int b = d[i] & 255;
            if (b < 0x80) {
                sb.append((char) b);
                i += 1;
            } else if ((b & 0xE0) == 0xC0 && i + 1 < end) {
                sb.append((char) (((b & 0x1F) << 6) | (d[i + 1] & 0x3F)));
                i += 2;
            } else if ((b & 0xF0) == 0xE0 && i + 2 < end) {
                sb.append((char) (((b & 0x0F) << 12)
                        | ((d[i + 1] & 0x3F) << 6)
                        | (d[i + 2] & 0x3F)));
                i += 3;
            } else if ((b & 0xF8) == 0xF0 && i + 3 < end) {
                int cp = ((b & 0x07) << 18)
                        | ((d[i + 1] & 0x3F) << 12)
                        | ((d[i + 2] & 0x3F) << 6)
                        | (d[i + 3] & 0x3F);
                sb.appendCodePoint(cp);
                i += 4;
            } else {
                sb.append('?');
                i += 1;
            }
        }
        return sb.toString();
    }

    private int u16(int i) {
        byte[] bArr = this.data;
        return ((bArr[i + 1] & 255) << 8) | (bArr[i] & 255);
    }

    private int i32(int i) {
        byte[] bArr = this.data;
        return ((bArr[i + 3] & 255) << 24) | (bArr[i] & 255) | ((bArr[i + 1] & 255) << 8) | ((bArr[i + 2] & 255) << 16);
    }

    static byte[] utf8(String str) {
        return str.getBytes(StandardCharsets.UTF_8);
    }
}
