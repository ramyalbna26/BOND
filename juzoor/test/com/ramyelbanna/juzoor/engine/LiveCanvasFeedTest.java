package com.ramyelbanna.juzoor.engine;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LiveCanvasFeedTest {

    @Test
    public void keepsOnlyTheNewestRowsInMemory() {
        LiveCanvasFeed feed = new LiveCanvasFeed(5);
        for (int i = 0; i < 9; i++) feed.addLog("INFO", "line " + i);

        List<LiveCanvasFeed.Entry> rows = feed.snapshot();
        assertEquals(5, rows.size());
        assertEquals("line 4", rows.get(0).text);
        assertEquals("line 8", rows.get(4).text);
        assertEquals(4, feed.dropped());
    }

    @Test
    public void defaultCapIsThreeHundredRows() {
        LiveCanvasFeed feed = new LiveCanvasFeed();
        for (int i = 0; i < 500; i++) feed.addFile("src/Class" + i + ".java", 50);
        assertEquals(LiveCanvasFeed.MAX_ROWS, feed.size());
        assertEquals(200, feed.dropped());
        assertTrue(feed.snapshot().get(feed.size() - 1).text.endsWith("Class499.java"));
    }

    @Test
    public void repeatedFileUpdateReusesOneRow() {
        LiveCanvasFeed feed = new LiveCanvasFeed(10);
        feed.addFile("classes.dex → Java", 87);
        feed.addFile("classes.dex → Java", 88);
        feed.addFile("classes.dex → Java", 89);

        List<LiveCanvasFeed.Entry> rows = feed.snapshot();
        assertEquals(1, rows.size());
        assertEquals(89, rows.get(0).percent);
        assertEquals(LiveCanvasFeed.Kind.FILE, rows.get(0).kind);
    }

    @Test
    public void sameFileAfterAnotherRowIsKeptSeparately() {
        LiveCanvasFeed feed = new LiveCanvasFeed(10);
        feed.addFile("classes.dex", 1);
        feed.addLog("INFO", "between");
        feed.addFile("classes.dex", 2);

        assertEquals(3, feed.snapshot().size());
    }

    @Test
    public void searchFiltersPathsAndLogsCaseInsensitively() {
        LiveCanvasFeed feed = new LiveCanvasFeed(10);
        feed.addFile("src/main/java/MyClass.java", 10);
        feed.addLog("ERROR", "Failed to decode resources");
        feed.addFile("assets/flutter_assets/AssetManifest.json", 20);

        List<LiveCanvasFeed.Entry> paths = feed.matching("JAVA");
        assertEquals(1, paths.size());
        assertEquals("src/main/java/MyClass.java", paths.get(0).text);

        List<LiveCanvasFeed.Entry> logs = feed.matching("decode");
        assertEquals(1, logs.size());
        assertEquals(LiveCanvasFeed.Kind.LOG, logs.get(0).kind);

        assertEquals(3, feed.matching("").size());
        assertEquals(0, feed.matching("missing").size());
    }

    @Test
    public void emptyValuesAreIgnored() {
        LiveCanvasFeed feed = new LiveCanvasFeed(10);
        feed.addFile("", 1);
        feed.addFile(null, 1);
        feed.addLog("INFO", "");
        feed.addLog("INFO", null);
        assertEquals(0, feed.size());
    }
}
