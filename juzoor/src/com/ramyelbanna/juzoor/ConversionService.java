package com.ramyelbanna.juzoor;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import com.ramyelbanna.juzoor.core.LogManager;
import com.ramyelbanna.juzoor.core.Prefs;
import com.ramyelbanna.juzoor.engine.ConversionEngine;
import com.ramyelbanna.juzoor.engine.ConversionOptions;
import com.ramyelbanna.juzoor.engine.ConversionResult;
import com.ramyelbanna.juzoor.engine.LogSink;
import com.ramyelbanna.juzoor.engine.ZipUtil;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/* loaded from: /workspace/apk_extract/classes.dex */
public class ConversionService extends Service {
    public static final String ACTION_CANCEL = "com.ramyelbanna.juzoor.CANCEL";
    public static final String ACTION_START = "com.ramyelbanna.juzoor.START";
    private static final String CHANNEL = "juzoor_work";
    public static final String EXTRA_PATH = "path";
    private static final int NOTIF_ID = 41;
    private static volatile boolean cancelled;
    private static volatile boolean paused;
    private static volatile String currentFile;
    private static volatile int fileCount;
    private static volatile ConversionResult lastResult;
    private static volatile int progress;
    private static volatile boolean running;
    private static final List<Listener> LISTENERS = new ArrayList();
    private static volatile String step = "";

    public interface Listener {
        void onFinished(ConversionResult conversionResult);

        void onLog(String str, String str2);

        void onProgress(int i, String str);

        /** A new file entered the live canvas. */
        default void onFile(String path, int percent) {
        }
    }

    @Override // android.app.Service
    public IBinder onBind(Intent intent) {
        return null;
    }

    public static boolean isRunning() {
        return running;
    }

    public static int getProgress() {
        return progress;
    }

    public static String getStep() {
        return step;
    }

    public static String getCurrentFile() {
        return currentFile;
    }

    public static int getFileCount() {
        return fileCount;
    }

    public static boolean isPaused() {
        return paused;
    }

    public static void pause() {
        paused = true;
        LogManager.w("Conversion paused by user");
    }

    public static void resume() {
        paused = false;
        LogManager.i("Conversion resumed by user");
    }

    private static void checkpointWait() {
        while (paused) {
            if (cancelled) return;
            try {
                Thread.sleep(100L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    public static ConversionResult getLastResult() {
        return lastResult;
    }

    /** Lets the batch queue reuse the existing result screen. */
    public static void setLastResult(ConversionResult result) {
        lastResult = result;
    }

    public static void addListener(Listener listener) {
        List<Listener> list = LISTENERS;
        synchronized (list) {
            if (!list.contains(listener)) {
                list.add(listener);
            }
        }
    }

    public static void removeListener(Listener listener) {
        List<Listener> list = LISTENERS;
        synchronized (list) {
            list.remove(listener);
        }
    }

    public static void cancel() {
        cancelled = true;
        paused = false;
        LogManager.w("Cancel requested by user");
    }

    @Override // android.app.Service
    public int onStartCommand(Intent intent, int i, int i2) {
        if (intent != null && ACTION_CANCEL.equals(intent.getAction())) {
            cancel();
            return START_NOT_STICKY;
        }
        if (running) {
            return START_NOT_STICKY;
        }
        final String stringExtra = intent == null ? null : intent.getStringExtra(EXTRA_PATH);
        if (stringExtra == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        running = true;
        cancelled = false;
        paused = false;
        fileCount = 0;
        progress = 0;
        step = getString(R.string.progress_wait);
        lastResult = null;
        currentFile = new File(stringExtra).getName();
        startForeground(NOTIF_ID, buildNotification(0, getString(R.string.progress_wait)));
        new Thread(new Runnable() { // from class: com.ramyelbanna.juzoor.ConversionService$$ExternalSyntheticLambda0
            @Override // java.lang.Runnable
            public final void run() {
                ConversionService.this.lambda$onStartCommand$0(stringExtra);
            }
        }, "sf-convert").start();
        return START_NOT_STICKY;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onStartCommand$0(String str) {
        runConversion(new File(str));
    }

    private void runConversion(File file) {
        ConversionResult conversionResult;
        ConversionOptions conversionOptions = ConversionOptions.fromPrefs();
        File file2 = new File(getCacheDir(), "work");
        ZipUtil.deleteRecursive(file2);
        file2.mkdirs();
        try {
            conversionResult = new ConversionEngine(file, file2, conversionOptions, new LogSink() { // from class: com.ramyelbanna.juzoor.ConversionService.1
                @Override // com.ramyelbanna.juzoor.engine.LogSink
                public void log(String str, String str2) {
                    if (LogManager.ERROR.equals(str)) {
                        LogManager.e(str2);
                    } else if (LogManager.WARN.equals(str)) {
                        LogManager.w(str2);
                    } else if (LogManager.SUCCESS.equals(str)) {
                        LogManager.s(str2);
                    } else if (LogManager.DEBUG.equals(str)) {
                        LogManager.d(str2);
                    } else {
                        LogManager.i(str2);
                    }
                    synchronized (ConversionService.LISTENERS) {
                        Iterator it = new ArrayList(ConversionService.LISTENERS).iterator();
                        while (it.hasNext()) {
                            ((Listener) it.next()).onLog(str, str2);
                        }
                    }
                }

                @Override // com.ramyelbanna.juzoor.engine.LogSink
                public boolean isCancelled() {
                    return ConversionService.cancelled;
                }

                @Override // com.ramyelbanna.juzoor.engine.LogSink
                public boolean isPaused() {
                    return ConversionService.paused;
                }

                @Override // com.ramyelbanna.juzoor.engine.LogSink
                public void file(String path, int pct) {
                    if (path == null || path.isEmpty()) return;
                    checkpointWait();
                    if (ConversionService.cancelled) return;
                    ConversionService.currentFile = path;
                    ConversionService.fileCount++;
                    synchronized (ConversionService.LISTENERS) {
                        Iterator it = new ArrayList(ConversionService.LISTENERS).iterator();
                        while (it.hasNext()) {
                            ((Listener) it.next()).onFile(path, pct);
                        }
                    }
                }

                @Override // com.ramyelbanna.juzoor.engine.LogSink
                public void progress(int i, String str) {
                    ConversionService.progress = i;
                    ConversionService.step = str;
                    ConversionService.this.updateNotification(i, str);
                    synchronized (ConversionService.LISTENERS) {
                        Iterator it = new ArrayList(ConversionService.LISTENERS).iterator();
                        while (it.hasNext()) {
                            ((Listener) it.next()).onProgress(i, str);
                        }
                    }
                }
            }).run();
        } catch (Throwable th) {
            ConversionResult conversionResult2 = new ConversionResult();
            conversionResult2.error = String.valueOf(th);
            LogManager.e("Fatal: " + String.valueOf(th));
            conversionResult = conversionResult2;
        }
        lastResult = conversionResult;
        if (conversionResult.success) {
            Prefs.bumpConversions();
        }
        List<Listener> list = LISTENERS;
        synchronized (list) {
            Iterator it = new ArrayList(list).iterator();
            while (it.hasNext()) {
                ((Listener) it.next()).onFinished(conversionResult);
            }
        }
        running = false;
        paused = false;
        if (Prefs.notifications()) {
            notifyDone(conversionResult);
        }
        stopForeground(true);
        stopSelf();
    }

    private Notification buildNotification(int i, String str) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel notificationChannel = new NotificationChannel(CHANNEL, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW);
            notificationChannel.setShowBadge(false);
            notificationManager.createNotificationChannel(notificationChannel);
        }
        PendingIntent activity = PendingIntent.getActivity(this, 0, new Intent(this, (Class<?>) LiveCanvasActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        builder.setContentTitle(getString(R.string.notif_title)).setContentText(str).setSmallIcon(android.R.drawable.stat_sys_download).setOngoing(true).setContentIntent(activity);
        builder.setProgress(100, i, i <= 0);
        return builder.build();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void updateNotification(int i, String str) {
        try {
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(NOTIF_ID, buildNotification(i, str));
        } catch (Throwable unused) {
        }
    }

    private void notifyDone(ConversionResult conversionResult) {
        String valueOf;
        try {
            NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= 26) {
                notificationManager.createNotificationChannel(new NotificationChannel(CHANNEL, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_DEFAULT));
            }
            PendingIntent activity = PendingIntent.getActivity(this, 1, new Intent(this, (Class<?>) ResultActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification.Builder builder = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
            Notification.Builder contentTitle = builder.setContentTitle(conversionResult.success ? getString(R.string.result_success) : getString(R.string.result_failed));
            if (conversionResult.success) {
                valueOf = conversionResult.classCount + " " + getString(R.string.result_classes);
            } else {
                valueOf = String.valueOf(conversionResult.error);
            }
            contentTitle.setContentText(valueOf).setSmallIcon(android.R.drawable.stat_sys_download_done).setAutoCancel(true).setContentIntent(activity);
            notificationManager.notify(42, builder.build());
        } catch (Throwable unused) {
        }
    }
}
