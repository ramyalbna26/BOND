package com.ramyelbanna.juzoor.engine;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Lists one bounded page of a recovered project folder.
 *
 * <p>Only the rows for the requested page are materialised as {@link Entry}
 * objects.  The directory is read as names, sorted, and the page is selected
 * without building a UI row for every file.  The project screen therefore keeps
 * at most one page of rows in memory even when a folder contains 18,000+
 * entries.</p>
 */
public final class ProjectTreePager {

    public static final int DEFAULT_PAGE_SIZE = 100;

    private final int pageSize;

    public ProjectTreePager() {
        this(DEFAULT_PAGE_SIZE);
    }

    public ProjectTreePager(int pageSize) {
        if (pageSize < 1) throw new IllegalArgumentException("pageSize must be positive");
        this.pageSize = pageSize;
    }

    public int getPageSize() {
        return pageSize;
    }

    /** A single directory row. */
    public static final class Entry {
        public final File file;
        public final String name;
        public final boolean directory;

        Entry(File file, String name, boolean directory) {
            this.file = file;
            this.name = name;
            this.directory = directory;
        }
    }

    /** One page plus the cursor needed to request the following page. */
    public static final class Page {
        public final List<Entry> entries;
        public final int offset;
        public final int nextOffset;
        public final int total;
        public final boolean hasMore;

        Page(List<Entry> entries, int offset, int nextOffset, int total, boolean hasMore) {
            this.entries = Collections.unmodifiableList(entries);
            this.offset = offset;
            this.nextOffset = nextOffset;
            this.total = total;
            this.hasMore = hasMore;
        }
    }

    /**
     * Returns entries {@code [offset, offset + pageSize)} with folders first and
     * names in case-insensitive order.  A missing or unreadable folder yields an
     * empty page instead of throwing.
     */
    public Page page(File dir, int offset) {
        int from = Math.max(0, offset);
        if (dir == null || !dir.isDirectory()) return empty(from, 0);
        String[] names = dir.list();
        if (names == null || names.length == 0) return empty(from, 0);

        Arrays.sort(names, String.CASE_INSENSITIVE_ORDER);

        List<Entry> out = new ArrayList<>(Math.min(pageSize, names.length));
        int index = 0;
        for (String name : names) {
            if (!new File(dir, name).isDirectory()) continue;
            if (index >= from && out.size() < pageSize) out.add(new Entry(new File(dir, name), name, true));
            index++;
        }
        for (String name : names) {
            if (new File(dir, name).isDirectory()) continue;
            if (index >= from && out.size() < pageSize) out.add(new Entry(new File(dir, name), name, false));
            index++;
        }

        int total = names.length;
        int next = Math.min(total, from + out.size());
        return new Page(out, from, next, total, next < total);
    }

    private static Page empty(int offset, int total) {
        return new Page(new ArrayList<Entry>(), offset, offset, total, false);
    }
}
