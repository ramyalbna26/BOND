package com.ramyelbanna.juzoor.engine;

import com.ramyelbanna.juzoor.core.LogManager;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Recovers Dart strings from a Flutter AOT library.
 *
 * <p>A Flutter release stores its program and constants in a Dart snapshot
 * inside {@code libapp.so}.  The snapshot is located by the
 * {@code _kDartVmSnapshotData} / {@code _kDartIsolateSnapshotData} symbols;
 * instructions are in the matching {@code *SnapshotInstructions} symbols.
 * Juzoor reads those symbols from the ELF tables and streams the snapshot
 * regions, so a large engine library is never loaded into memory as one
 * buffer.</p>
 *
 * <p>Dart serialises one-byte strings as a ULEB128 length followed by UTF-8
 * bytes and two-byte strings as a ULEB128 length followed by UTF-16 code
 * units.  Both layouts are decoded, and ordinary printable runs are kept as a
 * fallback.  Everything is written to a separate {@code dart_strings/} file
 * with its source region, so the result can be audited.</p>
 */
public final class DartStrings {

    public static final String[] DATA_MARKERS = {
            "_kDartVmSnapshotData",
            "_kDartIsolateSnapshotData",
            "_kDartSnapshotData"
    };

    public static final String[] INSTRUCTION_MARKERS = {
            "_kDartVmSnapshotInstructions",
            "_kDartIsolateSnapshotInstructions",
            "_kDartSnapshotInstructions"
    };

    private static final int CHUNK = 256 * 1024;
    private static final int CARRY = 16 * 1024;
    private static final int MIN_STRING = 3;
    private static final int MAX_STRING = 16 * 1024;
    private static final int MAX_STREAM_STRING = 256 * 1024;
    private static final int MAX_DEDUPE = 400000;
    private static final long MAX_REGION = 256L * 1024L * 1024L;
    private static final int MAX_SYMBOL_BYTES = 64 * 1024 * 1024;

    /** One snapshot region described by an ELF symbol. */
    public static final class Region {
        public final String symbol;
        public final long offset;
        public final long size;
        public final boolean instructions;

        Region(String symbol, long offset, long size, boolean instructions) {
            this.symbol = symbol;
            this.offset = offset;
            this.size = size;
            this.instructions = instructions;
        }

        public String describe() {
            return symbol + " @ 0x" + Long.toHexString(offset) + " (" + size + " bytes)";
        }
    }

    public static final class Report {
        public String name = "";
        public String abi = "";
        public boolean engine;
        public int markers;
        public int snapshotMarkers;
        public long snapshotBytes;
        public int dartStrings;
        public int plainStrings;
        public int utf16Strings;
        public int total;
        public final List<Region> regions = new ArrayList<>();
        public File file;
        public File jsonFile;

        public String summary() {
            return name + (abi.isEmpty() ? "" : " [" + abi + "]") + " — " + total
                    + " Dart string(s), " + markers + " snapshot marker(s), "
                    + regions.size() + " region(s)";
        }
    }

    private static final class Section {
        int type;
        long addr;
        long offset;
        long size;
        long link;
        long entsize;
    }

    private static final class Symbol {
        String name;
        long value;
        long size;
        int section;
    }

    private DartStrings() {
    }

    /**
     * Extracts Dart strings from {@code so} into {@code outDir}.  The optional
     * {@code analysisDir} receives a small JSON report; either directory may be
     * null, in which case that output is skipped.
     */
    public static Report analyze(File so, File outDir, File analysisDir, LogSink log) {
        Report r = new Report();
        if (so == null || !so.isFile()) return r;
        r.name = so.getName();
        r.abi = abiOf(so);
        String lower = r.name.toLowerCase(Locale.US);
        r.engine = lower.contains("flutter");
        r.markers = countMarkers(so, r);
        List<Region> found = readSnapshotRegions(so, r);
        // libapp.so is the Dart application even when its symbols were
        // stripped. The engine library is scanned when a Dart marker or a real
        // snapshot region proves that it contains one.
        boolean flutterLibrary = lower.equals("libapp.so")
                || (r.engine && (r.markers > 0 || !found.isEmpty()));
        if (found.isEmpty() && r.markers == 0 && !flutterLibrary) {
            // A plain .so with no Flutter markers is not worth a separate file.
            return r;
        }
        if (found.isEmpty()) {
            // Stripped Flutter libraries still contain readable Dart strings;
            // scan the whole file rather than losing them.
            found.add(new Region("whole-library", 0, so.length(), false));
        }
        r.regions.addAll(found);
        for (Region region : found) r.snapshotBytes += region.size;

        File dir = outDir == null ? null : outDir;
        if (dir != null) dir.mkdirs();
        String base = r.abi.isEmpty() ? safeName(r.name) : safeName(r.abi) + "-" + safeName(r.name);
        Writer writer = null;
        try {
            if (dir != null) {
                r.file = new File(dir, base + ".dart.txt");
                writer = new BufferedWriter(new OutputStreamWriter(
                        new FileOutputStream(r.file), StandardCharsets.UTF_8), 65536);
                writer.write("# Juzoor " + ConversionResult.GENERATOR_VERSION + " — Dart strings\n");
                writer.write("# Library: " + r.name + "\n");
                writer.write("# Snapshot markers: " + r.markers
                        + " (data=" + r.snapshotMarkers + ")\n");
                for (Region region : found) {
                    writer.write("# Region: " + region.describe() + "\n");
                }
                writer.write("# Format: [DART] length-prefixed, [DART16] UTF-16, [TEXT] printable run\n\n");
            }
            Set<Long> seen = new HashSet<>();
            for (Region region : found) {
                scanRegion(so, region, writer, r, seen);
            }
        } catch (IOException e) {
            if (log != null) log.log(LogManager.WARN, "Dart string extraction failed for " + r.name + ": " + e);
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException ignored) {
                }
            }
        }
        r.total = r.dartStrings + r.plainStrings;

        if (analysisDir != null) {
            File jsonDir = new File(analysisDir, "dart_strings");
            jsonDir.mkdirs();
            r.jsonFile = new File(jsonDir, base + ".json");
            try {
                ZipUtil.writeFile(r.jsonFile, toJson(r));
            } catch (IOException ignored) {
            }
        }
        if (log != null) {
            if (r.total > 0) {
                log.log(LogManager.SUCCESS, "Dart strings: " + r.name + " — " + r.total
                        + " string(s) from " + found.size() + " snapshot region(s)"
                        + (r.markers > 0 ? ", " + r.markers + " marker(s)" : ""));
            } else {
                log.log(LogManager.INFO, "Dart snapshot scanned: " + r.name
                        + " — no additional strings found");
            }
        }
        return r;
    }

    /* -------------------------------------------------------- snapshot ELF */

    /**
     * Reads ELF section and symbol tables and maps every Dart snapshot symbol
     * to a file offset.  Only the tables are read; the library itself is
     * streamed later.
     */
    private static List<Region> readSnapshotRegions(File file, Report r) {
        List<Region> out = new ArrayList<>();
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(file, "r");
            long length = raf.length();
            if (length < 52) return out;
            byte[] header = readAt(raf, 0, 64);
            if (header == null || (header[0] & 0xFF) != 0x7F || header[1] != 'E'
                    || header[2] != 'L' || header[3] != 'F') return out;
            boolean is64 = (header[4] & 0xFF) == 2;
            boolean be = (header[5] & 0xFF) == 2;
            long shoff = is64 ? u64(header, 0x28, be) : u32(header, 0x20, be);
            int shentsize = (int) u16(header, is64 ? 0x3A : 0x2E, be);
            int shnum = (int) u16(header, is64 ? 0x3C : 0x30, be);
            int minEnt = is64 ? 64 : 40;
            if (shoff <= 0 || shoff >= length || shentsize < minEnt || shnum <= 0 || shnum > 8192) {
                return out;
            }
            long tableSize = (long) shentsize * shnum;
            if (tableSize > 8 * 1024 * 1024 || shoff + tableSize > length) return out;
            byte[] table = readAt(raf, shoff, (int) tableSize);
            if (table == null) return out;

            Section[] sections = new Section[shnum];
            for (int i = 0; i < shnum; i++) {
                int p = i * shentsize;
                Section s = new Section();
                s.type = (int) u32(table, p + 4, be);
                s.addr = is64 ? u64(table, p + 0x10, be) : u32(table, p + 0x0C, be);
                s.offset = is64 ? u64(table, p + 0x18, be) : u32(table, p + 0x10, be);
                s.size = is64 ? u64(table, p + 0x20, be) : u32(table, p + 0x14, be);
                s.link = is64 ? u32(table, p + 0x28, be) : u32(table, p + 0x18, be);
                s.entsize = is64 ? u64(table, p + 0x38, be) : u32(table, p + 0x24, be);
                sections[i] = s;
            }
            List<Symbol> symbols = readSymbols(raf, sections, be, is64);
            for (Symbol sym : symbols) {
                boolean data = matches(sym.name, DATA_MARKERS);
                boolean code = matches(sym.name, INSTRUCTION_MARKERS);
                if (!data && !code) continue;
                if (data) r.snapshotMarkers++;
                if (sym.section < 0 || sym.section >= sections.length) continue;
                Section section = sections[sym.section];
                if (section == null || section.type == 8) continue; // SHT_NOBITS has no file bytes
                long start = sym.value;
                long size = sym.size;
                if (start < section.addr || start >= section.addr + section.size) continue;
                long offset = section.offset + (start - section.addr);
                if (size <= 0) size = section.addr + section.size - start;
                if (offset < 0 || offset >= length || size <= 0) continue;
                size = Math.min(size, length - offset);
                size = Math.min(size, MAX_REGION);
                if (size < 16) continue;
                boolean duplicate = false;
                for (Region old : out) {
                    if (old.offset == offset && old.size == size) {
                        duplicate = true;
                        break;
                    }
                }
                if (!duplicate) out.add(new Region(sym.name, offset, size, code));
            }
        } catch (Throwable t) {
            // A stripped or malformed ELF still gets the marker/whole-file scan.
        } finally {
            if (raf != null) {
                try {
                    raf.close();
                } catch (IOException ignored) {
                }
            }
        }
        return out;
    }

    private static List<Symbol> readSymbols(RandomAccessFile raf, Section[] sections,
                                            boolean be, boolean is64) throws IOException {
        List<Symbol> out = new ArrayList<>();
        int entrySize = is64 ? 24 : 16;
        for (Section section : sections) {
            if (section == null || (section.type != 2 && section.type != 11)) continue; // SYMTAB / DYNSYM
            long count = section.size / Math.max(1, section.entsize > 0 ? section.entsize : entrySize);
            if (count <= 0 || count > 500000) continue;
            long bytes = section.size;
            if (bytes <= 0 || bytes > MAX_SYMBOL_BYTES) continue;
            int link = (int) section.link;
            if (link < 0 || link >= sections.length) continue;
            byte[] names = readStringTable(raf, sections[link]);
            if (names == null) continue;
            byte[] table = readAt(raf, section.offset, (int) bytes);
            if (table == null) continue;
            int step = section.entsize > 0 ? (int) section.entsize : entrySize;
            for (long k = 0; k < count; k++) {
                int p = (int) (k * step);
                if (p + entrySize > table.length) break;
                long nameIdx = u32(table, p, be);
                if (nameIdx <= 0 || nameIdx >= names.length) continue;
                String name = cString(names, (int) nameIdx);
                if (!matches(name, DATA_MARKERS) && !matches(name, INSTRUCTION_MARKERS)) {
                    continue;
                }
                Symbol s = new Symbol();
                s.name = name;
                s.value = is64 ? u64(table, p + 8, be) : u32(table, p + 4, be);
                s.size = is64 ? u64(table, p + 16, be) : u32(table, p + 8, be);
                s.section = is64 ? (int) u16(table, p + 6, be) : (int) u16(table, p + 14, be);
                out.add(s);
            }
        }
        return out;
    }

    private static byte[] readStringTable(RandomAccessFile raf, Section section) throws IOException {
        if (section == null || section.size <= 0 || section.size > MAX_SYMBOL_BYTES) return null;
        return readAt(raf, section.offset, (int) section.size);
    }

    /** Reads one NUL-terminated ELF string at a byte offset. */
    private static String cString(byte[] data, int offset) {
        if (data == null || offset < 0 || offset >= data.length) return "";
        int end = offset;
        while (end < data.length && data[end] != 0) end++;
        return new String(data, offset, end - offset, StandardCharsets.ISO_8859_1);
    }

    /** Counts snapshot marker names without loading the library. */
    private static int countMarkers(File file, Report r) {
        int count = 0;
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(file, "r");
            long length = raf.length();
            byte[] chunk = new byte[CHUNK + CARRY];
            int carry = 0;
            long left = length;
            int longest = longestMarker();
            while (left > 0) {
                int read = raf.read(chunk, carry, Math.min(CHUNK, (int) Math.min(left, CHUNK)));
                if (read <= 0) break;
                left -= read;
                int len = carry + read;
                String text = new String(chunk, 0, len, StandardCharsets.ISO_8859_1);
                // Ignore the overlap from the previous chunk except for a
                // marker that straddles the boundary.
                int scanFrom = carry == 0 ? 0 : Math.max(0, carry - longest + 1);
                int skipEnd = carry;
                count += countIn(text, DATA_MARKERS, scanFrom, skipEnd);
                count += countIn(text, INSTRUCTION_MARKERS, scanFrom, skipEnd);
                carry = Math.min(CARRY, len);
                System.arraycopy(chunk, len - carry, chunk, 0, carry);
            }
        } catch (IOException ignored) {
        } finally {
            if (raf != null) {
                try {
                    raf.close();
                } catch (IOException ignored) {
                }
            }
        }
        return count;
    }

    private static int countIn(String text, String[] markers, int from, int skipEnd) {
        int n = 0;
        for (String marker : markers) {
            int at = text.indexOf(marker, from);
            while (at >= 0) {
                // Matches that end inside the previous overlap were already
                // counted; only new or boundary-straddling matches count now.
                if (at + marker.length() > skipEnd) n++;
                at = text.indexOf(marker, at + marker.length());
            }
        }
        return n;
    }

    private static int longestMarker() {
        int max = 0;
        for (String marker : DATA_MARKERS) max = Math.max(max, marker.length());
        for (String marker : INSTRUCTION_MARKERS) max = Math.max(max, marker.length());
        return max;
    }

    /* --------------------------------------------------------- string scan */

    private static void scanRegion(File file, Region region, Writer writer, Report r,
                                   Set<Long> seen) throws IOException {
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(file, "r");
            long length = raf.length();
            long start = Math.max(0, region.offset);
            long end = Math.min(length, region.offset + region.size);
            if (end <= start) return;
            raf.seek(start);
            byte[] buffer = new byte[CHUNK + CARRY];
            int carry = 0;
            long pos = start;
            while (pos < end) {
                int want = (int) Math.min(CHUNK, end - pos);
                int read = raf.read(buffer, carry, want);
                if (read <= 0) break;
                int len = carry + read;
                scanBuffer(buffer, len, writer, r, seen);
                pos += read;
                carry = Math.min(CARRY, len);
                System.arraycopy(buffer, len - carry, buffer, 0, carry);
            }
        } finally {
            if (raf != null) {
                try {
                    raf.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static void scanBuffer(byte[] b, int len, Writer w, Report r,
                                   Set<Long> seen) throws IOException {
        scanLengthPrefixed(b, len, w, r, seen);
        scanPrintableRuns(b, len, w, r, seen);
    }

    /**
     * Reads Dart's ULEB128 length prefixes.  A run is only accepted when the
     * whole payload is valid text; otherwise the scanner advances one byte and
     * tries the next possible boundary.
     */
    private static void scanLengthPrefixed(byte[] b, int len, Writer w, Report r,
                                           Set<Long> seen) throws IOException {
        int i = 0;
        while (i < len && seen.size() < MAX_DEDUPE) {
            int[] uleb = uleb(b, i, len);
            if (uleb == null) {
                i++;
                continue;
            }
            int value = uleb[0];
            int head = uleb[1];
            int payload = i + head;
            if (value >= MIN_STRING && value <= MAX_STRING) {
                if (payload + value <= len) {
                    String text = decodeUtf8(b, payload, value);
                    if (text != null && looksText(text)) {
                        if (emit(w, seen, text, "DART")) r.dartStrings++;
                        i = payload + value;
                        continue;
                    }
                }
                if (payload + value * 2 <= len) {
                    String le = decodeUtf16(b, payload, value, true);
                    String be = decodeUtf16(b, payload, value, false);
                    String text = betterText(le, be);
                    if (text != null) {
                        if (emit(w, seen, text, "DART16")) {
                            r.dartStrings++;
                            r.utf16Strings++;
                        }
                        i = payload + value * 2;
                        continue;
                    }
                }
            }
            i++;
        }
    }

    /** Printable UTF-8 / ASCII fallback for snapshot variants that omit lengths. */
    private static void scanPrintableRuns(byte[] b, int len, Writer w, Report r,
                                          Set<Long> seen) throws IOException {
        int start = -1;
        for (int i = 0; i <= len; i++) {
            boolean printable = i < len && isRunByte(b[i] & 0xFF);
            if (printable) {
                if (start < 0) start = i;
            } else {
                if (start >= 0 && i - start >= MIN_STRING) {
                    String text = decodeUtf8(b, start, i - start);
                    if (text != null && looksText(text)) {
                        if (emit(w, seen, text, "TEXT")) r.plainStrings++;
                    }
                }
                start = -1;
            }
        }
    }

    private static boolean isRunByte(int c) {
        if (c >= 0x20 && c < 0x7F) return true;
        if (c == 0x09) return true;
        return c >= 0xC2; // UTF-8 lead or continuation byte
    }

    private static String decodeUtf8(byte[] b, int off, int len) {
        if (len <= 0 || off < 0 || off + len > b.length) return null;
        String text = new String(b, off, len, StandardCharsets.UTF_8);
        return text.indexOf('\uFFFD') >= 0 ? null : text;
    }

    private static String decodeUtf16(byte[] b, int off, int units, boolean little) {
        if (units <= 0 || off < 0 || off + units * 2 > b.length) return null;
        StringBuilder sb = new StringBuilder(units);
        for (int i = 0; i < units; i++) {
            int p = off + i * 2;
            int a = b[p] & 0xFF;
            int c = b[p + 1] & 0xFF;
            char ch = (char) (little ? (a | (c << 8)) : ((a << 8) | c));
            if (ch == 0xFFFD) return null;
            sb.append(ch);
        }
        return sb.toString();
    }

    private static String betterText(String a, String b) {
        boolean okA = a != null && looksText(a);
        boolean okB = b != null && looksText(b);
        if (!okA) return okB ? b : null;
        if (!okB) return a;
        return textScore(a) >= textScore(b) ? a : b;
    }

    /** Conservative text test: no controls, mostly letters/digits and no padding. */
    private static boolean looksText(String s) {
        if (s == null) return false;
        int chars = s.codePointCount(0, s.length());
        if (chars < MIN_STRING || chars > MAX_STREAM_STRING) return false;
        int good = 0;
        int letters = 0;
        int control = 0;
        int repeats = 0;
        int previous = -1;
        int run = 0;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == 0xFFFD || (cp < 0x20 && cp != '\t' && cp != '\n' && cp != '\r')) {
                return false;
            }
            if (cp >= 0x7F && cp < 0xA0) return false;
            if (Character.isLetterOrDigit(cp)) letters++;
            if (cp == ' ' || cp == '\t' || cp == '_' || cp == '-' || cp == '.'
                    || cp == '/' || cp == ':' || cp == '=' || cp == '?' || cp == '&'
                    || cp == '#' || cp == '%' || cp == '+' || cp == ',' || cp == ';'
                    || cp == '(' || cp == ')' || cp == '[' || cp == ']' || cp == '{'
                    || cp == '}' || cp == '\'' || cp == '"' || cp == '@' || cp == '!'
                    || cp == '*' || cp == '<' || cp == '>' || cp == '|' || cp == '\\'
                    || cp == '~' || cp == '^' || cp == '$') {
                good++;
            } else if (Character.isLetterOrDigit(cp) || cp >= 0xA0) {
                good++;
            }
            if (cp == previous) {
                if (++run >= 3) repeats++;
            } else {
                run = 0;
            }
            previous = cp;
        }
        if (good < chars * 0.85) return false;
        if (letters < chars * 0.3) return false;
        return repeats <= Math.max(1, chars / 8);
    }

    private static int textScore(String s) {
        int score = 0;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.isLetter(cp)) score += 2;
            else if (Character.isDigit(cp)) score++;
            else if (cp == ' ') score += 2;
            else if (cp >= 0x20 && cp < 0x7F) score += 1;
        }
        return score;
    }

    private static boolean emit(Writer w, Set<Long> seen, String text, String tag)
            throws IOException {
        if (w == null || text == null || text.length() < MIN_STRING) return false;
        if (seen.size() >= MAX_DEDUPE) return false;
        long key = ((long) text.hashCode() << 32) ^ (text.length() * 0x9E3779B97F4A7C15L);
        if (!seen.add(key)) return false;
        w.write('[');
        w.write(tag);
        w.write("] ");
        w.write(text.replace('\n', ' ').replace('\r', ' '));
        w.write('\n');
        return true;
    }

    /* --------------------------------------------------------------- helpers */

    private static boolean matches(String name, String[] markers) {
        if (name == null) return false;
        for (String marker : markers) {
            if (marker.equals(name)) return true;
        }
        return false;
    }

    /** Parses a ULEB128, returning {value, byteCount} or null. */
    private static int[] uleb(byte[] b, int off, int len) {
        int value = 0;
        int shift = 0;
        for (int i = 0; i < 5 && off + i < len; i++) {
            int c = b[off + i] & 0xFF;
            value |= (c & 0x7F) << shift;
            if ((c & 0x80) == 0) {
                if (value < 0 || value > MAX_STREAM_STRING) return null;
                return new int[]{value, i + 1};
            }
            shift += 7;
        }
        return null;
    }

    private static byte[] readAt(RandomAccessFile raf, long offset, int length) {
        if (offset < 0 || length < 0) return null;
        try {
            if (offset + length > raf.length()) return null;
            byte[] data = new byte[length];
            raf.seek(offset);
            raf.readFully(data);
            return data;
        } catch (IOException e) {
            return null;
        }
    }

    private static long u16(byte[] d, long o, boolean be) {
        if (o < 0 || o + 2 > d.length) return 0;
        int a = d[(int) o] & 0xFF;
        int b = d[(int) o + 1] & 0xFF;
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

    /** ABI folder name, used to keep multi-ABI Dart outputs separate. */
    private static String abiOf(File so) {
        File parent = so == null ? null : so.getParentFile();
        if (parent == null) return "";
        String name = parent.getName().toLowerCase(Locale.US);
        if (name.equals("arm64-v8a") || name.equals("armeabi-v7a") || name.equals("armeabi")
                || name.equals("x86") || name.equals("x86_64") || name.equals("mips")
                || name.equals("mips64") || name.equals("riscv64")) {
            return name;
        }
        return "";
    }

    private static String safeName(String name) {
        return name == null ? "library" : name.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "");
    }

    public static String toJson(Report r) {
        StringBuilder b = new StringBuilder();
        b.append("{\n  \"library\": \"").append(esc(r.name)).append("\",\n");
        b.append("  \"engine\": ").append(r.engine).append(",\n");
        b.append("  \"markers\": ").append(r.markers).append(",\n");
        b.append("  \"snapshotMarkers\": ").append(r.snapshotMarkers).append(",\n");
        b.append("  \"snapshotBytes\": ").append(r.snapshotBytes).append(",\n");
        b.append("  \"dartStrings\": ").append(r.dartStrings).append(",\n");
        b.append("  \"utf16Strings\": ").append(r.utf16Strings).append(",\n");
        b.append("  \"plainStrings\": ").append(r.plainStrings).append(",\n");
        b.append("  \"total\": ").append(r.total).append(",\n");
        b.append("  \"regions\": [");
        for (int i = 0; i < r.regions.size(); i++) {
            if (i > 0) b.append(',');
            Region region = r.regions.get(i);
            b.append("\n    {\"symbol\": \"").append(esc(region.symbol))
                    .append("\", \"offset\": ").append(region.offset)
                    .append(", \"size\": ").append(region.size)
                    .append(", \"instructions\": ").append(region.instructions).append('}');
        }
        b.append("\n  ]\n}\n");
        return b.toString();
    }
}
