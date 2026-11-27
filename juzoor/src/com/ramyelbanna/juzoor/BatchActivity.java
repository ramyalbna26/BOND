package com.ramyelbanna.juzoor;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.ramyelbanna.juzoor.core.LogManager;
import com.ramyelbanna.juzoor.engine.ConversionResult;
import com.ramyelbanna.juzoor.engine.SplitBundle;
import com.ramyelbanna.juzoor.ui.Ui;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Batch conversion queue with an independent progress bar for every app.
 * Two workers run in parallel; the queue is entirely in-process and offline.
 */
public class BatchActivity extends BaseActivity implements BatchQueue.Listener {

    private static final int REQ_PICK = 61;

    private LinearLayout holder;
    private TextView status;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable renderTick = new Runnable() {
        @Override
        public void run() {
            renderPending = false;
            render();
        }
    };
    private boolean renderPending;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        buildUi();
        BatchQueue.addListener(this);
    }

    private void buildUi() {
        this.body.removeAllViews();
        topBar(getString(R.string.batch_title), true);

        LinearLayout intro = Ui.card(this);
        intro.addView(Ui.text(this, getString(R.string.batch_sub), 13.0f, Ui.muted(), false));
        LinearLayout badges = Ui.row(this);
        badges.setPadding(0, Ui.dp(this, 10.0f), 0, 0);
        badges.addView(Ui.chip(this, getString(R.string.home_badge_offline), Ui.SUCCESS));
        badges.addView(Ui.spacer(this, 8));
        badges.addView(Ui.chip(this, getString(R.string.batch_worker), Ui.GOLD));
        intro.addView(badges);
        this.body.addView(intro);

        LinearLayout actions = Ui.row(this);
        LinearLayout.LayoutParams alp = Ui.lp(-1, -2);
        alp.topMargin = Ui.dp(this, 12.0f);
        this.body.addView(actions, alp);
        TextView add = Ui.button(this, getString(R.string.batch_add), true);
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                pickFiles();
            }
        });
        actions.addView(add, Ui.lp(0, -2, 1.0f));
        TextView start = Ui.button(this, getString(R.string.batch_start), false);
        LinearLayout.LayoutParams slp = Ui.lp(0, -2, 1.0f);
        slp.leftMargin = Ui.dp(this, 8.0f);
        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                BatchQueue.start(BatchActivity.this);
                scheduleRender();
            }
        });
        actions.addView(start, slp);

        LinearLayout actions2 = Ui.row(this);
        LinearLayout.LayoutParams a2lp = Ui.lp(-1, -2);
        a2lp.topMargin = Ui.dp(this, 8.0f);
        this.body.addView(actions2, a2lp);
        TextView clear = Ui.button(this, getString(R.string.batch_clear), false);
        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                BatchQueue.clearFinished();
            }
        });
        actions2.addView(clear, Ui.lp(0, -2, 1.0f));
        TextView cancel = Ui.button(this, getString(R.string.batch_cancel_all), false);
        LinearLayout.LayoutParams clp = Ui.lp(0, -2, 1.0f);
        clp.leftMargin = Ui.dp(this, 8.0f);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                BatchQueue.cancelAll();
                scheduleRender();
            }
        });
        actions2.addView(cancel, clp);

        this.status = Ui.text(this, "", 11.0f, Ui.GOLD, false);
        this.status.setPadding(0, Ui.dp(this, 10.0f), 0, 0);
        this.body.addView(this.status);

        this.holder = Ui.column(this);
        LinearLayout.LayoutParams hlp = Ui.lp(-1, -2);
        hlp.topMargin = Ui.dp(this, 6.0f);
        this.body.addView(this.holder, hlp);
        render();
    }

    private void pickFiles() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        try {
            startActivityForResult(intent, REQ_PICK);
        } catch (Throwable t) {
            toast(getString(R.string.batch_pick));
        }
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != REQ_PICK || result != RESULT_OK || data == null) return;
        final List<Uri> uris = new ArrayList<Uri>();
        if (data.getClipData() != null) {
            for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                uris.add(data.getClipData().getItemAt(i).getUri());
            }
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }
        if (uris.isEmpty()) return;
        status.setText(getString(R.string.batch_files, uris.size()));
        new Thread(new Runnable() {
            @Override
            public void run() {
                final int added = importFiles(uris);
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        status.setText(getString(R.string.batch_files, added));
                        scheduleRender();
                    }
                });
            }
        }, "juzoor-batch-import").start();
    }

    private int importFiles(List<Uri> uris) {
        int added = 0;
        File dir = new File(getCacheDir(), "incoming-batch");
        if (!dir.exists()) dir.mkdirs();
        for (Uri uri : uris) {
            String name = displayName(uri);
            if (name == null || name.isEmpty()) name = "application.apk";
            if (!SplitBundle.isSupported(name.toLowerCase(Locale.US))
                    || name.toLowerCase(Locale.US).endsWith(".aab")) {
                LogManager.w("Batch: unsupported file skipped — " + name);
                continue;
            }
            File out = new File(dir, System.currentTimeMillis() + "-" + name.replaceAll("[^A-Za-z0-9._-]", "_"));
            try {
                InputStream in = getContentResolver().openInputStream(uri);
                if (in == null) continue;
                FileOutputStream fos = new FileOutputStream(out);
                byte[] buffer = new byte[65536];
                int n;
                while ((n = in.read(buffer)) > 0) fos.write(buffer, 0, n);
                fos.close();
                in.close();
                if (BatchQueue.add(out) != null) added++;
            } catch (Throwable t) {
                LogManager.w("Batch: could not import " + name + ": " + t);
                out.delete();
            }
        }
        return added;
    }

    private String displayName(Uri uri) {
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return cursor.getString(index);
            }
        } catch (Throwable ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        return uri.getLastPathSegment();
    }

    private void scheduleRender() {
        synchronized (ui) {
            if (renderPending) return;
            renderPending = true;
            ui.removeCallbacks(renderTick);
            ui.postDelayed(renderTick, 180L);
        }
    }

    private void render() {
        if (holder == null) return;
        holder.removeAllViews();
        List<BatchQueue.Item> items = BatchQueue.items();
        if (items.isEmpty()) {
            TextView empty = Ui.text(this, getString(R.string.batch_empty), 13.0f, Ui.muted(), false);
            empty.setGravity(17);
            empty.setPadding(0, Ui.dp(this, 24.0f), 0, 0);
            holder.addView(empty);
            return;
        }
        for (final BatchQueue.Item item : items) {
            LinearLayout card = Ui.card(this);
            LinearLayout.LayoutParams lp = Ui.lp(-1, -2);
            lp.topMargin = Ui.dp(this, 8.0f);
            holder.addView(card, lp);

            LinearLayout head = Ui.row(this);
            head.addView(Ui.text(this, item.name, 14.0f, Ui.text(), true), Ui.lp(0, -2, 1.0f));
            head.addView(Ui.chip(this, statusLabel(item), statusColor(item)));
            card.addView(head);

            ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            bar.setMax(100);
            bar.setProgress(item.progress);
            LinearLayout.LayoutParams blp = Ui.lp(-1, Ui.dp(this, 7.0f));
            blp.topMargin = Ui.dp(this, 10.0f);
            card.addView(bar, blp);

            LinearLayout meta = Ui.row(this);
            meta.setPadding(0, Ui.dp(this, 6.0f), 0, 0);
            String detail = item.finished() ? item.step : (item.progress + "%  ·  " + item.step);
            meta.addView(Ui.text(this, detail == null ? "" : detail, 11.0f, Ui.muted(), false), Ui.lp(0, -2, 1.0f));
            if (!item.finished() && item.currentFile != null && !item.currentFile.isEmpty()) {
                meta.addView(Ui.text(this, trim(item.currentFile, 28), 10.0f, Ui.GOLD, false));
            }
            card.addView(meta);

            if (item.status == BatchQueue.DONE && item.result != null) {
                TextView open = Ui.button(this, getString(R.string.batch_open_result), false);
                LinearLayout.LayoutParams olp = Ui.lp(-1, -2);
                olp.topMargin = Ui.dp(this, 8.0f);
                open.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        ConversionResult result = item.result;
                        if (result == null) return;
                        ConversionService.setLastResult(result);
                        startActivity(new Intent(BatchActivity.this, ResultActivity.class));
                    }
                });
                card.addView(open, olp);
            }
        }
    }

    private String statusLabel(BatchQueue.Item item) {
        if (item.status == BatchQueue.RUNNING) return getString(R.string.batch_running);
        if (item.status == BatchQueue.DONE) return getString(R.string.batch_done);
        if (item.status == BatchQueue.FAILED) return getString(R.string.batch_failed);
        if (item.status == BatchQueue.CANCELLED) return getString(R.string.batch_cancelled);
        return getString(R.string.batch_queued);
    }

    private int statusColor(BatchQueue.Item item) {
        if (item.status == BatchQueue.RUNNING) return Ui.GOLD;
        if (item.status == BatchQueue.DONE) return Ui.SUCCESS;
        if (item.status == BatchQueue.FAILED) return Ui.ERROR;
        if (item.status == BatchQueue.CANCELLED) return Ui.WARNING;
        return Ui.muted();
    }

    private static String trim(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : "…" + s.substring(s.length() - max + 1);
    }

    @Override
    public void onBatchChanged() {
        scheduleRender();
    }

    @Override
    protected void onDestroy() {
        BatchQueue.removeListener(this);
        ui.removeCallbacks(renderTick);
        super.onDestroy();
    }
}
