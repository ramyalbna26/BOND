package com.ramyelbanna.juzoor.engine;

import com.ramyelbanna.juzoor.core.LogManager;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Offline "search everywhere" over a recovered project.
 *
 * <p>Text files are read line by line.  Binary files (native libraries,
 * assets, images) are read in 128 KB windows and only printable runs are fed
 * to the matcher, so searching a 50 MB {@code .so} never allocates the whole
 * file.  Every hit keeps the relative path, line number (or byte offset for
 * binary files) and a short snippet.</p>
 */
public final class GlobalSearch {

    public static final class Request {
        public String query = "";
        public boolean regex;
        public boolean caseSensitive;
        public int maxResults = 800;
        public boolean includeBinary = true;
    }

    public static final class Hit {
        public String path;
        public int line;
        public long offset = -1;
        public String text;
        public boolean binary;
    }

    public static final class Report {
        public final List<Hit> hits = new ArrayList<>();
        public int filesScanned;
        public int filesMatched;
        public long bytesScanned;
        public long durationMs;
        public boolean truncated;
        public String error;
    }

    private static final int MAX_TEXT_FILE = 32 * 1024 * 1024;
    private static final int MAX_LINE = 16 * 1024;

    private GlobalSearch() {
    }

    public static Report search(File root, Request request, LogSink log) {
        Report report = new Report();
        long started = System.currentTimeMillis();
        Request req = request == null ? new Request() : request;
        if (req.query == null || req.query.isEmpty()) {
            report.error = "Empty search query";
            return report;
        }
        Pattern pattern;
        try {
            int flags = req.caseSensitive ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
            pattern = Pattern.compile(req.regex ? req.query : Pattern.quote(req.query), flags);
        } catch (PatternSyntaxException e) {
            report.error = "Invalid regular expression: " + e.getDescription();
            return report;
        }
        if (root == null || !root.isDirectory()) {
            report.error = "Project folder not found";
            return report;
        }
        walk(root, root, pattern, req, report, log, 0);
        report.durationMs = System.currentTimeMillis() - started;
        if (log != null) {
            log.log(LogManager.INFO, "Global search: " + report.hits.size() + " hit(s) in "
                    + report.filesMatched + " file(s), " + report.filesScanned + " scanned");
        }
        return report;
    }

    private static void walk(File root, File dir, Pattern pattern, Request req, Report report,
                             LogSink log, int depth) {
        if (depth > 48 || report.truncated) return;
        if (log != null && log.isCancelled()) return;
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File f : children) {
            if (report.truncated) return;
            if (log != null && log.isCancelled()) return;
            String name = f.getName();
            if (f.isDirectory()) {
                if (name.equals(".git") || name.equals(".gradle") || name.equals("build")) continue;
                walk(root, f, pattern, req, report, log, depth + 1);
                continue;
            }
            if (!f.isFile()) continue;
            report.filesScanned++;
            String rel = rel(root, f);
            boolean binary = isBinary(f);
            boolean matched;
            if (binary) {
                matched = req.includeBinary && searchBinary(f, rel, pattern, report, req.maxResults);
            } else {
                matched = searchText(f, rel, pattern, report, req.maxResults);
            }
            if (matched) report.filesMatched++;
            report.bytesScanned += f.length();
        }
    }

    private static boolean searchText(File file, String rel, Pattern pattern, Report report, int max) {
        if (file.length() > MAX_TEXT_FILE) return false;
        boolean matched = false;
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(
                    new FileInputStream(file), StandardCharsets.UTF_8), 65536);
            String line;
            int number = 0;
            while ((line = reader.readLine()) != null) {
                number++;
                String scan = line.length() > MAX_LINE ? line.substring(0, MAX_LINE) : line;
                Matcher m = pattern.matcher(scan);
                if (m.find()) {
                    matched = true;
                    addHit(report, rel, number, -1, scan, false, max);
                    if (report.truncated) break;
                }
            }
        } catch (Throwable ignored) {
        } finally {
            close(reader);
        }
        return matched;
    }

    /**
     * Binary files are scanned in fixed windows; a small carry keeps runs that
     * cross a window boundary intact.
     */
    private static boolean searchBinary(File file, String rel, Pattern pattern, Report report, int max) {
        boolean matched = false;
        BufferedInputStream in = null;
        try {
            in = new BufferedInputStream(new FileInputStream(file), 131072);
            byte[] buffer = new byte[131072 + 1024];
            int carry = 0;
            long base = 0;
            while (true) {
                int read = in.read(buffer, carry, 131072);
                if (read <= 0) break;
                int len = carry + read;
                int start = -1;
                for (int i = 0; i <= len; i++) {
                    int c = i < len ? buffer[i] & 0xFF : 0;
                    boolean printable = c >= 0x20 && c < 0x7F;
                    if (printable) {
                        if (start < 0) start = i;
                    } else {
                        if (start >= 0 && i - start >= 4) {
                            String s = new String(buffer, start, i - start, StandardCharsets.US_ASCII);
                            Matcher m = pattern.matcher(s);
                            if (m.find()) {
                                matched = true;
                                addHit(report, rel, -1, base + start, s, true, max);
                                if (report.truncated) return true;
                            }
                        }
                        start = -1;
                    }
                }
                carry = Math.min(3, len);
                System.arraycopy(buffer, len - carry, buffer, 0, carry);
                base += len - carry;
            }
        } catch (Throwable ignored) {
        } finally {
            close(in);
        }
        return matched;
    }

    private static void addHit(Report report, String rel, int line, long offset,
                               String text, boolean binary, int max) {
        if (report.hits.size() >= Math.max(1, max)) {
            report.truncated = true;
            return;
        }
        Hit h = new Hit();
        h.path = rel;
        h.line = line;
        h.offset = offset;
        h.text = text == null ? "" : text.trim();
        h.binary = binary;
        report.hits.add(h);
    }

    /** Heuristic: a NUL byte in the first 8 KB means binary. */
    public static boolean isBinary(File file) {
        String name = file.getName().toLowerCase(Locale.US);
        if (name.endsWith(".so") || name.endsWith(".dex") || name.endsWith(".png")
                || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".webp")
                || name.endsWith(".gif") || name.endsWith(".ttf") || name.endsWith(".otf")
                || name.endsWith(".woff") || name.endsWith(".woff2") || name.endsWith(".mp3")
                || name.endsWith(".mp4") || name.endsWith(".wav") || name.endsWith(".ogg")
                || name.endsWith(".zip") || name.endsWith(".jar") || name.endsWith(".aar")
                || name.endsWith(".apk") || name.endsWith(".arsc") || name.endsWith(".bin")) {
            return true;
        }
        BufferedInputStream in = null;
        try {
            in = new BufferedInputStream(new FileInputStream(file), 8192);
            byte[] buf = new byte[8192];
            int n = in.read(buf);
            for (int i = 0; i < n; i++) if (buf[i] == 0) return true;
        } catch (Throwable ignored) {
            return true;
        } finally {
            close(in);
        }
        return false;
    }

    public static String toText(Report report, String query) {
        StringBuilder b = new StringBuilder();
        b.append("Juzoor — global search\n");
        b.append("Query: ").append(query).append('\n');
        b.append("Hits: ").append(report.hits.size()).append(" in ")
                .append(report.filesMatched).append(" file(s)\n");
        if (report.truncated) b.append("Note: result limit reached\n");
        if (report.error != null) b.append("Error: ").append(report.error).append('\n');
        b.append('\n');
        for (Hit h : report.hits) {
            b.append(h.path);
            if (h.line > 0) b.append(':').append(h.line);
            else if (h.offset >= 0) b.append(" @0x").append(Long.toHexString(h.offset));
            b.append("  ").append(h.text).append('\n');
        }
        return b.toString();
    }

    private static String rel(File root, File f) {
        String p = root.toURI().relativize(f.toURI()).getPath();
        return p.endsWith("/") ? p.substring(0, p.length() - 1) : p;
    }

    private static void close(java.io.Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (IOException ignored) {
            }
        }
    }
}
