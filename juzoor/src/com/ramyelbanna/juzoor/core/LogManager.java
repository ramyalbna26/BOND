package com.ramyelbanna.juzoor.core;

import android.content.Context;
import android.os.Environment;
import android.util.Log;
import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/* loaded from: /workspace/apk_extract/classes.dex */
public final class LogManager {
    public static final String DEBUG = "DEBUG";
    public static final String ERROR = "ERROR";
    public static final String INFO = "INFO";
    private static final int MAX = 4000;
    public static final String SUCCESS = "SUCCESS";
    public static final String WARN = "WARN";
    private static Context appContext;
    private static File logDir;
    private static final List<Entry> ENTRIES = new ArrayList();
    private static final SimpleDateFormat TS = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
    private static final SimpleDateFormat DAY = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private static boolean autoSave = true;

    private LogManager() {
    }

    public static synchronized void init(Context context) {
        synchronized (LogManager.class) {
            if (appContext == null) {
                appContext = context.getApplicationContext();
            }
            File file = new File(Environment.getExternalStorageDirectory(), "download/log/txt");
            logDir = file;
            if (!file.exists()) {
                logDir.mkdirs();
            }
            if (!logDir.canWrite()) {
                File file2 = new File(context.getExternalFilesDir(null), "log/txt");
                if (!file2.exists()) {
                    file2.mkdirs();
                }
                logDir = file2;
            }
        }
    }

    public static void setAutoSave(boolean z) {
        autoSave = z;
    }

    public static boolean isAutoSave() {
        return autoSave;
    }

    public static File getLogDir() {
        return logDir;
    }

    public static void i(String str) {
        add(INFO, str);
    }

    public static void d(String str) {
        add(DEBUG, str);
    }

    public static void w(String str) {
        add(WARN, str);
    }

    public static void e(String str) {
        add(ERROR, str);
    }

    public static void s(String str) {
        add(SUCCESS, str);
    }

    public static synchronized void add(String str, String str2) {
        synchronized (LogManager.class) {
            Entry entry = new Entry(System.currentTimeMillis(), str, str2);
            ENTRIES.add(entry);
            while (true) {
                List<Entry> list = ENTRIES;
                if (list.size() <= MAX) {
                    break;
                } else {
                    list.remove(0);
                }
            }
            Log.println(str.equals(ERROR) ? Log.ERROR : str.equals(WARN) ? Log.WARN : Log.INFO, "Juzoor", str + " " + str2);
            if (autoSave) {
                persist(entry);
            }
        }
    }

    private static void persist(Entry entry) {
        if (logDir == null) {
            return;
        }
        FileWriter fileWriter = null;
        try {
            try {
                FileWriter fileWriter2 = new FileWriter(new File(logDir, "juzoor-" + DAY.format(new Date(entry.time)) + ".txt"), true);
                try {
                    fileWriter2.write("[" + TS.format(new Date(entry.time)) + "] [" + pad(entry.level) + "] " + entry.message + "\n");
                    fileWriter2.close();
                } catch (Throwable unused) {
                    fileWriter = fileWriter2;
                    if (fileWriter != null) {
                        fileWriter.close();
                    }
                }
            } catch (Exception unused2) {
            }
        } catch (Throwable unused3) {
        }
    }

    private static String pad(String str) {
        StringBuilder sb = new StringBuilder(str);
        while (sb.length() < 7) {
            sb.append(' ');
        }
        return sb.toString();
    }

    public static synchronized List<Entry> all() {
        ArrayList arrayList;
        synchronized (LogManager.class) {
            arrayList = new ArrayList(ENTRIES);
        }
        return arrayList;
    }

    public static synchronized void clear() {
        synchronized (LogManager.class) {
            ENTRIES.clear();
        }
    }

    public static synchronized String dump() {
        String sb;
        synchronized (LogManager.class) {
            StringBuilder sb2 = new StringBuilder();
            for (Entry entry : ENTRIES) {
                sb2.append('[');
                sb2.append(TS.format(new Date(entry.time)));
                sb2.append("] [");
                sb2.append(pad(entry.level));
                sb2.append("] ");
                sb2.append(entry.message);
                sb2.append('\n');
            }
            sb = sb2.toString();
        }
        return sb;
    }

    public static synchronized int count(String str) {
        int i;
        synchronized (LogManager.class) {
            Iterator<Entry> it = ENTRIES.iterator();
            i = 0;
            while (it.hasNext()) {
                if (it.next().level.equals(str)) {
                    i++;
                }
            }
        }
        return i;
    }

    public static final class Entry {
        public final String level;
        public final String message;
        public final long time;

        Entry(long j, String str, String str2) {
            this.time = j;
            this.level = str;
            this.message = str2;
        }
    }
}
