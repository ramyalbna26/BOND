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
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * On-device APK repackaging and signing.
 *
 * <p>The pipeline works directly on the original archive, which keeps the
 * result byte-for-byte compatible with everything Juzoor does not touch:</p>
 *
 * <ol>
 *   <li>DEX string patching - rewrites {@code string_data_item} entries in
 *       place (replacement must not be longer than the original).</li>
 *   <li>Binary manifest patching - flips typed boolean attributes such as
 *       {@code debuggable}, {@code allowBackup} and
 *       {@code usesCleartextTraffic} without re-encoding the AXML.</li>
 *   <li>Optional file injection.</li>
 *   <li>Archive rebuild with 4-byte alignment for stored entries.</li>
 *   <li>v1 / v2 / v3 signing with the bundled {@code apksig} engine.</li>
 * </ol>
 */
public final class Repackager {

    private static final Charset UTF8 = Charset.forName("UTF-8");

    public static final class Options {
        public boolean v1 = true;
        public boolean v2 = true;
        public boolean v3 = true;
        public int minSdk = 21;
        public boolean patchDebuggable;
        public boolean patchBackup;
        public boolean patchCleartext;
        public final List<String[]> stringPatches = new ArrayList<>();
        public File injectFile;
        public String injectPath;
        public File keystore;
        public String storePass = "juzoor";
        public String keyPass = "juzoor";
        public String alias = "juzoor";
    }

    public static final class Result {
        public boolean success;
        public String error = "";
        public File output;
        public long bytes;
        public int dexStringsPatched;
        public int manifestFlagsPatched;
        public int filesWritten;
        public boolean injected;
        public String signature = "unsigned";
        public String signer = "";
        public final List<String> log = new ArrayList<>();

        public String summary() {
            return filesWritten + " files · " + dexStringsPatched + " strings · "
                    + manifestFlagsPatched + " manifest flags · " + signature;
        }
    }

    private Repackager() {
    }

    public static Result run(File input, File output, Options o, LogSink log) {
        Result r = new Result();
        File tmp = new File(output.getParentFile(), output.getName() + ".unsigned");
        try {
            if (input == null || !input.isFile()) throw new IOException("Input APK not found");
            if (o.keystore == null || !o.keystore.isFile()) throw new IOException("Keystore not found");
            if (!o.v1 && !o.v2 && !o.v3) throw new IOException("Enable at least one signature scheme");

            ZipFile zip = new ZipFile(input);
            CountingOutputStream counter = new CountingOutputStream(
                    new BufferedOutputStream(new FileOutputStream(tmp), 1 << 16));
            ZipOutputStream out = new ZipOutputStream(counter);
            out.setLevel(6);
            try {
                Enumeration<? extends ZipEntry> en = zip.entries();
                while (en.hasMoreElements()) {
                    if (log != null && log.isCancelled()) throw new ZipUtil.CancelledException();
                    ZipEntry e = en.nextElement();
                    String name = e.getName();
                    if (isSignatureFile(name)) continue;

                    byte[] data;
                    InputStream in = zip.getInputStream(e);
                    try {
                        data = readAll(in);
                    } finally {
                        in.close();
                    }

                    boolean stored = e.getMethod() == ZipEntry.STORED;
                    if (isDex(name) && !o.stringPatches.isEmpty()) {
                        int n = patchDexStrings(data, o.stringPatches);
                        r.dexStringsPatched += n;
                        if (n > 0) line(r, "patched " + n + " string(s) in " + name);
                    }
                    if (name.equals("AndroidManifest.xml")) {
                        int n = patchManifest(data, o);
                        r.manifestFlagsPatched += n;
                        if (n > 0) line(r, "patched " + n + " manifest flag(s)");
                    }
                    if (o.injectFile != null && o.injectPath != null && name.equals(o.injectPath)) {
                        data = readFile(o.injectFile);
                        r.injected = true;
                        line(r, "replaced " + name + " with injected file");
                    }
                    writeEntry(out, counter, e, name, data, stored);
                    r.filesWritten++;
                }

                if (o.injectFile != null && o.injectPath != null && !r.injected) {
                    byte[] data = readFile(o.injectFile);
                    ZipEntry e = new ZipEntry(o.injectPath);
                    e.setTime(System.currentTimeMillis());
                    writeEntry(out, counter, e, o.injectPath, data, true);
                    r.filesWritten++;
                    r.injected = true;
                    line(r, "injected " + o.injectPath);
                }
            } finally {
                out.close();
                zip.close();
            }

            if (!o.stringPatches.isEmpty() && r.dexStringsPatched == 0) {
                line(r, "no DEX string matched - replacement must be shorter or equal in length");
            }

            ApkSigning.SignOptions so = new ApkSigning.SignOptions();
            so.v1 = o.v1;
            so.v2 = o.v2;
            so.v3 = o.v3;
            so.minSdk = o.minSdk;
            so.keystore = o.keystore;
            so.storePass = o.storePass;
            so.keyPass = o.keyPass;
            so.alias = o.alias;
            ApkSigning.SignResult sr = ApkSigning.sign(tmp, output, so, log);
            r.signature = sr.describe();
            r.signer = sr.signer;
            if (!sr.success) throw new IOException("Signing failed: " + sr.error);

            r.output = output;
            r.bytes = output.length();
            r.success = true;
            line(r, "signed output: " + output.getName() + " (" + ProjectTree.human(r.bytes) + ")");
            if (log != null) {
                log.log(LogManager.SUCCESS, "Repackaged " + output.getName() + " - " + r.summary());
            }
        } catch (ZipUtil.CancelledException c) {
            r.error = "Cancelled";
        } catch (Throwable t) {
            r.error = String.valueOf(t);
            if (log != null) log.log(LogManager.ERROR, "Repackaging failed: " + t);
        } finally {
            if (tmp.exists()) tmp.delete();
        }
        return r;
    }

    private static void line(Result r, String s) {
        r.log.add(s);
    }

    /* ------------------------------------------------------------- patchers */

    /** Rewrites DEX string_data_item payloads in place. */
    public static int patchDexStrings(byte[] d, List<String[]> patches) {
        if (d == null || d.length < 112) return 0;
        int size = i32(d, 56);
        int off = i32(d, 60);
        if (size <= 0 || off <= 0 || off + size * 4 > d.length) return 0;
        int count = 0;
        for (int i = 0; i < size; i++) {
            int dataOff = i32(d, off + i * 4);
            if (dataOff <= 0 || dataOff >= d.length) continue;
            int p = dataOff;
            int shift = 0;
            while (p < d.length) {
                int b = d[p++] & 0xff;
                if ((b & 0x80) == 0) break;
                shift += 7;
                if (shift > 35) break;
            }
            int start = p;
            int end = start;
            while (end < d.length && d[end] != 0) end++;
            if (end <= start) continue;
            String value = new String(d, start, end - start, UTF8);
            for (String[] patch : patches) {
                if (patch == null || patch.length < 2 || patch[0] == null || patch[1] == null) continue;
                if (!patch[0].equals(value)) continue;
                byte[] nb = patch[1].getBytes(UTF8);
                int room = end - start;
                if (nb.length > room) continue;
                System.arraycopy(nb, 0, d, start, nb.length);
                for (int k = start + nb.length; k < end; k++) d[k] = 0;
                count++;
                break;
            }
        }
        return count;
    }

    /** Flips typed boolean attributes in a binary AndroidManifest.xml. */
    public static int patchManifest(byte[] d, Options o) {
        if (d == null || d.length < 8) return 0;
        if ((d[0] & 0xff) != 0x03 || (d[1] & 0xff) != 0x00) return 0;
        String[] pool = null;
        int count = 0;
        int pos = 8;
        while (pos + 8 <= d.length) {
            int type = u16(d, pos);
            int size = i32(d, pos + 4);
            if (size < 8 || pos + size > d.length) break;
            if (type == 0x0001 && pool == null) {
                pool = readStringPool(d, pos);
            } else if (type == 0x0102 && pool != null) {
                int attrStart = u16(d, pos + 16 + 8);
                int attrSize = u16(d, pos + 16 + 10);
                int attrCount = u16(d, pos + 16 + 12);
                if (attrSize <= 0) attrSize = 20;
                for (int i = 0; i < attrCount; i++) {
                    int a = pos + 16 + attrStart + i * attrSize;
                    if (a + 20 > d.length) break;
                    int nameIdx = i32(d, a + 4);
                    if (nameIdx < 0 || nameIdx >= pool.length) continue;
                    String name = pool[nameIdx];
                    Boolean value = null;
                    if ("debuggable".equals(name) && o.patchDebuggable) value = Boolean.TRUE;
                    else if ("allowBackup".equals(name) && o.patchBackup) value = Boolean.FALSE;
                    else if ("usesCleartextTraffic".equals(name) && o.patchCleartext) value = Boolean.TRUE;
                    if (value == null) continue;
                    d[a + 15] = 0x12; // TYPE_INT_BOOLEAN
                    int v = value ? -1 : 0;
                    d[a + 16] = (byte) (v & 0xff);
                    d[a + 17] = (byte) ((v >> 8) & 0xff);
                    d[a + 18] = (byte) ((v >> 16) & 0xff);
                    d[a + 19] = (byte) ((v >> 24) & 0xff);
                    d[a + 8] = d[a + 9] = d[a + 10] = d[a + 11] = (byte) 0xff; // rawValue = -1
                    count++;
                }
            }
            pos += size;
        }
        return count;
    }

    private static String[] readStringPool(byte[] d, int chunk) {
        int headerSize = u16(d, chunk + 2);
        int stringCount = i32(d, chunk + 8);
        int flags = i32(d, chunk + 16);
        int stringsStart = i32(d, chunk + 20);
        boolean utf8 = (flags & 0x100) != 0;
        String[] out = new String[Math.max(0, stringCount)];
        for (int i = 0; i < stringCount; i++) {
            int offPos = chunk + headerSize + i * 4;
            if (offPos + 4 > d.length) break;
            int strOff = chunk + stringsStart + i32(d, offPos);
            if (strOff <= 0 || strOff >= d.length) continue;
            try {
                out[i] = utf8 ? readUtf8(d, strOff) : readUtf16(d, strOff);
            } catch (Throwable ignored) {
                out[i] = "";
            }
        }
        return out;
    }

    private static String readUtf8(byte[] d, int p) {
        int len = d[p++] & 0xff;
        if ((len & 0x80) != 0) len = ((len & 0x7f) << 8) | (d[p++] & 0xff);
        int end = p;
        while (end < d.length && d[end] != 0) end++;
        return new String(d, p, end - p, UTF8);
    }

    private static String readUtf16(byte[] d, int p) {
        int len = u16(d, p);
        p += 2;
        if ((len & 0x8000) != 0) {
            len = ((len & 0x7fff) << 16) | u16(d, p);
            p += 2;
        }
        StringBuilder b = new StringBuilder(len);
        for (int i = 0; i < len && p + 1 < d.length; i++, p += 2) b.append((char) u16(d, p));
        return b.toString();
    }

    /* ----------------------------------------------------------- zip writer */

    private static void writeEntry(ZipOutputStream out, CountingOutputStream counter,
                                   ZipEntry original, String name, byte[] data, boolean stored)
            throws IOException {
        ZipEntry e = new ZipEntry(name);
        e.setTime(original.getTime() > 0 ? original.getTime() : System.currentTimeMillis());
        if (stored) {
            e.setMethod(ZipEntry.STORED);
            e.setSize(data.length);
            CRC32 crc = new CRC32();
            crc.update(data);
            e.setCrc(crc.getValue());
            int extra = padding(counter.count, name.length());
            if (extra > 0) e.setExtra(paddingBlock(extra));
        } else {
            e.setMethod(ZipEntry.DEFLATED);
        }
        out.putNextEntry(e);
        out.write(data);
        out.closeEntry();
    }

    private static int padding(long offset, int nameLen) {
        int base = (int) ((offset + 30 + nameLen) & 3);
        int pad = (4 - base) & 3;
        if (pad == 0) return 0;
        if (pad < 4) pad += 4;
        return pad;
    }

    private static byte[] paddingBlock(int len) {
        byte[] b = new byte[len];
        b[0] = 0x35;
        b[1] = (byte) 0xD9;
        int payload = len - 4;
        b[2] = (byte) (payload & 0xff);
        b[3] = (byte) ((payload >> 8) & 0xff);
        return b;
    }

    private static boolean isDex(String name) {
        return name.matches("classes[0-9]*\\.dex");
    }

    private static boolean isSignatureFile(String name) {
        if (!name.startsWith("META-INF/")) return false;
        String upper = name.toUpperCase(Locale.US);
        return upper.endsWith(".SF") || upper.endsWith(".RSA") || upper.endsWith(".DSA")
                || upper.endsWith(".EC") || upper.equals("META-INF/MANIFEST.MF");
    }

    /* --------------------------------------------------------------- utils */

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(1 << 16);
        byte[] buf = new byte[1 << 16];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        return bos.toByteArray();
    }

    private static byte[] readFile(File f) throws IOException {
        InputStream in = new BufferedInputStream(new FileInputStream(f));
        try {
            return readAll(in);
        } finally {
            in.close();
        }
    }

    private static int u16(byte[] d, int off) {
        if (off < 0 || off + 1 >= d.length) return 0;
        return (d[off] & 0xff) | ((d[off + 1] & 0xff) << 8);
    }

    private static int i32(byte[] d, int off) {
        if (off < 0 || off + 3 >= d.length) return 0;
        return (d[off] & 0xff) | ((d[off + 1] & 0xff) << 8)
                | ((d[off + 2] & 0xff) << 16) | ((d[off + 3] & 0xff) << 24);
    }

    private static final class CountingOutputStream extends OutputStream {
        private final OutputStream out;
        long count;

        CountingOutputStream(OutputStream out) {
            this.out = out;
        }

        @Override
        public void write(int b) throws IOException {
            out.write(b);
            count++;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
            count += len;
        }

        @Override
        public void flush() throws IOException {
            out.flush();
        }

        @Override
        public void close() throws IOException {
            out.close();
        }
    }
}
