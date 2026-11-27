package com.ramyelbanna.juzoor.engine;

import org.junit.Test;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ProjectTreePagerTest {

    @Test
    public void largeFolderIsPagedWithoutRepeatingEntries() throws Exception {
        File root = new File(System.getProperty("java.io.tmpdir"),
                "juzoor-tree-" + System.nanoTime());
        assertTrue(root.mkdirs());
        try {
            for (int i = 0; i < 140; i++) {
                assertTrue(new File(root, String.format("file-%03d.txt", i)).createNewFile());
            }
            for (int i = 0; i < 110; i++) {
                assertTrue(new File(root, String.format("dir-%03d", i)).mkdirs());
            }

            ProjectTreePager pager = new ProjectTreePager(100);
            ProjectTreePager.Page first = pager.page(root, 0);
            ProjectTreePager.Page second = pager.page(root, first.nextOffset);
            ProjectTreePager.Page third = pager.page(root, second.nextOffset);

            assertEquals(100, first.entries.size());
            assertEquals(100, second.entries.size());
            assertEquals(50, third.entries.size());
            assertTrue(first.hasMore);
            assertTrue(second.hasMore);
            assertFalse(third.hasMore);
            assertEquals(250, first.total);
            assertEquals(250, third.nextOffset);

            Set<String> names = new HashSet<>();
            for (ProjectTreePager.Page page : new ProjectTreePager.Page[]{first, second, third}) {
                for (ProjectTreePager.Entry entry : page.entries) {
                    assertTrue("duplicate row: " + entry.name, names.add(entry.name));
                }
            }
            assertEquals(250, names.size());
            assertTrue(first.entries.get(0).directory);
            assertTrue(second.entries.get(0).directory);
            assertFalse("110 folders fill the first two pages, so page three starts with a file",
                    third.entries.get(0).directory);
        } finally {
            delete(root);
        }
    }

    @Test
    public void missingFolderIsAnEmptyPage() {
        ProjectTreePager.Page page = new ProjectTreePager(50)
                .page(new File("/definitely/not/here"), 0);
        assertTrue(page.entries.isEmpty());
        assertFalse(page.hasMore);
        assertEquals(0, page.total);
    }

    private static void delete(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) delete(child);
        }
        file.delete();
    }
}
