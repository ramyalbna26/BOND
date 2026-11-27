package com.ramyelbanna.juzoor.engine;

import com.ramyelbanna.juzoor.core.LogManager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Compares two APK / APK-set / AAB archives without unpacking them.
 *
 * <p>Entries are matched by name and compared by size and CRC-32, which is the
 * standard ZIP integrity value.  The result lists added, removed and modified
 * entries with their size delta, and can be exported as a text report.</p>
 */
public final class ApkDiff {

    public static final class Entry {
        public String name;
        public long oldSize;
        public long newSize;
        public long oldCrc;
        public long newCrc;
        public boolean directory;
        public String reason;
    }

    public static final class Report {
        public String oldName;
        public String newName;
        public long oldLength;
        public long newLength;
        public long durationMs;
        public String error;
        public final List<Entry> added = new ArrayList<>();
        public final List<Entry> removed = new ArrayList<>();
        public final List<Entry> modified = new ArrayList<>();
        public int unchanged;

        public int totalChanges() {
            return added.size() + removed.size() + modified.size();
        }
    }

    private ApkDiff() {
    }

    public static Report diff(File oldApk, File newApk, LogSink log) {
        Report r = new Report();
        long started = System.currentTimeMillis();
        if (oldApk == null || newApk == null || !oldApk.isFile() || !newApk.isFile()) {
            r.error = "Both APK files are required";
            return r;
        }
        r.oldName = oldApk.getName();
        r.newName = newApk.getName();
        r.oldLength = oldApk.length();
        r.newLength = newApk.length();
        Map<String, ZipEntry> oldEntries = read(oldApk);
        Map<String, ZipEntry> newEntries = read(newApk);
        if (oldEntries == null || newEntries == null) {
            r.error = "One of the files is not a valid APK/ZIP archive";
            return r;
        }
        for (Map.Entry<String, ZipEntry> e : oldEntries.entrySet()) {
            ZipEntry n = newEntries.get(e.getKey());
            if (n == null) {
                Entry entry = convert(e.getValue(), null);
                entry.reason = "removed";
                r.removed.add(entry);
            } else if (e.getValue().getCrc() != n.getCrc() || e.getValue().getSize() != n.getSize()) {
                Entry entry = convert(e.getValue(), n);
                entry.reason = "modified";
                r.modified.add(entry);
            } else {
                r.unchanged++;
            }
        }
        for (Map.Entry<String, ZipEntry> e : newEntries.entrySet()) {
            if (!oldEntries.containsKey(e.getKey())) {
                Entry entry = convert(null, e.getValue());
                entry.reason = "added";
                r.added.add(entry);
            }
        }
        sort(r.added);
        sort(r.removed);
        sort(r.modified);
        r.durationMs = System.currentTimeMillis() - started;
        if (log != null) {
            log.log(LogManager.SUCCESS, "APK diff: " + r.added.size() + " added, "
                    + r.removed.size() + " removed, " + r.modified.size() + " modified");
        }
        return r;
    }

    private static Map<String, ZipEntry> read(File file) {
        Map<String, ZipEntry> entries = new LinkedHashMap<>();
        ZipFile zip = null;
        try {
            zip = new ZipFile(file);
            Enumeration<? extends ZipEntry> en = zip.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                entries.put(e.getName(), e);
            }
            return entries;
        } catch (IOException e) {
            return null;
        } finally {
            if (zip != null) {
                try {
                    zip.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static Entry convert(ZipEntry oldEntry, ZipEntry newEntry) {
        Entry e = new Entry();
        e.name = newEntry != null ? newEntry.getName() : oldEntry.getName();
        e.directory = e.name.endsWith("/");
        e.oldSize = oldEntry == null ? 0 : oldEntry.getSize();
        e.newSize = newEntry == null ? 0 : newEntry.getSize();
        e.oldCrc = oldEntry == null ? 0 : oldEntry.getCrc();
        e.newCrc = newEntry == null ? 0 : newEntry.getCrc();
        return e;
    }

    private static void sort(List<Entry> list) {
        java.util.Collections.sort(list, new java.util.Comparator<Entry>() {
            @Override
            public int compare(Entry a, Entry b) {
                return a.name.compareToIgnoreCase(b.name);
            }
        });
    }

    public static String toText(Report r) {
        StringBuilder b = new StringBuilder();
        b.append("Juzoor — APK diff\n\n");
        b.append("Old: ").append(r.oldName).append(" (").append(human(r.oldLength)).append(")\n");
        b.append("New: ").append(r.newName).append(" (").append(human(r.newLength)).append(")\n");
        if (r.error != null) b.append("Error: ").append(r.error).append('\n');
        b.append("\nSummary\n-------\n");
        b.append("Added    : ").append(r.added.size()).append('\n');
        b.append("Removed  : ").append(r.removed.size()).append('\n');
        b.append("Modified : ").append(r.modified.size()).append('\n');
        b.append("Unchanged: ").append(r.unchanged).append('\n');
        section(b, "Added", r.added);
        section(b, "Removed", r.removed);
        section(b, "Modified", r.modified);
        return b.toString();
    }

    private static void section(StringBuilder b, String title, List<Entry> entries) {
        b.append("\n").append(title).append(" (").append(entries.size()).append(")\n-------\n");
        if (entries.isEmpty()) {
            b.append("—\n");
            return;
        }
        for (Entry e : entries) {
            b.append(e.directory ? "[D] " : "    ").append(e.name);
            if (!e.directory) {
                b.append("  ").append(human(e.oldSize)).append(" → ").append(human(e.newSize));
                long delta = e.newSize - e.oldSize;
                if (delta != 0) b.append(" (").append(delta > 0 ? "+" : "").append(human(delta)).append(')');
            }
            b.append('\n');
        }
    }

    private static String human(long v) {
        long abs = Math.abs(v);
        if (abs < 1024) return v + " B";
        double d = abs / 1024.0d;
        if (d < 1024.0d) return String.format(Locale.US, "%s%.1f KB", v < 0 ? "-" : "", d);
        return String.format(Locale.US, "%s%.2f MB", v < 0 ? "-" : "", d / 1024.0d);
    }
}
