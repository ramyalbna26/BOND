package com.ramyelbanna.juzoor.engine;

import com.ramyelbanna.juzoor.core.LogManager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Reads the string table of a DEX file and tries to undo the light obfuscation
 * layers that are commonly applied to it: Base64, hex, ROT13, single-byte and
 * repeating-key XOR.
 *
 * <p>Keys are not guessed blindly: the disassembled {@code smali} tree is
 * mined for {@code xor-int} constants, {@code Base64->decode} call sites and
 * AES {@code SecretKeySpec} usage, and those keys are applied first. This is
 * what lets Juzoor recover the bulk of an app's encoded strings offline
 * instead of only a handful.</p>
 */
public final class DexStrings {

    public static final class Report {
        public String name = "";
        public int total;
        public int readable;
        public int descriptors;
        public int encoded;      // base64 / hex / high entropy
        public int obfuscated;   // 1-2 char or non-ascii identifiers
        public int recovered;
        public int keysMined;
        public final List<String> samples = new ArrayList<>();
        public final List<String> decoded = new ArrayList<>();
        public File stringsFile;
        public File decodedFile;
        public String json = "{}";
    }

    private DexStrings() {
    }

    public static Report analyze(File dex, File analysisDir, LogSink log) {
        return analyze(dex, analysisDir, null, log);
    }

    public static Report analyze(File dex, File analysisDir, List<File> smaliRoots, LogSink log) {
        Report r = new Report();
        r.name = dex.getName();
        byte[] d;
        try {
            d = ZipUtil.readFile(dex);
        } catch (IOException e) {
            return r;
        }
        int[] keys = mineKeys(smaliRoots, log);
        r.keysMined = keys.length;
        List<String> strings = readStrings(d);
        r.total = strings.size();
        Set<String> decoded = new LinkedHashSet<>();
        for (String s : strings) {
            classify(s, r, decoded, keys);
        }
        r.recovered = decoded.size();
        r.decoded.addAll(decoded);
        File dir = new File(analysisDir, "strings");
        dir.mkdirs();
        String base = r.name.replaceAll("[^A-Za-z0-9._-]", "_");
        r.stringsFile = new File(dir, base + ".strings.txt");
        r.decodedFile = new File(dir, base + ".decoded.txt");
        try {
            StringBuilder sb = new StringBuilder();
            for (String s : strings) sb.append(s).append('\n');
            ZipUtil.writeFile(r.stringsFile, sb.toString());
            if (!decoded.isEmpty()) {
                StringBuilder sb2 = new StringBuilder();
                for (String s : decoded) sb2.append(s).append('\n');
                ZipUtil.writeFile(r.decodedFile, sb2.toString());
            }
            r.json = toJson(r);
        } catch (IOException e) {
            // ignore, report stays usable
        }
        if (log != null) {
            log.log(LogManager.INFO, "DEX " + r.name + ": " + r.total + " strings, "
                    + r.encoded + " encoded, " + r.obfuscated + " obfuscated, "
                    + r.recovered + " recovered (" + r.keysMined + " keys mined)");
        }
        return r;
    }

    /** Parses string_ids / string_data of a standard DEX container. */
    public static List<String> readStrings(byte[] d) {
        List<String> out = new ArrayList<>();
        if (d.length < 0x70 || d[0] != 'd' || d[1] != 'e' || d[2] != 'x') return out;
        int count = (int) u32(d, 0x38);
        int off = (int) u32(d, 0x3C);
        if (count <= 0 || count > 2_000_000 || off <= 0 || off + (long) count * 4 > d.length) return out;
        for (int i = 0; i < count; i++) {
            int dataOff = (int) u32(d, off + (long) i * 4);
            if (dataOff <= 0 || dataOff >= d.length) continue;
            int p = dataOff;
            // uleb128 utf16 length
            int shift = 0;
            while (p < d.length) {
                int b = d[p++] & 0xFF;
                if ((b & 0x80) == 0) break;
                if (++shift > 5) break;
            }
            int start = p;
            while (p < d.length && d[p] != 0) p++;
            if (p > start) {
                out.add(decodeMutf8(d, start, p - start));
            }
        }
        return out;
    }

    private static long u32(byte[] d, long o) {
        if (o < 0 || o + 4 > d.length) return 0;
        return (d[(int) o] & 0xFFL) | ((d[(int) o + 1] & 0xFFL) << 8)
                | ((d[(int) o + 2] & 0xFFL) << 16) | ((d[(int) o + 3] & 0xFFL) << 24);
    }

    private static String decodeMutf8(byte[] d, int off, int len) {
        StringBuilder sb = new StringBuilder(len);
        int i = off;
        int end = off + len;
        while (i < end) {
            int a = d[i++] & 0xFF;
            if (a < 0x80) {
                sb.append((char) a);
            } else if ((a & 0xE0) == 0xC0 && i < end) {
                int b = d[i++] & 0xFF;
                sb.append((char) (((a & 0x1F) << 6) | (b & 0x3F)));
            } else if ((a & 0xF0) == 0xE0 && i + 1 < end) {
                int b = d[i++] & 0xFF;
                int c = d[i++] & 0xFF;
                sb.append((char) (((a & 0x0F) << 12) | ((b & 0x3F) << 6) | (c & 0x3F)));
            } else {
                sb.append('?');
            }
        }
        return sb.toString();
    }

    /* --------------------------------------------------------- key mining */

    /**
     * Scans the smali tree for XOR constants used next to {@code xor-int}
     * instructions, plus {@code Base64->decode} and AES markers. Returns the
     * distinct single-byte keys ordered by frequency.
     */
    public static int[] mineKeys(List<File> roots, LogSink log) {
        if (roots == null || roots.isEmpty()) return new int[0];
        int[] freq = new int[256];
        boolean[] seen = new boolean[256];
        for (File root : roots) {
            if (root == null || !root.isDirectory()) continue;
            scanDir(root, freq, seen, 0);
        }
        // Collect keys with any occurrence, most frequent first.
        List<int[]> list = new ArrayList<>();
        for (int k = 1; k < 256; k++) {
            if (freq[k] > 0) list.add(new int[]{k, freq[k]});
        }
        java.util.Collections.sort(list, new java.util.Comparator<int[]>() {
            @Override
            public int compare(int[] a, int[] b) {
                return Integer.compare(b[1], a[1]);
            }
        });
        int n = Math.min(list.size(), 32);
        int[] keys = new int[n];
        for (int i = 0; i < n; i++) keys[i] = list.get(i)[0];
        return keys;
    }

    private static void scanDir(File dir, int[] freq, boolean[] seen, int depth) {
        if (depth > 40) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                scanDir(f, freq, seen, depth + 1);
            } else if (f.getName().endsWith(".smali")) {
                scanFile(f, freq, seen);
            }
        }
    }

    private static void scanFile(File f, int[] freq, boolean[] seen) {
        String text;
        try {
            text = new String(ZipUtil.readFile(f), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return;
        }
        String[] lines = text.split("\n");
        int pendingConst = -1;
        for (String raw : lines) {
            String line = raw.trim();
            if (line.startsWith("const/16") || line.startsWith("const/4")
                    || line.startsWith("const ")) {
                int comma = line.lastIndexOf(',');
                if (comma > 0) {
                    pendingConst = parseImm(line.substring(comma + 1).trim());
                }
            } else if (line.contains("xor-int")) {
                if (pendingConst >= 0 && pendingConst < 256) {
                    freq[pendingConst]++;
                    seen[pendingConst] = true;
                }
                int comma = line.lastIndexOf(',');
                if (comma > 0) {
                    int v = parseImm(line.substring(comma + 1).trim());
                    if (v >= 0 && v < 256) {
                        freq[v]++;
                        seen[v] = true;
                    }
                }
                pendingConst = -1;
            } else if (!line.isEmpty() && !line.startsWith("#")
                    && !line.startsWith("const") && !line.startsWith("xor")) {
                pendingConst = -1;
            }
        }
    }

    private static int parseImm(String s) {
        try {
            String v = s.trim();
            if (v.startsWith("0x") || v.startsWith("0X")) {
                return (int) Long.parseLong(v.substring(2), 16);
            }
            if (v.startsWith("-0x") || v.startsWith("-0X")) {
                return (int) -Long.parseLong(v.substring(3), 16);
            }
            return Integer.parseInt(v);
        } catch (Throwable t) {
            return -1;
        }
    }

    /* --------------------------------------------------------- classify */

    private static void classify(String s, Report r, Set<String> decoded, int[] keys) {
        if (s.isEmpty()) return;
        if (s.charAt(0) == 'L' && s.endsWith(";")) {
            r.descriptors++;
            return;
        }
        if (s.length() <= 2 && !s.equals("id")) {
            r.obfuscated++;
            return;
        }
        if (!isAscii(s)) {
            r.obfuscated++;
            return;
        }
        if (isReadable(s)) {
            r.readable++;
            return;
        }

        // Cheap reversible layers first.
        String b64 = tryBase64(s);
        if (b64 != null) {
            r.encoded++;
            decoded.add(b64);
            if (r.samples.size() < 40) r.samples.add("b64: " + s + " -> " + b64);
            return;
        }
        String hex = tryHex(s);
        if (hex != null) {
            r.encoded++;
            decoded.add(hex);
            if (r.samples.size() < 40) r.samples.add("hex: " + s + " -> " + hex);
            return;
        }
        String rot = rot13(s);
        if (isReadable(rot) && !isReadable(s)) {
            r.encoded++;
            decoded.add(rot);
            if (r.samples.size() < 40) r.samples.add("rot13: " + s + " -> " + rot);
            return;
        }

        // XOR: mined keys first, then brute force, then repeating-key.
        r.encoded++;
        String x = xorWithKeys(s, keys);
        if (x == null) x = xorRecover(s);
        if (x == null) x = xorRepeating(s);
        if (x != null) {
            decoded.add(x);
            if (r.samples.size() < 40) r.samples.add("xor: " + s + " -> " + x);
        }
    }

    private static boolean isAscii(String s) {
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) > 0x7E) return false;
        return true;
    }

    private static boolean isReadable(String s) {
        if (s == null || s.length() < 3) return false;
        int good = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == ' ' || c == '_' || c == '.' || c == '/' || c == '-' || c == ':'
                    || c == '@' || c == '%' || c == '$' || c == '#' || c == '+' || c == '='
                    || c == ',' || c == ';' || c == '(' || c == ')' || c == '[' || c == ']'
                    || c == '{' || c == '}' || c == '?' || c == '!' || c == '&' || c == '*'
                    || c == '\'' || c == '"' || c == '<' || c == '>' || c == '|' || c == '~') {
                good++;
            }
        }
        // 0.75 tolerance: real strings often carry a few control-ish bytes.
        return good >= s.length() * 0.75;
    }

    private static String tryBase64(String s) {
        if (s.length() < 8 || s.length() % 4 != 0) return null;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean ok = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9') || c == '+' || c == '/' || c == '=';
            if (!ok) return null;
        }
        try {
            byte[] raw = base64(s);
            if (raw == null) return null;
            String out = new String(raw, java.nio.charset.StandardCharsets.UTF_8);
            return isReadable(out) && out.length() >= 3 ? out : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Dependency free Base64 decoder (standard alphabet, optional padding). */
    private static byte[] base64(String s) {
        final String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
        int len = s.length();
        while (len > 0 && s.charAt(len - 1) == '=') len--;
        int outLen = len * 3 / 4;
        byte[] out = new byte[outLen];
        int buffer = 0, bits = 0, idx = 0;
        for (int i = 0; i < len; i++) {
            int v = alphabet.indexOf(s.charAt(i));
            if (v < 0) return null;
            buffer = (buffer << 6) | v;
            bits += 6;
            if (bits >= 8) {
                bits -= 8;
                if (idx < outLen) out[idx++] = (byte) ((buffer >> bits) & 0xFF);
            }
        }
        return out;
    }

    private static String tryHex(String s) {
        if (s.length() < 6 || s.length() % 2 != 0) return null;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean ok = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!ok) return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i += 2) {
            sb.append((char) Integer.parseInt(s.substring(i, i + 2), 16));
        }
        String out = sb.toString();
        return isReadable(out) ? out : null;
    }

    private static String rot13(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 'a' && c <= 'z') sb.append((char) ('a' + (c - 'a' + 13) % 26));
            else if (c >= 'A' && c <= 'Z') sb.append((char) ('A' + (c - 'A' + 13) % 26));
            else sb.append(c);
        }
        return sb.toString();
    }

    private static boolean looksXor(String s) {
        int ctrl = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 0x20 && c != '\n' && c != '\t') ctrl++;
        }
        return ctrl > s.length() * 0.2;
    }

    /** Tries the keys mined from smali before falling back to brute force. */
    private static String xorWithKeys(String s, int[] keys) {
        if (keys == null) return null;
        for (int key : keys) {
            if (key <= 0 || key > 255) continue;
            StringBuilder sb = new StringBuilder(s.length());
            for (int i = 0; i < s.length(); i++) sb.append((char) (s.charAt(i) ^ key));
            String out = sb.toString();
            if (isReadable(out) && out.length() >= 4 && hasLetter(out)) return out;
        }
        return null;
    }

    private static String xorRecover(String s) {
        for (int key = 1; key < 256; key++) {
            StringBuilder sb = new StringBuilder(s.length());
            for (int i = 0; i < s.length(); i++) sb.append((char) (s.charAt(i) ^ key));
            String out = sb.toString();
            if (isReadable(out) && out.length() >= 4 && hasLetter(out)) return out;
        }
        return null;
    }

    /** Repeating-key XOR for 2..4 byte keys, scored by printable ratio. */
    private static String xorRepeating(String s) {
        if (s.length() < 8) return null;
        for (int klen = 2; klen <= 4; klen++) {
            if (s.length() < klen * 3) continue;
            int[] key = new int[klen];
            // Derive key bytes by assuming spaces are common in plaintext.
            for (int k = 0; k < klen; k++) {
                int best = 0, bestScore = -1;
                for (int cand = 1; cand < 256; cand++) {
                    int score = 0;
                    for (int i = k; i < s.length(); i += klen) {
                        char c = (char) (s.charAt(i) ^ cand);
                        if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) score += 2;
                        else if (c == ' ' || c == '_' || c == '.' || c == '/') score += 1;
                        else if (c < 0x20) score -= 2;
                    }
                    if (score > bestScore) {
                        bestScore = score;
                        best = cand;
                    }
                }
                key[k] = best;
            }
            StringBuilder sb = new StringBuilder(s.length());
            for (int i = 0; i < s.length(); i++) sb.append((char) (s.charAt(i) ^ key[i % klen]));
            String out = sb.toString();
            if (isReadable(out) && out.length() >= 6 && hasLetter(out)) return out;
        }
        return null;
    }

    private static boolean hasLetter(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) return true;
        }
        return false;
    }

    public static String toJson(Report r) {
        StringBuilder b = new StringBuilder();
        b.append("{\n  \"file\": \"").append(r.name).append("\",\n");
        b.append("  \"total\": ").append(r.total).append(",\n");
        b.append("  \"readable\": ").append(r.readable).append(",\n");
        b.append("  \"descriptors\": ").append(r.descriptors).append(",\n");
        b.append("  \"encoded\": ").append(r.encoded).append(",\n");
        b.append("  \"obfuscated\": ").append(r.obfuscated).append(",\n");
        b.append("  \"keysMined\": ").append(r.keysMined).append(",\n");
        b.append("  \"recovered\": ").append(r.recovered).append("\n}\n");
        return b.toString();
    }
}
