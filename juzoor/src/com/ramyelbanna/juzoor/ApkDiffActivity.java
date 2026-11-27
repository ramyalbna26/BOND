package com.ramyelbanna.juzoor;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.ramyelbanna.juzoor.core.JuzoorFiles;
import com.ramyelbanna.juzoor.engine.ApkDiff;
import com.ramyelbanna.juzoor.engine.ZipUtil;
import com.ramyelbanna.juzoor.ui.Ui;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Compares an old and a new APK locally. */
public class ApkDiffActivity extends BaseActivity {

    private static final int REQ_OLD = 61;
    private static final int REQ_NEW = 62;
    private File older;
    private File newer;
    private TextView oldLabel;
    private TextView newLabel;
    private TextView summary;
    private DiffAdapter adapter;
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        buildUi();
    }

    private void buildUi() {
        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.bg());
        int pad = Ui.dp(this, 14.0f);
        root.setPadding(pad, Ui.dp(this, 12.0f), pad, Ui.dp(this, 8.0f));

        LinearLayout bar = Ui.row(this);
        TextView back = Ui.text(this, "‹", 30.0f, Ui.GOLD, true);
        back.setPadding(Ui.dp(this, 4.0f), 0, Ui.dp(this, 12.0f), 0);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
        bar.addView(back);
        bar.addView(Ui.text(this, getString(R.string.diff_title), 17.0f, Ui.text(), true),
                Ui.lp(0, -2, 1.0f));
        TextView share = Ui.chip(this, getString(R.string.common_share), Ui.GOLD);
        share.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                share();
            }
        });
        bar.addView(share);
        root.addView(bar);

        TextView intro = Ui.text(this, getString(R.string.diff_sub), 12.0f, Ui.muted(), false);
        intro.setPadding(0, Ui.dp(this, 4.0f), 0, Ui.dp(this, 10.0f));
        root.addView(intro);

        this.oldLabel = fileRow(root, getString(R.string.diff_old), REQ_OLD);
        this.newLabel = fileRow(root, getString(R.string.diff_new), REQ_NEW);

        LinearLayout actions = Ui.row(this);
        this.summary = Ui.text(this, "", 11.0f, Ui.muted(), false);
        actions.addView(this.summary, Ui.lp(0, -2, 1.0f));
        TextView run = Ui.button(this, getString(R.string.diff_run), true);
        run.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                run();
            }
        });
        actions.addView(run);
        LinearLayout.LayoutParams alp = Ui.lp(-1, -2);
        alp.topMargin = Ui.dp(this, 12.0f);
        root.addView(actions, alp);

        ListView list = new ListView(this);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setCacheColorHint(0);
        list.setBackgroundColor(Ui.bg());
        this.adapter = new DiffAdapter();
        list.setAdapter(adapter);
        root.addView(list, Ui.lp(-1, 0, 1.0f));
        setContentView(root);
    }

    private TextView fileRow(LinearLayout root, String label, final int request) {
        LinearLayout card = Ui.card(this);
        LinearLayout row = Ui.row(this);
        row.addView(Ui.text(this, label, 12.0f, Ui.GOLD, true), Ui.lp(0, -2, 1.0f));
        TextView button = Ui.chip(this, getString(R.string.diff_pick), Ui.GOLD);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(ApkDiffActivity.this, FileBrowserActivity.class);
                intent.putExtra("pick", true);
                intent.putExtra("all", true);
                startActivityForResult(intent, request);
            }
        });
        row.addView(button);
        card.addView(row);
        TextView value = Ui.text(this, getString(R.string.diff_not_selected), 10.0f, Ui.muted(), false);
        value.setPadding(0, Ui.dp(this, 5.0f), 0, 0);
        card.addView(value);
        LinearLayout.LayoutParams lp = Ui.lp(-1, -2);
        lp.topMargin = Ui.dp(this, 8.0f);
        root.addView(card, lp);
        return value;
    }

    private void run() {
        if (older == null || newer == null) {
            summary.setText(getString(R.string.diff_need_files));
            return;
        }
        summary.setText(getString(R.string.diff_running));
        new Thread(new Runnable() {
            @Override
            public void run() {
                final ApkDiff.Report report = ApkDiff.diff(older, newer, null);
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        if (report.error != null) {
                            summary.setText(report.error);
                            return;
                        }
                        adapter.setReport(report);
                        summary.setText(getString(R.string.diff_summary, report.added.size(),
                                report.removed.size(), report.modified.size()));
                    }
                });
            }
        }, "juzoor-diff").start();
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null) return;
        String path = data.getStringExtra(ConversionService.EXTRA_PATH);
        if (path == null) return;
        if (request == REQ_OLD) {
            older = new File(path);
            oldLabel.setText(older.getAbsolutePath());
        } else if (request == REQ_NEW) {
            newer = new File(path);
            newLabel.setText(newer.getAbsolutePath());
        }
    }

    private void share() {
        try {
            ApkDiff.Report report = adapter.report;
            if (report == null) {
                toast(getString(R.string.diff_need_files));
                return;
            }
            File out = new File(getCacheDir(), "juzoor-apk-diff.txt");
            ZipUtil.writeFile(out, ApkDiff.toText(report));
            startActivity(Intent.createChooser(
                    JuzoorFiles.shareIntent(this, out, getString(R.string.diff_title)),
                    getString(R.string.diff_title)));
        } catch (Throwable t) {
            toast(getString(R.string.err_save));
        }
    }

    private final class DiffAdapter extends BaseAdapter {
        ApkDiff.Report report;
        final List<ApkDiff.Entry> rows = new ArrayList<>();

        void setReport(ApkDiff.Report report) {
            this.report = report;
            rows.clear();
            for (ApkDiff.Entry e : report.added) rows.add(e);
            for (ApkDiff.Entry e : report.removed) rows.add(e);
            for (ApkDiff.Entry e : report.modified) rows.add(e);
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return rows.size();
        }

        @Override
        public Object getItem(int position) {
            return rows.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout row;
            TextView name;
            TextView meta;
            if (convertView == null) {
                row = Ui.row(ApkDiffActivity.this);
                row.setPadding(Ui.dp(ApkDiffActivity.this, 10.0f),
                        Ui.dp(ApkDiffActivity.this, 8.0f),
                        Ui.dp(ApkDiffActivity.this, 10.0f),
                        Ui.dp(ApkDiffActivity.this, 8.0f));
                row.setBackground(Ui.round(Ui.surface(), Ui.dp(ApkDiffActivity.this, 8.0f)));
                LinearLayout.LayoutParams lp = Ui.lp(-1, -2);
                lp.topMargin = Ui.dp(ApkDiffActivity.this, 4.0f);
                row.setLayoutParams(lp);
                name = Ui.text(ApkDiffActivity.this, "", 10.5f, Ui.text(), false);
                row.addView(name, Ui.lp(0, -2, 1.0f));
                meta = Ui.text(ApkDiffActivity.this, "", 9.0f, Ui.muted(), false);
                row.addView(meta);
                convertView = row;
            } else {
                row = (LinearLayout) convertView;
                name = (TextView) row.getChildAt(0);
                meta = (TextView) row.getChildAt(1);
            }
            ApkDiff.Entry e = rows.get(position);
            name.setText(e.name);
            int color = "added".equals(e.reason) ? Ui.SUCCESS
                    : "removed".equals(e.reason) ? Ui.ERROR : Ui.WARNING;
            name.setTextColor(color);
            meta.setText(e.reason + " · " + (e.newSize - e.oldSize) + " B");
            return convertView;
        }
    }
}
