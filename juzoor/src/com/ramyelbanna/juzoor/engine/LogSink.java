package com.ramyelbanna.juzoor.engine;

/* loaded from: /workspace/apk_extract/classes.dex */
public interface LogSink {
    boolean isCancelled();

    void log(String str, String str2);

    void progress(int i, String str);

    /**
     * Reports a concrete file that is currently being processed. Screens use
     * this for the live extraction stream and per-file progress. {@code percent}
     * is -1 when the file has no measurable sub-progress.
     */
    default void file(String path, int percent) {
    }

    /** True when the user paused the running conversion. */
    default boolean isPaused() {
        return false;
    }
}
