package com.ramyelbanna.juzoor.engine;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lightweight bytecode hints mined from the disassembled Smali tree.
 *
 * <p>Obfuscated APKs rarely use a plain XOR loop that can be guessed in
 * isolation: the cipher call and its key are in the same method.  This scanner
 * reads the Smali instructions as text (it never executes them) and records
 * which decoder is called near which {@code const-string}: Base64, hex,
 * {@link StringBuilder#reverse() reverse}, XOR constants and digest/cipher
 * markers.  The resulting hints are keyed by a hash so the map can hold
 * hundreds of thousands of strings without retaining copies of the DEX string
 * table.</p>
 */
final class SmaliHints {

    private static final int MAX_HINTS = 200000;
    private static final int MAX_RECENT = 8;
    private static final int MAX_KEYS = 6;

    static final class Hint {
        private final int[] keys = new int[MAX_KEYS];
        private int keyCount;
        boolean base64;
        boolean hex;
        boolean reverse;
        boolean digest;
        boolean cipher;

        boolean hasKeys() {
            return keyCount > 0;
        }

        int[] keys() {
            int[] out = new int[keyCount];
            System.arraycopy(keys, 0, out, 0, keyCount);
            return out;
        }

        boolean hasLayers() {
            return base64 || hex || reverse;
        }

        private void addKey(int key) {
            if (key <= 0 || key > 255) return;
            for (int i = 0; i < keyCount; i++) {
                if (keys[i] == key) return;
            }
            if (keyCount < keys.length) keys[keyCount++] = key;
        }
    }

    private final Map<Long, Hint> hints = new HashMap<>();
    private final int[] freq = new int[256];
    private int decryptorSites;

    private SmaliHints() {
    }

    static SmaliHints scan(List<File> roots) {
        SmaliHints out = new SmaliHints();
        if (roots == null) return out;
        for (File root : roots) {
            if (root == null || !root.isDirectory()) continue;
            out.scanDir(root, 0);
        }
        return out;
    }

    Hint get(String text) {
        if (text == null) return null;
        return hints.get(keyOf(text));
    }

    /** Keys ordered by how often they are used next to {@code xor-int}. */
    int[] keys() {
        List<int[]> list = new ArrayList<>();
        for (int k = 1; k < 256; k++) {
            if (freq[k] > 0) list.add(new int[]{k, freq[k]});
        }
        Collections.sort(list, new Comparator<int[]>() {
            @Override
            public int compare(int[] a, int[] b) {
                return Integer.compare(b[1], a[1]);
            }
        });
        int n = Math.min(list.size(), 64);
        int[] keys = new int[n];
        for (int i = 0; i < n; i++) keys[i] = list.get(i)[0];
        return keys;
    }

    int siteCount() {
        return decryptorSites;
    }

    int hintCount() {
        return hints.size();
    }

    private void scanDir(File dir, int depth) {
        if (depth > 40) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                scanDir(f, depth + 1);
            } else if (f.getName().endsWith(".smali")) {
                scanFile(f);
            }
        }
    }

    private void scanFile(File file) {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(
                    new FileInputStream(file), StandardCharsets.UTF_8), 65536);
            ArrayDeque<String> recent = new ArrayDeque<>();
            int pendingConst = -1;
            String line;
            while ((line = reader.readLine()) != null) {
                String trim = line.trim();
                if (trim.startsWith(".method")) {
                    recent.clear();
                    pendingConst = -1;
                    continue;
                }
                if (trim.startsWith("const-string") || trim.startsWith("const-string/jumbo")) {
                    String value = smaliString(trim);
                    if (!value.isEmpty()) {
                        if (recent.size() >= MAX_RECENT) recent.removeFirst();
                        recent.addLast(value);
                    }
                    continue;
                }
                if (trim.startsWith("const/4") || trim.startsWith("const/16")
                        || (trim.startsWith("const ") && !trim.startsWith("const-string")
                        && !trim.startsWith("const-class") && !trim.startsWith("const-wide")
                        && !trim.startsWith("const-method"))) {
                    int comma = trim.lastIndexOf(',');
                    if (comma > 0) pendingConst = parseImm(trim.substring(comma + 1).trim());
                    continue;
                }
                if (trim.contains("xor-int")) {
                    int key = -1;
                    if (!trim.contains("/2addr")) {
                        int comma = trim.lastIndexOf(',');
                        if (comma > 0) key = parseImm(trim.substring(comma + 1).trim());
                    }
                    if (key < 0) key = pendingConst;
                    if (key >= 0 && key < 256) {
                        freq[key]++;
                        addKeyHints(recent, key);
                    }
                    pendingConst = -1;
                    continue;
                }
                if (trim.startsWith("invoke")) {
                    String lower = trim.toLowerCase(Locale.US);
                    if (lower.contains("base64;->decode")) {
                        addLayerHints(recent, HintKind.BASE64);
                    } else if (lower.contains("stringbuilder;->reverse")) {
                        addLayerHints(recent, HintKind.REVERSE);
                    } else if (lower.contains("messagedigest;->digest")) {
                        addLayerHints(recent, HintKind.DIGEST);
                    } else if (lower.contains("cipher;->dofinal") || lower.contains("cipher;->update")) {
                        addLayerHints(recent, HintKind.CIPHER);
                    }
                }
            }
        } catch (IOException ignored) {
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private enum HintKind { BASE64, REVERSE, DIGEST, CIPHER }

    private void addLayerHints(ArrayDeque<String> recent, HintKind kind) {
        decryptorSites++;
        int added = 0;
        for (java.util.Iterator<String> it = recent.descendingIterator(); it.hasNext() && added < 6; ) {
            Hint hint = hintFor(it.next(), true);
            if (hint == null) continue;
            if (kind == HintKind.BASE64) hint.base64 = true;
            else if (kind == HintKind.REVERSE) hint.reverse = true;
            else if (kind == HintKind.DIGEST) hint.digest = true;
            else hint.cipher = true;
            added++;
        }
    }

    private void addKeyHints(ArrayDeque<String> recent, int key) {
        int added = 0;
        for (java.util.Iterator<String> it = recent.descendingIterator(); it.hasNext() && added < 3; ) {
            Hint hint = hintFor(it.next(), true);
            if (hint == null) continue;
            hint.addKey(key);
            added++;
        }
    }

    private Hint hintFor(String text, boolean create) {
        long key = keyOf(text);
        Hint hint = hints.get(key);
        if (hint == null && create && hints.size() < MAX_HINTS) {
            hint = new Hint();
            hints.put(key, hint);
        }
        return hint;
    }

    private static long keyOf(String text) {
        return ((long) text.hashCode() << 32) ^ text.length();
    }

    /**
     * Extracts the quoted value from {@code const-string v0, "..."}.  Smali
     * escapes are reversed so the hint matches the real DEX string.
     */
    static String smaliString(String line) {
        int from = line.indexOf(", \"");
        if (from < 0) return "";
        from += 3;
        int to = line.lastIndexOf('"');
        if (to <= from) return "";
        return unescape(line.substring(from, to));
    }

    private static String unescape(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '\\' || i + 1 >= s.length()) {
                b.append(c);
                continue;
            }
            char next = s.charAt(++i);
            switch (next) {
                case 'n': b.append('\n'); break;
                case 'r': b.append('\r'); break;
                case 't': b.append('\t'); break;
                case '"': b.append('"'); break;
                case '\\': b.append('\\'); break;
                case 'u':
                    if (i + 4 < s.length()) {
                        try {
                            b.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16));
                            i += 4;
                        } catch (NumberFormatException e) {
                            b.append('u');
                        }
                    } else {
                        b.append('u');
                    }
                    break;
                default:
                    b.append(next);
            }
        }
        return b.toString();
    }

    static int parseImm(String s) {
        try {
            String v = s.trim();
            if (v.endsWith("L") || v.endsWith("l")) v = v.substring(0, v.length() - 1);
            if (v.startsWith("#")) v = v.substring(1);
            boolean negative = v.startsWith("-");
            if (negative) v = v.substring(1);
            int value;
            if (v.startsWith("0x") || v.startsWith("0X")) {
                value = (int) Long.parseLong(v.substring(2), 16);
            } else {
                value = Integer.parseInt(v);
            }
            return negative ? -value : value;
        } catch (Throwable t) {
            return -1;
        }
    }
}
