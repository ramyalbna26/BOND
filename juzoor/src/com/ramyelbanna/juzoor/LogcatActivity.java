package com.ramyelbanna.juzoor;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.ramyelbanna.juzoor.core.LogManager;
import com.ramyelbanna.juzoor.core.Prefs;
import com.ramyelbanna.juzoor.ui.Ui;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/* loaded from: /workspace/apk_extract/classes.dex */
public class LogcatActivity extends BaseActivity {
    private static final SimpleDateFormat TS = new SimpleDateFormat("HH:mm:ss", Locale.US);
    private LinearLayout holder;
    private String filter = null;
    private String query = "";
    private boolean live = true;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() { // from class: com.ramyelbanna.juzoor.LogcatActivity.1
        @Override // java.lang.Runnable
        public void run() {
            if (!LogcatActivity.this.live) return;
            LogcatActivity.this.render();
            LogcatActivity.this.h.postDelayed(this, 1500L);
        }
    };

    @Override // com.ramyelbanna.juzoor.BaseActivity, android.app.Activity
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        buildUi();
        this.h.postDelayed(this.tick, 1500L);
    }

    private void buildUi() {
        this.body.removeAllViews();
        topBar(getString(R.string.log_title), true);
        LinearLayout card = Ui.card(this);
        card.addView(Ui.text(this, getString(R.string.log_path), 11.0f, Ui.GOLD, true));
        card.addView(Ui.text(this, LogManager.getLogDir() == null ? "-" : LogManager.getLogDir().getAbsolutePath(), 11.0f, Ui.muted(), false));
        LinearLayout row = Ui.row(this);
        row.setPadding(0, Ui.dp(this, 10.0f), 0, 0);
        row.addView(Ui.chip(this, "INFO " + LogManager.count(LogManager.INFO), Ui.GOLD));
        addChip(row, "DEBUG " + LogManager.count(LogManager.DEBUG), Ui.INFO);
        addChip(row, "WARN " + LogManager.count(LogManager.WARN), Ui.WARNING);
        addChip(row, "ERR " + LogManager.count(LogManager.ERROR), Ui.ERROR);
        final TextView liveChip = Ui.chip(this, getString(R.string.log_pause), Ui.GOLD);
        liveChip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                live = !live;
                liveChip.setText(getString(live ? R.string.log_pause : R.string.log_resume));
                liveChip.setTextColor(live ? Ui.GOLD : Ui.muted());
                if (live) {
                    h.removeCallbacks(tick);
                    h.post(tick);
                }
            }
        });
        LinearLayout.LayoutParams llp = Ui.lp(-2, -2);
        llp.leftMargin = Ui.dp(this, 8.0f);
        row.addView(liveChip, llp);
        card.addView(row);
        this.body.addView(card);
        LinearLayout row2 = Ui.row(this);
        LinearLayout.LayoutParams lp = Ui.lp(-1, -2);
        lp.topMargin = Ui.dp(this, 12.0f);
        TextView button = Ui.button(this, getString(R.string.log_copy), true);
        button.setOnClickListener(new View.OnClickListener() { // from class: com.ramyelbanna.juzoor.LogcatActivity$$ExternalSyntheticLambda0
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                LogcatActivity.this.lambda$buildUi$0(view);
            }
        });
        row2.addView(button, Ui.lp(0, -2, 1.0f));
        TextView button2 = Ui.button(this, getString(R.string.log_clear), false);
        LinearLayout.LayoutParams lp2 = Ui.lp(0, -2, 1.0f);
        lp2.leftMargin = Ui.dp(this, 8.0f);
        button2.setOnClickListener(new View.OnClickListener() { // from class: com.ramyelbanna.juzoor.LogcatActivity$$ExternalSyntheticLambda1
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                LogcatActivity.this.lambda$buildUi$1(view);
            }
        });
        row2.addView(button2, lp2);
        TextView button3 = Ui.button(this, getString(R.string.log_export), false);
        LinearLayout.LayoutParams lp3 = Ui.lp(0, -2, 1.0f);
        lp3.leftMargin = Ui.dp(this, 8.0f);
        button3.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                exportLog();
            }
        });
        row2.addView(button3, lp3);
        this.body.addView(row2, lp);
        EditText search = new EditText(this);
        search.setHint(getString(R.string.log_search));
        search.setHintTextColor(Ui.muted());
        search.setTextColor(Ui.text());
        search.setTextSize(13.0f);
        search.setSingleLine(true);
        search.setInputType(InputType.TYPE_CLASS_TEXT);
        search.setBackground(Ui.round(Ui.surface2(), Ui.dp(this, 10.0f)));
        int sp = Ui.dp(this, 12.0f);
        search.setPadding(sp, Ui.dp(this, 8.0f), sp, Ui.dp(this, 8.0f));
        search.setText(query);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                query = s.toString().trim();
                render();
            }
        });
        LinearLayout.LayoutParams lps = Ui.lp(-1, -2);
        lps.topMargin = Ui.dp(this, 10.0f);
        this.body.addView(search, lps);
        LinearLayout row3 = Ui.row(this);
        row3.setPadding(0, Ui.dp(this, 10.0f), 0, 0);
        addFilter(row3, getString(R.string.log_all), null);
        addFilter(row3, LogManager.INFO, LogManager.INFO);
        addFilter(row3, LogManager.DEBUG, LogManager.DEBUG);
        addFilter(row3, LogManager.WARN, LogManager.WARN);
        addFilter(row3, LogManager.ERROR, LogManager.ERROR);
        this.body.addView(row3);
        this.holder = Ui.column(this);
        LinearLayout.LayoutParams lp4 = Ui.lp(-1, -2);
        lp4.topMargin = Ui.dp(this, 12.0f);
        this.body.addView(this.holder, lp4);
        render();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$buildUi$0(View view) {
        copyAll();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$buildUi$1(View view) {
        LogManager.clear();
        render();
    }

    private void addChip(LinearLayout linearLayout, String str, int i) {
        View chip = Ui.chip(this, str, i);
        LinearLayout.LayoutParams lp = Ui.lp(-2, -2);
        lp.leftMargin = Ui.dp(this, 6.0f);
        linearLayout.addView(chip, lp);
    }

    private void addFilter(LinearLayout linearLayout, String str, final String str2) {
        String str3 = this.filter;
        int i = Ui.GOLD;
        if ((str3 != null || str2 != null) && (str2 == null || !str2.equals(str3))) {
            i = Ui.muted();
        }
        TextView chip = Ui.chip(this, str, i);
        LinearLayout.LayoutParams lp = Ui.lp(-2, -2);
        lp.rightMargin = Ui.dp(this, 6.0f);
        chip.setOnClickListener(new View.OnClickListener() { // from class: com.ramyelbanna.juzoor.LogcatActivity$$ExternalSyntheticLambda2
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                LogcatActivity.this.lambda$addFilter$2(str2, view);
            }
        });
        linearLayout.addView(chip, lp);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$addFilter$2(String str, View view) {
        this.filter = str;
        buildUi();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void render() {
        LinearLayout linearLayout = this.holder;
        if (linearLayout == null) {
            return;
        }
        linearLayout.removeAllViews();
        List<LogManager.Entry> all = LogManager.all();
        int minRank = Prefs.logLevel();
        int i = 0;
        for (int size = all.size() - 1; size >= 0 && i < 300; size--) {
            LogManager.Entry entry = all.get(size);
            String str = this.filter;
            if (str != null && !str.equals(entry.level)) continue;
            if (rank(entry.level) < minRank) continue;
            if (query.length() > 0 && !entry.message.toLowerCase(Locale.US).contains(query.toLowerCase(Locale.US))) continue;
            i++;
            LinearLayout column = Ui.column(this);
            column.setBackground(Ui.round(-15921902, Ui.dp(this, 8.0f)));
            int dp = Ui.dp(this, 9.0f);
            column.setPadding(dp, dp, dp, dp);
            LinearLayout row = Ui.row(this);
            row.addView(Ui.text(this, TS.format(new Date(entry.time)), 10.0f, Ui.muted(), false));
            TextView text = Ui.text(this, entry.level, 10.0f, Ui.levelColor(entry.level), true);
            LinearLayout.LayoutParams lp = Ui.lp(-2, -2);
            lp.leftMargin = Ui.dp(this, 8.0f);
            row.addView(text, lp);
            column.addView(row);
            TextView text2 = Ui.text(this, entry.message, 12.0f, Ui.text(), false);
            text2.setTypeface(Typeface.MONOSPACE);
            text2.setPadding(0, Ui.dp(this, 3.0f), 0, 0);
            column.addView(text2);
            LinearLayout.LayoutParams lp2 = Ui.lp(-1, -2);
            lp2.topMargin = Ui.dp(this, 5.0f);
            this.holder.addView(column, lp2);
        }
        if (i == 0) {
            TextView text3 = Ui.text(this, getString(R.string.log_empty), 13.0f, Ui.muted(), false);
            text3.setGravity(17);
            text3.setPadding(0, Ui.dp(this, 24.0f), 0, 0);
            this.holder.addView(text3);
        }
        if (live) {
            android.view.ViewParent parent = this.body.getParent();
            if (parent instanceof ScrollView) ((ScrollView) parent).scrollTo(0, 0);
        }
    }

    private static int rank(String level) {
        if (LogManager.DEBUG.equals(level)) return 0;
        if (LogManager.INFO.equals(level) || LogManager.SUCCESS.equals(level)) return 1;
        if (LogManager.WARN.equals(level)) return 2;
        return 3;
    }

    private void exportLog() {
        try {
            File dir = LogManager.getLogDir();
            if (dir == null) dir = getCacheDir();
            if (!dir.exists()) dir.mkdirs();
            File out = new File(dir, "juzoor-export-" + System.currentTimeMillis() + ".txt");
            FileOutputStream fos = new FileOutputStream(out);
            fos.write(LogManager.dump().getBytes("UTF-8"));
            fos.close();
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_SUBJECT, "Juzoor logs");
            send.putExtra(Intent.EXTRA_TEXT, LogManager.dump());
            startActivity(Intent.createChooser(send, getString(R.string.log_export)));
            toast(getString(R.string.log_exported) + ": " + out.getName());
            LogManager.s("Logs exported to " + out.getAbsolutePath());
        } catch (Throwable t) {
            toast(getString(R.string.err_save));
        }
    }

    private void copyAll() {
        try {
            ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Juzoor logs", LogManager.dump()));
            toast(getString(R.string.log_copied));
            LogManager.i("Logs copied to clipboard");
        } catch (Throwable unused) {
            toast(getString(R.string.log_copy_failed));
        }
    }

    @Override // android.app.Activity
    protected void onDestroy() {
        this.h.removeCallbacks(this.tick);
        super.onDestroy();
    }
}
