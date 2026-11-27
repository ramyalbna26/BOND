package com.ramyelbanna.juzoor.engine;

import com.ramyelbanna.juzoor.core.LogManager;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

/**
 * DEB (ar + tar) and RPM (header + cpio) readers used by the cross-platform
 * analyser.  Everything is streamed from the original file; only the small
 * metadata headers are buffered.
 */
final class PackageFormats {

    private static final int MAX_ENTRIES = 30000;
    private static final long MAX_EXTRACT = 512L * 1024L * 1024L;

    private PackageFormats() {
    }

    /* ----------------------------------------------------------------- DEB */

    static void deb(CrossPlatform.Report r, File file, File outDir, String base, LogSink log) {
        File root = new File(outDir, base + "-deb");
        root.mkdirs();
        r.extractedDir = root;
        long total = 0;
        int members = 0;
        try {
            RandomAccessFile raf = new RandomAccessFile(file, "r");
            try {
                if (file.length() < 8) {
                    r.notes.add("ar archive truncated");
                    return;
                }
                byte[] magic = new byte[8];
                raf.readFully(magic);
                if (!new String(magic, StandardCharsets.US_ASCII).equals("!<arch>\n")) {
                    r.notes.add("Not an ar archive");
                    return;
                }
                long pos = 8;
                while (pos + 60 <= file.length()) {
                    raf.seek(pos);
                    byte[] h = new byte[60];
                    raf.readFully(h);
                    String name = new String(h, 0, 16, StandardCharsets.US_ASCII).trim();
                    if (name.endsWith("/")) name = name.substring(0, name.length() - 1);
                    long size;
                    try {
                        size = Long.parseLong(new String(h, 48, 10, StandardCharsets.US_ASCII).trim());
                    } catch (Throwable t) {
                        break;
                    }
                    long dataStart = pos + 60;
                    if (dataStart + size > file.length() || size < 0) break;
                    members++;
                    r.contents.add(name + " (" + size + " bytes)");
                    if (name.equals("control.tar.gz") || name.equals("control.tar")
                            || name.equals("control.tar.xz") || name.equals("control.tar.zst")) {
                        File controlDir = new File(root, "control");
                        controlDir.mkdirs();
                        int n = extractTar(file, dataStart, size, name, controlDir, r.contents);
                        r.notes.add("control archive: " + n + " entries");
                        readDebControl(controlDir, r);
                    } else if (name.startsWith("data.tar")) {
                        String type = name.endsWith(".xz") ? "xz"
                                : name.endsWith(".zst") ? "zstd"
                                : name.endsWith(".bz2") ? "bzip2" : "gzip/plain";
                        if (type.equals("xz") || type.equals("zstd") || type.equals("bzip2")) {
                            r.notes.add("data archive uses " + type + " — listing metadata only (no built-in decoder)");
                        } else {
                            File dataDir = new File(root, "root");
                            dataDir.mkdirs();
                            int n = extractTar(file, dataStart, size, name, dataDir, r.contents);
                            r.notes.add("data archive: " + n + " entries extracted");
                        }
                    } else if (name.equals("debian-binary")) {
                        String text = readMember(file, dataStart, size, 64);
                        r.notes.add("debian-binary: " + text.trim());
                    }
                    total += size;
                    pos = dataStart + size + (size & 1);
                }
            } finally {
                raf.close();
            }
        } catch (Throwable t) {
            r.notes.add("DEB parse failed: " + t);
        }
        r.notes.add("ar members: " + members + ", uncompressed payload " + ConversionEngine.human(total));
        if (log != null) log.log(LogManager.INFO, "DEB: " + r.contents.size() + " member(s) read");
    }

    private static void readDebControl(File dir, CrossPlatform.Report r) {
        File control = new File(dir, "control");
        if (!control.isFile()) return;
        try {
            String text = new String(ZipUtil.readFile(control), StandardCharsets.UTF_8);
            for (String line : text.split("\\r?\\n")) {
                if (line.startsWith("Package:") || line.startsWith("Version:")
                        || line.startsWith("Architecture:") || line.startsWith("Maintainer:")
                        || line.startsWith("Description:")) {
                    r.notes.add("control " + line.trim());
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /* ----------------------------------------------------------------- RPM */

    static void rpm(CrossPlatform.Report r, File file, File outDir, String base, LogSink log) {
        File root = new File(outDir, base + "-rpm");
        root.mkdirs();
        r.extractedDir = root;
        try {
            RandomAccessFile raf = new RandomAccessFile(file, "r");
            try {
                if (file.length() < 112) {
                    r.notes.add("RPM too small");
                    return;
                }
                // RPM layout: lead (96) -> signature header record -> main
                // header record -> compressed cpio payload.  Both records are
                // 16 + n*16 + dataSize, padded to 8 bytes.  Reading the
                // signature record as the package header loses every name /
                // version tag, so skip it unless it is the only header (the
                // small synthetic/test variant).
                Header first = readHeader(raf, 96);
                if (first == null) {
                    r.notes.add("RPM header magic missing");
                    return;
                }
                Header main = first;
                if (tagString(first.index, first.data, first.count, 1000).isEmpty()) {
                    main = readHeader(raf, first.end);
                }
                if (main == null) {
                    r.notes.add("RPM main header missing");
                    return;
                }
                String name = tagString(main.index, main.data, main.count, 1000);
                String version = tagString(main.index, main.data, main.count, 1001);
                String release = tagString(main.index, main.data, main.count, 1002);
                String arch = tagString(main.index, main.data, main.count, 1022);
                String summary = tagString(main.index, main.data, main.count, 1004);
                String compressor = tagString(main.index, main.data, main.count, 1125);
                if (!name.isEmpty()) r.notes.add("package " + name + " " + version + "-" + release + " · " + arch);
                if (!summary.isEmpty()) r.notes.add("summary " + summary);
                if (!compressor.isEmpty()) r.notes.add("payload compressor " + compressor);
                int cn = 0;
                for (int i = 0; i < main.count; i++) {
                    int tag = be32(main.index, i * 16, 0);
                    if (tag >= 1005 && tag <= 1010) cn++;
                }
                r.notes.add("RPM tags: " + main.count + ", dependency tags: " + cn);
                long payload = main.end;
                if (payload < file.length()) {
                    byte[] magic = new byte[6];
                    raf.seek(payload);
                    raf.readFully(magic);
                    String type = payloadType(magic);
                    r.notes.add("payload starts at " + payload + " (" + type + ")");
                    if ("gzip".equals(type) || "cpio".equals(type)) {
                        File dest = new File(root, "root");
                        dest.mkdirs();
                        int count = extractCpio(file, payload, dest, r.contents);
                        r.notes.add("cpio: " + count + " entries extracted");
                    } else {
                        r.notes.add("payload compression " + type + " is not decoded on-device");
                    }
                }
            } finally {
                raf.close();
            }
        } catch (Throwable t) {
            r.notes.add("RPM parse failed: " + t);
        }
        if (log != null) log.log(LogManager.INFO, "RPM: " + r.contents.size() + " entry line(s)");
    }

    /** One RPM header record, including its 8-byte alignment. */
    private static final class Header {
        byte[] index;
        byte[] data;
        int count;
        long end;
    }

    private static Header readHeader(RandomAccessFile raf, long offset) {
        try {
            if (offset < 0 || offset + 16 > raf.length()) return null;
            raf.seek(offset);
            byte[] h = new byte[16];
            raf.readFully(h);
            if ((h[0] & 0xFF) != 0x8E || (h[1] & 0xFF) != 0xAD || (h[2] & 0xFF) != 0xE8) {
                return null;
            }
            int n = be32(h, 8, 0);
            int dataSize = be32(h, 12, 0);
            if (n <= 0 || n > 100000 || dataSize < 0 || dataSize > 64 * 1024 * 1024) return null;
            if (offset + 16 + (long) n * 16 + dataSize > raf.length()) return null;
            Header header = new Header();
            header.count = n;
            header.index = new byte[n * 16];
            raf.readFully(header.index);
            header.data = new byte[dataSize];
            raf.readFully(header.data);
            long end = offset + 16 + (long) n * 16 + dataSize;
            header.end = (end + 7) & ~7L;
            return header;
        } catch (Throwable t) {
            return null;
        }
    }

    private static String tagString(byte[] index, byte[] data, int count, int wanted) {
        for (int i = 0; i < count; i++) {
            int tag = be32(index, i * 16, 0);
            int type = be32(index, i * 16 + 4, 0);
            int off = be32(index, i * 16 + 8, 0);
            if (tag != wanted) continue;
            if (off < 0 || off >= data.length) return "";
            if (type == 6 || type == 8 || type == 9) {
                int end = off;
                while (end < data.length && data[end] != 0) end++;
                return new String(data, off, end - off, StandardCharsets.ISO_8859_1);
            }
        }
        return "";
    }

    private static int be32(byte[] d, int o, int unused) {
        return ((d[o] & 0xFF) << 24) | ((d[o + 1] & 0xFF) << 16)
                | ((d[o + 2] & 0xFF) << 8) | (d[o + 3] & 0xFF);
    }

    private static String payloadType(byte[] m) {
        if ((m[0] & 0xFF) == 0x1F && (m[1] & 0xFF) == 0x8B) return "gzip";
        if ((m[0] & 0xFF) == 0xFD && m[1] == '7' && m[2] == 'z' && m[3] == 'X') return "xz";
        if ((m[0] & 0xFF) == 0x28 && (m[1] & 0xFF) == 0xB5 && (m[2] & 0xFF) == 0x2F && (m[3] & 0xFF) == 0xFD) return "zstd";
        if (m[0] == 'B' && m[1] == 'Z' && m[2] == 'h') return "bzip2";
        if (m[0] == '0' && m[1] == '7' && m[2] == '0' && (m[3] == '7' || m[3] == '1')) return "cpio";
        return "unknown";
    }

    /* ---------------------------------------------------------- tar / cpio */

    private static int extractTar(File archive, long offset, long size, String name, File dest,
                                  List<String> listing) {
        int count = 0;
        try {
            InputStream in = openMember(archive, offset, size);
            String lower = name.toLowerCase(Locale.US);
            if (lower.endsWith(".gz")) in = new GZIPInputStream(in, 65536);
            else if (!lower.endsWith(".tar")) {
                in.close();
                return 0;
            }
            try {
                count = extractTarStream(in, dest, listing);
            } finally {
                in.close();
            }
        } catch (Throwable ignored) {
        }
        return count;
    }

    private static int extractTarStream(InputStream raw, File dest, List<String> listing) throws IOException {
        BufferedInputStream in = new BufferedInputStream(raw, 65536);
        byte[] header = new byte[512];
        int count = 0;
        String longName = null;
        long total = 0;
        while (count < MAX_ENTRIES) {
            int read = readFully(in, header);
            if (read == 0) break;
            if (read < 512) break;
            boolean zero = true;
            for (byte b : header) if (b != 0) { zero = false; break; }
            if (zero) break;
            String name = cstr(header, 0, 100);
            long size = octal(header, 124, 12);
            char type = (char) (header[156] & 0xFF);
            String prefix = cstr(header, 345, 155);
            if (type == 'L') {
                longName = new String(readBytes(in, size), StandardCharsets.UTF_8).replace("\u0000", "");
                skipTarPadding(in, size);
                continue;
            }
            if (type == 'x' || type == 'g') {
                skipTarData(in, size);
                continue;
            }
            if (!prefix.isEmpty()) name = prefix + "/" + name;
            if (longName != null) {
                name = longName;
                longName = null;
            }
            name = safePath(name);
            if (name == null) {
                skipTarData(in, size);
                continue;
            }
            count++;
            if (!listing.contains(name)) {
                if (listing.size() < 5000) listing.add(name);
            }
            if (type == '5') {
                File dir = new File(dest, name);
                dir.mkdirs();
                skipTarData(in, size);
                continue;
            }
            if (type == '2' || type == '1') {
                skipTarData(in, size);
                continue;
            }
            File out = new File(dest, name);
            File parent = out.getParentFile();
            if (parent != null) parent.mkdirs();
            if (total + size > MAX_EXTRACT) {
                skipTarData(in, size);
                continue;
            }
            total += size;
            if (!writeStream(in, out, size)) break;
            skipTarPadding(in, size);
        }
        return count;
    }

    private static int extractCpio(File archive, long offset, File dest, List<String> listing) {
        int count = 0;
        try {
            InputStream raw = openMember(archive, offset, archive.length() - offset);
            BufferedInputStream in = new BufferedInputStream(raw, 65536);
            in.mark(8);
            byte[] magic = new byte[6];
            int r = readFully(in, magic);
            if (r < 6) {
                in.close();
                return 0;
            }
            in.reset();
            if ((magic[0] & 0xFF) == 0x1F && (magic[1] & 0xFF) == 0x8B) {
                in = new BufferedInputStream(new GZIPInputStream(in, 65536), 65536);
            } else if (!(magic[0] == '0' && magic[1] == '7' && magic[2] == '0')) {
                in.close();
                return 0;
            }
            try {
                count = extractCpioStream(in, dest, listing);
            } finally {
                in.close();
            }
        } catch (Throwable ignored) {
        }
        return count;
    }

    private static int extractCpioStream(InputStream raw, File dest, List<String> listing) throws IOException {
        BufferedInputStream in = new BufferedInputStream(raw, 65536);
        byte[] header = new byte[110];
        int count = 0;
        long total = 0;
        while (count < MAX_ENTRIES) {
            int read = readFully(in, header);
            if (read == 0) break;
            if (read < 110) break;
            String magic = new String(header, 0, 6, StandardCharsets.US_ASCII);
            if (!(magic.equals("070701") || magic.equals("070702"))) break;
            long size = hex(header, 54, 8);
            long nameSize = hex(header, 94, 8);
            if (nameSize <= 0 || nameSize > 4096) break;
            byte[] nameBytes = readBytes(in, nameSize);
            String name = new String(nameBytes, StandardCharsets.UTF_8).replace("\u0000", "");
            int pad = (int) ((4 - ((110 + nameSize) % 4)) % 4);
            skipBytes(in, pad);
            name = safePath(name);
            if (name == null) {
                skipBytes(in, size);
                skipBytes(in, (int) ((4 - (size % 4)) % 4));
                continue;
            }
            if (name.equals("TRAILER!!!")) break;
            count++;
            if (!listing.contains(name) && listing.size() < 5000) listing.add(name);
            long mode = hex(header, 14, 8);
            File out = new File(dest, name);
            if ((mode & 0xF000) == 0x4000) {
                out.mkdirs();
                skipBytes(in, size);
                skipBytes(in, (int) ((4 - (size % 4)) % 4));
                continue;
            }
            File parent = out.getParentFile();
            if (parent != null) parent.mkdirs();
            if (total + size > MAX_EXTRACT) {
                skipBytes(in, size);
                skipBytes(in, (int) ((4 - (size % 4)) % 4));
                continue;
            }
            total += size;
            writeStream(in, out, size);
            skipBytes(in, (int) ((4 - (size % 4)) % 4));
        }
        return count;
    }

    /* ------------------------------------------------------------- streams */

    private static InputStream openMember(File file, long offset, long size) throws IOException {
        FileInputStream in = new FileInputStream(file);
        java.nio.channels.FileChannel ch = in.getChannel();
        ch.position(offset);
        return new BoundedInputStream(in, size);
    }

    private static String readMember(File file, long offset, long size, int max) {
        try {
            InputStream in = openMember(file, offset, Math.min(size, max));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[512];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            in.close();
            return new String(out.toByteArray(), StandardCharsets.US_ASCII);
        } catch (Throwable t) {
            return "";
        }
    }

    private static boolean writeStream(InputStream in, File out, long size) throws IOException {
        BufferedOutputStream os = new BufferedOutputStream(new FileOutputStream(out), 65536);
        long left = size;
        byte[] buf = new byte[65536];
        try {
            while (left > 0) {
                int n = in.read(buf, 0, (int) Math.min(buf.length, left));
                if (n < 0) return false;
                os.write(buf, 0, n);
                left -= n;
            }
            return true;
        } finally {
            os.close();
        }
    }

    private static void skipTarPadding(InputStream in, long size) throws IOException {
        long pad = (512 - (size % 512)) % 512;
        skipBytes(in, pad);
    }

    private static void skipTarData(InputStream in, long size) throws IOException {
        skipBytes(in, size);
        skipTarPadding(in, size);
    }

    private static void skipBytes(InputStream in, long n) throws IOException {
        while (n > 0) {
            long skipped = in.skip(n);
            if (skipped <= 0) {
                if (in.read() < 0) return;
                skipped = 1;
            }
            n -= skipped;
        }
    }

    private static int readFully(InputStream in, byte[] buf) throws IOException {
        int off = 0;
        while (off < buf.length) {
            int n = in.read(buf, off, buf.length - off);
            if (n < 0) break;
            off += n;
        }
        return off;
    }

    private static byte[] readBytes(InputStream in, long n) throws IOException {
        if (n < 0 || n > 8 * 1024 * 1024) throw new IOException("entry too large");
        byte[] out = new byte[(int) n];
        int off = 0;
        while (off < out.length) {
            int r = in.read(out, off, out.length - off);
            if (r < 0) throw new IOException("truncated entry");
            off += r;
        }
        return out;
    }

    private static String cstr(byte[] d, int off, int max) {
        int end = off;
        while (end < d.length && end - off < max && d[end] != 0) end++;
        return new String(d, off, end - off, StandardCharsets.ISO_8859_1);
    }

    private static long octal(byte[] d, int off, int len) {
        String s = new String(d, off, len, StandardCharsets.US_ASCII).replace("\u0000", " ").trim();
        if (s.isEmpty()) return 0;
        try {
            return Long.parseLong(s, 8);
        } catch (Throwable t) {
            return 0;
        }
    }

    private static long hex(byte[] d, int off, int len) {
        String s = new String(d, off, len, StandardCharsets.US_ASCII).trim();
        try {
            return Long.parseLong(s, 16);
        } catch (Throwable t) {
            return 0;
        }
    }

    private static String safePath(String name) {
        if (name == null) return null;
        String n = name.replace('\\', '/');
        while (n.startsWith("./")) n = n.substring(2);
        while (n.startsWith("/")) n = n.substring(1);
        if (n.isEmpty() || n.contains("..")) return null;
        return n;
    }

    /** InputStream limited to a fixed byte range of the underlying file. */
    private static final class BoundedInputStream extends InputStream {
        private final InputStream in;
        private long left;

        BoundedInputStream(InputStream in, long left) {
            this.in = in;
            this.left = left;
        }

        @Override
        public int read() throws IOException {
            if (left <= 0) return -1;
            int b = in.read();
            if (b >= 0) left--;
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (left <= 0) return -1;
            int n = in.read(b, off, (int) Math.min(len, left));
            if (n > 0) left -= n;
            return n;
        }

        @Override
        public long skip(long n) throws IOException {
            long s = in.skip(Math.min(n, left));
            left -= s;
            return s;
        }

        @Override
        public void close() throws IOException {
            in.close();
        }
    }

}
