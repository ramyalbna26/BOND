package com.ramyelbanna.juzoor;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.ramyelbanna.juzoor.ConversionService;
import com.ramyelbanna.juzoor.engine.ConversionResult;
import com.ramyelbanna.juzoor.ui.Ui;

/* loaded from: /workspace/apk_extract/classes.dex */
public class ProgressActivity extends BaseActivity implements ConversionService.Listener {
    private ProgressBar bar;
    private boolean finished;
    private String path;
    private TextView pct;
    private TextView step;
    private TextView tail;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final StringBuilder tailBuf = new StringBuilder();

    @Override // com.ramyelbanna.juzoor.BaseActivity, android.app.Activity
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        this.path = getIntent().getStringExtra(ConversionService.EXTRA_PATH);
        String stringExtra = this.path;
        buildUi();
        ConversionService.addListener(this);
        if (stringExtra != null && !ConversionService.isRunning()) {
            Intent intent = new Intent(this, (Class<?>) ConversionService.class);
            intent.setAction(ConversionService.ACTION_START);
            intent.putExtra(ConversionService.EXTRA_PATH, stringExtra);
            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(intent);
                return;
            } else {
                startService(intent);
                return;
            }
        }
        if (ConversionService.isRunning()) {
            onProgress(ConversionService.getProgress(), ConversionService.getStep());
        }
    }

    private void buildUi() {
        topBar(getString(R.string.progress_title), false);
        LinearLayout card = Ui.card(this);
        card.addView(Ui.text(this, ConversionService.getCurrentFile() == null ? "" : ConversionService.getCurrentFile(), 16.0f, Ui.text(), true));
        LinearLayout row = Ui.row(this);
        row.setPadding(0, Ui.dp(this, 14.0f), 0, 0);
        ProgressBar progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        this.bar = progressBar;
        progressBar.setMax(100);
        this.bar.setProgress(0);
        row.addView(this.bar, Ui.lp(0, Ui.dp(this, 8.0f), 1.0f));
        TextView text = Ui.text(this, "0%", 14.0f, Ui.GOLD, true);
        this.pct = text;
        text.setPadding(Ui.dp(this, 12.0f), 0, 0, 0);
        row.addView(this.pct);
        card.addView(row);
        TextView text2 = Ui.text(this, "…", 13.0f, Ui.muted(), false);
        this.step = text2;
        text2.setPadding(0, Ui.dp(this, 10.0f), 0, 0);
        card.addView(this.step);
        this.body.addView(card);
        TextView text3 = Ui.text(this, getString(R.string.progress_log), 11.0f, Ui.GOLD, true);
        text3.setPadding(0, Ui.dp(this, 18.0f), 0, Ui.dp(this, 6.0f));
        this.body.addView(text3);
        TextView text4 = Ui.text(this, "", 11.0f, Ui.muted(), false);
        this.tail = text4;
        text4.setTypeface(Typeface.MONOSPACE);
        this.tail.setBackground(Ui.round(-15921902, Ui.dp(this, 12.0f)));
        int dp = Ui.dp(this, 12.0f);
        this.tail.setPadding(dp, dp, dp, dp);
        this.tail.setMinLines(8);
        this.body.addView(this.tail);
        final TextView button = Ui.button(this, getString(R.string.progress_cancel), false);
        LinearLayout.LayoutParams lp = Ui.lp(-1, -2);
        lp.topMargin = Ui.dp(this, 20.0f);
        button.setOnClickListener(new View.OnClickListener() { // from class: com.ramyelbanna.juzoor.ProgressActivity$$ExternalSyntheticLambda0
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                ProgressActivity.this.lambda$buildUi$0(button, view);
            }
        });
        this.body.addView(button, lp);
        TextView live = Ui.button(this, getString(R.string.canvas_title), false);
        LinearLayout.LayoutParams liveLp = Ui.lp(-1, -2);
        liveLp.topMargin = Ui.dp(this, 10.0f);
        live.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent canvas = new Intent(ProgressActivity.this, LiveCanvasActivity.class);
                if (path != null) canvas.putExtra(ConversionService.EXTRA_PATH, path);
                startActivity(canvas);
            }
        });
        this.body.addView(live, liveLp);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$buildUi$0(TextView textView, View view) {
        ConversionService.cancel();
        textView.setEnabled(false);
        textView.setText(getString(R.string.progress_cancelling));
    }

    @Override // com.ramyelbanna.juzoor.ConversionService.Listener
    public void onProgress(final int i, final String str) {
        this.ui.post(new Runnable() { // from class: com.ramyelbanna.juzoor.ProgressActivity$$ExternalSyntheticLambda2
            @Override // java.lang.Runnable
            public final void run() {
                ProgressActivity.this.lambda$onProgress$1(i, str);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onProgress$1(int i, String str) {
        this.bar.setProgress(i);
        this.pct.setText(i + "%");
        this.step.setText(str);
    }

    @Override // com.ramyelbanna.juzoor.ConversionService.Listener
    public void onLog(final String str, final String str2) {
        this.ui.post(new Runnable() { // from class: com.ramyelbanna.juzoor.ProgressActivity$$ExternalSyntheticLambda1
            @Override // java.lang.Runnable
            public final void run() {
                ProgressActivity.this.lambda$onLog$2(str, str2);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onLog$2(String str, String str2) {
        StringBuilder sb = this.tailBuf;
        sb.append('[');
        sb.append(str);
        sb.append("] ");
        sb.append(str2);
        sb.append('\n');
        if (this.tailBuf.length() > 4000) {
            StringBuilder sb2 = this.tailBuf;
            sb2.delete(0, sb2.length() - 4000);
        }
        this.tail.setText(this.tailBuf.toString());
    }

    @Override // com.ramyelbanna.juzoor.ConversionService.Listener
    public void onFinished(ConversionResult conversionResult) {
        if (this.finished) {
            return;
        }
        this.finished = true;
        this.ui.post(new Runnable() { // from class: com.ramyelbanna.juzoor.ProgressActivity$$ExternalSyntheticLambda3
            @Override // java.lang.Runnable
            public final void run() {
                ProgressActivity.this.lambda$onFinished$3();
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onFinished$3() {
        this.bar.setProgress(100);
        this.pct.setText("100%");
        startActivity(new Intent(this, (Class<?>) ResultActivity.class));
        finish();
    }

    @Override // android.app.Activity
    protected void onDestroy() {
        ConversionService.removeListener(this);
        super.onDestroy();
    }
}
