package com.ramyelbanna.juzoor.engine;

import com.ramyelbanna.juzoor.core.LogManager;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.InflaterInputStream;

/**
 * Offline analyser for the non-Android formats in the Juzoor roadmap:
 * Windows PE/EXE/DLL (including PyInstaller and .NET detection), Linux
 * ELF/DEB/RPM, macOS Mach-O/DMG.
 *
 * <p>The analyser never executes a sample.  It reads headers with a bounded
 * buffer, streams the complete file for strings, and writes every extracted
 * artefact below {@code analysis/cross/}.  PyInstaller archives are carved and
 * their entries inflated with the standard {@link java.util.zip} classes, so
 * the feature works with no network and no external tool.</p>
 */
public final class CrossPlatform {

    public static final String PE = "PE";
    public static final String ELF = "ELF";
    public static final String MACHO = "Mach-O";
    public static final String FAT_MACHO = "Mach-O (fat)";
    public static final String DMG = "DMG";
    public static final String DEB = "DEB";
    public static final String RPM = "RPM";
    public static final String UNKNOWN = "UNKNOWN";

    private static final byte[] PYI_MAGIC = {'M', 'E', 'I', 0x0C, 0x0B, 0x0A, 0x0B, 0x0E};
    private static final int MAX_FULL = 128 * 1024 * 1024;

    public static final class Section {
        public String name;
        public long offset;
        public long size;
        public long address;
        public String flags = "";
    }

    public static final class Report {
        public File file;
        public String format = UNKNOWN;
        public String arch = "";
        public int bits;
        public boolean bigEndian;
        public long fileSize;
        public String error;
        public final List<Section> sections = new ArrayList<>();
        public final List<String> notes = new ArrayList<>();
        public final List<String> contents = new ArrayList<>();
        public final List<String> extracted = new ArrayList<>();
        public int stringCount;
        public int pyinstallerEntries;
        public boolean pyinstaller;
        public boolean fullRead;
        public File stringsFile;
        public File extractedDir;
        public File reportFile;

        public String summary() {
            return format + (arch.isEmpty() ? "" : " · " + arch)
                    + (bits > 0 ? " · " + bits + "-bit" : "")
                    + " · " + stringCount + " strings";
        }
    }

    private CrossPlatform() {
    }

    public static Report analyze(File file, File analysisDir, LogSink log) {
        Report r = new Report();
        r.file = file;
        if (file == null || !file.isFile()) {
            r.error = "File not found";
            return r;
        }
        r.fileSize = file.length();
        File outDir = analysisDir == null
                ? new File(file.getParentFile(), "juzoor-cross") : new File(analysisDir, "cross");
        outDir.mkdirs();
        String base = file.getName().replaceAll("[^A-Za-z0-9._-]", "_");
        r.stringsFile = new File(outDir, base + ".strings.txt");
        try {
            NativeStrings.Result ns = NativeStrings.scan(file, r.stringsFile, false);
            r.stringCount = ns.total;
        } catch (IOException e) {
            r.notes.add("String extraction failed: " + e);
        }

        long maxFull = Math.min(MAX_FULL, Runtime.getRuntime().maxMemory() / 3L);
        r.fullRead = file.length() <= maxFull;
        byte[] data;
        try {
            data = r.fullRead ? ZipUtil.readFile(file) : readPrefix(file, 32 * 1024 * 1024);
        } catch (IOException e) {
            r.error = "Could not read file: " + e;
            return r;
        }
        detect(r, data, file);
        if (!r.fullRead) r.notes.add("Large file: header-prefix analysis (strings are still streamed from the whole file)");
        try {
            if (PE.equals(r.format)) parsePe(r, data, outDir, base, log);
            else if (ELF.equals(r.format)) parseElf(r, data);
            else if (MACHO.equals(r.format) || FAT_MACHO.equals(r.format)) parseMachO(r, data);
            else if (DMG.equals(r.format)) parseDmg(r, file, outDir, base);
            else if (DEB.equals(r.format)) PackageFormats.deb(r, file, outDir, base, log);
            else if (RPM.equals(r.format)) PackageFormats.rpm(r, file, outDir, base, log);
            else r.notes.add("Unknown container — only strings were extracted");
        } catch (Throwable t) {
            r.error = "Analysis failed: " + t;
            if (log != null) log.log(LogManager.WARN, "Cross-platform analysis failed: " + t);
        }
        try {
            r.reportFile = new File(outDir, base + ".report.txt");
            ZipUtil.writeFile(r.reportFile, toText(r));
        } catch (IOException e) {
            r.notes.add("Report write failed: " + e);
        }
        if (log != null) {
            log.log(LogManager.INFO, "Cross-platform: " + r.summary());
            for (String note : r.notes) log.log(LogManager.DEBUG, note);
        }
        return r;
    }

    /* -------------------------------------------------------------- detect */

    private static void detect(Report r, byte[] d, File file) {
        String lower = file.getName().toLowerCase(Locale.US);
        if (d.length >= 2 && (d[0] & 0xFF) == 'M' && (d[1] & 0xFF) == 'Z') {
            r.format = PE;
            r.arch = "x86/x64";
        } else if (d.length >= 4 && (d[0] & 0xFF) == 0x7F && d[1] == 'E' && d[2] == 'L' && d[3] == 'F') {
            r.format = ELF;
        } else if (d.length >= 4 && ((d[0] & 0xFF) == 0xFE && (d[1] & 0xFF) == 0xED
                || (d[0] & 0xFF) == 0xCE && (d[1] & 0xFF) == 0xFA
                || (d[0] & 0xFF) == 0xCF && (d[1] & 0xFF) == 0xFA)) {
            r.format = MACHO;
        } else if (d.length >= 4 && ((d[0] & 0xFF) == 0xCA && (d[1] & 0xFF) == 0xFE
                && (d[2] & 0xFF) == 0xBA && (d[3] & 0xFF) == 0xBE
                || (d[0] & 0xFF) == 0xBE && (d[1] & 0xFF) == 0xBA
                && (d[2] & 0xFF) == 0xFE && (d[3] & 0xFF) == 0xCA)) {
            r.format = FAT_MACHO;
        } else if (d.length >= 8 && new String(d, 0, 8, StandardCharsets.US_ASCII).equals("!<arch>\n")) {
            r.format = DEB;
        } else if (d.length >= 4 && (d[0] & 0xFF) == 0xED && (d[1] & 0xFF) == 0xAB
                && (d[2] & 0xFF) == 0xEE && (d[3] & 0xFF) == 0xDB) {
            r.format = RPM;
        } else if (lower.endsWith(".dmg") || hasKoly(file)) {
            r.format = DMG;
        } else if (lower.endsWith(".deb")) {
            r.format = DEB;
        } else if (lower.endsWith(".rpm")) {
            r.format = RPM;
        } else {
            r.format = UNKNOWN;
        }
    }

    private static boolean hasKoly(File file) {
        try {
            if (file.length() < 512) return false;
            RandomAccessFile raf = new RandomAccessFile(file, "r");
            try {
                raf.seek(file.length() - 512);
                byte[] tail = new byte[4];
                raf.readFully(tail);
                return tail[0] == 'k' && tail[1] == 'o' && tail[2] == 'l' && tail[3] == 'y';
            } finally {
                raf.close();
            }
        } catch (Throwable ignored) {
            return false;
        }
    }

    /* ------------------------------------------------------------------ PE */

    private static void parsePe(Report r, byte[] d, File outDir, String base, LogSink log) {
        r.bits = 32;
        if (d.length < 0x40) {
            r.notes.add("PE header truncated");
            return;
        }
        int peOff = le32(d, 0x3C);
        if (peOff <= 0 || peOff + 24 > d.length
                || d[peOff] != 'P' || d[peOff + 1] != 'E' || d[peOff + 2] != 0 || d[peOff + 3] != 0) {
            r.notes.add("DOS header present but PE signature missing");
            return;
        }
        int machine = le16(d, peOff + 4);
        int sections = le16(d, peOff + 6);
        int optSize = le16(d, peOff + 20);
        int opt = peOff + 24;
        int magic = opt + 2 <= d.length ? le16(d, opt) : 0;
        r.bits = magic == 0x20B ? 64 : 32;
        r.arch = machineName(machine);
        int subsystem = le16(d, opt + 68);
        String subsystemName = subsystem == 2 ? "Windows GUI" : subsystem == 3 ? "Windows console" : "subsystem " + subsystem;
        r.notes.add("PE32" + (r.bits == 64 ? "+" : "") + " · " + r.arch + " · " + subsystemName);
        int secOff = opt + optSize;
        for (int i = 0; i < Math.min(sections, 96); i++) {
            int s = secOff + i * 40;
            if (s + 40 > d.length) break;
            Section sec = new Section();
            sec.name = cstr(d, s, 8);
            sec.size = le32(d, s + 16) & 0xFFFFFFFFL;
            sec.address = le32(d, s + 12) & 0xFFFFFFFFL;
            sec.offset = le32(d, s + 20) & 0xFFFFFFFFL;
            long characteristics = le32(d, s + 36) & 0xFFFFFFFFL;
            sec.flags = (characteristics & 0x20000000L) != 0 ? "exec"
                    : (characteristics & 0x80000000L) != 0 ? "data" : "read";
            r.sections.add(sec);
        }
        if (contains(d, "BSJB".getBytes(StandardCharsets.US_ASCII))) {
            r.notes.add(".NET metadata root (BSJB) detected — managed assembly");
        }
        if (contains(d, "pyinstaller".getBytes(StandardCharsets.US_ASCII))
                || contains(d, "PyInstaller".getBytes(StandardCharsets.US_ASCII))
                || contains(d, PYI_MAGIC)) {
            r.notes.add("PyInstaller markers detected");
        }
        extractPyInstaller(r, d, outDir, base, log);
    }

    private static void extractPyInstaller(Report r, byte[] d, File outDir, String base, LogSink log) {
        int cookie = lastIndexOf(d, PYI_MAGIC);
        if (cookie < 0 || cookie + 88 > d.length) return;
        long pkgLen = be32(d, cookie + 8) & 0xFFFFFFFFL;
        long tocOff = be32(d, cookie + 12) & 0xFFFFFFFFL;
        long tocLen = be32(d, cookie + 16) & 0xFFFFFFFFL;
        int pyvers = be32(d, cookie + 20);
        if (pkgLen <= 0 || pkgLen > d.length || tocOff < 0 || tocLen <= 0) {
            r.notes.add("PyInstaller cookie found but its table is invalid");
            return;
        }
        long start = cookie - pkgLen + 88L;
        if (start < 0 || start + tocOff + tocLen > d.length) {
            r.notes.add("PyInstaller archive is truncated");
            return;
        }
        File dir = new File(outDir, base + "-pyinstaller");
        dir.mkdirs();
        r.pyinstaller = true;
        r.extractedDir = dir;
        r.notes.add("PyInstaller archive: pyvers=" + pyvers + ", toc=" + tocLen + " bytes");
        int pos = (int) (start + tocOff);
        int end = (int) Math.min(d.length, start + tocOff + tocLen);
        int count = 0;
        while (pos + 18 <= end && count < 50000) {
            int entryLen = be32(d, pos);
            long off = be32(d, pos + 4) & 0xFFFFFFFFL;
            int csize = be32(d, pos + 8);
            int usize = be32(d, pos + 12);
            boolean compressed = (d[pos + 16] & 0xFF) != 0;
            char type = (char) (d[pos + 17] & 0xFF);
            if (entryLen < 18 || entryLen > 65536 || pos + entryLen > end) break;
            String name = new String(d, pos + 18, entryLen - 18, StandardCharsets.ISO_8859_1)
                    .replace('\u0000', ' ').trim();
            pos += entryLen;
            if (name.isEmpty() || off < 0 || start + off > d.length) continue;
            int dataOff = (int) (start + off);
            int len = compressed ? csize : (usize > 0 ? usize : csize);
            if (len <= 0 || dataOff + len > d.length || len > 64 * 1024 * 1024) continue;
            byte[] payload = new byte[len];
            System.arraycopy(d, dataOff, payload, 0, len);
            if (compressed) {
                try {
                    payload = inflate(payload);
                } catch (Throwable t) {
                    r.notes.add("PyInstaller entry not inflatable: " + name);
                    continue;
                }
            }
            writePyiEntry(r, dir, name, type, payload, pyvers);
            count++;
        }
        r.pyinstallerEntries = count;
        if (count > 0) {
            r.notes.add("PyInstaller: " + count + " entries extracted to " + dir.getName());
            if (log != null) log.log(LogManager.SUCCESS, "PyInstaller archive extracted: " + count + " entries");
        } else {
            r.notes.add("PyInstaller table contained no readable entries");
        }
    }

    private static void writePyiEntry(Report r, File dir, String name, char type, byte[] data, int pyvers) {
        String safe = name.replace('\\', '/').replace("..", "_");
        while (safe.startsWith("/")) safe = safe.substring(1);
        if (safe.isEmpty()) safe = "entry";
        File target;
        if (type == 's' || type == 'm' || type == 'M') {
            target = new File(dir, safe + ".pyc");
            byte[] header = pycHeader(pyvers, data.length);
            byte[] all = new byte[header.length + data.length];
            System.arraycopy(header, 0, all, 0, header.length);
            System.arraycopy(data, 0, all, header.length, data.length);
            data = all;
        } else if (type == 'z' || type == 'Z') {
            target = new File(dir, safe.endsWith(".zip") ? safe : safe + ".zip");
        } else if (type == 'b') {
            target = new File(dir, safe.endsWith(".so") || safe.endsWith(".dll") ? safe : safe + ".bin");
        } else {
            target = new File(dir, safe);
        }
        target = new File(dir, target.getName());
        int n = 1;
        while (target.exists()) target = new File(dir, target.getName().replaceAll("(\\.[^.]+)?$", "_" + n++ + "$1"));
        try {
            ZipUtil.writeFile(target, data);
            r.extracted.add(target.getName());
        } catch (IOException e) {
            r.notes.add("Could not write " + target.getName() + ": " + e);
        }
    }

    private static byte[] pycHeader(int pyvers, int size) {
        int magic;
        switch (pyvers) {
            case 39: magic = 3425; break;
            case 310: magic = 3439; break;
            case 311: magic = 3495; break;
            case 312: magic = 3531; break;
            case 313: magic = 3571; break;
            default: magic = 3413; break; // Python 3.8, widely readable
        }
        byte[] h = new byte[16];
        h[0] = (byte) (magic & 0xFF);
        h[1] = (byte) ((magic >> 8) & 0xFF);
        h[2] = 0x0D;
        h[3] = 0x0A;
        // PEP 552 timestamp-based header: magic, bit field, mtime, source size.
        h[12] = (byte) (size & 0xFF);
        h[13] = (byte) ((size >> 8) & 0xFF);
        h[14] = (byte) ((size >> 16) & 0xFF);
        h[15] = (byte) ((size >> 24) & 0xFF);
        return h;
    }

    /* ----------------------------------------------------------------- ELF */

    private static void parseElf(Report r, byte[] d) {
        if (d.length < 52) {
            r.notes.add("ELF header truncated");
            return;
        }
        r.bits = (d[4] & 0xFF) == 2 ? 64 : 32;
        r.bigEndian = (d[5] & 0xFF) == 2;
        boolean be = r.bigEndian;
        int machine = (int) u16(d, 18, be);
        r.arch = elfMachine(machine);
        long shoff = r.bits == 64 ? u64(d, 0x28, be) : u32(d, 0x20, be);
        int shentsize = (int) u16(d, r.bits == 64 ? 0x3A : 0x2E, be);
        int shnum = (int) u16(d, r.bits == 64 ? 0x3C : 0x30, be);
        int shstrndx = (int) u16(d, r.bits == 64 ? 0x3E : 0x32, be);
        if (shoff <= 0 || shoff >= d.length || shentsize <= 0 || shnum <= 0 || shnum > 4096) {
            r.notes.add("ELF section header table not usable (stripped or truncated)");
            return;
        }
        long strOff = 0;
        if (shstrndx < shnum) {
            long sh = shoff + (long) shstrndx * shentsize;
            strOff = r.bits == 64 ? u64(d, sh + 0x18, be) : u32(d, sh + 0x10, be);
        }
        for (int i = 0; i < shnum; i++) {
            long sh = shoff + (long) i * shentsize;
            if (sh + shentsize > d.length) break;
            long nameOff = u32(d, sh, be);
            Section s = new Section();
            if (strOff > 0 && strOff + nameOff < d.length) s.name = cstr(d, (int) (strOff + nameOff), 64);
            s.address = r.bits == 64 ? u64(d, sh + 0x10, be) : u32(d, sh + 0x0C, be);
            s.offset = r.bits == 64 ? u64(d, sh + 0x18, be) : u32(d, sh + 0x10, be);
            s.size = r.bits == 64 ? u64(d, sh + 0x20, be) : u32(d, sh + 0x14, be);
            s.flags = "section";
            r.sections.add(s);
        }
        if (contains(d, "UPX!".getBytes(StandardCharsets.US_ASCII))) r.notes.add("UPX packer marker detected");
    }

    /* -------------------------------------------------------------- Mach-O */

    private static void parseMachO(Report r, byte[] d) {
        if (d.length < 4) return;
        int b0 = d[0] & 0xFF, b1 = d[1] & 0xFF, b2 = d[2] & 0xFF, b3 = d[3] & 0xFF;
        if ((b0 == 0xCA && b1 == 0xFE && b2 == 0xBA && b3 == 0xBE)
                || (b0 == 0xBE && b1 == 0xBA && b2 == 0xFE && b3 == 0xCA)) {
            r.format = FAT_MACHO;
            boolean fatBe = b0 == 0xCA;
            int n = fatBe ? be32(d, 4) : le32(d, 4);
            r.notes.add("Universal binary with " + n + " architecture(s)");
            for (int i = 0; i < Math.min(n, 32); i++) {
                int base = 8 + i * 20;
                if (base + 20 > d.length) break;
                int cpu = fatBe ? be32(d, base) : le32(d, base);
                int off = fatBe ? be32(d, base + 8) : le32(d, base + 8);
                int size = fatBe ? be32(d, base + 12) : le32(d, base + 12);
                r.notes.add("arch " + machoCpu(cpu) + " @0x" + Integer.toHexString(off)
                        + " (" + ConversionEngine.human(size & 0xFFFFFFFFL) + ")");
            }
            return;
        }
        // Big-endian Mach-O starts FE ED, little-endian starts CE FA or CF FA.
        boolean be = b0 == 0xFE;
        r.bits = be ? (b3 == 0xCF ? 64 : 32) : (b0 == 0xCF ? 64 : 32);
        int cpu = (int) u32(d, 4, be);
        int filetype = (int) u32(d, 12, be);
        int ncmds = (int) u32(d, 16, be);
        r.arch = machoCpu(cpu);
        r.notes.add("Mach-O type " + filetype + " · " + r.arch + " · " + ncmds + " load commands");
        int pos = r.bits == 64 ? 32 : 28;
        for (int i = 0; i < Math.min(ncmds, 4096) && pos + 8 <= d.length; i++) {
            int cmd = (int) u32(d, pos, be);
            int size = (int) u32(d, pos + 4, be);
            if (size < 8 || pos + size > d.length) break;
            if (cmd == 0x19 || cmd == 0x1) { // LC_SEGMENT / LC_SEGMENT_64
                int nameOff = pos + 8;
                String name = cstr(d, nameOff, 16);
                r.notes.add("segment " + name);
            } else if (cmd == 0xC) { // LC_LOAD_DYLIB
                int off = (int) u32(d, pos + 8, be);
                if (pos + off < d.length) r.notes.add("dylib " + cstr(d, pos + off, 256));
            } else if (cmd == 0x2) { // LC_SYMTAB
                int nsyms = (int) u32(d, pos + 12, be);
                r.notes.add("symbols " + nsyms);
            }
            pos += size;
        }
    }

    /* ----------------------------------------------------------------- DMG */

    private static void parseDmg(Report r, File file, File outDir, String base) {
        try {
            RandomAccessFile raf = new RandomAccessFile(file, "r");
            try {
                if (file.length() < 512) {
                    r.notes.add("DMG too small");
                    return;
                }
                raf.seek(file.length() - 512);
                byte[] k = new byte[512];
                raf.readFully(k);
                if (!(k[0] == 'k' && k[1] == 'o' && k[2] == 'l' && k[3] == 'y')) {
                    r.notes.add("No UDIF koly trailer");
                    return;
                }
                long dataOff = be64(k, 24);
                long dataLen = be64(k, 32);
                long rsrcOff = be64(k, 40);
                long rsrcLen = be64(k, 48);
                long xmlOff = be64(k, 216);
                long xmlLen = be64(k, 224);
                long sectors = be64(k, 492);
                r.notes.add("UDIF image: data @" + dataOff + " (" + dataLen + " bytes), resource "
                        + rsrcOff + " (" + rsrcLen + " bytes), sectors " + sectors);
                if (xmlOff > 0 && xmlLen > 0 && xmlLen < 64L * 1024L * 1024L
                        && xmlOff + xmlLen <= file.length()) {
                    byte[] xml = new byte[(int) xmlLen];
                    raf.seek(xmlOff);
                    raf.readFully(xml);
                    String plist = new String(xml, StandardCharsets.UTF_8);
                    Matcher m = Pattern.compile("<key>name</key>\\s*<string>([^<]*)</string>").matcher(plist);
                    if (m.find()) r.notes.add("volume name: " + m.group(1));
                    r.notes.add("partition plist: " + xmlLen + " bytes");
                    ZipUtil.writeFile(new File(outDir, base + ".dmg.plist"), plist);
                }
            } finally {
                raf.close();
            }
        } catch (Throwable t) {
            r.notes.add("DMG parse failed: " + t);
        }
    }

    /* -------------------------------------------------------------- output */

    public static String toText(Report r) {
        StringBuilder b = new StringBuilder();
        b.append("Juzoor — cross-platform analysis\n\n");
        b.append("File   : ").append(r.file == null ? "?" : r.file.getName()).append('\n');
        b.append("Format : ").append(r.format).append('\n');
        b.append("Arch   : ").append(r.arch).append(r.bits > 0 ? " (" + r.bits + "-bit)" : "").append('\n');
        b.append("Size   : ").append(ConversionEngine.human(r.fileSize)).append('\n');
        b.append("Strings: ").append(r.stringCount).append('\n');
        if (r.error != null) b.append("Error  : ").append(r.error).append('\n');
        if (!r.notes.isEmpty()) {
            b.append("\nFindings\n--------\n");
            for (String n : r.notes) b.append("- ").append(n).append('\n');
        }
        if (!r.sections.isEmpty()) {
            b.append("\nSections\n--------\n");
            b.append("Name                 Offset      Size        Flags\n");
            for (Section s : r.sections) {
                b.append(String.format(Locale.US, "%-20s 0x%-8x %-10d %s%n",
                        s.name, s.offset, s.size, s.flags));
            }
        }
        if (!r.extracted.isEmpty()) {
            b.append("\nExtracted\n---------\n");
            for (String e : r.extracted) b.append("- ").append(e).append('\n');
        }
        b.append("\n© 2027 Ramy El-Banna — Juzoor™\n");
        return b.toString();
    }

    /* ------------------------------------------------------------ helpers */

    private static byte[] readPrefix(File f, int max) throws IOException {
        int n = (int) Math.min(f.length(), max);
        byte[] out = new byte[n];
        BufferedInputStream in = new BufferedInputStream(new FileInputStream(f), 65536);
        try {
            int read = 0;
            while (read < n) {
                int r = in.read(out, read, n - read);
                if (r < 0) break;
                read += r;
            }
        } finally {
            in.close();
        }
        return out;
    }

    private static byte[] inflate(byte[] data) throws IOException {
        InflaterInputStream in = new InflaterInputStream(new java.io.ByteArrayInputStream(data));
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(Math.max(64, data.length * 3));
        byte[] buf = new byte[65536];
        int n;
        try {
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        } finally {
            in.close();
        }
        return out.toByteArray();
    }

    private static int lastIndexOf(byte[] d, byte[] needle) {
        outer:
        for (int i = d.length - needle.length; i >= 0; i--) {
            for (int k = 0; k < needle.length; k++) {
                if (d[i + k] != needle[k]) continue outer;
            }
            return i;
        }
        return -1;
    }

    private static boolean contains(byte[] d, byte[] needle) {
        return lastIndexOf(d, needle) >= 0;
    }

    private static String cstr(byte[] d, int off, int max) {
        if (off < 0 || off >= d.length) return "";
        int end = off;
        while (end < d.length && end - off < max && d[end] != 0) end++;
        return new String(d, off, end - off, StandardCharsets.ISO_8859_1);
    }

    private static int le16(byte[] d, int o) {
        if (o < 0 || o + 2 > d.length) return 0;
        return (d[o] & 0xFF) | ((d[o + 1] & 0xFF) << 8);
    }

    private static int le32(byte[] d, int o) {
        if (o < 0 || o + 4 > d.length) return 0;
        return (d[o] & 0xFF) | ((d[o + 1] & 0xFF) << 8)
                | ((d[o + 2] & 0xFF) << 16) | ((d[o + 3] & 0xFF) << 24);
    }

    private static int be32(byte[] d, int o) {
        if (o < 0 || o + 4 > d.length) return 0;
        return ((d[o] & 0xFF) << 24) | ((d[o + 1] & 0xFF) << 16)
                | ((d[o + 2] & 0xFF) << 8) | (d[o + 3] & 0xFF);
    }

    private static long be64(byte[] d, int o) {
        return ((long) be32(d, o) << 32) | (be32(d, o + 4) & 0xFFFFFFFFL);
    }

    private static long u16(byte[] d, long offset, boolean be) {
        if (offset < 0 || offset + 2 > d.length) return 0;
        int o = (int) offset;
        return be ? ((d[o] & 0xFF) << 8 | (d[o + 1] & 0xFF))
                : ((d[o + 1] & 0xFF) << 8 | (d[o] & 0xFF));
    }

    private static long u32(byte[] d, long offset, boolean be) {
        if (offset < 0 || offset + 4 > d.length) return 0;
        int o = (int) offset;
        long v = 0;
        for (int i = 0; i < 4; i++) {
            int b = d[o + i] & 0xFF;
            v |= be ? ((long) b << (8 * (3 - i))) : ((long) b << (8 * i));
        }
        return v;
    }

    private static long u64(byte[] d, long offset, boolean be) {
        if (offset < 0 || offset + 8 > d.length) return 0;
        int o = (int) offset;
        long v = 0;
        for (int i = 0; i < 8; i++) {
            long b = d[o + i] & 0xFF;
            v |= be ? (b << (8 * (7 - i))) : (b << (8 * i));
        }
        return v;
    }

    private static String machineName(int m) {
        switch (m) {
            case 0x14c: return "x86";
            case 0x8664: return "x86_64";
            case 0x1c0: return "ARM";
            case 0x1c4: return "ARMv7";
            case 0xaa64: return "ARM64";
            default: return "machine#" + Integer.toHexString(m);
        }
    }

    private static String elfMachine(int m) {
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

    private static String machoCpu(int cpu) {
        switch (cpu) {
            case 7: return "x86";
            case 0x01000007: return "x86_64";
            case 12: return "ARM";
            case 0x0100000C: return "ARM64";
            case 0x01000012: return "PowerPC64";
            default: return "cpu#" + Integer.toHexString(cpu);
        }
    }
}
