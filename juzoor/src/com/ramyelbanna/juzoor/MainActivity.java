package com.ramyelbanna.juzoor;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.ramyelbanna.juzoor.core.LogManager;
import com.ramyelbanna.juzoor.core.Prefs;
import com.ramyelbanna.juzoor.ui.Ui;

/* loaded from: /workspace/apk_extract/classes.dex */
public class MainActivity extends BaseActivity {
    @Override // com.ramyelbanna.juzoor.BaseActivity, android.app.Activity
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        build();
        if (bundle == null) handleIncoming(getIntent());
    }

    /** Accept an APK shared from another app (ACTION_VIEW / ACTION_SEND). */
    private void handleIncoming(Intent intent) {
        if (intent == null) return;
        Uri uri = intent.getData();
        if (uri == null && intent.hasExtra(Intent.EXTRA_STREAM)) {
            Object extra = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (extra instanceof Uri) uri = (Uri) extra;
        }
        if (uri == null || "file".equals(uri.getScheme())) return;
        try {
            String name = null;
            Cursor c = getContentResolver().query(uri, null, null, null, null);
            if (c != null) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (c.moveToFirst() && idx >= 0) name = c.getString(idx);
                c.close();
            }
            if (name == null || name.isEmpty()) name = "incoming.apk";
            File dir = new File(getCacheDir(), "incoming");
            if (!dir.exists()) dir.mkdirs();
            File out = new File(dir, name);
            InputStream in = getContentResolver().openInputStream(uri);
            if (in == null) return;
            FileOutputStream fos = new FileOutputStream(out);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) fos.write(buf, 0, n);
            fos.close();
            in.close();
            LogManager.i("Received shared file: " + name);
            Intent next = new Intent(this, ConfirmActivity.class);
            next.putExtra(ConversionService.EXTRA_PATH, out.getAbsolutePath());
            startActivity(next);
        } catch (Throwable t) {
            LogManager.w("Could not import shared file: " + t);
        }
    }

    @Override // android.app.Activity
    protected void onResume() {
        super.onResume();
        this.body.removeAllViews();
        build();
    }

    private void build() {
        LinearLayout row = Ui.row(this);
        android.widget.ImageView logo = new android.widget.ImageView(this);
        logo.setImageResource(R.drawable.logo_juzoor);
        logo.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        logo.setContentDescription(getString(R.string.home_logo_desc));
        logo.setBackground(Ui.round(Ui.surface2(), Ui.dp(this, 12.0f)));
        int logoSize = Ui.dp(this, 46.0f);
        row.addView(logo, new LinearLayout.LayoutParams(logoSize, logoSize));
        LinearLayout column = Ui.column(this);
        column.addView(Ui.text(this, getString(R.string.app_name), 19.0f, Ui.text(), true));
        column.addView(Ui.text(this, getString(R.string.app_tagline), 11.0f, Ui.muted(), false));
        LinearLayout.LayoutParams lp = Ui.lp(0, -2, 1.0f);
        lp.leftMargin = Ui.dp(this, 10.0f);
        row.addView(column, lp);
        TextView chip = Ui.chip(this, "LOGCAT", Ui.GOLD);
        chip.setOnClickListener(new View.OnClickListener() { // from class: com.ramyelbanna.juzoor.MainActivity$$ExternalSyntheticLambda0
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$build$0(view);
            }
        });
        row.addView(chip);
        TextView chip2 = Ui.chip(this, "⚙", Ui.GOLD);
        chip2.setPadding(Ui.dp(this, 11.0f), Ui.dp(this, 4.0f), Ui.dp(this, 11.0f), Ui.dp(this, 4.0f));
        chip2.setOnClickListener(new View.OnClickListener() { // from class: com.ramyelbanna.juzoor.MainActivity$$ExternalSyntheticLambda1
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$build$1(view);
            }
        });
        LinearLayout.LayoutParams lp2 = Ui.lp(-2, -2);
        lp2.leftMargin = Ui.dp(this, 6.0f);
        row.addView(chip2, lp2);
        this.body.addView(row);
        LinearLayout badges = Ui.row(this);
        LinearLayout.LayoutParams blp = Ui.lp(-1, -2);
        blp.topMargin = Ui.dp(this, 10.0f);
        badges.addView(Ui.chip(this, getString(R.string.home_badge_offline), Ui.SUCCESS));
        TextView engine = Ui.chip(this, getString(R.string.home_engine), Ui.GOLD);
        LinearLayout.LayoutParams elp = Ui.lp(-2, -2);
        elp.leftMargin = Ui.dp(this, 6.0f);
        badges.addView(engine, elp);
        this.body.addView(badges, blp);
        LinearLayout card = Ui.card(this);
        LinearLayout.LayoutParams lp3 = Ui.lp(-1, -2);
        lp3.topMargin = Ui.dp(this, 18.0f);
        this.body.addView(card, lp3);
        card.addView(Ui.text(this, "APK → SOURCE", 11.0f, Ui.GOLD, true));
        TextView text = Ui.text(this, getString(R.string.home_hero_title), 28.0f, Ui.text(), true);
        text.setPadding(0, Ui.dp(this, 8.0f), 0, Ui.dp(this, 6.0f));
        card.addView(text);
        card.addView(Ui.text(this, getString(R.string.home_hero_sub), 14.0f, Ui.muted(), false));
        TextView button = Ui.button(this, getString(R.string.home_start), true);
        LinearLayout.LayoutParams lp4 = Ui.lp(-1, -2);
        lp4.topMargin = Ui.dp(this, 18.0f);
        button.setOnClickListener(new View.OnClickListener() { // from class: com.ramyelbanna.juzoor.MainActivity$$ExternalSyntheticLambda2
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$build$2(view);
            }
        });
        card.addView(button, lp4);
        LinearLayout row2 = Ui.row(this);
        row2.setPadding(0, Ui.dp(this, 12.0f), 0, 0);
        addQuick(row2, "APK", "apk");
        addQuick(row2, "APK+", "apk+");
        addQuick(row2, "XAPK", "xapk");
        addQuick(row2, "AAB", "aab");
        card.addView(row2);
        LinearLayout row3 = Ui.row(this);
        LinearLayout.LayoutParams lp5 = Ui.lp(-1, -2);
        lp5.topMargin = Ui.dp(this, 14.0f);
        this.body.addView(row3, lp5);
        addStat(row3, String.valueOf(Prefs.conversions()), getString(R.string.stat_conversions));
        addStat(row3, String.valueOf(LogManager.count(LogManager.ERROR)), getString(R.string.stat_errors));
        addStat(row3, LogManager.isAutoSave() ? getString(R.string.settings_on) : getString(R.string.settings_off), getString(R.string.stat_autolog));
        addHeader(getString(R.string.home_tools));
        tool(getString(R.string.home_tool_security), getString(R.string.home_tool_security_d), SecurityActivity.class, "SAST");
        tool(getString(R.string.home_tool_repackage), getString(R.string.home_tool_repackage_d), RepackageActivity.class, "SIGN");
        tool(getString(R.string.home_tool_source), getString(R.string.home_tool_source_d), ResultActivity.class, "TREE");
        tool(getString(R.string.home_tool_canvas), getString(R.string.home_tool_canvas_d), LiveCanvasActivity.class, "LIVE");
        tool(getString(R.string.home_tool_batch), getString(R.string.home_tool_batch_d), BatchActivity.class, "QUEUE");
        section(getString(R.string.feature_apkplus_title), getString(R.string.feature_apkplus_desc));
        section(getString(R.string.feature_engine_title), getString(R.string.feature_engine_desc));
        section(getString(R.string.feature_axml_title), getString(R.string.feature_axml_desc));
        section(getString(R.string.feature_arsc_title), getString(R.string.feature_arsc_desc));
        section(getString(R.string.feature_dex_title), getString(R.string.feature_dex_desc));
        section(getString(R.string.feature_native_title), getString(R.string.feature_native_desc));
        section(getString(R.string.feature_strings_title), getString(R.string.feature_strings_desc));
        section(getString(R.string.feature_libs_title), getString(R.string.feature_libs_desc));
        section(getString(R.string.feature_report_title), getString(R.string.feature_report_desc));
        section(getString(R.string.feature_project_title), getString(R.string.feature_project_desc));
        section(getString(R.string.feature_log_title), getString(R.string.feature_log_desc));
        addHeader(getString(R.string.tiers_title));
        tier(getString(R.string.tier_gold), getString(R.string.tier_gold_desc), "GOLD");
        tier(getString(R.string.tier_native), getString(R.string.tier_native_desc), "NATIVE");
        tier(getString(R.string.tier_styles), getString(R.string.tier_styles_desc), "STYLES");
        tier(getString(R.string.tier_coding), getString(R.string.tier_coding_desc), "CODING");
        TextView button2 = Ui.button(this, getString(R.string.menu_about), false);
        LinearLayout.LayoutParams lp6 = Ui.lp(-1, -2);
        lp6.topMargin = Ui.dp(this, 20.0f);
        button2.setOnClickListener(new View.OnClickListener() { // from class: com.ramyelbanna.juzoor.MainActivity$$ExternalSyntheticLambda3
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$build$3(view);
            }
        });
        this.body.addView(button2, lp6);
        TextView text2 = Ui.text(this, getString(R.string.app_tagline), 12.0f, Ui.muted(), false);
        text2.setGravity(17);
        text2.setPadding(0, Ui.dp(this, 14.0f), 0, 0);
        this.body.addView(text2);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$build$0(View view) {
        open(LogcatActivity.class);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$build$1(View view) {
        open(SettingsActivity.class);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$build$2(View view) {
        open(FileBrowserActivity.class);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$build$3(View view) {
        open(AboutActivity.class);
    }

    private void addQuick(LinearLayout linearLayout, String str, final String str2) {
        TextView chip = Ui.chip(this, str, Ui.GOLD);
        chip.setPadding(Ui.dp(this, 12.0f), Ui.dp(this, 6.0f), Ui.dp(this, 12.0f), Ui.dp(this, 6.0f));
        LinearLayout.LayoutParams lp = Ui.lp(-2, -2);
        lp.rightMargin = Ui.dp(this, 8.0f);
        chip.setOnClickListener(new View.OnClickListener() { // from class: com.ramyelbanna.juzoor.MainActivity$$ExternalSyntheticLambda4
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$addQuick$4(str2, view);
            }
        });
        linearLayout.addView(chip, lp);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$addQuick$4(String str, View view) {
        Intent intent = new Intent(this, (Class<?>) FileBrowserActivity.class);
        intent.putExtra("filter", str);
        startActivity(intent);
    }

    private void addStat(LinearLayout linearLayout, String str, String str2) {
        LinearLayout column = Ui.column(this);
        column.setGravity(17);
        column.addView(Ui.text(this, str, 20.0f, Ui.GOLD, true));
        column.addView(Ui.text(this, str2, 10.0f, Ui.muted(), false));
        linearLayout.addView(column, Ui.lp(0, -2, 1.0f));
    }

    private void addHeader(String str) {
        TextView text = Ui.text(this, str, 12.0f, Ui.GOLD, true);
        text.setLetterSpacing(0.08f);
        LinearLayout.LayoutParams lp = Ui.lp(-1, -2);
        lp.topMargin = Ui.dp(this, 26.0f);
        lp.bottomMargin = Ui.dp(this, 8.0f);
        this.body.addView(text, lp);
    }

    private void tool(String str, String str2, final Class<?> cls, String str3) {
        LinearLayout card = Ui.card(this);
        LinearLayout.LayoutParams lp = Ui.lp(-1, -2);
        lp.topMargin = Ui.dp(this, 8.0f);
        LinearLayout row = Ui.row(this);
        row.addView(Ui.text(this, str, 15.0f, Ui.GOLD, true), Ui.lp(0, -2, 1.0f));
        row.addView(Ui.chip(this, str3, Ui.GOLD));
        card.addView(row);
        TextView text = Ui.text(this, str2, 13.0f, Ui.muted(), false);
        text.setPadding(0, Ui.dp(this, 6.0f), 0, 0);
        card.addView(text);
        card.setClickable(true);
        Ui.ripple(card, 0);
        card.setOnClickListener(new View.OnClickListener() {
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                open(cls);
            }
        });
        this.body.addView(card, lp);
    }

    private void section(String str, String str2) {
        LinearLayout card = Ui.card(this);
        LinearLayout.LayoutParams lp = Ui.lp(-1, -2);
        lp.topMargin = Ui.dp(this, 10.0f);
        card.addView(Ui.text(this, str, 15.0f, Ui.text(), true));
        TextView text = Ui.text(this, str2, 13.0f, Ui.muted(), false);
        text.setPadding(0, Ui.dp(this, 5.0f), 0, 0);
        card.addView(text);
        this.body.addView(card, lp);
    }

    private void tier(String str, String str2, String str3) {
        LinearLayout card = Ui.card(this);
        LinearLayout.LayoutParams lp = Ui.lp(-1, -2);
        lp.topMargin = Ui.dp(this, 8.0f);
        LinearLayout row = Ui.row(this);
        row.addView(Ui.text(this, str, 15.0f, Ui.GOLD, true), Ui.lp(0, -2, 1.0f));
        row.addView(Ui.chip(this, str3, Ui.GOLD));
        card.addView(row);
        TextView text = Ui.text(this, str2, 13.0f, Ui.muted(), false);
        text.setPadding(0, Ui.dp(this, 6.0f), 0, 0);
        card.addView(text);
        this.body.addView(card, lp);
    }
}
