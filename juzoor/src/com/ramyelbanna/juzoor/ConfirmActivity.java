package com.ramyelbanna.juzoor;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.ramyelbanna.juzoor.core.LogManager;
import com.ramyelbanna.juzoor.core.Prefs;
import com.ramyelbanna.juzoor.engine.ConversionEngine;
import com.ramyelbanna.juzoor.engine.SplitBundle;
import com.ramyelbanna.juzoor.ui.Ui;
import java.io.File;

/* loaded from: /workspace/apk_extract/classes.dex */
public class ConfirmActivity extends BaseActivity {
    private LinearLayout optsHolder;
    private String path;

    interface OnToggle {
        void on(boolean z);
    }

    @Override // com.ramyelbanna.juzoor.BaseActivity, android.app.Activity
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        this.path = getIntent().getStringExtra(ConversionService.EXTRA_PATH);
        buildUi();
    }

    private void buildUi() {
        topBar(getString(R.string.confirm_title), true);
        if (this.path == null) {
            toast("No file");
            finish();
            return;
        }
        final File file = new File(this.path);
        LinearLayout card = Ui.card(this);
        card.addView(Ui.text(this, getString(R.string.confirm_file), 11.0f, Ui.GOLD, true));
        card.addView(Ui.text(this, file.getName(), 20.0f, Ui.text(), true));
        card.addView(Ui.text(this, ConversionEngine.human(file.length()) + "  ·  " + file.getParent(), 12.0f, Ui.muted(), false));
        String badge = SplitBundle.badge(file.getName());
        if (!"APK".equals(badge)) {
            int countApks = SplitBundle.countApks(file);
            LinearLayout row = Ui.row(this);
            row.setPadding(0, Ui.dp(this, 10.0f), 0, 0);
            row.addView(Ui.chip(this, badge, Ui.GOLD));
            row.addView(Ui.spacer(this, 8));
            if (countApks > 1) {
                row.addView(Ui.chip(this, getString(R.string.confirm_splits, new Object[]{Integer.valueOf(countApks)}), Ui.GOLD_LIGHT));
            } else {
                row.addView(Ui.chip(this, getString(R.string.confirm_bundle), Ui.GOLD_LIGHT));
            }
            card.addView(row);
            TextView text = Ui.text(this, getString(R.string.confirm_bundle_note), 11.5f, Ui.muted(), false);
            text.setPadding(0, Ui.dp(this, 8.0f), 0, 0);
            card.addView(text);
        }
        this.body.addView(card);
        TextView text2 = Ui.text(this, getString(R.string.confirm_warning), 12.0f, Ui.WARNING, false);
        text2.setPadding(0, Ui.dp(this, 12.0f), 0, 0);
        this.body.addView(text2);
        final boolean aab = file.getName().toLowerCase(java.util.Locale.US).endsWith(".aab");
        if (aab) {
            TextView aabNote = Ui.text(this, getString(R.string.err_aab), 12.0f, Ui.ERROR, false);
            aabNote.setPadding(0, Ui.dp(this, 10.0f), 0, 0);
            this.body.addView(aabNote);
        }
        TextView text3 = Ui.text(this, getString(R.string.confirm_options), 12.0f, Ui.GOLD, true);
        text3.setPadding(0, Ui.dp(this, 20.0f), 0, Ui.dp(this, 6.0f));
        this.body.addView(text3);
        this.optsHolder = Ui.column(this);
        this.body.addView(this.optsHolder);
        renderOptions();
        TextView button = Ui.button(this, getString(R.string.confirm_start), true);
        LinearLayout.LayoutParams lp = Ui.lp(-1, -2);
        lp.topMargin = Ui.dp(this, 22.0f);
        button.setOnClickListener(new View.OnClickListener() { // from class: com.ramyelbanna.juzoor.ConfirmActivity$$ExternalSyntheticLambda4
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                ConfirmActivity.this.lambda$buildUi$0(file, view);
            }
        });
        if (aab) {
            button.setEnabled(false);
            button.setAlpha(0.5f);
        }
        this.body.addView(button, lp);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$buildUi$0(File file, View view) {
        LogManager.i("Confirmation accepted for " + file.getName());
        Intent intent = new Intent(this, (Class<?>) LiveCanvasActivity.class);
        intent.putExtra(ConversionService.EXTRA_PATH, this.path);
        startActivity(intent);
        finish();
    }

    private void renderOptions() {
        this.optsHolder.removeAllViews();
        addToggle(getString(R.string.confirm_opt_resources), getString(R.string.confirm_opt_resources_d), Prefs.decodeResources(), new OnToggle() { // from class: com.ramyelbanna.juzoor.ConfirmActivity$$ExternalSyntheticLambda0
            @Override // com.ramyelbanna.juzoor.ConfirmActivity.OnToggle
            public final void on(boolean z) {
                Prefs.setDecodeResources(z);
            }
        });
        addToggle(getString(R.string.confirm_opt_smali), getString(R.string.confirm_opt_smali_d), Prefs.disassembleDex(), new OnToggle() { // from class: com.ramyelbanna.juzoor.ConfirmActivity$$ExternalSyntheticLambda1
            @Override // com.ramyelbanna.juzoor.ConfirmActivity.OnToggle
            public final void on(boolean z) {
                Prefs.setDisassembleDex(z);
            }
        });
        addToggle(getString(R.string.confirm_opt_project), getString(R.string.confirm_opt_project_d), Prefs.generateProject(), new OnToggle() { // from class: com.ramyelbanna.juzoor.ConfirmActivity$$ExternalSyntheticLambda2
            @Override // com.ramyelbanna.juzoor.ConfirmActivity.OnToggle
            public final void on(boolean z) {
                Prefs.setGenerateProject(z);
            }
        });
        addToggle(getString(R.string.confirm_opt_assets), getString(R.string.confirm_opt_assets_d), Prefs.copyAssets(), new OnToggle() { // from class: com.ramyelbanna.juzoor.ConfirmActivity$$ExternalSyntheticLambda3
            @Override // com.ramyelbanna.juzoor.ConfirmActivity.OnToggle
            public final void on(boolean z) {
                Prefs.setCopyAssets(z);
            }
        });
        addToggle(getString(R.string.confirm_opt_native), getString(R.string.confirm_opt_native_d), Prefs.decryptNative(), new OnToggle() {
            @Override
            public void on(boolean z) {
                Prefs.setDecryptNative(z);
            }
        });
        addToggle(getString(R.string.confirm_opt_strings), getString(R.string.confirm_opt_strings_d), Prefs.decryptStrings(), new OnToggle() {
            @Override
            public void on(boolean z) {
                Prefs.setDecryptStrings(z);
            }
        });
        addToggle(getString(R.string.confirm_opt_libs), getString(R.string.confirm_opt_libs_d), Prefs.analyzeLibraries(), new OnToggle() {
            @Override
            public void on(boolean z) {
                Prefs.setAnalyzeLibraries(z);
            }
        });
        addToggle(getString(R.string.confirm_opt_report), getString(R.string.confirm_opt_report_d), Prefs.writeReport(), new OnToggle() {
            @Override
            public void on(boolean z) {
                Prefs.setWriteReport(z);
            }
        });
        addToggle(getString(R.string.confirm_opt_source), getString(R.string.confirm_opt_source_d), Prefs.javaTree(), new OnToggle() {
            @Override
            public void on(boolean z) {
                Prefs.setJavaTree(z);
            }
        });
        addToggle(getString(R.string.confirm_opt_kotlin), getString(R.string.confirm_opt_kotlin_d), Prefs.kotlinStubs(), new OnToggle() {
            @Override
            public void on(boolean z) {
                Prefs.setKotlinStubs(z);
            }
        });
        addToggle(getString(R.string.confirm_opt_security), getString(R.string.confirm_opt_security_d), Prefs.securityScan(), new OnToggle() {
            @Override
            public void on(boolean z) {
                Prefs.setSecurityScan(z);
            }
        });
    }

    private void addToggle(String str, String str2, boolean z, final OnToggle onToggle) {
        LinearLayout row = Ui.row(this);
        row.setBackground(Ui.round(Ui.surface(), Ui.dp(this, 12.0f)));
        int dp = Ui.dp(this, 13.0f);
        row.setPadding(dp, dp, dp, dp);
        LinearLayout column = Ui.column(this);
        column.addView(Ui.text(this, str, 14.0f, Ui.text(), false));
        column.addView(Ui.text(this, str2, 11.0f, Ui.muted(), false));
        row.addView(column, Ui.lp(0, -2, 1.0f));
        final boolean[] zArr = {z};
        String str3 = z ? getString(R.string.settings_on) : getString(R.string.settings_off);
        int i = Ui.SUCCESS;
        final TextView text = Ui.text(this, str3, 12.0f, z ? Ui.SUCCESS : Ui.muted(), true);
        text.setPadding(Ui.dp(this, 12.0f), Ui.dp(this, 6.0f), Ui.dp(this, 12.0f), Ui.dp(this, 6.0f));
        int surface2 = Ui.surface2();
        if (!z) {
            i = 872415231;
        }
        text.setBackground(Ui.stroke(surface2, i, Ui.dp(this, 20.0f), Ui.dp(this, 1.0f)));
        row.addView(text);
        row.setClickable(true);
        row.setOnClickListener(new View.OnClickListener() { // from class: com.ramyelbanna.juzoor.ConfirmActivity$$ExternalSyntheticLambda5
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                ConfirmActivity.this.lambda$addToggle$5(zArr, text, onToggle, view);
            }
        });
        LinearLayout.LayoutParams lp = Ui.lp(-1, -2);
        lp.topMargin = Ui.dp(this, 8.0f);
        this.optsHolder.addView(row, lp);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$addToggle$5(boolean[] zArr, TextView textView, OnToggle onToggle, View view) {
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
        onToggle.on(zArr[0]);
    }
}
