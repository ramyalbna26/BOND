package com.ramyelbanna.juzoor.engine;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

/**
 * Streaming byte-by-byte string extractor for native libraries.
 *
 * <p>A 50 MB {@code .so} is read in 256 KB windows through a
 * {@link BufferedInputStream}; strings are written straight to disk, so the
 * extractor never holds the whole file (or the whole string table) in memory.
 * Four layers are recovered:</p>
 *
 * <ul>
 *   <li>printable ASCII runs (>= 4 characters),</li>
 *   <li>UTF-8 runs including Arabic, CJK and emoji,</li>
 *   <li>UTF-16 LE/BE runs (the encoding used by many packers),</li>
 *   <li>Base64 and hex payloads that decode to readable text.</li>
 * </ul>
 */
public final class NativeStrings {

    private static final int CHUNK = 256 * 1024;
    private static final int MIN = 4;
    private static final int MAX_RUN = 16 * 1024;
    private static final int MAX_DEDUPE = 400000;

    public static final class Result {
        public int ascii;
        public int utf8;
        public int utf16;
        public int encoded;
        public int total;
        public File file;
        public long bytesRead;
    }

    private NativeStrings() {
    }

    /**
     * Scans {@code in} and writes every recovered string to {@code out}.
     * When {@code append} is false the file is truncated first.
     */
    public static Result scan(File in, File out, boolean append) throws IOException {
        return scan(in, out, append, "native");
    }

    /** Same scanner, with a caller-supplied label used in the file header. */
    public static Result scan(File in, File out, boolean append, String kind) throws IOException {
        Result r = new Result();
        r.file = out;
        File parent = out.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        Set<Long> seen = new HashSet<>();
        Writer writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(out, append), StandardCharsets.UTF_8), 65536);
        try {
            if (!append) {
                writer.write("# Juzoor " + kind + " strings — " + in.getName() + "\n");
                writer.write("# ASCII · UTF-8 · UTF-16 LE/BE · decoded Base64/Hex\n\n");
            } else {
                writer.write("\n# Appended: " + in.getName() + "\n\n");
            }
            InputStream raw = new BufferedInputStream(new FileInputStream(in), CHUNK);
            try {
                byte[] chunk = new byte[CHUNK + 8];
                int carry = 0;
                while (true) {
                    int read = raw.read(chunk, carry, CHUNK);
                    if (read <= 0) break;
                    int len = carry + read;
                    r.bytesRead += read;
                    ascii(chunk, len, writer, r, seen);
                    utf8(chunk, len, writer, r, seen);
                    utf16(chunk, len, writer, r, seen);
                    // Keep the tail so a run that crosses the window boundary is
                    // not cut.  The scanners de-duplicate on their next pass.
                    carry = Math.min(7, len);
                    System.arraycopy(chunk, len - carry, chunk, 0, carry);
                }
            } finally {
                raw.close();
            }
        } finally {
            writer.close();
        }
        r.total = r.ascii + r.utf8 + r.utf16 + r.encoded;
        return r;
    }

    /* --------------------------------------------------------------- ASCII */

    private static void ascii(byte[] d, int len, Writer w, Result r, Set<Long> seen) throws IOException {
        int start = -1;
        for (int i = 0; i <= len; i++) {
            int c = i < len ? d[i] & 0xFF : 0;
            boolean ok = c >= 0x20 && c < 0x7F;
            if (ok) {
                if (start < 0) start = i;
            } else {
                if (start >= 0 && i - start >= MIN) {
                    String s = new String(d, start, i - start, StandardCharsets.US_ASCII);
                    int emitted = emit(w, seen, s, "A");
                    if (emitted > 0) { r.ascii++; r.encoded += emitted - 1; }
                }
                start = -1;
            }
        }
    }

    /* ---------------------------------------------------------------- UTF-8 */

    private static void utf8(byte[] d, int len, Writer w, Result r, Set<Long> seen) throws IOException {
        StringBuilder sb = new StringBuilder();
        boolean nonAscii = false;
        int i = 0;
        while (i < len) {
            int b = d[i] & 0xFF;
            if (b < 0x80) {
                if (printableAscii(b)) {
                    if (sb.length() < MAX_RUN) sb.append((char) b);
                } else {
                    flushUtf8(sb, nonAscii, w, r, seen);
                    nonAscii = false;
                }
                i++;
                continue;
            }
            int need = (b & 0xE0) == 0xC0 ? 1 : (b & 0xF0) == 0xE0 ? 2 : (b & 0xF8) == 0xF0 ? 3 : -1;
            if (need < 0 || i + need >= len) {
                flushUtf8(sb, nonAscii, w, r, seen);
                nonAscii = false;
                i++;
                continue;
            }
            int cp = b & (need == 1 ? 0x1F : need == 2 ? 0x0F : 0x07);
            boolean valid = true;
            for (int k = 1; k <= need; k++) {
                int cb = d[i + k] & 0xFF;
                if ((cb & 0xC0) != 0x80) { valid = false; break; }
                cp = (cp << 6) | (cb & 0x3F);
            }
            if (!valid || cp < 0x80 || cp > 0x10FFFF || (cp >= 0xD800 && cp <= 0xDFFF)) {
                flushUtf8(sb, nonAscii, w, r, seen);
                nonAscii = false;
                i++;
                continue;
            }
            if (readable(cp)) {
                if (sb.length() < MAX_RUN) sb.appendCodePoint(cp);
                if (cp > 0x7F) nonAscii = true;
            } else {
                flushUtf8(sb, nonAscii, w, r, seen);
                nonAscii = false;
            }
            i += need + 1;
        }
        flushUtf8(sb, nonAscii, w, r, seen);
    }

    private static void flushUtf8(StringBuilder sb, boolean nonAscii, Writer w, Result r,
                                  Set<Long> seen) throws IOException {
        if (nonAscii && sb.length() >= MIN) {
            int emitted = emit(w, seen, sb.toString(), "U8");
            if (emitted > 0) { r.utf8++; r.encoded += emitted - 1; }
        }
        sb.setLength(0);
    }

    /* --------------------------------------------------------------- UTF-16 */

    private static void utf16(byte[] d, int len, Writer w, Result r, Set<Long> seen) throws IOException {
        utf16Pass(d, len, 0, w, r, seen);
        utf16Pass(d, len, 1, w, r, seen);
    }

    /** Tests one of the two possible UTF-16 alignments in the buffer. */
    private static void utf16Pass(byte[] d, int len, int start, Writer w, Result r,
                                  Set<Long> seen) throws IOException {
        StringBuilder le = new StringBuilder();
        StringBuilder be = new StringBuilder();
        for (int i = start; i + 1 < len; i += 2) {
            int a = d[i] & 0xFF;
            int b = d[i + 1] & 0xFF;
            if (b == 0 && printableAscii(a)) {
                if (le.length() < MAX_RUN) le.append((char) a);
            } else {
                if (le.length() >= MIN) {
                    int emitted = emit(w, seen, le.toString(), "U16LE");
                    if (emitted > 0) { r.utf16++; r.encoded += emitted - 1; }
                }
                le.setLength(0);
            }
            if (a == 0 && printableAscii(b)) {
                if (be.length() < MAX_RUN) be.append((char) b);
            } else {
                if (be.length() >= MIN) {
                    int emitted = emit(w, seen, be.toString(), "U16BE");
                    if (emitted > 0) { r.utf16++; r.encoded += emitted - 1; }
                }
                be.setLength(0);
            }
        }
        if (le.length() >= MIN) {
            int emitted = emit(w, seen, le.toString(), "U16LE");
            if (emitted > 0) { r.utf16++; r.encoded += emitted - 1; }
        }
        if (be.length() >= MIN) {
            int emitted = emit(w, seen, be.toString(), "U16BE");
            if (emitted > 0) { r.utf16++; r.encoded += emitted - 1; }
        }
    }

    /* ---------------------------------------------------- decoded payloads */

    private static int emit(Writer w, Set<Long> seen, String s, String tag) throws IOException {
        if (s == null || s.length() < MIN) return 0;
        long key = ((long) s.hashCode() << 32) ^ (s.length() * 0x9E3779B97F4A7C15L);
        if (seen.size() < MAX_DEDUPE && !seen.add(key)) return 0;
        w.write('[');
        w.write(tag);
        w.write("] ");
        w.write(s.replace('\n', ' ').replace('\r', ' '));
        w.write('\n');
        int decoded = decodes(s) ? writeDecoded(w, s) : 0;
        return 1 + decoded;
    }

    /** True when a run is dense enough to be an encoded payload. */
    private static boolean decodes(String s) {
        if (s.length() < 8 || s.length() > 512) return false;
        if (s.indexOf(' ') >= 0) return false;
        return looksBase64(s) || looksHex(s);
    }

    private static int writeDecoded(Writer w, String s) throws IOException {
        int count = 0;
        if (looksHex(s) && s.length() % 2 == 0) {
            byte[] b = hex(s);
            String text = readableText(b);
            if (text != null) {
                w.write("[HEX] ");
                w.write(text);
                w.write('\n');
                count++;
            }
        }
        if (looksBase64(s)) {
            try {
                String normalized = s.replace('-', '+').replace('_', '/');
                int pad = (4 - normalized.length() % 4) % 4;
                for (int i = 0; i < pad; i++) normalized += '=';
                byte[] b = Base64.getDecoder().decode(normalized);
                String text = readableText(b);
                if (text != null) {
                    w.write("[B64] ");
                    w.write(text);
                    w.write('\n');
                    count++;
                }
            } catch (Throwable ignored) {
            }
        }
        return count;
    }

    private static boolean looksBase64(String s) {
        int letters = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean ok = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9') || c == '+' || c == '/' || c == '=' || c == '-' || c == '_';
            if (!ok) return false;
            if (Character.isLetter(c)) letters++;
        }
        return letters >= s.length() * 0.6;
    }

    private static boolean looksHex(String s) {
        if (s.length() < 8 || s.length() % 2 != 0) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) return false;
        }
        return true;
    }

    private static byte[] hex(String s) {
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    /** Returns the decoded payload only when it is mostly readable text. */
    private static String readableText(byte[] b) {
        if (b == null || b.length < 4) return null;
        StringBuilder sb = new StringBuilder(b.length);
        int printable = 0;
        for (byte value : b) {
            int c = value & 0xFF;
            if (c >= 0x20 && c < 0x7F) {
                printable++;
                sb.append((char) c);
            } else if (c == '\n' || c == '\r' || c == '\t') {
                printable++;
                sb.append(' ');
            } else {
                return null;
            }
        }
        if (printable < b.length * 0.9) return null;
        String out = sb.toString().trim();
        if (out.length() < 4) return null;
        int letters = 0;
        for (int i = 0; i < out.length(); i++) {
            if (Character.isLetter(out.charAt(i))) letters++;
        }
        return letters >= out.length() * 0.4 ? out : null;
    }

    private static boolean printableAscii(int c) {
        return c >= 0x20 && c < 0x7F;
    }

    private static boolean readable(int cp) {
        if (cp >= 0x20 && cp < 0x7F) return true;
        if (cp == '\t' || cp == '\n' || cp == '\r') return true;
        return Character.isLetterOrDigit(cp) || (cp >= 0x2000 && cp <= 0x2BFF);
    }
}
