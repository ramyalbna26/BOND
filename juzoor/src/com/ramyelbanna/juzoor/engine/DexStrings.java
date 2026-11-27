package com.ramyelbanna.juzoor.engine;

import com.ramyelbanna.juzoor.core.LogManager;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
 * AES {@code SecretKeySpec} usage, and those keys are applied first.</p>
 *
 * <p>DEX stores strings as MUTF-8. An encrypted string is therefore stored as
 * arbitrary bytes, not as the original Unicode characters; decoding the
 * MUTF-8 first and then XOR-ing the characters destroys the ciphertext. This
 * version keeps the raw bytes of every string entry and tries Base64, hex,
 * ROT13, single-byte XOR and repeating-key XOR on those bytes as well as on
 * the decoded text. It also streams the whole DEX byte-by-byte (the same
 * engine used for native libraries) so strings outside the string table and
 * embedded payloads are not missed.</p>
 */
public final class DexStrings {

    public static final class Report {
        public String name = "";
        public int total;        // every string table entry, i.e. 100% of DEX strings
        public int readable;
        public int descriptors;
        public int encoded;      // non-readable strings that were fed to a decoder
        public int obfuscated;   // 1-2 char identifiers with no readable plaintext
        public int recovered;
        public int keysMined;
        public int hints;        // const-string/decryptor pairs mined from Smali
        public int decryptorSites;
        public int resolved;     // readable + decoded entries (usable as text)
        public int rawStrings;   // extra strings found by the byte-by-byte scan
        public int rawDecoded;   // Base64/hex payloads decoded by that scan
        public final List<String> samples = new ArrayList<>();
        public File stringsFile;
        public File decodedFile;
        public File rawFile;
        public String json = "{}";
    }

    /** One string table entry: decoded text plus the exact MUTF-8 bytes. */
    public static final class RawString {
        public final String text;
        public final byte[] raw;

        RawString(String text, byte[] raw) {
            this.text = text;
            this.raw = raw;
        }
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
        SmaliHints hints = SmaliHints.scan(smaliRoots);
        int[] keys = hints.keys();
        r.keysMined = keys.length;
        r.hints = hints.hintCount();
        r.decryptorSites = hints.siteCount();
        List<RawString> rawStrings = readRawStrings(d);
        r.total = rawStrings.size();
        Set<String> decoded = new LinkedHashSet<>();
        long[] budget = {180_000_000L};
        for (RawString entry : rawStrings) {
            classify(entry, r, decoded, keys, hints, budget);
        }
        r.recovered = decoded.size();
        r.resolved = r.readable + r.recovered;
        File dir = new File(analysisDir, "strings");
        dir.mkdirs();
        String base = r.name.replaceAll("[^A-Za-z0-9._-]", "_");
        r.stringsFile = new File(dir, base + ".strings.txt");
        r.decodedFile = new File(dir, base + ".decoded.txt");
        r.rawFile = new File(dir, base + ".raw.txt");
        try {
            StringBuilder sb = new StringBuilder();
            for (RawString entry : rawStrings) sb.append(entry.text).append('\n');
            ZipUtil.writeFile(r.stringsFile, sb.toString());
            if (!decoded.isEmpty()) {
                StringBuilder sb2 = new StringBuilder();
                for (String s : decoded) sb2.append(s).append('\n');
                ZipUtil.writeFile(r.decodedFile, sb2.toString());
            }
            // Byte-by-byte pass: DEX data sections can hide strings that are
            // not referenced by string_ids (packed assets, embedded DEX/ZIP,
            // obfuscated string blobs).
            NativeStrings.Result raw = NativeStrings.scan(dex, r.rawFile, false, "DEX raw");
            r.rawStrings = raw.total;
            r.rawDecoded = raw.encoded;
            r.json = toJson(r);
        } catch (IOException e) {
            // ignore, report stays usable
        }
        if (log != null) {
            log.log(LogManager.INFO, "DEX " + r.name + ": " + r.total + " strings extracted (100%), "
                    + r.encoded + " encoded, " + r.obfuscated + " obfuscated, "
                    + r.recovered + " decoded (" + r.keysMined + " keys mined, "
                    + r.hints + " hints), " + r.rawStrings + " raw byte-run strings");
        }
        return r;
    }

    /** Parses string_ids / string_data of a standard DEX container. */
    public static List<String> readStrings(byte[] d) {
        List<String> out = new ArrayList<>();
        for (RawString entry : readRawStrings(d)) out.add(entry.text);
        return out;
    }

    /** Parses every string entry while preserving its exact MUTF-8 bytes. */
    public static List<RawString> readRawStrings(byte[] d) {
        List<RawString> out = new ArrayList<>();
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
                byte[] raw = new byte[p - start];
                System.arraycopy(d, start, raw, 0, raw.length);
                out.add(new RawString(decodeMutf8(d, start, p - start), raw));
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

    /**
     * Strict MUTF-8 check. DEX string_data must be valid MUTF-8; a string whose
     * bytes are not is ciphertext (or a deliberately malformed obfuscated
     * entry), so it must never be accepted as readable text just because the
     * naive decoder produced Arabic/Cyrillic-looking code points.
     */
    private static boolean isValidMutf8(byte[] d) {
        if (d == null) return false;
        int i = 0;
        while (i < d.length) {
            int a = d[i++] & 0xFF;
            if (a == 0) return false;
            if (a < 0x80) continue;
            if (a >= 0xC0 && a <= 0xDF) {
                if (i >= d.length || (d[i++] & 0xC0) != 0x80) return false;
            } else if (a >= 0xE0 && a <= 0xEF) {
                if (i + 1 >= d.length) return false;
                if ((d[i++] & 0xC0) != 0x80 || (d[i++] & 0xC0) != 0x80) return false;
            } else {
                // Four-byte UTF-8 (and stray continuation/lead bytes) are not
                // valid Modified UTF-8 used by DEX.
                return false;
            }
        }
        return true;
    }

    /* --------------------------------------------------------- key mining */

    /**
     * Scans the Smali tree for XOR constants and decryptor call sites.
     * Kept as a public entry point for callers that only need the key list;
     * {@link #analyze} scans once and reuses the full hint table.
     */
    public static int[] mineKeys(List<File> roots, LogSink log) {
        return SmaliHints.scan(roots).keys();
    }

    /* --------------------------------------------------------- classify */

    /**
     * Keys that obfuscators use most often.  Trying them first makes the live
     * pass predictable and covers the common "weird first character" tables
     * before the exhaustive 255-key fallback.
     */
    private static final int[] COMMON_KEYS = {
            0x3D, 0x55, 0xFF, 0x33, 0x5A, 0x13, 0x42, 0x66, 0x77, 0xAA, 0xCC, 0x7F, 0x1F, 0x2A
    };

    private static void classify(RawString entry, Report r, Set<String> decoded,
                                 int[] keys, SmaliHints hints, long[] budget) {
        String s = entry.text;
        if (s == null || s.isEmpty()) return;
        if (s.charAt(0) == 'L' && s.endsWith(";")) {
            r.descriptors++;
            return;
        }
        SmaliHints.Hint hint = hints == null ? null : hints.get(s);
        boolean plain = (isAscii(s) && isReadable(s))
                || (isValidMutf8(entry.raw) && isHighText(s));
        if (plain) {
            // Base64 and hex payloads are printable, so the old "readable"
            // shortcut hid them. Cheap literal layers are always tried; the
            // Smali hints add reverse/XOR when a decryptor call is adjacent.
            String layered = decodePrintableLayers(s);
            if (accept(layered, s, null)) {
                r.encoded++;
                record(r, decoded, "layered", s, layered);
                return;
            }
            String contextual = decodeContext(s, entry.raw, hint, budget);
            if (accept(contextual, s, hint)) {
                r.encoded++;
                record(r, decoded, "context", s, contextual);
                return;
            }
            String smart = smartXor(s, entry.raw, hint, keys, budget);
            if (smart != null && textScore(smart) >= textScore(s) + 6) {
                r.encoded++;
                record(r, decoded, "xor", s, smart);
                return;
            }
            r.readable++;
            return;
        }
        if (s.length() <= 2 && entry.raw.length <= 2) {
            r.obfuscated++;
            return;
        }
        // Every non-readable entry is a decoder candidate. In the old code a
        // non-ASCII string was counted as "obfuscated" and never decoded, so
        // XOR-encrypted tables were exactly the ones that were skipped.
        r.encoded++;
        String plainText = decodeContext(s, entry.raw, hint, budget);
        if (plainText == null || !accept(plainText, s, hint)) {
            plainText = decodeText(s, keys, budget);
        }
        // MUTF-8 cannot represent arbitrary ciphertext. When the decoded text
        // contains replacement/question marks or non-ASCII code points, the
        // raw bytes are the authoritative ciphertext and must be tried too.
        if (entry.raw.length > 0 && (plainText == null || s.indexOf('?') >= 0 || !isAscii(s))) {
            String rawPlain = decodeBytes(entry.raw, keys, budget);
            if (rawPlain != null && (plainText == null || textScore(rawPlain) > textScore(plainText))) {
                plainText = rawPlain;
            }
        }
        if (accept(plainText, s, hint)) {
            record(r, decoded, "decoded", s, plainText);
        } else if (s.length() <= 3) {
            r.obfuscated++;
        }
    }

    /** Records one successful decode and keeps the audit sample list bounded. */
    private static void record(Report r, Set<String> decoded, String kind,
                               String source, String plain) {
        decoded.add(plain);
        if (r.samples.size() < 40) {
            r.samples.add(kind + ": " + printableSample(source) + " -> " + printableSample(plain));
        }
    }

    /**
     * A decoder result is accepted when it is real text, or when bytecode
     * context proves a decoder was used and the output is at least printable.
     */
    private static boolean accept(String out, String source, SmaliHints.Hint hint) {
        if (out == null || out.length() < 3 || out.equals(source)) return false;
        if (isMeaningful(out)) return true;
        if (hint == null) return false;
        return (hint.hasKeys() || hint.hasLayers()) && isUsableDecoded(out);
    }

    /** Printable, low-noise output used for context-confirmed decoders. */
    private static boolean isUsableDecoded(String s) {
        if (!isReadable(s)) return false;
        int alnum = 0;
        int question = 0;
        int i = 0;
        while (i < s.length()) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.isLetterOrDigit(cp) || isScriptCodePoint(cp)) alnum++;
            if (cp == '?') question++;
        }
        if (question > Math.max(1, s.length() / 12)) return false;
        return alnum >= s.length() * 0.55;
    }

    private static String decodePrintableLayers(String s) {
        if (!isAscii(s) || s.length() < 8 || s.length() > 4096) return null;
        String hex = tryHex(s);
        if (hex != null) return hex;
        return tryBase64(s);
    }

    private static String decodeText(String s, int[] keys, long[] budget) {
        String layered = decodePrintableLayers(s);
        if (isMeaningful(layered)) return layered;
        String rot = rot13(s);
        if (isMeaningful(rot)) return rot;
        String x = xorCommonLoose(s, budget);
        // A loose common-key candidate must not hide a stronger candidate from
        // a Smali-mined key or the exhaustive scan. Only a fully meaningful
        // common-key result may short-circuit those passes.
        if (!isMeaningful(x)) x = null;
        if (x == null) x = xorWithKeys(s, keys, budget);
        if (x == null) x = xorRecover(s, budget);
        if (x == null) x = xorRepeating(s, budget);
        return x;
    }

    /**
     * Applies the decoders that the surrounding Smali actually calls.  This is
     * what recovers a readable-looking ciphertext (reverse, XOR constants) that
     * the generic "is this printable?" shortcut would otherwise keep as plain
     * text.
     */
    private static String decodeContext(String s, byte[] raw, SmaliHints.Hint hint,
                                        long[] budget) {
        if (hint == null || (!hint.hasLayers() && !hint.hasKeys())) return null;
        String best = null;
        int bestScore = Integer.MIN_VALUE;
        if (hint.reverse) {
            String rev = reverse(s);
            if (accept(rev, s, hint) && textScore(rev) > bestScore) {
                best = rev;
                bestScore = textScore(rev);
            }
        }
        if (hint.base64) {
            String b64 = tryBase64(s);
            if (accept(b64, s, hint) && textScore(b64) > bestScore) {
                best = b64;
                bestScore = textScore(b64);
            }
        }
        if (hint.hex) {
            String hex = tryHex(s);
            if (accept(hex, s, hint) && textScore(hex) > bestScore) {
                best = hex;
                bestScore = textScore(hex);
            }
        }
        if (hint.hasKeys()) {
            for (int key : hint.keys()) {
                String text = xorTextLoose(s, key, budget);
                if (accept(text, s, hint) && textScore(text) > bestScore) {
                    best = text;
                    bestScore = textScore(text);
                }
                if (raw != null) {
                    String bytes = xorBytesLoose(raw, key, budget);
                    if (accept(bytes, s, hint) && textScore(bytes) > bestScore) {
                        best = bytes;
                        bestScore = textScore(bytes);
                    }
                }
            }
        }
        return best;
    }

    /**
     * Applies common XOR keys to printable strings that start with an unusual
     * symbol or that Smali tied to an XOR instruction.
     */
    private static String smartXor(String s, byte[] raw, SmaliHints.Hint hint,
                                   int[] keys, long[] budget) {
        if (!looksSuspicious(s) && (hint == null || !hint.hasKeys())) return null;
        String best = null;
        int bestScore = textScore(s);
        best = better(best, bestScore, xorCommonLoose(s, budget), s, hint);
        if (best != null) bestScore = textScore(best);
        if (hint != null && hint.hasKeys()) {
            for (int key : hint.keys()) {
                String text = xorTextLoose(s, key, budget);
                if (text != null && accept(text, s, hint) && textScore(text) > bestScore + 6) {
                    best = text;
                    bestScore = textScore(text);
                }
                if (raw != null) {
                    String bytes = xorBytesLoose(raw, key, budget);
                    if (bytes != null && accept(bytes, s, hint) && textScore(bytes) > bestScore + 6) {
                        best = bytes;
                        bestScore = textScore(bytes);
                    }
                }
            }
        }
        if (best == null && keys != null && keys.length > 0) {
            for (int i = 0; i < Math.min(8, keys.length); i++) {
                String text = xorTextLoose(s, keys[i], budget);
                if (text != null && accept(text, s, hint) && textScore(text) > bestScore + 6) {
                    best = text;
                    bestScore = textScore(text);
                }
            }
        }
        return best;
    }

    private static String better(String best, int bestScore, String candidate,
                                 String source, SmaliHints.Hint hint) {
        if (candidate == null || !accept(candidate, source, hint)) return best;
        int score = textScore(candidate);
        return score > bestScore + 6 ? candidate : best;
    }

    private static boolean looksSuspicious(String s) {
        if (s == null || s.length() < 4 || !isAscii(s)) return false;
        char first = s.charAt(0);
        boolean weirdFirst = !(Character.isLetterOrDigit(first) || first == '/' || first == '_'
                || first == '.' || first == '-' || first == '#' || first == '@' || first == '[');
        if (weirdFirst) return true;
        if (s.indexOf(' ') >= 0) return false;
        int unusual = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{' || c == '}' || c == '\\' || c == '^' || c == '~' || c == '|' || c == '`') {
                unusual++;
            }
        }
        return unusual >= 2;
    }

    private static boolean containsCommonKey(int key) {
        for (int k : COMMON_KEYS) if (k == key) return true;
        return false;
    }

    /** Decodes the raw MUTF-8 bytes of one string entry. */
    private static String decodeBytes(byte[] raw, int[] keys, long[] budget) {
        if (raw == null || raw.length < 4 || raw.length > 16384) return null;
        String ascii = new String(raw, StandardCharsets.ISO_8859_1);
        if (isAscii(ascii)) {
            String hex = tryHex(ascii);
            if (hex != null) return hex;
            String b64 = tryBase64(ascii);
            if (b64 != null) return b64;
        }
        String u16 = tryUtf16(raw);
        if (u16 != null) return u16;
        String x = xorCommonBytes(raw, budget);
        if (x == null) x = xorBytes(raw, keys, budget);
        if (x == null) x = xorRepeatingBytes(raw, budget);
        return x;
    }

    private static String reverse(String s) {
        if (s == null || s.length() < 2) return null;
        StringBuilder b = new StringBuilder(s.length());
        for (int i = s.length() - 1; i >= 0; i--) b.append(s.charAt(i));
        return b.toString();
    }

    private static boolean isAscii(String s) {
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) > 0x7E) return false;
        return true;
    }

    /**
     * True for text a human would call readable. It accepts Arabic, CJK and
     * the other scripts an APK can legitimately ship; random bytes decoded as
     * MUTF-8 normally land in Latin-1 controls or private-use code points and
     * fail the 75% threshold.
     */
    private static boolean isReadable(String s) {
        if (s == null || s.length() < 3) return false;
        int good = 0;
        int i = 0;
        while (i < s.length()) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == 0xFFFD) return false;
            if (cp < 0x20 && cp != '\t' && cp != '\n' && cp != '\r') return false;
            if (isTextCodePoint(cp)) good++;
        }
        return good >= s.length() * 0.75;
    }

    /** Relaxed readable test for text that is already valid MUTF-8. */
    private static boolean isHighText(String s) {
        if (!isReadable(s)) return false;
        int alpha = 0;
        int i = 0;
        while (i < s.length()) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.isLetterOrDigit(cp) || isScriptCodePoint(cp)) alpha++;
        }
        return alpha >= s.length() * 0.5;
    }

    /**
     * Stricter test used only for decoder output. A random XOR/Base64 decode
     * is often printable but consists of punctuation and question marks;
     * requiring half letters/digits removes that noise.
     */
    private static boolean isMeaningful(String s) {
        if (s == null || s.length() < 4 || !isReadable(s)) return false;
        int asciiAlpha = 0;
        int asciiDigits = 0;
        int scriptLetters = 0;
        int latinLetters = 0;
        int spaces = 0;
        int question = 0;
        int i = 0;
        while (i < s.length()) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            if (cp >= 0x20 && cp < 0x7F) {
                if ((cp >= 'a' && cp <= 'z') || (cp >= 'A' && cp <= 'Z')) asciiAlpha++;
                else if (cp >= '0' && cp <= '9') asciiDigits++;
                if (cp == ' ') spaces++;
            } else if (isScriptCodePoint(cp)) {
                scriptLetters++;
            } else if (cp >= 0xC0 && cp <= 0x24F) {
                latinLetters++;
            }
            if (cp == '?') question++;
        }
        if (question > Math.max(1, s.length() / 10)) return false;
        int alpha = asciiAlpha + scriptLetters + latinLetters;
        int alnum = alpha + asciiDigits;
        if (alnum < s.length() * 0.5) return false;
        if (alpha < Math.max(2, s.length() * 0.4)) return false;
        if (scriptLetters >= s.length() * 0.4) return true;
        if (scriptLetters > 0 && spaces > 0) return true;
        // A pure Latin-1 byte run is not text unless it has word spaces, and
        // random XOR output must not be dominated by accented bytes.
        if (latinLetters > 0) {
            if (spaces == 0) return false;
            if (asciiAlpha < alpha * 0.6) return false;
        }
        if (spaces > 0) {
            return alpha >= s.length() * 0.5 && hasPlausibleWords(s);
        }
        String lower = s.toLowerCase(Locale.US);
        if (containsKnownMarker(lower)) return true;
        if (containsUncommonPunctuation(lower)) return false;
        int vowels = 0;
        for (int k = 0; k < lower.length(); k++) {
            char c = lower.charAt(k);
            if (c == 'a' || c == 'e' || c == 'i' || c == 'o' || c == 'u' || c == 'y') vowels++;
        }
        int needed = lower.length() >= 10 ? 3 : 2;
        return alpha >= 6 && vowels >= Math.max(2, alpha / 4) && commonBigrams(lower) >= needed;
    }

    private static boolean containsUncommonPunctuation(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{' || c == '}' || c == '\\' || c == '^' || c == '~' || c == '|' || c == '`') {
                return true;
            }
        }
        return false;
    }

    private static boolean hasPlausibleWords(String s) {
        String lowerAll = s.toLowerCase(Locale.US);
        boolean knownText = containsKnownMarker(lowerAll);
        String[] tokens = s.split("[^A-Za-z0-9\u00C0-\u024F]+");
        boolean sawWord = false;
        for (String token : tokens) {
            if (token.isEmpty()) continue;
            boolean digits = true;
            boolean vowel = false;
            for (int i = 0; i < token.length(); i++) {
                char c = Character.toLowerCase(token.charAt(i));
                if (c < '0' || c > '9') digits = false;
                if (c == 'a' || c == 'e' || c == 'i' || c == 'o' || c == 'u' || c == 'y') vowel = true;
            }
            if (digits) continue;
            if (hasTripleRepeat(token)) return false;
            String lower = token.toLowerCase(Locale.US);
            boolean known = containsKnownMarker(lower);
            if (!knownText) {
                if (token.length() == 1) {
                    if (lower.equals("a") || lower.equals("i")) sawWord = true;
                    continue;
                }
                if (token.length() == 2) {
                    if (isCommonTwoLetter(lower)) sawWord = true;
                    else return false;
                    continue;
                }
                if (!Character.isLetter(token.charAt(0))) return false;
                if (!vowel && !known) return false;
            }
            if (vowel || known) sawWord = true;
        }
        return sawWord;
    }

    private static boolean isCommonTwoLetter(String s) {
        String[] words = {"ok", "no", "is", "in", "on", "at", "to", "of", "or", "my",
                "by", "up", "we", "it", "as", "be", "do", "go", "if", "me", "he", "so", "us"};
        for (String word : words) if (word.equals(s)) return true;
        return false;
    }

    private static boolean hasTripleRepeat(String s) {
        for (int i = 2; i < s.length(); i++) {
            char c = Character.toLowerCase(s.charAt(i));
            if (c == Character.toLowerCase(s.charAt(i - 1))
                    && c == Character.toLowerCase(s.charAt(i - 2))) return true;
        }
        return false;
    }

    private static boolean containsKnownMarker(String s) {
        String[] markers = {"http", "www.", ".com", ".org", ".net", ".io", ".json", ".xml",
                ".html", ".js", ".php", "android", "java", "kotlin", "flutter", "dart",
                "firebase", "google", "error", "warning", "failed", "success", "admin",
                "login", "logout", "password", "token", "secret", "api_", "api.", "user",
                "name", "email", "phone", "config", "debug", "release", "version", "file",
                "path", "data", "true", "false", "null", "license", "privacy", "https"};
        for (String marker : markers) if (s.contains(marker)) return true;
        return false;
    }

    private static int commonBigrams(String s) {
        String[] grams = {"th", "he", "in", "er", "an", "re", "on", "at", "en", "nd",
                "ti", "es", "or", "te", "of", "ed", "is", "it", "al", "ar", "st", "to",
                "nt", "ng", "se", "ha", "as", "ou", "io", "le", "ve", "co", "me", "de",
                "hi", "ri", "ro", "ic", "ne", "ea", "ra", "ce", "li", "ch", "ll", "be",
                "ma", "si", "om", "ur", "ca", "el", "ta", "la", "ns", "di", "fo", "ho",
                "pe", "ec", "pr", "no", "ct", "us", "ac", "il", "tr", "ly", "un", "wh",
                "ge", "lo", "we", "do", "ck", "ss", "tt", "ff", "rr", "nn", "pp", "cc",
                "dd", "gg", "mm", "bb", "kk"};
        java.util.Set<String> hits = new java.util.HashSet<>();
        for (int i = 0; i + 1 < s.length(); i++) {
            String pair = s.substring(i, i + 2);
            for (String gram : grams) {
                if (pair.equals(gram)) {
                    hits.add(gram);
                    break;
                }
            }
        }
        return hits.size();
    }

    /** Non-Latin scripts that clearly indicate real text, not random bytes. */
    private static boolean isScriptCodePoint(int cp) {
        return (cp >= 0x370 && cp <= 0x4FF)
                || (cp >= 0x590 && cp <= 0x6FF)
                || (cp >= 0x900 && cp <= 0xDFF)
                || (cp >= 0xF00 && cp <= 0x10FF)
                || (cp >= 0x1100 && cp <= 0x11FF)
                || (cp >= 0x2E80 && cp <= 0x9FFF)
                || (cp >= 0xA000 && cp <= 0xA4CF)
                || (cp >= 0xAC00 && cp <= 0xD7AF)
                || (cp >= 0xF900 && cp <= 0xFAFF)
                || (cp >= 0x1F000 && cp <= 0x1FAFF);
    }

    private static boolean isTextCodePoint(int cp) {
        if (cp >= 0x20 && cp < 0x7F) return true;
        if (cp == '\t' || cp == '\n' || cp == '\r') return true;
        if (cp >= 0xA0 && cp <= 0x24F) return true;      // Latin / IPA
        if (cp >= 0x370 && cp <= 0x4FF) return true;     // Greek / Cyrillic
        if (cp >= 0x590 && cp <= 0x6FF) return true;     // Hebrew / Arabic
        if (cp >= 0x750 && cp <= 0x77F) return true;     // Arabic supplement
        if (cp >= 0x900 && cp <= 0xDFF) return true;     // Indic scripts
        if (cp >= 0xE00 && cp <= 0xE7F) return true;     // Thai / Lao
        if (cp >= 0xF00 && cp <= 0x10FF) return true;    // Tibetan / Myanmar / Georgian
        if (cp >= 0x1100 && cp <= 0x11FF) return true;   // Hangul Jamo
        if (cp >= 0x1E00 && cp <= 0x1EFF) return true;   // Latin extended
        if (cp >= 0x2000 && cp <= 0x206F) return true;   // punctuation
        if (cp >= 0x20A0 && cp <= 0x20CF) return true;   // currency
        if (cp >= 0x2100 && cp <= 0x22FF) return true;   // symbols / math
        if (cp >= 0x25A0 && cp <= 0x25FF) return true;   // shapes
        if (cp >= 0x2E80 && cp <= 0x9FFF) return true;   // CJK
        if (cp >= 0xA000 && cp <= 0xA4CF) return true;   // Yi
        if (cp >= 0xAC00 && cp <= 0xD7AF) return true;   // Hangul
        if (cp >= 0xF900 && cp <= 0xFAFF) return true;   // CJK compatibility
        if (cp >= 0xFE30 && cp <= 0xFE4F) return true;
        if (cp >= 0xFF00 && cp <= 0xFFEF) return true;   // full-width forms
        if (cp >= 0x1F000 && cp <= 0x1FAFF) return true; // emoji
        return cp == 0xFE0F;
    }

    private static String tryUtf16(byte[] raw) {
        if (raw.length < 8 || (raw.length & 1) != 0) return null;
        StringBuilder le = new StringBuilder(raw.length / 2);
        StringBuilder be = new StringBuilder(raw.length / 2);
        int zeroes = 0;
        for (int i = 0; i + 1 < raw.length; i += 2) {
            int a = raw[i] & 0xFF;
            int b = raw[i + 1] & 0xFF;
            if (a == 0 || b == 0) zeroes++;
            le.append((char) ((b << 8) | a));
            be.append((char) ((a << 8) | b));
        }
        if (zeroes < raw.length / 8) return null;
        if (isMeaningful(le.toString())) return le.toString();
        if (isMeaningful(be.toString())) return be.toString();
        return null;
    }

    private static String tryBase64(String s) {
        if (s.length() < 8 || s.length() > 4096) return null;
        StringBuilder normalized = new StringBuilder(s.length() + 3);
        int useful = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '=') break;
            if (c == '-') c = '+';
            else if (c == '_') c = '/';
            boolean ok = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9') || c == '+' || c == '/';
            if (!ok) return null;
            normalized.append(c);
            useful++;
        }
        if (useful < 6) return null;
        while (normalized.length() % 4 != 0) normalized.append('=');
        try {
            byte[] raw = base64(normalized.toString());
            String out = readableString(raw);
            return out != null && out.length() >= 3 ? out : null;
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
        byte[] raw = new byte[s.length() / 2];
        for (int i = 0; i < raw.length; i++) {
            raw[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        }
        String out = readableString(raw);
        return out != null && out.length() >= 3 ? out : null;
    }

    private static String readableString(byte[] raw) {
        if (raw == null || raw.length < 3) return null;
        String utf8 = new String(raw, StandardCharsets.UTF_8);
        if (!utf8.contains("\uFFFD") && isMeaningful(utf8)) return utf8;
        String latin = new String(raw, StandardCharsets.ISO_8859_1);
        if (isMeaningful(latin)) return latin;
        return null;
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

    private static String xorCommonLoose(String s, long[] budget) {
        String best = null;
        int bestScore = Integer.MIN_VALUE;
        for (int key : COMMON_KEYS) {
            String out = xorTextLoose(s, key, budget);
            if (out == null) continue;
            int score = textScore(out);
            if (score > bestScore) {
                best = out;
                bestScore = score;
            }
        }
        return best;
    }

    private static String xorCommonBytes(byte[] raw, long[] budget) {
        String best = null;
        int bestScore = Integer.MIN_VALUE;
        for (int key : COMMON_KEYS) {
            String out = xorBytes(raw, key, budget);
            if (out == null) continue;
            int score = textScore(out);
            if (score > bestScore) {
                best = out;
                bestScore = score;
            }
        }
        return best;
    }

    /** Tries the keys mined from smali before falling back to brute force. */
    private static String xorWithKeys(String s, int[] keys, long[] budget) {
        if (keys == null) return null;
        String best = null;
        int bestScore = Integer.MIN_VALUE;
        for (int key : keys) {
            if (key <= 0 || key > 255) continue;
            String out = xorText(s, key, budget);
            if (out != null) {
                int score = textScore(out);
                if (score > bestScore) {
                    bestScore = score;
                    best = out;
                }
            }
        }
        return best;
    }

    private static String xorRecover(String s, long[] budget) {
        String best = null;
        int bestScore = Integer.MIN_VALUE;
        for (int key = 1; key < 256; key++) {
            if (containsCommonKey(key)) continue;
            String out = xorText(s, key, budget);
            if (out != null) {
                int score = textScore(out);
                if (score > bestScore) {
                    bestScore = score;
                    best = out;
                }
            }
        }
        return best;
    }

    private static String xorText(String s, int key, long[] budget) {
        if (!spend(budget, s.length())) return null;
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) sb.append((char) (s.charAt(i) ^ key));
        String out = sb.toString();
        return out.length() >= 4 && isMeaningful(out) ? out : null;
    }

    /**
     * Loose XOR used only when Smali or a common key gives the candidate
     * special weight. Random brute-force keys still use the strict path.
     */
    private static String xorTextLoose(String s, int key, long[] budget) {
        if (!spend(budget, s.length())) return null;
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) sb.append((char) (s.charAt(i) ^ key));
        String out = sb.toString();
        return out.length() >= 3 && (isMeaningful(out) || isUsableDecoded(out)) ? out : null;
    }

    /** Repeating-key XOR for 2..4 byte keys, scored by printable ratio. */
    private static String xorRepeating(String s, long[] budget) {
        if (s.length() < 8) return null;
        String best = null;
        int bestScore = Integer.MIN_VALUE;
        for (int klen = 2; klen <= 4; klen++) {
            if (s.length() < klen * 3) continue;
            int[] key = deriveKey(s.length(), klen, new ByteSource() {
                @Override
                public int at(int index) {
                    return s.charAt(index);
                }
            }, budget);
            if (key == null) return best;
            StringBuilder sb = new StringBuilder(s.length());
            for (int i = 0; i < s.length(); i++) sb.append((char) (s.charAt(i) ^ key[i % klen]));
            String out = sb.toString();
            if (out.length() >= 6 && isMeaningful(out)) {
                int score = textScore(out);
                if (score > bestScore) {
                    bestScore = score;
                    best = out;
                }
            }
        }
        return best;
    }

    /* ------------------------------------------------- raw byte XOR pass */

    private static String xorBytes(byte[] raw, int[] keys, long[] budget) {
        String best = null;
        int bestScore = Integer.MIN_VALUE;
        if (keys != null) {
            for (int key : keys) {
                if (key <= 0 || key > 255) continue;
                String out = xorBytes(raw, key, budget);
                if (out != null) {
                    int score = textScore(out);
                    if (score > bestScore) {
                        bestScore = score;
                        best = out;
                    }
                }
            }
        }
        for (int key = 1; key < 256; key++) {
            if (keys != null && contains(keys, key)) continue;
            if (containsCommonKey(key)) continue;
            String out = xorBytes(raw, key, budget);
            if (out != null) {
                int score = textScore(out);
                if (score > bestScore) {
                    bestScore = score;
                    best = out;
                }
            }
        }
        return best;
    }

    private static boolean contains(int[] values, int value) {
        for (int v : values) if (v == value) return true;
        return false;
    }

    private static int textScore(String s) {
        int score = 0;
        int i = 0;
        while (i < s.length()) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == ' ') score += 6;
            else if (isScriptCodePoint(cp)) score += 3;
            else if (cp >= 0x20 && cp < 0x7F) {
                char c = Character.toLowerCase((char) cp);
                if (c == 'e' || c == 't' || c == 'a' || c == 'o' || c == 'i' || c == 'n') score += 2;
                else if (c >= 'a' && c <= 'z') score++;
            }
        }
        score += commonBigrams(s.toLowerCase(Locale.US)) * 3;
        if (containsKnownMarker(s.toLowerCase(Locale.US))) score += 15;
        if (containsUncommonPunctuation(s)) score -= 10;
        return score;
    }

    private static String xorBytes(byte[] raw, int key, long[] budget) {
        if (raw.length > 8192 || !spend(budget, raw.length)) return null;
        StringBuilder sb = new StringBuilder(raw.length);
        for (byte value : raw) sb.append((char) ((value & 0xFF) ^ key));
        String out = sb.toString();
        return out.length() >= 4 && isMeaningful(out) ? out : null;
    }

    private static String xorBytesLoose(byte[] raw, int key, long[] budget) {
        if (raw.length > 8192 || !spend(budget, raw.length)) return null;
        StringBuilder sb = new StringBuilder(raw.length);
        for (byte value : raw) sb.append((char) ((value & 0xFF) ^ key));
        String out = sb.toString();
        return out.length() >= 3 && (isMeaningful(out) || isUsableDecoded(out)) ? out : null;
    }

    private static String xorRepeatingBytes(byte[] raw, long[] budget) {
        if (raw.length < 8) return null;
        String best = null;
        int bestScore = Integer.MIN_VALUE;
        for (int klen = 2; klen <= 4; klen++) {
            if (raw.length < klen * 3) continue;
            int[] key = deriveKey(raw.length, klen, new ByteSource() {
                @Override
                public int at(int index) {
                    return raw[index] & 0xFF;
                }
            }, budget);
            if (key == null) return best;
            StringBuilder sb = new StringBuilder(raw.length);
            for (int i = 0; i < raw.length; i++) sb.append((char) ((raw[i] & 0xFF) ^ key[i % klen]));
            String out = sb.toString();
            if (out.length() >= 6 && isMeaningful(out)) {
                int score = textScore(out);
                if (score > bestScore) {
                    bestScore = score;
                    best = out;
                }
            }
        }
        return best;
    }

    private interface ByteSource {
        int at(int index);
    }

    private static int[] deriveKey(int length, int klen, ByteSource source, long[] budget) {
        // Each candidate key scans every k-th byte: ~255 passes per key byte.
        if (!spend(budget, (long) length * 255)) return null;
        int[] key = new int[klen];
        for (int k = 0; k < klen; k++) {
            int best = 0, bestScore = -1;
            for (int cand = 1; cand < 256; cand++) {
                int score = 0;
                for (int i = k; i < length; i += klen) {
                    char c = (char) (source.at(i) ^ cand);
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
        return key;
    }

    private static boolean spend(long[] budget, long amount) {
        if (amount <= 0) return true;
        if (budget[0] < amount) return false;
        budget[0] -= amount;
        return true;
    }

    private static String printableSample(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length() && b.length() < 60; i++) {
            char c = s.charAt(i);
            b.append(c >= 0x20 && c < 0x7F ? c : '.');
        }
        return b.toString();
    }

    public static String toJson(Report r) {
        StringBuilder b = new StringBuilder();
        b.append("{\n  \"file\": \"").append(r.name).append("\",\n");
        b.append("  \"total\": ").append(r.total).append(",\n");
        b.append("  \"readable\": ").append(r.readable).append(",\n");
        b.append("  \"resolved\": ").append(r.resolved).append(",\n");
        b.append("  \"descriptors\": ").append(r.descriptors).append(",\n");
        b.append("  \"encoded\": ").append(r.encoded).append(",\n");
        b.append("  \"obfuscated\": ").append(r.obfuscated).append(",\n");
        b.append("  \"keysMined\": ").append(r.keysMined).append(",\n");
        b.append("  \"hints\": ").append(r.hints).append(",\n");
        b.append("  \"decryptorSites\": ").append(r.decryptorSites).append(",\n");
        b.append("  \"rawStrings\": ").append(r.rawStrings).append(",\n");
        b.append("  \"rawDecoded\": ").append(r.rawDecoded).append(",\n");
        b.append("  \"extracted\": ").append(r.total).append(",\n");
        b.append("  \"decoded\": ").append(r.recovered).append(",\n");
        b.append("  \"recovered\": ").append(r.resolved).append("\n}\n");
        return b.toString();
    }
}
