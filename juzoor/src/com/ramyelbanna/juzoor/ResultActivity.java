package com.ramyelbanna.juzoor;

import android.content.Intent;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.ramyelbanna.juzoor.core.LogManager;
import com.ramyelbanna.juzoor.core.Prefs;
import com.ramyelbanna.juzoor.engine.ConversionEngine;
import com.ramyelbanna.juzoor.engine.ConversionResult;
import com.ramyelbanna.juzoor.ui.Ui;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;

/* loaded from: /workspace/apk_extract/classes.dex */
public class ResultActivity extends BaseActivity {
    private static final int REQ_SAVE = 21;
    private ConversionResult r;

    @Override // com.ramyelbanna.juzoor.BaseActivity, android.app.Activity
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        this.r = ConversionService.getLastResult();
        buildUi();
    }

    private void buildUi() {
        topBar(getString(R.string.result_title), false);
        ConversionResult conversionResult = this.r;
        if (conversionResult == null) {
            this.body.addView(Ui.text(this, getString(R.string.result_none), 14.0f, Ui.muted(), false));
            return;
        }
        boolean z = conversionResult.success;
        LinearLayout card = Ui.card(this);
        String string = z ? getString(R.string.result_done) : getString(R.string.result_failed);
        int i = Ui.ERROR;
        card.addView(Ui.chip(this, string, z ? Ui.SUCCESS : Ui.ERROR));
        String string2 = z ? getString(R.string.result_done) : getString(R.string.result_failed);
        if (z) {
            i = Ui.text();
        }
        TextView text = Ui.text(this, string2, 22.0f, i, true);
        text.setPadding(0, Ui.dp(this, 10.0f), 0, 0);
        card.addView(text);
        if (!z && this.r.error != null) {
            card.addView(Ui.text(this, this.r.error, 12.0f, Ui.muted(), false));
        }
        this.body.addView(card);
        if (z) {
            LinearLayout card2 = Ui.card(this);
            addRow(card2, getString(R.string.result_classes), String.valueOf(this.r.classCount));
            addRow(card2, getString(R.string.result_files), String.valueOf(this.r.fileCount));
            addRow(card2, getString(R.string.result_resources), String.valueOf(this.r.resourceCount));
            addRow(card2, getString(R.string.result_dex), String.valueOf(this.r.dexCount));
            addRow(card2, getString(R.string.result_size), ConversionEngine.human(this.r.bytes));
            addRow(card2, getString(R.string.result_time), (this.r.durationMs / 1000) + "s");
            if (this.r.packageName != null) {
                addRow(card2, "Package", this.r.packageName);
            }
            if (this.r.frameworkLabel != null && !this.r.frameworkLabel.isEmpty()) {
                addRow(card2, getString(R.string.result_framework), this.r.frameworkLabel
                        + (this.r.frameworkAssets > 0 ? " · " + this.r.frameworkAssets : ""));
            }
            if (this.r.isBundle) {
                addRow(card2, getString(R.string.result_bundle), getString(R.string.result_bundle_value, new Object[]{this.r.bundleKind, Integer.valueOf(Math.max(1, this.r.splitCount))}));
            }
            if (this.r.nativeLibCount > 0) {
                addRow(card2, getString(R.string.result_native), this.r.nativeLibCount + " (" + this.r.nativeDecryptedCount + " " + getString(R.string.result_decrypted) + ")");
            }
            if (this.r.packerCount > 0) {
                addRow(card2, getString(R.string.result_packers), TextUtils.join(", ", this.r.packers));
            }
            if (this.r.libraryCount > 0) {
                addRow(card2, getString(R.string.result_libraries), String.valueOf(this.r.libraryCount));
            }
            if (this.r.stringTotal > 0) {
                addRow(card2, getString(R.string.result_strings), this.r.stringTotal + " / " + this.r.stringRecovered + " " + getString(R.string.result_recovered));
            }
            LinearLayout.LayoutParams lp = Ui.lp(-1, -2);
            lp.topMargin = Ui.dp(this, 12.0f);
            this.body.addView(card2, lp);
            if (this.r.reportFile != null && this.r.reportFile.exists()) {
                TextView report = Ui.button(this, getString(R.string.result_report), false);
                LinearLayout.LayoutParams lpr = Ui.lp(-1, -2);
                lpr.topMargin = Ui.dp(this, 10.0f);
                report.setOnClickListener(new View.OnClickListener() { // from class: com.ramyelbanna.juzoor.ResultActivity$$ExternalSyntheticLambda4
                    @Override // android.view.View.OnClickListener
                    public final void onClick(View view) {
                        ResultActivity.this.openReport();
                    }
                });
                this.body.addView(report, lpr);
            }
            if (this.r.security != null && this.r.security.markdown != null && this.r.security.markdown.exists()) {
                TextView sec = Ui.button(this, getString(R.string.result_view_security), false);
                LinearLayout.LayoutParams lps = Ui.lp(-1, -2);
                lps.topMargin = Ui.dp(this, 10.0f);
                sec.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        shareText(ResultActivity.this.r.security.markdown, getString(R.string.result_security));
                    }
                });
                this.body.addView(sec, lps);
            }
            java.io.File index = this.r.outputDir == null ? null
                    : new java.io.File(this.r.outputDir, "PROJECT_INDEX.md");
            if (index != null && index.isFile()) {
                TextView idx = Ui.button(this, getString(R.string.result_index), false);
                LinearLayout.LayoutParams lpi = Ui.lp(-1, -2);
                lpi.topMargin = Ui.dp(this, 10.0f);
                final java.io.File indexFile = index;
                idx.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        shareText(indexFile, getString(R.string.result_index));
                    }
                });
                this.body.addView(idx, lpi);
            }
            if (this.r.outputDir != null && this.r.outputDir.isDirectory()) {
                TextView browse = Ui.button(this, getString(R.string.result_view_tree), false);
                LinearLayout.LayoutParams lpb = Ui.lp(-1, -2);
                lpb.topMargin = Ui.dp(this, 10.0f);
                browse.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Intent tree = new Intent(ResultActivity.this, ProjectTreeActivity.class);
                        tree.putExtra(ProjectTreeActivity.EXTRA_PROJECT, r.outputDir.getAbsolutePath());
                        startActivity(tree);
                    }
                });
                this.body.addView(browse, lpb);
                TextView find = Ui.button(this, getString(R.string.home_tool_search), false);
                LinearLayout.LayoutParams lpf = Ui.lp(-1, -2);
                lpf.topMargin = Ui.dp(this, 10.0f);
                find.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Intent search = new Intent(ResultActivity.this, GlobalSearchActivity.class);
                        search.putExtra(GlobalSearchActivity.EXTRA_PROJECT, r.outputDir.getAbsolutePath());
                        startActivity(search);
                    }
                });
                this.body.addView(find, lpf);
                TextView assets = Ui.button(this, getString(R.string.assets_title), false);
                LinearLayout.LayoutParams lpa = Ui.lp(-1, -2);
                lpa.topMargin = Ui.dp(this, 10.0f);
                assets.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Intent asset = new Intent(ResultActivity.this, AssetsViewerActivity.class);
                        asset.putExtra(AssetsViewerActivity.EXTRA_PROJECT, r.outputDir.getAbsolutePath());
                        startActivity(asset);
                    }
                });
                this.body.addView(assets, lpa);
            }
            if (this.r.treeFile != null && this.r.treeFile.exists()) {
                TextView tree = Ui.button(this, getString(R.string.result_tree), false);
                LinearLayout.LayoutParams lpt = Ui.lp(-1, -2);
                lpt.topMargin = Ui.dp(this, 10.0f);
                tree.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        shareText(ResultActivity.this.r.treeFile, getString(R.string.result_tree));
                    }
                });
                this.body.addView(tree, lpt);
            }
            if (this.r.outputDir != null && this.r.outputDir.isDirectory()) {
                TextView edit = Ui.button(this, getString(R.string.result_edit_project), false);
                LinearLayout.LayoutParams lpe = Ui.lp(-1, -2);
                lpe.topMargin = Ui.dp(this, 10.0f);
                edit.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Intent editor = new Intent(ResultActivity.this, ProjectEditorActivity.class);
                        editor.putExtra(ProjectEditorActivity.EXTRA_PROJECT, r.outputDir.getAbsolutePath());
                        if (r.zipFile != null) {
                            editor.putExtra(ProjectEditorActivity.EXTRA_ZIP, r.zipFile.getAbsolutePath());
                        }
                        startActivity(editor);
                    }
                });
                this.body.addView(edit, lpe);
            }
            TextView button = Ui.button(this, getString(R.string.result_zip), true);
            LinearLayout.LayoutParams lp2 = Ui.lp(-1, -2);
            lp2.topMargin = Ui.dp(this, 18.0f);
            button.setOnClickListener(new View.OnClickListener() { // from class: com.ramyelbanna.juzoor.ResultActivity$$ExternalSyntheticLambda0
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    ResultActivity.this.lambda$buildUi$0(view);
                }
            });
            this.body.addView(button, lp2);
            TextView button2 = Ui.button(this, getString(R.string.result_quick_save), false);
            LinearLayout.LayoutParams lp3 = Ui.lp(-1, -2);
            lp3.topMargin = Ui.dp(this, 10.0f);
            button2.setOnClickListener(new View.OnClickListener() { // from class: com.ramyelbanna.juzoor.ResultActivity$$ExternalSyntheticLambda1
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    ResultActivity.this.lambda$buildUi$1(view);
                }
            });
            this.body.addView(button2, lp3);
        }
        TextView button3 = Ui.button(this, getString(R.string.menu_logcat), false);
        LinearLayout.LayoutParams lp4 = Ui.lp(-1, -2);
        lp4.topMargin = Ui.dp(this, 10.0f);
        button3.setOnClickListener(new View.OnClickListener() { // from class: com.ramyelbanna.juzoor.ResultActivity$$ExternalSyntheticLambda2
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                ResultActivity.this.lambda$buildUi$2(view);
            }
        });
        this.body.addView(button3, lp4);
        TextView button4 = Ui.button(this, getString(R.string.common_home), false);
        LinearLayout.LayoutParams lp5 = Ui.lp(-1, -2);
        lp5.topMargin = Ui.dp(this, 10.0f);
        button4.setOnClickListener(new View.OnClickListener() { // from class: com.ramyelbanna.juzoor.ResultActivity$$ExternalSyntheticLambda3
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                ResultActivity.this.lambda$buildUi$3(view);
            }
        });
        this.body.addView(button4, lp5);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$buildUi$0(View view) {
        pickDestination();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$buildUi$1(View view) {
        quickSave();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$buildUi$2(View view) {
        open(LogcatActivity.class);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$buildUi$3(View view) {
        Intent intent = new Intent(this, (Class<?>) MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }

    private void openReport() {
        shareText(this.r.reportFile, getString(R.string.result_report));
    }

    private void shareText(File f, String subject) {
        try {
            if (f == null || !f.exists()) return;
            StringBuilder sb = new StringBuilder();
            java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(new FileInputStream(f), "UTF-8"));
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
            br.close();
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_SUBJECT, "Juzoor · " + subject);
            send.putExtra(Intent.EXTRA_TEXT, sb.toString());
            startActivity(Intent.createChooser(send, subject));
            LogManager.i("Shared: " + f.getName());
        } catch (Throwable t) {
            LogManager.e("Share failed: " + t);
            toast(getString(R.string.err_save));
        }
    }

    private void addRow(LinearLayout linearLayout, String str, String str2) {
        LinearLayout row = Ui.row(this);
        row.setPadding(0, Ui.dp(this, 6.0f), 0, Ui.dp(this, 6.0f));
        row.addView(Ui.text(this, str, 13.0f, Ui.muted(), false), Ui.lp(0, -2, 1.0f));
        row.addView(Ui.text(this, str2, 13.0f, Ui.text(), true));
        linearLayout.addView(row);
    }

    private void pickDestination() {
        ConversionResult conversionResult = this.r;
        if (conversionResult == null || conversionResult.zipFile == null) {
            return;
        }
        Intent intent = new Intent("android.intent.action.CREATE_DOCUMENT");
        intent.addCategory("android.intent.category.OPENABLE");
        intent.setType("application/zip");
        intent.putExtra("android.intent.extra.TITLE", this.r.zipFile.getName());
        try {
            startActivityForResult(intent, REQ_SAVE);
        } catch (Exception unused) {
            toast(getString(R.string.err_save));
        }
    }

    private void quickSave() {
        ConversionResult conversionResult = this.r;
        if (conversionResult == null || conversionResult.zipFile == null) {
            return;
        }
        if (!Prefs.saveTreeUri().isEmpty() && saveToTree(conversionResult.zipFile)) {
            return;
        }
        try {
            File file = new File(Prefs.savePath());
            if (!file.exists()) {
                file.mkdirs();
            }
            File file2 = new File(file, this.r.zipFile.getName());
            FileInputStream fileInputStream = new FileInputStream(this.r.zipFile);
            FileOutputStream fileOutputStream = new FileOutputStream(file2);
            byte[] bArr = new byte[65536];
            while (true) {
                int read = fileInputStream.read(bArr);
                if (read <= 0) {
                    fileInputStream.close();
                    fileOutputStream.close();
                    LogManager.s("ZIP saved to " + file2.getAbsolutePath());
                    toast(getString(R.string.result_saved) + ": " + file2.getAbsolutePath());
                    return;
                }
                fileOutputStream.write(bArr, 0, read);
            }
        } catch (Throwable th) {
            LogManager.e("Save failed: " + String.valueOf(th));
            toast(getString(R.string.err_save));
        }
    }

    /**
     * Saves through the Storage Access Framework tree the user picked in
     * Settings. This avoids treating a SAF tree URI such as
     * {@code /tree/primary:Download} as a filesystem path.
     */
    private boolean saveToTree(File zipFile) {
        try {
            Uri tree = Uri.parse(Prefs.saveTreeUri());
            String docId = DocumentsContract.getTreeDocumentId(tree);
            Uri parent = DocumentsContract.buildDocumentUriUsingTree(tree, docId);
            Uri target = DocumentsContract.createDocument(getContentResolver(), parent,
                    "application/zip", zipFile.getName());
            if (target == null) return false;
            java.io.InputStream in = new FileInputStream(zipFile);
            java.io.OutputStream out = getContentResolver().openOutputStream(target);
            if (out == null) {
                in.close();
                return false;
            }
            byte[] buffer = new byte[65536];
            int n;
            while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
            in.close();
            out.close();
            LogManager.s("ZIP saved through SAF: " + target);
            toast(getString(R.string.result_saved));
            return true;
        } catch (Throwable t) {
            LogManager.w("SAF save failed, falling back: " + t);
            return false;
        }
    }

    @Override // android.app.Activity
    protected void onActivityResult(int i, int i2, Intent intent) {
        super.onActivityResult(i, i2, intent);
        if (i != REQ_SAVE || i2 != -1 || intent == null || intent.getData() == null || this.r == null) {
            return;
        }
        Uri data = intent.getData();
        try {
            FileInputStream fileInputStream = new FileInputStream(this.r.zipFile);
            OutputStream openOutputStream = getContentResolver().openOutputStream(data);
            byte[] bArr = new byte[65536];
            while (true) {
                int read = fileInputStream.read(bArr);
                if (read <= 0) {
                    fileInputStream.close();
                    openOutputStream.close();
                    LogManager.s("ZIP exported to " + String.valueOf(data));
                    toast(getString(R.string.result_zip_done));
                    return;
                }
                openOutputStream.write(bArr, 0, read);
            }
        } catch (Throwable th) {
            LogManager.e("Export failed: " + String.valueOf(th));
            toast(getString(R.string.err_save));
        }
    }
}
