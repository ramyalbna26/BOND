package com.ramyelbanna.juzoor;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.ramyelbanna.juzoor.core.LogManager;
import com.ramyelbanna.juzoor.engine.ConversionResult;
import com.ramyelbanna.juzoor.engine.ZipUtil;
import com.ramyelbanna.juzoor.ui.Ui;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Small on-device editor for the two files users most often need to touch
 * before exporting a recovered project: {@code AndroidManifest.xml} and
 * {@code app/build.gradle}.  The ZIP is rebuilt from the edited tree.
 */
public class ProjectEditorActivity extends BaseActivity {

    public static final String EXTRA_PROJECT = "project";
    public static final String EXTRA_ZIP = "zip";

    private File project;
    private File zip;
    private File current;
    private EditText editor;
    private TextView pathLabel;
    private TextView savedLabel;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        String projectPath = getIntent() == null ? null : getIntent().getStringExtra(EXTRA_PROJECT);
        String zipPath = getIntent() == null ? null : getIntent().getStringExtra(EXTRA_ZIP);
        this.project = projectPath == null ? null : new File(projectPath);
        this.zip = zipPath == null ? null : new File(zipPath);
        buildUi();
    }

    private void buildUi() {
        topBar(getString(R.string.editor_title), true);
        LinearLayout intro = Ui.card(this);
        intro.addView(Ui.text(this, getString(R.string.editor_sub), 13.0f, Ui.muted(), false));
        intro.addView(Ui.text(this, getString(R.string.editor_hint), 11.0f, Ui.GOLD, false));
        this.body.addView(intro);

        if (project == null || !project.isDirectory()) {
            this.body.addView(Ui.text(this, getString(R.string.editor_missing), 13.0f, Ui.ERROR, false));
            return;
        }

        LinearLayout tabs = Ui.row(this);
        LinearLayout.LayoutParams tlp = Ui.lp(-1, -2);
        tlp.topMargin = Ui.dp(this, 12.0f);
        this.body.addView(tabs, tlp);
        TextView manifest = Ui.button(this, getString(R.string.editor_manifest), false);
        manifest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                select(new File(project, "app/src/main/AndroidManifest.xml"));
            }
        });
        tabs.addView(manifest, Ui.lp(0, -2, 1.0f));
        final TextView gradle = Ui.button(this, getString(R.string.editor_gradle), false);
        LinearLayout.LayoutParams glp = Ui.lp(0, -2, 1.0f);
        glp.leftMargin = Ui.dp(this, 8.0f);
        gradle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                select(new File(project, "app/build.gradle"));
            }
        });
        tabs.addView(gradle, glp);

        this.pathLabel = Ui.text(this, "", 11.0f, Ui.GOLD, true);
        this.pathLabel.setPadding(0, Ui.dp(this, 12.0f), 0, Ui.dp(this, 6.0f));
        this.body.addView(this.pathLabel);

        this.editor = new EditText(this);
        this.editor.setTextColor(Ui.text());
        this.editor.setHintTextColor(Ui.muted());
        this.editor.setTypeface(Typeface.MONOSPACE);
        this.editor.setTextSize(11.0f);
        this.editor.setGravity(Gravity.TOP | Gravity.START);
        this.editor.setMinLines(18);
        this.editor.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        this.editor.setBackground(Ui.stroke(Ui.surface2(), 0x33D4AF37, Ui.dp(this, 12.0f), Ui.dp(this, 1.0f)));
        int pad = Ui.dp(this, 12.0f);
        this.editor.setPadding(pad, pad, pad, pad);
        this.editor.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (savedLabel != null) savedLabel.setText("");
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        this.body.addView(this.editor, Ui.lp(-1, Ui.dp(this, 360.0f)));

        this.savedLabel = Ui.text(this, "", 11.0f, Ui.SUCCESS, false);
        this.savedLabel.setPadding(0, Ui.dp(this, 8.0f), 0, 0);
        this.body.addView(this.savedLabel);

        LinearLayout actions = Ui.row(this);
        LinearLayout.LayoutParams alp = Ui.lp(-1, -2);
        alp.topMargin = Ui.dp(this, 10.0f);
        this.body.addView(actions, alp);
        TextView save = Ui.button(this, getString(R.string.editor_save), true);
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveCurrent(true);
            }
        });
        actions.addView(save, Ui.lp(0, -2, 1.0f));
        TextView reload = Ui.button(this, getString(R.string.editor_reload), false);
        LinearLayout.LayoutParams rlp = Ui.lp(0, -2, 1.0f);
        rlp.leftMargin = Ui.dp(this, 8.0f);
        reload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                load(current);
            }
        });
        actions.addView(reload, rlp);

        TextView export = Ui.button(this, getString(R.string.editor_export), false);
        LinearLayout.LayoutParams elp = Ui.lp(-1, -2);
        elp.topMargin = Ui.dp(this, 10.0f);
        export.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveCurrent(false);
                rezip();
            }
        });
        this.body.addView(export, elp);

        select(new File(project, "app/src/main/AndroidManifest.xml"));
    }

    private void select(File file) {
        if (file == null) return;
        if (current != null && editor != null) saveCurrent(false);
        load(file);
    }

    private void load(File file) {
        if (file == null || !file.isFile()) {
            if (editor != null) editor.setText("");
            if (pathLabel != null) pathLabel.setText(getString(R.string.editor_missing));
            return;
        }
        current = file;
        pathLabel.setText(file.getAbsolutePath().replace(project.getAbsolutePath() + "/", ""));
        editor.setText(read(file));
        editor.setSelection(editor.getText().length());
    }

    private void saveCurrent(boolean toast) {
        if (current == null || editor == null) return;
        try {
            write(current, editor.getText().toString());
            if (savedLabel != null) savedLabel.setText(getString(R.string.editor_saved));
            if (toast) toast(getString(R.string.editor_saved));
            LogManager.i("Project file saved: " + current.getAbsolutePath());
        } catch (Throwable t) {
            toast(getString(R.string.err_save));
            LogManager.e("Project file save failed: " + t);
        }
    }

    private void rezip() {
        if (project == null || !project.isDirectory()) return;
        try {
            File target = zip;
            if (target == null) target = new File(project.getParentFile(), project.getName() + "-source.zip");
            File temp = new File(project.getParentFile(), target.getName() + ".new");
            ZipUtil.zipDirectory(project, temp);
            copy(temp, target);
            temp.delete();
            ConversionResult result = ConversionService.getLastResult();
            if (result != null) result.zipFile = target;
            toast(getString(R.string.editor_exported, target.getName()));
            LogManager.s("Project re-exported: " + target.getAbsolutePath());
        } catch (Throwable t) {
            toast(getString(R.string.err_save));
            LogManager.e("Re-export failed: " + t);
        }
    }

    private static String read(File file) {
        try {
            InputStream in = new FileInputStream(file);
            byte[] buffer = new byte[(int) Math.min(file.length(), 4L * 1024 * 1024)];
            int read = in.read(buffer);
            in.close();
            if (read <= 0) return "";
            return new String(buffer, 0, read, "UTF-8");
        } catch (Throwable t) {
            return "";
        }
    }

    private static void write(File file, String text) throws Exception {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        OutputStream out = new FileOutputStream(file);
        out.write(text.getBytes("UTF-8"));
        out.close();
    }

    private static void copy(File from, File to) throws Exception {
        InputStream in = new FileInputStream(from);
        OutputStream out = new FileOutputStream(to);
        byte[] buffer = new byte[65536];
        int n;
        while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
        in.close();
        out.close();
    }
}
