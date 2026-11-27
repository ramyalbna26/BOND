package com.ramyelbanna.juzoor;

import android.content.Context;
import android.os.Build;
import android.os.Debug;

import com.ramyelbanna.juzoor.core.LogManager;
import com.ramyelbanna.juzoor.core.Prefs;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Offline anti-tampering checks for Juzoor itself.
 *
 * <p>The checks are intentionally local and cheap: a connected debugger, an
 * instrumentation framework mapped into this process, or an emulator.  When a
 * signal is found the caller must show {@link SecurityWarningActivity} and stop
 * the app.  No data leaves the device: the incident is written to the Juzoor
 * log directory and to the platform log.</p>
 */
public final class SecurityGuard {

    private static final String[] HOOK_MARKERS = {
            "frida", "gum-js-loop", "gadget", "linjector", "xposed",
            "lsposed", "substrate", "edxp", "riru", "zygisk"
    };

    private static final String[] EMULATOR_MARKERS = {
            "goldfish", "ranchu", "vbox86", "genymotion", "google_sdk", "sdk_gphone",
            "emulator", "qemu"
    };

    private SecurityGuard() {
    }

    /**
     * Returns a human readable reason when the current process looks tampered
     * with, or {@code null} when everything is clean.
     */
    public static String inspect(Context context) {
        // Debug builds are the only intentional test surface; the shipped
        // release package is protected normally.
        if (context.getPackageName().endsWith(".debug")) return null;
        if (Debug.isDebuggerConnected() || Debug.waitingForDebugger()) {
            return "debugger";
        }
        String maps = readText(new File("/proc/self/maps"));
        if (maps != null) {
            String lower = maps.toLowerCase(Locale.US);
            for (String marker : HOOK_MARKERS) {
                if (lower.contains(marker)) return "hook:" + marker;
            }
        }
        if (isEmulator()) return "emulator";
        return null;
    }

    private static boolean isEmulator() {
        String fp = lower(Build.FINGERPRINT);
        String model = lower(Build.MODEL);
        String hardware = lower(Build.HARDWARE);
        String product = lower(Build.PRODUCT);
        String brand = lower(Build.BRAND);
        String device = lower(Build.DEVICE);
        String board = lower(Build.BOARD);
        String combined = fp + " " + model + " " + hardware + " " + product + " "
                + brand + " " + device + " " + board;
        for (String marker : EMULATOR_MARKERS) {
            if (combined.contains(marker)) return true;
        }
        // QEMU exposes its own kernel marker on most emulator images.
        String qemu = readText(new File("/proc/cpuinfo"));
        return qemu != null && lower(qemu).contains("qemu");
    }

    private static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.US);
    }

    /** Reads a small proc/text file without following symlinks. */
    private static String readText(File file) {
        if (file == null || !file.isFile() || file.length() > 4L * 1024 * 1024) return null;
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
            StringBuilder sb = new StringBuilder(4096);
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
                if (sb.length() > 1024 * 1024) break;
            }
            reader.close();
            return sb.toString();
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Persists a local incident report and mirrors it to LogManager. */
    public static File record(Context context, String reason) {
        String stamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
        String body = "Juzoor security incident\n"
                + "Time: " + stamp + "\n"
                + "Reason: " + reason + "\n"
                + "Package: " + context.getPackageName() + "\n"
                + "Version: " + Prefs.VERSION + "\n"
                + "Device: " + Build.MANUFACTURER + " " + Build.MODEL + "\n"
                + "Fingerprint: " + Build.FINGERPRINT + "\n"
                + "Android: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")\n"
                + "© 2027 Ramy El-Banna — all rights reserved.\n";
        File out = null;
        try {
            File dir = LogManager.getLogDir();
            if (dir == null) dir = context.getCacheDir();
            if (!dir.exists()) dir.mkdirs();
            out = new File(dir, "security-alert-" + System.currentTimeMillis() + ".txt");
            FileOutputStream fos = new FileOutputStream(out);
            fos.write(body.getBytes("UTF-8"));
            fos.close();
        } catch (Throwable ignored) {
        }
        LogManager.e("SECURITY ALERT: " + reason + " — " + body.replace('\n', ' '));
        return out;
    }
}
