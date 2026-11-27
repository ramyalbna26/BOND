package com.ramyelbanna.juzoor;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import com.ramyelbanna.juzoor.SettingsActivity;
import com.ramyelbanna.juzoor.core.LogManager;
import com.ramyelbanna.juzoor.core.Prefs;
import com.ramyelbanna.juzoor.ui.Ui;

/* loaded from: /workspace/apk_extract/classes.dex */
public class SettingsActivity extends BaseActivity {

    interface Cb {
        void on(boolean z);
    }

    interface Scb {
        void on(String str);
    }

    @Override // com.ramyelbanna.juzoor.BaseActivity, android.app.Activity
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        buildUi();
    }

    private void buildUi() {
        this.body.removeAllViews();
        topBar(getString(R.string.settings_title), true);
        header(getString(R.string.settings_conversion));
        toggle(getString(R.string.confirm_opt_resources), Prefs.decodeResources(), new Cb() { // from class: com.ramyelbanna.juzoor.SettingsActivity$$ExternalSyntheticLambda0
            @Override // com.ramyelbanna.juzoor.SettingsActivity.Cb
            public final void on(boolean z) {
                SettingsActivity.lambda$buildUi$0(z);
            }
        });
        toggle(getString(R.string.confirm_opt_smali), Prefs.disassembleDex(), new Cb() { // from class: com.ramyelbanna.juzoor.SettingsActivity$$ExternalSyntheticLambda2
            @Override // com.ramyelbanna.juzoor.SettingsActivity.Cb
            public final void on(boolean z) {
                SettingsActivity.lambda$buildUi$1(z);
            }
        });
        toggle(getString(R.string.confirm_opt_project), Prefs.generateProject(), new Cb() { // from class: com.ramyelbanna.juzoor.SettingsActivity$$ExternalSyntheticLambda3
            @Override // com.ramyelbanna.juzoor.SettingsActivity.Cb
            public final void on(boolean z) {
                SettingsActivity.lambda$buildUi$2(z);
            }
        });
        toggle(getString(R.string.confirm_opt_assets), Prefs.copyAssets(), new Cb() { // from class: com.ramyelbanna.juzoor.SettingsActivity$$ExternalSyntheticLambda4
            @Override // com.ramyelbanna.juzoor.SettingsActivity.Cb
            public final void on(boolean z) {
                SettingsActivity.lambda$buildUi$3(z);
            }
        });
        header(getString(R.string.settings_decrypt));
        toggle(getString(R.string.confirm_opt_native), Prefs.decryptNative(), new Cb() {
            @Override
            public void on(boolean z) {
                Prefs.setDecryptNative(z);
                LogManager.i("Setting: decrypt native = " + z);
            }
        });
        toggle(getString(R.string.confirm_opt_strings), Prefs.decryptStrings(), new Cb() {
            @Override
            public void on(boolean z) {
                Prefs.setDecryptStrings(z);
                LogManager.i("Setting: decrypt strings = " + z);
            }
        });
        toggle(getString(R.string.confirm_opt_libs), Prefs.analyzeLibraries(), new Cb() {
            @Override
            public void on(boolean z) {
                Prefs.setAnalyzeLibraries(z);
                LogManager.i("Setting: analyze libraries = " + z);
            }
        });
        toggle(getString(R.string.confirm_opt_report), Prefs.writeReport(), new Cb() {
            @Override
            public void on(boolean z) {
                Prefs.setWriteReport(z);
                LogManager.i("Setting: write report = " + z);
            }
        });
        toggle(getString(R.string.confirm_opt_java), Prefs.javaTree(), new Cb() {
            @Override
            public void on(boolean z) {
                Prefs.setJavaTree(z);
                LogManager.i("Setting: java tree = " + z);
            }
        });
        header(getString(R.string.settings_security));
        info(getString(R.string.settings_security_guard), getString(R.string.settings_security_guard_d));
        toggle(getString(R.string.settings_scan), Prefs.securityScan(), new Cb() {
            @Override
            public void on(boolean z) {
                Prefs.setSecurityScan(z);
                LogManager.i("Setting: security analysis = " + z);
            }
        });
        info(getString(R.string.settings_scan), getString(R.string.settings_scan_d));
        header(getString(R.string.source_title));
        toggle(getString(R.string.settings_kotlin), Prefs.kotlinStubs(), new Cb() {
            @Override
            public void on(boolean z) {
                Prefs.setKotlinStubs(z);
                LogManager.i("Setting: kotlin stubs = " + z);
            }
        });
        info(getString(R.string.settings_kotlin), getString(R.string.settings_kotlin_d));
        header(getString(R.string.settings_cross));
        info(getString(R.string.settings_cross_windows), getString(R.string.settings_cross_windows_d));
        info(getString(R.string.settings_cross_linux), getString(R.string.settings_cross_linux_d));
        info(getString(R.string.settings_cross_macos), getString(R.string.settings_cross_macos_d));
        TextView cross = Ui.button(this, getString(R.string.settings_cross_open), false);
        LinearLayout.LayoutParams crossLp = Ui.lp(-1, -2);
        crossLp.topMargin = Ui.dp(this, 10.0f);
        cross.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                open(CrossPlatformActivity.class);
            }
        });
        this.body.addView(cross, crossLp);
        header(getString(R.string.settings_signing));
        toggle(getString(R.string.settings_v1), Prefs.signV1(), new Cb() {
            @Override
            public void on(boolean z) {
                Prefs.setSignV1(z);
                LogManager.i("Setting: signature v1 = " + z);
            }
        });
        toggle(getString(R.string.settings_v2), Prefs.signV2(), new Cb() {
            @Override
            public void on(boolean z) {
                Prefs.setSignV2(z);
                LogManager.i("Setting: signature v2 = " + z);
            }
        });
        toggle(getString(R.string.settings_v3), Prefs.signV3(), new Cb() {
            @Override
            public void on(boolean z) {
                Prefs.setSignV3(z);
                LogManager.i("Setting: signature v3 = " + z);
            }
        });
        toggle(getString(R.string.settings_default_key), Prefs.defaultKey(), new Cb() {
            @Override
            public void on(boolean z) {
                Prefs.setDefaultKey(z);
                LogManager.i("Setting: built-in signing key = " + z);
            }
        });
        info(getString(R.string.settings_default_key), getString(R.string.settings_default_key_d));
        header(getString(R.string.settings_theme));
        toggle(getString(R.string.settings_dark), Prefs.dark(), new Cb() { // from class: com.ramyelbanna.juzoor.SettingsActivity$$ExternalSyntheticLambda5
            @Override // com.ramyelbanna.juzoor.SettingsActivity.Cb
            public final void on(boolean z) {
                SettingsActivity.this.lambda$buildUi$4(z);
            }
        });
        fontScaleRow();
        header(getString(R.string.settings_language));
        choice(getString(R.string.settings_language), new String[]{"العربية", "English"}, new String[]{"ar", "en"}, Prefs.lang(), new Scb() { // from class: com.ramyelbanna.juzoor.SettingsActivity$$ExternalSyntheticLambda6
            @Override // com.ramyelbanna.juzoor.SettingsActivity.Scb
            public final void on(String str) {
                SettingsActivity.this.lambda$buildUi$5(str);
            }
        });
        header(getString(R.string.settings_server));
        info(getString(R.string.settings_server), getString(R.string.settings_server_value));
        info(getString(R.string.settings_privacy), getString(R.string.settings_privacy_value));
        header(getString(R.string.settings_export));
        info(getString(R.string.settings_save_path), Prefs.saveTreeUri().isEmpty()
                ? Prefs.savePath() : android.net.Uri.decode(Prefs.saveTreeUri()));
        TextView button = Ui.button(this, getString(R.string.settings_change_path), false);
        LinearLayout.LayoutParams lp = Ui.lp(-1, -2);
        lp.topMargin = Ui.dp(this, 10.0f);
        button.setOnClickListener(new View.OnClickListener() { // from class: com.ramyelbanna.juzoor.SettingsActivity$$ExternalSyntheticLambda7
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SettingsActivity.this.lambda$buildUi$6(view);
            }
        });
        this.body.addView(button, lp);
        header(getString(R.string.settings_logs));
        toggle(getString(R.string.settings_autolog), Prefs.autoLog(), new Cb() { // from class: com.ramyelbanna.juzoor.SettingsActivity$$ExternalSyntheticLambda8
            @Override // com.ramyelbanna.juzoor.SettingsActivity.Cb
            public final void on(boolean z) {
                SettingsActivity.lambda$buildUi$7(z);
            }
        });
        info(getString(R.string.log_path), LogManager.getLogDir() == null ? "-" : LogManager.getLogDir().getAbsolutePath());
        choice(getString(R.string.settings_log_level),
                new String[]{getString(R.string.log_all), LogManager.INFO, LogManager.WARN, LogManager.ERROR},
                new String[]{"0", "1", "2", "3"},
                String.valueOf(Prefs.logLevel()),
                new Scb() {
                    @Override
                    public void on(String str) {
                        Prefs.setLogLevel(Integer.parseInt(str));
                        LogManager.i("Log level = " + str);
                        buildUi();
                    }
                });
        header(getString(R.string.settings_notifications));
        toggle(getString(R.string.settings_notifications), Prefs.notifications(), new Cb() { // from class: com.ramyelbanna.juzoor.SettingsActivity$$ExternalSyntheticLambda9
            @Override // com.ramyelbanna.juzoor.SettingsActivity.Cb
            public final void on(boolean z) {
                SettingsActivity.lambda$buildUi$8(z);
            }
        });
        header(getString(R.string.settings_about_section));
        info(getString(R.string.settings_version), Prefs.VERSION + " (" + Prefs.conversions() + " " + getString(R.string.result_conversions) + ")");
        TextView button2 = Ui.button(this, getString(R.string.settings_reset), false);
        LinearLayout.LayoutParams lp2 = Ui.lp(-1, -2);
        lp2.topMargin = Ui.dp(this, 10.0f);
        button2.setOnClickListener(new View.OnClickListener() { // from class: com.ramyelbanna.juzoor.SettingsActivity$$ExternalSyntheticLambda10
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SettingsActivity.this.lambda$buildUi$9(view);
            }
        });
        this.body.addView(button2, lp2);
        TextView button3 = Ui.button(this, getString(R.string.menu_about), true);
        LinearLayout.LayoutParams lp3 = Ui.lp(-1, -2);
        lp3.topMargin = Ui.dp(this, 10.0f);
        button3.setOnClickListener(new View.OnClickListener() { // from class: com.ramyelbanna.juzoor.SettingsActivity$$ExternalSyntheticLambda1
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SettingsActivity.this.lambda$buildUi$10(view);
            }
        });
        this.body.addView(button3, lp3);
    }

    static /* synthetic */ void lambda$buildUi$0(boolean z) {
        Prefs.setDecodeResources(z);
        LogManager.i("Setting: decode resources = " + z);
    }

    static /* synthetic */ void lambda$buildUi$1(boolean z) {
        Prefs.setDisassembleDex(z);
        LogManager.i("Setting: disassemble dex = " + z);
    }

    static /* synthetic */ void lambda$buildUi$2(boolean z) {
        Prefs.setGenerateProject(z);
        LogManager.i("Setting: generate project = " + z);
    }

    static /* synthetic */ void lambda$buildUi$3(boolean z) {
        Prefs.setCopyAssets(z);
        LogManager.i("Setting: copy assets = " + z);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$buildUi$4(boolean z) {
        Prefs.setDark(z);
        LogManager.i("Theme changed: ".concat(z ? "dark" : "light"));
        recreate();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$buildUi$5(String str) {
        Prefs.setLang(str);
        LogManager.i("Language changed: " + str);
        ((App) getApplication()).applyLocale();
        recreate();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$buildUi$6(View view) {
        pickFolder();
    }

    static /* synthetic */ void lambda$buildUi$7(boolean z) {
        Prefs.setAutoLog(z);
        LogManager.setAutoSave(z);
        LogManager.i("Auto-save logs = " + z);
    }

    static /* synthetic */ void lambda$buildUi$8(boolean z) {
        Prefs.setNotifications(z);
        LogManager.i("Notifications = " + z);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$buildUi$9(View view) {
        Prefs.reset();
        LogManager.setAutoSave(true);
        ((App) getApplication()).applyLocale();
        LogManager.i("Settings reset to defaults");
        recreate();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$buildUi$10(View view) {
        open(AboutActivity.class);
    }

    /** 100% – 150% slider, applied to every screen as soon as it is released. */
    private void fontScaleRow() {
        LinearLayout card = Ui.column(this);
        card.setBackground(Ui.round(Ui.surface(), Ui.dp(this, 12.0f)));
        int dp = Ui.dp(this, 13.0f);
        card.setPadding(dp, dp, dp, dp);
        LinearLayout row = Ui.row(this);
        row.addView(Ui.text(this, getString(R.string.settings_font_scale), 14.0f, Ui.text(), false),
                Ui.lp(0, -2, 1.0f));
        final TextView value = Ui.text(this, percent(Prefs.fontScale()), 13.0f, Ui.GOLD, true);
        row.addView(value);
        card.addView(row);
        final SeekBar bar = new SeekBar(this);
        bar.setMax(10);
        bar.setProgress(scaleToStep(Prefs.fontScale()));
        bar.setPadding(0, Ui.dp(this, 6.0f), 0, 0);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int step, boolean fromUser) {
                value.setText(percent(stepToScale(step)));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                float scale = stepToScale(seekBar.getProgress());
                Prefs.setFontScale(scale);
                ((App) getApplication()).applyFontScale();
                LogManager.i("Font scale = " + percent(scale));
                // Recreate once, not on every pixel of the drag.
                recreate();
            }
        });
        card.addView(bar, Ui.lp(-1, -2));
        card.addView(Ui.text(this, getString(R.string.settings_font_scale_d), 11.0f, Ui.muted(), false));
        LinearLayout.LayoutParams lp = Ui.lp(-1, -2);
        lp.topMargin = Ui.dp(this, 8.0f);
        this.body.addView(card, lp);
    }

    private static int scaleToStep(float scale) {
        int step = Math.round((scale - 1.0f) * 20.0f);
        return Math.max(0, Math.min(10, step));
    }

    private static float stepToScale(int step) {
        return 1.0f + Math.max(0, Math.min(10, step)) * 0.05f;
    }

    private static String percent(float scale) {
        return Math.round(scale * 100.0f) + "%";
    }

    private void pickFolder() {
        try {
            startActivityForResult(new Intent("android.intent.action.OPEN_DOCUMENT_TREE"), 33);
        } catch (Exception unused) {
            toast(getString(R.string.err_save));
        }
    }

    @Override // android.app.Activity
    protected void onActivityResult(int i, int i2, Intent intent) {
        super.onActivityResult(i, i2, intent);
        if (i != 33 || i2 != -1 || intent == null || intent.getData() == null) {
            return;
        }
        android.net.Uri treeUri = intent.getData();
        String tree = treeUri.toString();
        Prefs.setSaveTreeUri(tree);
        try {
            getContentResolver().takePersistableUriPermission(treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (Throwable t) {
            LogManager.w("Could not persist save tree permission: " + t);
        }
        LogManager.i("Save tree set to " + tree);
        buildUi();
    }

    private void header(String str) {
        TextView text = Ui.text(this, str, 12.0f, Ui.GOLD, true);
        text.setLetterSpacing(0.08f);
        LinearLayout.LayoutParams lp = Ui.lp(-1, -2);
        lp.topMargin = Ui.dp(this, 22.0f);
        lp.bottomMargin = Ui.dp(this, 8.0f);
        this.body.addView(text, lp);
    }

    private void toggle(String str, boolean z, final Cb cb) {
        LinearLayout row = Ui.row(this);
        row.setBackground(Ui.round(Ui.surface(), Ui.dp(this, 12.0f)));
        int dp = Ui.dp(this, 13.0f);
        row.setPadding(dp, dp, dp, dp);
        row.addView(Ui.text(this, str, 14.0f, Ui.text(), false), Ui.lp(0, -2, 1.0f));
        final boolean[] zArr = {z};
        String str2 = z ? getString(R.string.settings_on) : getString(R.string.settings_off);
        int i = Ui.SUCCESS;
        final TextView text = Ui.text(this, str2, 12.0f, z ? Ui.SUCCESS : Ui.muted(), true);
        text.setPadding(Ui.dp(this, 12.0f), Ui.dp(this, 6.0f), Ui.dp(this, 12.0f), Ui.dp(this, 6.0f));
        int surface2 = Ui.surface2();
        if (!z) {
            i = 872415231;
        }
        text.setBackground(Ui.stroke(surface2, i, Ui.dp(this, 20.0f), Ui.dp(this, 1.0f)));
        row.addView(text);
        row.setClickable(true);
        row.setOnClickListener(new View.OnClickListener() { // from class: com.ramyelbanna.juzoor.SettingsActivity$$ExternalSyntheticLambda11
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SettingsActivity.this.lambda$toggle$11(zArr, text, cb, view);
            }
        });
        LinearLayout.LayoutParams lp = Ui.lp(-1, -2);
        lp.topMargin = Ui.dp(this, 8.0f);
        this.body.addView(row, lp);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$toggle$11(boolean[] zArr, TextView textView, Cb cb, View view) {
        boolean z = !zArr[0];
        zArr[0] = z;
        textView.setText(z ? getString(R.string.settings_on) : getString(R.string.settings_off));
        boolean z2 = zArr[0];
        int i = Ui.SUCCESS;
        textView.setTextColor(z2 ? Ui.SUCCESS : Ui.muted());
        int surface2 = Ui.surface2();
        if (!zArr[0]) {
            i = 872415231;
        }
        textView.setBackground(Ui.stroke(surface2, i, Ui.dp(this, 20.0f), Ui.dp(this, 1.0f)));
        cb.on(zArr[0]);
    }

    private void choice(String str, String[] strArr, String[] strArr2, String str2, final Scb scb) {
        LinearLayout row = Ui.row(this);
        row.setBackground(Ui.round(Ui.surface(), Ui.dp(this, 12.0f)));
        int dp = Ui.dp(this, 13.0f);
        row.setPadding(dp, dp, dp, dp);
        row.addView(Ui.text(this, str, 14.0f, Ui.text(), false));
        LinearLayout chips = Ui.row(this);
        for (int i = 0; i < strArr.length; i++) {
            final String str3 = strArr2[i];
            TextView chip = Ui.chip(this, strArr[i], str3.equals(str2) ? Ui.GOLD : Ui.muted());
            chip.setPadding(Ui.dp(this, 12.0f), Ui.dp(this, 6.0f), Ui.dp(this, 12.0f), Ui.dp(this, 6.0f));
            LinearLayout.LayoutParams lp = Ui.lp(-2, -2);
            lp.leftMargin = Ui.dp(this, 6.0f);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    scb.on(str3);
                }
            });
            chips.addView(chip, lp);
        }
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(chips);
        row.addView(hs, Ui.lp(0, -2, 1.0f));
        LinearLayout.LayoutParams lp2 = Ui.lp(-1, -2);
        lp2.topMargin = Ui.dp(this, 8.0f);
        this.body.addView(row, lp2);
    }

    private void info(String str, String str2) {
        LinearLayout column = Ui.column(this);
        column.setBackground(Ui.round(Ui.surface(), Ui.dp(this, 12.0f)));
        int dp = Ui.dp(this, 13.0f);
        column.setPadding(dp, dp, dp, dp);
        column.addView(Ui.text(this, str, 13.0f, Ui.text(), false));
        column.addView(Ui.text(this, str2, 11.0f, Ui.muted(), false));
        LinearLayout.LayoutParams lp = Ui.lp(-1, -2);
        lp.topMargin = Ui.dp(this, 8.0f);
        this.body.addView(column, lp);
    }
}
