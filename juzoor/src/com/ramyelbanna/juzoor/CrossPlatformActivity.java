package com.ramyelbanna.juzoor;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.ramyelbanna.juzoor.core.JuzoorFiles;
import com.ramyelbanna.juzoor.engine.ConversionEngine;
import com.ramyelbanna.juzoor.engine.CrossPlatform;
import com.ramyelbanna.juzoor.engine.ZipUtil;
import com.ramyelbanna.juzoor.ui.Ui;

import java.io.File;
import java.util.Locale;

/**
 * Phase 3 workbench: PE/EXE/DLL, ELF/DEB/RPM and Mach-O/DMG analysis with
 * strings, headers, embedded payloads and PyInstaller extraction.
 */
public class CrossPlatformActivity extends BaseActivity {

    private static final int REQ_PICK = 71;
    private File file;
    private TextView fileLabel;
    private TextView status;
    private TextView output;
    private CrossPlatform.Report report;
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
        bar.addView(Ui.text(this, getString(R.string.cross_title), 17.0f, Ui.text(), true),
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

        TextView intro = Ui.text(this, getString(R.string.cross_sub), 12.0f, Ui.muted(), false);
        intro.setPadding(0, Ui.dp(this, 4.0f), 0, Ui.dp(this, 10.0f));
        root.addView(intro);

        LinearLayout pickRow = Ui.row(this);
        TextView pick = Ui.button(this, getString(R.string.cross_pick), false);
        pick.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(CrossPlatformActivity.this, FileBrowserActivity.class);
                intent.putExtra("pick", true);
                intent.putExtra("all", true);
                startActivityForResult(intent, REQ_PICK);
            }
        });
        pickRow.addView(pick, Ui.lp(0, -2, 1.0f));
        TextView run = Ui.button(this, getString(R.string.cross_run), true);
        LinearLayout.LayoutParams rlp = Ui.lp(0, -2, 1.0f);
        rlp.leftMargin = Ui.dp(this, 8.0f);
        run.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                analyze();
            }
        });
        pickRow.addView(run, rlp);
        LinearLayout.LayoutParams plp = Ui.lp(-1, -2);
        plp.topMargin = Ui.dp(this, 6.0f);
        root.addView(pickRow, plp);

        this.fileLabel = Ui.text(this, getString(R.string.cross_none), 10.0f, Ui.GOLD, false);
        this.fileLabel.setPadding(0, Ui.dp(this, 8.0f), 0, 0);
        root.addView(this.fileLabel);
        this.status = Ui.text(this, "", 10.0f, Ui.muted(), false);
        this.status.setPadding(0, Ui.dp(this, 4.0f), 0, Ui.dp(this, 6.0f));
        root.addView(this.status);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackground(Ui.stroke(Ui.surface2(), 0x33D4AF37, Ui.dp(this, 10.0f), Ui.dp(this, 1.0f)));
        this.output = Ui.text(this, getString(R.string.cross_help), 10.5f, Ui.text(), false);
        this.output.setTypeface(Typeface.MONOSPACE);
        int op = Ui.dp(this, 10.0f);
        this.output.setPadding(op, op, op, op);
        scroll.addView(this.output);
        root.addView(scroll, Ui.lp(-1, 0, 1.0f));
        setContentView(root);
    }

    private void analyze() {
        if (file == null || !file.isFile()) {
            status.setText(getString(R.string.cross_need_file));
            return;
        }
        status.setText(getString(R.string.cross_running));
        final File target = file;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final CrossPlatform.Report r = CrossPlatform.analyze(target, getCacheDir(), null);
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        report = r;
                        if (r.error != null) {
                            status.setText(r.error);
                        } else {
                            status.setText(getString(R.string.cross_done, r.format,
                                    r.stringCount, r.extracted.size()));
                        }
                        output.setText(CrossPlatform.toText(r));
                    }
                });
            }
        }, "juzoor-cross").start();
    }

    private void share() {
        try {
            if (report == null) {
                status.setText(getString(R.string.cross_need_file));
                return;
            }
            File out = new File(getCacheDir(), "juzoor-cross-report.txt");
            ZipUtil.writeFile(out, CrossPlatform.toText(report));
            startActivity(Intent.createChooser(
                    JuzoorFiles.shareIntent(this, out, getString(R.string.cross_title)),
                    getString(R.string.cross_title)));
        } catch (Throwable t) {
            toast(getString(R.string.err_save));
        }
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != REQ_PICK || result != RESULT_OK || data == null) return;
        String path = data.getStringExtra(ConversionService.EXTRA_PATH);
        if (path == null) return;
        file = new File(path);
        fileLabel.setText(file.getAbsolutePath() + " · " + ConversionEngine.human(file.length()));
        analyze();
    }

}
