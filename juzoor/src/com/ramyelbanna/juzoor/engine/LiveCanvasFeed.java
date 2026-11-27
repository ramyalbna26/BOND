package com.ramyelbanna.juzoor.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Bounded in-memory feed for the live decompilation screen.
 *
 * <p>The screen must stay responsive while a conversion reports thousands of
 * files and log lines.  Only the newest {@link #MAX_ROWS} rows are retained for
 * the adapter; older rows are already written to the Logcat file by
 * {@code LogManager}, so dropping them from memory loses no history.  Consecutive
 * updates for the same file reuse the existing row instead of appending a new
 * one.</p>
 */
public final class LiveCanvasFeed {

    /** Hard cap for rows held by the live screen. */
    public static final int MAX_ROWS = 300;

    /** The two kinds of rows shown in the single live list. */
    public enum Kind {
        LOG,
        FILE
    }

    /** One immutable row.  The id is stable across content updates. */
    public static final class Entry {
        public final long id;
        public final Kind kind;
        public final String level;
        public final String text;
        public final int percent;
        public final long time;

        Entry(long id, Kind kind, String level, String text, int percent, long time) {
            this.id = id;
            this.kind = kind;
            this.level = level;
            this.text = text;
            this.percent = percent;
            this.time = time;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Entry)) return false;
            Entry entry = (Entry) other;
            return id == entry.id
                    && kind == entry.kind
                    && percent == entry.percent
                    && time == entry.time
                    && text.equals(entry.text)
                    && level.equals(entry.level);
        }

        @Override
        public int hashCode() {
            int result = (int) (id ^ (id >>> 32));
            result = 31 * result + kind.hashCode();
            result = 31 * result + level.hashCode();
            result = 31 * result + text.hashCode();
            result = 31 * result + percent;
            result = 31 * result + (int) (time ^ (time >>> 32));
            return result;
        }
    }

    private final int maxRows;
    private final List<Entry> rows = new ArrayList<Entry>();
    private long nextId = 1L;
    private long dropped;

    public LiveCanvasFeed() {
        this(MAX_ROWS);
    }

    public LiveCanvasFeed(int maxRows) {
        if (maxRows < 1) throw new IllegalArgumentException("maxRows must be positive");
        this.maxRows = maxRows;
    }

    public synchronized void addLog(String level, String message) {
        if (message == null || message.isEmpty()) return;
        String safeLevel = level == null ? "" : level;
        add(new Entry(nextId++, Kind.LOG, safeLevel, message, -1, System.currentTimeMillis()));
    }

    public synchronized void addFile(String path, int percent) {
        if (path == null || path.isEmpty()) return;
        long now = System.currentTimeMillis();
        int last = rows.size() - 1;
        if (last >= 0) {
            Entry previous = rows.get(last);
            if (previous.kind == Kind.FILE && previous.text.equals(path)) {
                if (previous.percent == percent) return;
                rows.set(last, new Entry(previous.id, Kind.FILE, previous.level, path, percent, now));
                return;
            }
        }
        add(new Entry(nextId++, Kind.FILE, "", path, percent, now));
    }

    private void add(Entry entry) {
        rows.add(entry);
        while (rows.size() > maxRows) {
            rows.remove(0);
            dropped++;
        }
    }

    /** A stable copy for {@code ListAdapter.submitList}. */
    public synchronized List<Entry> snapshot() {
        return Collections.unmodifiableList(new ArrayList<Entry>(rows));
    }

    public synchronized int size() {
        return rows.size();
    }

    /** Number of rows evicted from memory since the feed was created. */
    public synchronized long dropped() {
        return dropped;
    }

    public int maxRows() {
        return maxRows;
    }
}
