package com.ramyelbanna.juzoor.core;

import android.content.ClipData;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Tiny, dependency-free file provider used to share generated APKs.
 *
 * <p>Files are staged inside {@code cache/export} and exposed through
 * {@code content://com.ramyelbanna.juzoor.files/export/<name>} with a temporary
 * read grant, which keeps Android 7+ happy without pulling in AndroidX.</p>
 */
public class JuzoorFiles extends ContentProvider {

    public static final String AUTHORITY = "com.ramyelbanna.juzoor.files";
    private static final String PREFIX = "export";

    public static Uri stage(Context context, File source) throws IOException {
        File dir = new File(context.getCacheDir(), PREFIX);
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("cannot create export dir");
        File target = new File(dir, source.getName());
        if (!target.equals(source)) {
            FileInputStream in = new FileInputStream(source);
            FileOutputStream out = new FileOutputStream(target);
            byte[] buf = new byte[65536];
            int n;
            try {
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            } finally {
                in.close();
                out.close();
            }
        }
        return new Uri.Builder().scheme("content").authority(AUTHORITY)
                .appendPath(PREFIX).appendPath(target.getName()).build();
    }

    public static Intent shareIntent(Context context, File apk, String chooserTitle) throws IOException {
        return shareIntent(context, apk, chooserTitle, mimeFor(apk.getName()));
    }

    /**
     * Builds a robust ACTION_SEND chooser. The URI grant is propagated through
     * {@link Intent#setClipData(ClipData)} as well as {@code EXTRA_STREAM}, and
     * is explicitly granted to every resolved target — this is what prevents
     * the {@code RuntimeException: Failure from system} seen on some OEM ROMs
     * where the chooser drops the flag when only EXTRA_STREAM is set.
     */
    public static Intent shareIntent(Context context, File file, String chooserTitle, String mime)
            throws IOException {
        Uri uri = stage(context, file);
        String type = (mime == null || mime.isEmpty()) ? mimeFor(file.getName()) : mime;

        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType(type);
        send.putExtra(Intent.EXTRA_STREAM, uri);
        send.putExtra(Intent.EXTRA_TITLE, file.getName());
        send.setClipData(ClipData.newRawUri(file.getName(), uri));
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        // Pre-grant to every app that can handle the intent so the receiving
        // process can read the staged file even if the chooser re-wraps it.
        try {
            java.util.List<android.content.pm.ResolveInfo> targets =
                    context.getPackageManager().queryIntentActivities(send, 0);
            for (android.content.pm.ResolveInfo ri : targets) {
                if (ri.activityInfo != null && ri.activityInfo.packageName != null) {
                    context.grantUriPermission(ri.activityInfo.packageName, uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION);
                }
            }
        } catch (Throwable ignored) {
            // Best-effort; the chooser grant still applies.
        }

        Intent chooser = Intent.createChooser(send, chooserTitle);
        chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        chooser.setClipData(ClipData.newRawUri(file.getName(), uri));
        return chooser;
    }

    /** Best-effort MIME type from a file name. */
    public static String mimeFor(String name) {
        if (name == null) return "application/octet-stream";
        String n = name.toLowerCase(java.util.Locale.US);
        if (n.endsWith(".apk")) return "application/vnd.android.package-archive";
        if (n.endsWith(".zip") || n.endsWith(".apks") || n.endsWith(".xapk")
                || n.endsWith(".apkm") || n.endsWith(".aab")) return "application/zip";
        if (n.endsWith(".md") || n.endsWith(".txt") || n.endsWith(".json")
                || n.endsWith(".smali") || n.endsWith(".java") || n.endsWith(".kt")) {
            return "text/plain";
        }
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        return "application/octet-stream";
    }

    private File resolve(Uri uri) {
        if (uri == null || uri.getPathSegments().size() < 2) return null;
        if (!PREFIX.equals(uri.getPathSegments().get(0))) return null;
        String name = uri.getPathSegments().get(1);
        if (name.contains("/") || name.contains("..")) return null;
        return new File(new File(getContext().getCacheDir(), PREFIX), name);
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        File file = resolve(uri);
        if (file == null || !file.isFile()) return null;
        String[] cols = projection != null ? projection
                : new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        MatrixCursor cursor = new MatrixCursor(cols, 1);
        Object[] row = new Object[cols.length];
        for (int i = 0; i < cols.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(cols[i])) row[i] = file.getName();
            else if (OpenableColumns.SIZE.equals(cols[i])) row[i] = file.length();
            else row[i] = null;
        }
        cursor.addRow(row);
        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        File file = resolve(uri);
        return mimeFor(file == null ? null : file.getName());
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws java.io.FileNotFoundException {
        File file = resolve(uri);
        if (file == null || !file.isFile()) throw new java.io.FileNotFoundException(String.valueOf(uri));
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }
}
