package com.ramyelbanna.juzoor;

import android.content.Context;

import com.ramyelbanna.juzoor.core.LogManager;
import com.ramyelbanna.juzoor.engine.ConversionEngine;
import com.ramyelbanna.juzoor.engine.ConversionOptions;
import com.ramyelbanna.juzoor.engine.ConversionResult;
import com.ramyelbanna.juzoor.engine.LogSink;
import com.ramyelbanna.juzoor.engine.ZipUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Two-worker conversion queue used by {@link BatchActivity}.  Each item gets
 * its own private work directory, progress and result, so several applications
 * can be processed at the same time without sharing state.
 */
public final class BatchQueue {

    public static final int QUEUED = 0;
    public static final int RUNNING = 1;
    public static final int DONE = 2;
    public static final int FAILED = 3;
    public static final int CANCELLED = 4;

    public interface Listener {
        void onBatchChanged();
    }

    public static final class Item {
        public final File input;
        public final String name;
        public volatile int status = QUEUED;
        public volatile int progress;
        public volatile String step = "";
        public volatile String currentFile = "";
        public volatile boolean cancelled;
        public volatile ConversionResult result;
        public volatile long startedAt;

        Item(File input) {
            this.input = input;
            this.name = input.getName();
        }

        public boolean finished() {
            return status == DONE || status == FAILED || status == CANCELLED;
        }
    }

    private static final List<Item> ITEMS = new ArrayList<Item>();
    private static final List<Listener> LISTENERS = new ArrayList<Listener>();
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2, new ThreadFactory() {
        private final AtomicInteger seq = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "juzoor-batch-" + seq.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    });

    private static Context appContext;
    private static boolean running;
    private static final AtomicInteger SEQUENCE = new AtomicInteger(1);

    private BatchQueue() {
    }

    public static synchronized void clearFinished() {
        for (int i = ITEMS.size() - 1; i >= 0; i--) {
            if (ITEMS.get(i).finished()) ITEMS.remove(i);
        }
        notifyChanged();
    }

    public static synchronized List<Item> items() {
        return new ArrayList<Item>(ITEMS);
    }

    public static synchronized boolean isRunning() {
        return running;
    }

    public static synchronized int activeCount() {
        int n = 0;
        for (Item item : ITEMS) {
            if (item.status == QUEUED || item.status == RUNNING) n++;
        }
        return n;
    }

    public static synchronized Item add(File file) {
        if (file == null || !file.isFile()) return null;
        for (Item item : ITEMS) {
            if (item.input.getAbsolutePath().equals(file.getAbsolutePath()) && !item.finished()) return item;
        }
        Item item = new Item(file);
        ITEMS.add(item);
        notifyChanged();
        return item;
    }

    public static synchronized void addAll(List<File> files) {
        for (File file : files) add(file);
    }

    public static synchronized void start(Context context) {
        if (context != null) appContext = context.getApplicationContext();
        if (appContext == null || running) return;
        boolean any = false;
        for (Item item : ITEMS) {
            if (item.status == QUEUED) any = true;
        }
        if (!any) return;
        running = true;
        for (final Item item : new ArrayList<Item>(ITEMS)) {
            if (item.status != QUEUED) continue;
            EXECUTOR.execute(new Runnable() {
                @Override
                public void run() {
                    process(item);
                }
            });
        }
        notifyChanged();
    }

    public static synchronized void cancelAll() {
        for (Item item : ITEMS) {
            if (!item.finished()) {
                item.cancelled = true;
                if (item.status == QUEUED) item.status = CANCELLED;
            }
        }
        notifyChanged();
    }

    public static synchronized void addListener(Listener listener) {
        if (listener != null && !LISTENERS.contains(listener)) LISTENERS.add(listener);
    }

    public static synchronized void removeListener(Listener listener) {
        LISTENERS.remove(listener);
    }

    private static void process(final Item item) {
        item.status = RUNNING;
        item.startedAt = System.currentTimeMillis();
        item.progress = 0;
        item.step = "Preparing…";
        notifyChanged();
        try {
            runItem(item);
        } catch (Throwable t) {
            item.status = FAILED;
            item.step = String.valueOf(t);
            LogManager.e("[" + item.name + "] " + t);
        }
        synchronized (BatchQueue.class) {
            boolean active = false;
            for (Item i : ITEMS) {
                if (i.status == QUEUED || i.status == RUNNING) active = true;
            }
            if (!active) running = false;
        }
        notifyChanged();
    }

    private static void runItem(final Item item) {
        File work = new File(appContext.getCacheDir(), "batch/" + SEQUENCE.getAndIncrement() + "-" + sanitize(item.name));
        ZipUtil.deleteRecursive(work);
        work.mkdirs();
        ConversionResult result = new ConversionEngine(item.input, work,
                ConversionOptions.fromPrefs(), new LogSink() {
                    @Override
                    public boolean isCancelled() {
                        return item.cancelled;
                    }

                    @Override
                    public void log(String level, String message) {
                        if (LogManager.ERROR.equals(level)) LogManager.e("[" + item.name + "] " + message);
                        else if (LogManager.WARN.equals(level)) LogManager.w("[" + item.name + "] " + message);
                        else if (LogManager.SUCCESS.equals(level)) LogManager.s("[" + item.name + "] " + message);
                        else if (LogManager.DEBUG.equals(level)) LogManager.d("[" + item.name + "] " + message);
                        else LogManager.i("[" + item.name + "] " + message);
                        notifyChanged();
                    }

                    @Override
                    public void progress(int percent, String step) {
                        item.progress = percent;
                        item.step = step;
                        notifyChanged();
                    }

                    @Override
                    public void file(String path, int percent) {
                        item.currentFile = path;
                        notifyChanged();
                    }
                }).run();
        item.result = result;
        if (item.cancelled) {
            item.status = CANCELLED;
        } else if (result != null && result.success) {
            item.status = DONE;
            item.progress = 100;
            item.step = result.classCount + " classes · " + result.fileCount + " files";
        } else {
            item.status = FAILED;
            item.step = result == null ? "failed" : (result.error == null ? "failed" : result.error);
        }
    }

    private static void notifyChanged() {
        List<Listener> copy;
        synchronized (BatchQueue.class) {
            copy = new ArrayList<Listener>(LISTENERS);
        }
        for (Listener listener : copy) {
            try {
                listener.onBatchChanged();
            } catch (Throwable ignored) {
            }
        }
    }

    private static String sanitize(String name) {
        return name == null ? "app" : name.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
