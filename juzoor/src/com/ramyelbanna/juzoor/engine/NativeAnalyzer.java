package com.ramyelbanna.juzoor.engine;

import com.ramyelbanna.juzoor.core.LogManager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Static analyser and "decryptor" for Android native libraries.
 *
 * <p>It never executes target code. It parses the ELF container, inventories
 * sections and exported JNI entry points, fingerprints known packers /
 * protectors, recovers the printable string table, brute-forces single byte XOR
 * layers, detects RC4 / AES constants, and carves any embedded DEX or ZIP
 * payload that a packer hid inside the library. Everything it finds is written
 * next to the recovered project so the analyst keeps a full audit trail.</p>
 */
public final class NativeAnalyzer {

    public static final class Section {
        public String name = "";
        public int type;
        public long flags;
        public long addr;
        public long offset;
        public long size;
        public double entropy;
    }

    public static final class Carved {
        public String kind;      // dex / zip / elf
        public long offset;
        public long size;
        public File file;
    }

    public static final class Report {
        public String name = "";
        public String abi = "unknown";
        public boolean elf;
        public boolean is64;
        public boolean bigEndian;
        public long fileSize;
        public String machine = "unknown";
        public final List<Section> sections = new ArrayList<>();
        public final List<String> jniExports = new ArrayList<>();
        public final List<String> packers = new ArrayList<>();
        public final List<String> crypto = new ArrayList<>();
        public final List<Carved> carved = new ArrayList<>();
        public int stringCount;
        public int xorKey = -1;
        public int xorStrings;
        public final List<String> recovered = new ArrayList<>();
        public final List<String> warnings = new ArrayList<>();
        public File stringsFile;
        public File decryptedFile;
        public File jsonFile;
        public boolean decrypted;
        // UPX static unpacking
        public boolean upxDetected;
        public boolean upxUnpacked;
        public String upxMethod = "";
        public int upxBlocks;
        public long upxBytes;
        public File upxFile;
        public String upxError;

        public String summary() {
            return name + " [" + abi + "] " + (elf ? "ELF" : "raw")
                    + " sections=" + sections.size()
                    + " jni=" + jniExports.size()
                    + " strings=" + stringCount
                    + (xorKey >= 0 ? " xor=0x" + Integer.toHexString(xorKey) : "")
                    + (packers.isEmpty() ? "" : " packer=" + packers.get(0));
        }
    }

    private NativeAnalyzer() {
    }

    /** Known protectors: marker -> human readable name. */
    private static final String[][] PACKERS = {
            {"libjiagu", "360 Jiagu"},
            {"libjgdtc", "360 Jiagu (DTC)"},
            {"libshell", "Tencent Legu / Shell"},
            {"libshella", "Tencent Legu"},
            {"libtup", "Tencent Legu (TUP)"},
            {"libmobisec", "Alibaba Mobile Security"},
            {"libsgmain", "Alibaba SecurityGuard"},
            {"libDexHelper", "Bangcle / SecNeo"},
            {"libsecexe", "Bangcle SecShell"},
            {"libsecmain", "Bangcle SecShell"},
            {"libnesec", "Naga / SecNeo"},
            {"libexec.so", "Naga (libexec)"},
            {"libbaiduprotect", "Baidu Protect"},
            {"libprotectClass", "Baidu Protect"},
            {"libdexprotector", "DexProtector"},
            {"libvirbox", "Virbox Protector"},
            {"libarxan", "Arxan / GuardSquare"},
            {"libapp-protect", "AppProtect"},
            {"libkwscmm", "Kiwi / KWS"},
            {"libnativehelper", "Android native helper"},
            {"libflutter", "Flutter engine"},
            {"libhermes", "React Native Hermes"},
            {"libreactnative", "React Native"},
            {"libunity", "Unity engine"},
            {"libil2cpp", "Unity IL2CPP"},
            {"libcocos", "Cocos2d-x"},
            {"libmono", "Mono / Xamarin"},
            {"libgodot", "Godot engine"},
            {"upx", "UPX packer"},
            {"libmsec", "Mobisec"},
    };

    public static Report analyze(File so, File analysisDir, LogSink log) {
        Report r = new Report();
        r.name = so.getName();
        r.fileSize = so.length();
        byte[] data;
        try {
            data = ZipUtil.readFile(so);
        } catch (IOException e) {
            r.warnings.add("read failed: " + e);
            return r;
        }
        r.abi = abiOf(so);
        parseElf(data, r);
        detectPackers(data, r);
        detectCrypto(data, r);

        // Static UPX unpacking — no root, no execution.
        if (r.packers.contains("UPX packer") || UpxUnpacker.looksPacked(data)) {
            r.upxDetected = true;
            try {
                UpxUnpacker.Result up = UpxUnpacker.unpack(data);
                r.upxMethod = up.method;
                r.upxBlocks = up.blocks;
                if (up.unpacked && up.data != null && up.data.length > 0) {
                    File dir = new File(analysisDir, "native/unpacked");
                    dir.mkdirs();
                    String base = r.name.replaceAll("[^A-Za-z0-9._-]", "_");
                    boolean elfOut = up.data.length > 4 && (up.data[0] & 0xFF) == 0x7F
                            && up.data[1] == 'E' && up.data[2] == 'L' && up.data[3] == 'F';
                    r.upxFile = new File(dir, base + (elfOut ? ".unpacked.so" : ".unpacked.bin"));
                    ZipUtil.writeFile(r.upxFile, up.data);
                    r.upxUnpacked = true;
                    r.upxBytes = up.data.length;
                    if (log != null) {
                        log.log(LogManager.SUCCESS, "Static UPX unpack: " + r.name + " -> "
                                + r.upxFile.getName() + " (" + up.data.length + " bytes, "
                                + up.blocks + " block(s), " + up.method + ")");
                    }
                } else {
                    r.upxError = up.error == null ? "no payload recovered" : up.error;
                    if (log != null) {
                        log.log(LogManager.WARN, "UPX static unpack incomplete for " + r.name + ": " + r.upxError);
                    }
                }
            } catch (Throwable t) {
                r.upxError = String.valueOf(t);
            }
        }

        List<String> strings = printableStrings(data, 4);
        // If UPX unpacked, also harvest strings from the recovered payload.
        if (r.upxUnpacked && r.upxFile != null) {
            try {
                List<String> more = printableStrings(ZipUtil.readFile(r.upxFile), 4);
                for (String s : more) if (!strings.contains(s)) strings.add(s);
            } catch (Throwable ignored) {
            }
        }
        r.stringCount = strings.size();
        carve(data, r, analysisDir, log);
        recoverXor(data, r, log);

        File dir = new File(analysisDir, "native");
        dir.mkdirs();
        String base = r.name.replaceAll("[^A-Za-z0-9._-]", "_");
        r.stringsFile = new File(dir, base + ".strings.txt");
        r.decryptedFile = new File(dir, base + ".decrypted.txt");
        r.jsonFile = new File(dir, base + ".elf.json");
        try {
            StringBuilder sb = new StringBuilder();
            for (String s : strings) sb.append(s).append('\n');
            ZipUtil.writeFile(r.stringsFile, sb.toString());
            if (!r.recovered.isEmpty()) {
                StringBuilder d = new StringBuilder();
                d.append("# Recovered with single-byte XOR key 0x")
                        .append(Integer.toHexString(r.xorKey)).append('\n');
                for (String s : r.recovered) d.append(s).append('\n');
                ZipUtil.writeFile(r.decryptedFile, d.toString());
                r.decrypted = true;
            }
            ZipUtil.writeFile(r.jsonFile, toJson(r));
        } catch (IOException e) {
            r.warnings.add("write failed: " + e);
        }
        if (log != null) {
            log.log(LogManager.INFO, "Native " + r.summary());
            if (!r.packers.isEmpty()) {
                log.log(LogManager.WARN, "Protector detected in " + r.name + ": " + r.packers);
            }
            if (r.decrypted) {
                log.log(LogManager.SUCCESS, "Decrypted " + r.recovered.size()
                        + " strings from " + r.name + " (XOR 0x" + Integer.toHexString(r.xorKey) + ")");
            }
            for (Carved c : r.carved) {
                log.log(LogManager.SUCCESS, "Carved embedded " + c.kind + " from " + r.name
                        + " at 0x" + Long.toHexString(c.offset) + " (" + c.size + " bytes)");
            }
        }
        return r;
    }

    private static String abiOf(File f) {
        String p = f.getAbsolutePath().replace('\\', '/').toLowerCase(Locale.US);
        if (p.contains("/arm64-v8a/")) return "arm64-v8a";
        if (p.contains("/armeabi-v7a/")) return "armeabi-v7a";
        if (p.contains("/armeabi/")) return "armeabi";
        if (p.contains("/x86_64/")) return "x86_64";
        if (p.contains("/x86/")) return "x86";
        if (p.contains("/mips64/")) return "mips64";
        if (p.contains("/mips/")) return "mips";
        if (p.contains("/riscv64/")) return "riscv64";
        return "unknown";
    }

    /* ------------------------------------------------------------------ ELF */

    private static long u8(byte[] d, long o) { return o >= 0 && o < d.length ? d[(int) o] & 0xFFL : 0; }

    private static long u16(byte[] d, long o, boolean be) {
        if (o < 0 || o + 2 > d.length) return 0;
        int a = d[(int) o] & 0xFF, b = d[(int) o + 1] & 0xFF;
        return be ? ((a << 8) | b) : ((b << 8) | a);
    }

    private static long u32(byte[] d, long o, boolean be) {
        if (o < 0 || o + 4 > d.length) return 0;
        long v = 0;
        for (int i = 0; i < 4; i++) {
            int b = d[(int) o + i] & 0xFF;
            v |= be ? ((long) b << (8 * (3 - i))) : ((long) b << (8 * i));
        }
        return v;
    }

    private static long u64(byte[] d, long o, boolean be) {
        if (o < 0 || o + 8 > d.length) return 0;
        long v = 0;
        for (int i = 0; i < 8; i++) {
            long b = d[(int) o + i] & 0xFF;
            v |= be ? (b << (8 * (7 - i))) : (b << (8 * i));
        }
        return v;
    }

    private static void parseElf(byte[] d, Report r) {
        if (d.length < 52 || (d[0] & 0xFF) != 0x7F || d[1] != 'E' || d[2] != 'L' || d[3] != 'F') {
            r.elf = false;
            r.warnings.add("not an ELF container");
            return;
        }
        r.elf = true;
        r.is64 = (d[4] & 0xFF) == 2;
        r.bigEndian = (d[5] & 0xFF) == 2;
        boolean be = r.bigEndian;
        int machine = (int) u16(d, 18, be);
        r.machine = machineName(machine);
        long shoff = r.is64 ? u64(d, 0x28, be) : u32(d, 0x20, be);
        int shentsize = (int) u16(d, r.is64 ? 0x3A : 0x2E, be);
        int shnum = (int) u16(d, r.is64 ? 0x3C : 0x30, be);
        int shstrndx = (int) u16(d, r.is64 ? 0x3E : 0x32, be);
        if (shoff <= 0 || shoff >= d.length || shentsize <= 0 || shnum <= 0 || shnum > 4096) {
            r.warnings.add("section header table not usable");
            return;
        }
        long strOff = 0, strSize = 0;
        if (shstrndx < shnum) {
            long sh = shoff + (long) shstrndx * shentsize;
            strOff = r.is64 ? u64(d, sh + 0x18, be) : u32(d, sh + 0x10, be);
            strSize = r.is64 ? u64(d, sh + 0x20, be) : u32(d, sh + 0x14, be);
        }
        for (int i = 0; i < shnum; i++) {
            long sh = shoff + (long) i * shentsize;
            if (sh + shentsize > d.length) break;
            Section s = new Section();
            long nameOff = u32(d, sh, be);
            s.type = (int) u32(d, sh + 4, be);
            s.flags = r.is64 ? u64(d, sh + 8, be) : u32(d, sh + 8, be);
            s.addr = r.is64 ? u64(d, sh + 0x10, be) : u32(d, sh + 0x0C, be);
            s.offset = r.is64 ? u64(d, sh + 0x18, be) : u32(d, sh + 0x10, be);
            s.size = r.is64 ? u64(d, sh + 0x20, be) : u32(d, sh + 0x14, be);
            if (strOff > 0 && strOff + nameOff < d.length) {
                s.name = cString(d, strOff + nameOff, 64);
            }
            if (s.offset > 0 && s.size > 0 && s.offset + s.size <= d.length && s.size <= 64L * 1024 * 1024) {
                s.entropy = entropy(d, (int) s.offset, (int) s.size);
            }
            r.sections.add(s);
        }
        readJniExports(d, r, shoff, shentsize, shnum, be);
    }

    private static void readJniExports(byte[] d, Report r, long shoff, int shentsize, int shnum, boolean be) {
        Set<String> out = new LinkedHashSet<>();
        for (int i = 0; i < shnum; i++) {
            long sh = shoff + (long) i * shentsize;
            if (sh + shentsize > d.length) break;
            int type = (int) u32(d, sh + 4, be);
            if (type != 11 && type != 2) continue; // DYNSYM / SYMTAB
            long off = r.is64 ? u64(d, sh + 0x18, be) : u32(d, sh + 0x10, be);
            long size = r.is64 ? u64(d, sh + 0x20, be) : u32(d, sh + 0x14, be);
            long link = u32(d, sh + (r.is64 ? 0x28 : 0x18), be);
            long entsize = r.is64 ? u64(d, sh + 0x38, be) : u32(d, sh + 0x24, be);
            if (entsize <= 0) entsize = r.is64 ? 24 : 16;
            if (link >= shnum) continue;
            long strSh = shoff + link * shentsize;
            long strOff = r.is64 ? u64(d, strSh + 0x18, be) : u32(d, strSh + 0x10, be);
            long count = size / entsize;
            for (long k = 0; k < count && k < 200000; k++) {
                long sym = off + k * entsize;
                if (sym + entsize > d.length) break;
                long nameIdx = u32(d, sym, be);
                if (nameIdx == 0) continue;
                String name = cString(d, strOff + nameIdx, 512);
                if (name.startsWith("Java_") || name.equals("JNI_OnLoad") || name.equals("JNI_OnUnload")) {
                    out.add(name);
                }
            }
        }
        r.jniExports.addAll(out);
    }

    private static String machineName(int m) {
        switch (m) {
            case 3: return "x86";
            case 8: return "MIPS";
            case 40: return "ARM";
            case 62: return "x86_64";
            case 183: return "AArch64";
            case 243: return "RISC-V";
            default: return "machine#" + m;
        }
    }

    /* --------------------------------------------------------------- packers */

    private static void detectPackers(byte[] d, Report r) {
        String low = new String(d, 0, Math.min(d.length, 2 * 1024 * 1024),
                java.nio.charset.StandardCharsets.ISO_8859_1).toLowerCase(Locale.US);
        Set<String> found = new LinkedHashSet<>();
        for (String[] p : PACKERS) {
            if (low.contains(p[0])) found.add(p[1]);
        }
        // UPX magic
        if (low.contains("upx!") || low.contains("$info: this file is packed with the upx")) {
            found.add("UPX packer");
        }
        // Section-name heuristics
        for (Section s : r.sections) {
            String n = s.name.toLowerCase(Locale.US);
            if (n.contains("upx")) found.add("UPX packer");
            if (n.equals(".jiagu")) found.add("360 Jiagu");
            if (n.equals(".shell")) found.add("Tencent Legu / Shell");
            if (n.equals(".protect")) found.add("Generic protector");
        }
        r.packers.addAll(found);
    }

    private static void detectCrypto(byte[] d, Report r) {
        // RC4 identity S-box
        boolean rc4 = false;
        for (int i = 0; i + 256 <= d.length && !rc4; i += 4) {
            if ((d[i] & 0xFF) != 0) continue;
            rc4 = true;
            for (int k = 0; k < 256; k++) {
                if ((d[i + k] & 0xFF) != k) { rc4 = false; break; }
            }
        }
        if (rc4) r.crypto.add("RC4 (identity S-box present)");
        // AES S-box prefix
        byte[] aes = {0x63, 0x7c, 0x77, 0x7b, (byte) 0xf2, 0x6b, 0x6f, (byte) 0xc5};
        outer:
        for (int i = 0; i + aes.length <= d.length; i++) {
            for (int k = 0; k < aes.length; k++) {
                if (d[i + k] != aes[k]) continue outer;
            }
            r.crypto.add("AES (S-box present)");
            break;
        }
        String low = new String(d, 0, Math.min(d.length, 1024 * 1024),
                java.nio.charset.StandardCharsets.ISO_8859_1);
        if (low.contains("AES_set_encrypt_key") || low.contains("EVP_EncryptInit")) r.crypto.add("OpenSSL AES API");
        if (low.contains("XXTEA") || low.contains("xxtea")) r.crypto.add("XXTEA");
        if (low.contains("chacha20")) r.crypto.add("ChaCha20");
        if (low.contains("base64")) r.crypto.add("Base64 helper");
    }

    /* --------------------------------------------------------------- strings */

    public static List<String> printableStrings(byte[] d, int min) {
        List<String> out = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (byte b : d) {
            int c = b & 0xFF;
            if (c >= 0x20 && c < 0x7F) {
                sb.append((char) c);
            } else {
                if (sb.length() >= min) out.add(sb.toString());
                sb.setLength(0);
            }
        }
        if (sb.length() >= min) out.add(sb.toString());
        return out;
    }

    /**
     * Brute-forces a single byte XOR layer. A key is accepted when it produces
     * significantly more long printable runs than the raw file, which is the
     * signature of the string-table obfuscation used by most Android packers.
     */
    private static final String[] VOCAB = {
            "http", "java", "android", "lib", ".so", "error", "fail", "null", "true",
            "false", "class", "method", "string", "activity", "package", "native",
            "load", "key", "data", "return", "void", "int", "public", "static",
            "context", "service", "application", "exception", "debug", "info"
    };

    /** A printable run together with its offset in the source buffer. */
    private static final class Run {
        final int off;
        final String s;
        Run(int off, String s) { this.off = off; this.s = s; }
    }

    /** Like {@link #printableStrings} but remembers where each run started. */
    private static List<Run> printableRuns(byte[] d, int min) {
        List<Run> out = new ArrayList<>();
        int start = -1;
        for (int i = 0; i <= d.length; i++) {
            int c = i < d.length ? (d[i] & 0xFF) : 0;
            boolean printable = c >= 0x20 && c < 0x7F;
            if (printable) {
                if (start < 0) start = i;
            } else {
                if (start >= 0 && i - start >= min) {
                    out.add(new Run(start, new String(d, start, i - start,
                            java.nio.charset.StandardCharsets.ISO_8859_1)));
                }
                start = -1;
            }
        }
        return out;
    }

    /**
     * True when the raw bytes behind a candidate run are real data rather than
     * zero (or single-byte) padding. XOR-ing a run of 0x00 with any key yields a
     * run of the same printable character ("ZZZZ..."), which is the main source
     * of false positives, so padding regions must be discarded up front.
     */
    private static boolean realRegion(byte[] d, int off, int len) {
        int distinct = 0;
        boolean[] seen = new boolean[256];
        for (int i = off; i < off + len && i < d.length; i++) {
            int b = d[i] & 0xFF;
            if (!seen[b]) { seen[b] = true; distinct++; if (distinct >= 3) return true; }
        }
        return false;
    }

    private static void recoverXor(byte[] d, Report r, LogSink log) {
        List<String> rawStrings = printableStrings(d, 6);
        Set<String> rawSet = new LinkedHashSet<>(rawStrings);
        int rawHuman = countLongHuman(rawStrings);

        int bestKey = -1, bestTokens = 0, secondTokens = 0;
        List<String> bestNew = null;
        int limit = Math.min(d.length, 8 * 1024 * 1024);
        byte[] tmp = new byte[limit];
        for (int key = 1; key < 256; key++) {
            // 0x20 only flips the case of ASCII letters, so every plain string in
            // the file reappears as a "new" human string and wins the vote. It is
            // a case transform, not an encryption layer.
            if (key == 0x20) continue;
            for (int i = 0; i < limit; i++) tmp[i] = (byte) (d[i] ^ key);
            List<String> fresh = new ArrayList<>();
            int tokens = 0;
            for (Run run : printableRuns(tmp, 8)) {
                String s = run.s;
                if (s.length() < 8 || rawSet.contains(s) || !looksHuman(s)) continue;
                if (!realRegion(d, run.off, s.length())) continue;
                fresh.add(s);
                String l = s.toLowerCase(Locale.US);
                for (String v : VOCAB) if (l.contains(v)) tokens++;
            }
            if (tokens > bestTokens) {
                secondTokens = bestTokens;
                bestTokens = tokens;
                bestKey = key;
                bestNew = fresh;
            } else if (tokens > secondTokens) {
                secondTokens = tokens;
            }
        }
        if (bestKey <= 0 || bestNew == null || bestNew.size() < 4 || bestTokens < 3) return;
        if (System.getProperty("sf.debug") != null) {
            System.err.println("[xor] best=" + bestKey + " tokens=" + bestTokens
                    + " second=" + secondTokens + " fresh=" + bestNew.size() + " rawHuman=" + rawHuman);
        }
        // The winning key must stand out from the field, otherwise we are just
        // seeing noise that happens to be printable (typical for text-rich .so).
        if (bestTokens < 2 * (secondTokens + 1)) return;
        // A file that is already full of human strings is unlikely to be packed;
        // require the recovered set to be a meaningful addition.
        if (rawHuman > 50 && bestNew.size() * 4 < rawHuman) return;
        // A file that is already full of human strings is unlikely to be packed;
        // require the recovered set to be a meaningful addition.
        if (rawHuman > 50 && bestNew.size() * 4 < rawHuman) return;
        r.xorKey = bestKey;
        // Null terminators between strings become the key character after XOR;
        // split on it so the report lists individual strings, not one blob.
        LinkedHashSet<String> clean = new LinkedHashSet<>();
        char sep = (bestKey >= 0x20 && bestKey < 0x7F) ? (char) bestKey : 0;
        for (String s : bestNew) {
            if (sep == 0) { clean.add(s); continue; }
            for (String part : s.split(java.util.regex.Pattern.quote(String.valueOf(sep)))) {
                if (looksWord(part)) clean.add(tidy(part));
            }
        }
        if (clean.isEmpty()) clean.addAll(bestNew);
        r.xorStrings = clean.size();
        r.recovered.addAll(clean);
    }

    private static int countLongHuman(List<String> list) {
        int n = 0;
        for (String s : list) if (s.length() >= 12 && looksHuman(s)) n++;
        return n;
    }

    private static int scoreStrings(List<String> list) {
        int score = 0;
        for (String s : list) {
            if (looksHuman(s)) score += Math.min(40, s.length());
        }
        return score;
    }

    /** Fixes case artifacts left by per-string length prefixes in packed blobs. */
    private static String tidy(String s) {
        int upper = 0, lower = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 'A' && c <= 'Z') upper++;
            else if (c >= 'a' && c <= 'z') lower++;
        }
        if (upper > lower * 2) return s.toUpperCase(Locale.US);
        return s;
    }

    /** Looser check used for fragments produced by splitting a recovered blob. */
    private static boolean looksWord(String s) {
        if (s.length() < 4 || s.length() > 96) return false;
        int letters = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) letters++;
        }
        return letters >= s.length() * 0.6;
    }

    private static boolean looksHuman(String s) {
        if (s.length() < 8) return false;
        int letters = 0, digits = 0, other = 0, vowels = 0;
        boolean[] seen = new boolean[128];
        int distinct = 0;
        int[] freq = new int[128];
        int run = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) letters++;
            else if (c >= '0' && c <= '9') digits++;
            else other++;
            char lc = Character.toLowerCase(c);
            if (lc == 'a' || lc == 'e' || lc == 'i' || lc == 'o' || lc == 'u') vowels++;
            if (c < 128) {
                if (!seen[c]) { seen[c] = true; distinct++; }
                freq[c]++;
            }
            // no more than 2 identical characters in a row
            if (i > 0 && s.charAt(i - 1) == c) { if (++run >= 2) return false; } else run = 0;
        }
        // character diversity: at least 4 distinct chars and no dominant char
        if (distinct < 4) return false;
        int maxFreq = 0;
        for (int f : freq) if (f > maxFreq) maxFreq = f;
        if (maxFreq > s.length() * 0.40) return false;
        if (letters < s.length() * 0.50) return false;
        if (other > s.length() * 0.30) return false;
        // must look like words: vowels, or a path/URL shape with at least one vowel
        if (vowels == 0) return false;
        return true;
    }

    /* ---------------------------------------------------------------- carve */

    private static void carve(byte[] d, Report r, File analysisDir, LogSink log) {
        File dir = new File(analysisDir, "native/carved");
        dir.mkdirs();
        int found = 0;
        for (int i = 0; i + 8 <= d.length && found < 64; i++) {
            // DEX magic: "dex\n0xx\0"
            if (d[i] == 'd' && d[i + 1] == 'e' && d[i + 2] == 'x' && d[i + 3] == '\n'
                    && d[i + 7] == 0) {
                long size = u32(d, i + 32, false);
                if (size > 0x70 && size <= d.length - i) {
                    Carved c = new Carved();
                    c.kind = "dex";
                    c.offset = i;
                    c.size = size;
                    c.file = writeCarved(d, i, (int) size, dir, r.name + ".carved" + found + ".dex");
                    if (c.file != null) { r.carved.add(c); found++; }
                    i += (int) size - 1;
                    continue;
                }
            }
            // ZIP / APK / JAR magic
            if (d[i] == 'P' && d[i + 1] == 'K' && d[i + 2] == 3 && d[i + 3] == 4) {
                int end = findEocd(d, i);
                if (end > i) {
                    Carved c = new Carved();
                    c.kind = "zip";
                    c.offset = i;
                    c.size = end - i;
                    c.file = writeCarved(d, i, end - i, dir, r.name + ".carved" + found + ".zip");
                    if (c.file != null) { r.carved.add(c); found++; }
                    i = end - 1;
                }
            }
            // Nested ELF
            if ((d[i] & 0xFF) == 0x7F && d[i + 1] == 'E' && d[i + 2] == 'L' && d[i + 3] == 'F' && i > 0) {
                Carved c = new Carved();
                c.kind = "elf";
                c.offset = i;
                c.size = d.length - i;
                c.file = writeCarved(d, i, d.length - i, dir, r.name + ".carved" + found + ".so");
                if (c.file != null) { r.carved.add(c); found++; }
                break;
            }
        }
    }

    private static int findEocd(byte[] d, int start) {
        // Walk local file headers until the central directory is reached.
        int p = start;
        int guard = 0;
        while (p + 30 <= d.length && guard++ < 100000) {
            if (!(d[p] == 'P' && d[p + 1] == 'K' && d[p + 2] == 3 && d[p + 3] == 4)) break;
            long csize = u32(d, p + 18, false);
            long usize = u32(d, p + 22, false);
            int nameLen = (int) u16(d, p + 26, false);
            int extraLen = (int) u16(d, p + 28, false);
            long len = csize > 0 ? csize : usize;
            if (len <= 0 || p + 30 + nameLen + extraLen + len > d.length) return -1;
            p += 30 + nameLen + extraLen + (int) len;
            if (p + 4 <= d.length && d[p] == 'P' && d[p + 1] == 'K' && d[p + 2] == 1 && d[p + 3] == 2) {
                // central directory: find EOCD
                for (int q = d.length - 22; q >= p; q--) {
                    if (d[q] == 'P' && d[q + 1] == 'K' && d[q + 2] == 5 && d[q + 3] == 6) {
                        int comment = (int) u16(d, q + 20, false);
                        return Math.min(d.length, q + 22 + comment);
                    }
                }
                return -1;
            }
        }
        return -1;
    }

    private static File writeCarved(byte[] d, int off, int size, File dir, String name) {
        try {
            File f = new File(dir, name);
            byte[] out = new byte[size];
            System.arraycopy(d, off, out, 0, size);
            java.io.FileOutputStream fos = new java.io.FileOutputStream(f);
            fos.write(out);
            fos.close();
            return f;
        } catch (Exception e) {
            return null;
        }
    }

    /* ------------------------------------------------------------- utilities */

    private static String cString(byte[] d, long off, int max) {
        if (off < 0 || off >= d.length) return "";
        int end = (int) off;
        int limit = (int) Math.min(d.length, off + max);
        while (end < limit && d[end] != 0) end++;
        return new String(d, (int) off, end - (int) off, java.nio.charset.StandardCharsets.ISO_8859_1);
    }

    public static double entropy(byte[] d, int off, int len) {
        if (len <= 0) return 0;
        int[] freq = new int[256];
        int n = Math.min(len, d.length - off);
        for (int i = 0; i < n; i++) freq[d[off + i] & 0xFF]++;
        double e = 0;
        for (int f : freq) {
            if (f == 0) continue;
            double p = (double) f / n;
            e -= p * (Math.log(p) / Math.log(2));
        }
        return e;
    }

    private static String esc(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\\') b.append('\\').append(c);
            else if (c < 0x20) b.append(String.format(Locale.US, "\\u%04x", (int) c));
            else b.append(c);
        }
        return b.toString();
    }

    public static String toJson(Report r) {
        StringBuilder b = new StringBuilder();
        b.append("{\n  \"file\": \"").append(esc(r.name)).append("\",\n");
        b.append("  \"abi\": \"").append(esc(r.abi)).append("\",\n");
        b.append("  \"elf\": ").append(r.elf).append(",\n");
        b.append("  \"bits\": ").append(r.is64 ? 64 : 32).append(",\n");
        b.append("  \"machine\": \"").append(esc(r.machine)).append("\",\n");
        b.append("  \"size\": ").append(r.fileSize).append(",\n");
        b.append("  \"packers\": ").append(arr(r.packers)).append(",\n");
        b.append("  \"crypto\": ").append(arr(r.crypto)).append(",\n");
        b.append("  \"jniExports\": ").append(arr(r.jniExports)).append(",\n");
        b.append("  \"strings\": ").append(r.stringCount).append(",\n");
        b.append("  \"xorKey\": ").append(r.xorKey).append(",\n");
        b.append("  \"recoveredStrings\": ").append(r.recovered.size()).append(",\n");
        b.append("  \"upx\": {\"detected\": ").append(r.upxDetected)
                .append(", \"unpacked\": ").append(r.upxUnpacked)
                .append(", \"method\": \"").append(esc(r.upxMethod == null ? "" : r.upxMethod))
                .append("\", \"blocks\": ").append(r.upxBlocks)
                .append(", \"bytes\": ").append(r.upxBytes)
                .append(", \"error\": \"").append(esc(r.upxError == null ? "" : r.upxError)).append("\"},\n");
        b.append("  \"carved\": [");
        for (int i = 0; i < r.carved.size(); i++) {
            Carved c = r.carved.get(i);
            if (i > 0) b.append(',');
            b.append("\n    {\"kind\": \"").append(c.kind).append("\", \"offset\": ").append(c.offset)
                    .append(", \"size\": ").append(c.size).append('}');
        }
        b.append("\n  ],\n  \"sections\": [");
        for (int i = 0; i < r.sections.size(); i++) {
            Section s = r.sections.get(i);
            if (i > 0) b.append(',');
            b.append("\n    {\"name\": \"").append(esc(s.name)).append("\", \"size\": ").append(s.size)
                    .append(", \"entropy\": ").append(String.format(Locale.US, "%.3f", s.entropy)).append('}');
        }
        b.append("\n  ]\n}\n");
        return b.toString();
    }

    private static String arr(List<String> list) {
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) b.append(", ");
            b.append('"').append(esc(list.get(i))).append('"');
        }
        return b.append(']').toString();
    }
}
