package com.ramyelbanna.juzoor;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.ramyelbanna.juzoor.core.LogManager;
import com.ramyelbanna.juzoor.engine.ConversionResult;
import com.ramyelbanna.juzoor.engine.Mapping;
import com.ramyelbanna.juzoor.engine.ZipUtil;
import com.ramyelbanna.juzoor.ui.Ui;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Offline code editor with lightweight Java / Kotlin / XML / JSON syntax
 * highlighting.  It edits any text file inside a recovered project and can
 * apply a {@code mapping.txt} to the whole tree.
 */
public class CodeEditorActivity extends BaseActivity {

    public static final String EXTRA_FILE = "file";
    public static final String EXTRA_PROJECT = "project";
    private static final int REQ_MAPPING = 91;

    private File project;
    private File file;
    private EditText editor;
    private TextView path;
    private TextView status;
    private TextView save;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable highlightTask = new Runnable() {
        @Override
        public void run() {
            highlight();
        }
    };

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        String filePath = getIntent() == null ? null : getIntent().getStringExtra(EXTRA_FILE);
        String projectPath = getIntent() == null ? null : getIntent().getStringExtra(EXTRA_PROJECT);
        if (projectPath == null && ConversionService.getLastResult() != null
                && ConversionService.getLastResult().outputDir != null) {
            projectPath = ConversionService.getLastResult().outputDir.getAbsolutePath();
        }
        this.project = projectPath == null ? null : new File(projectPath);
        this.file = filePath == null ? null : new File(filePath);
        buildUi();
    }

    private void buildUi() {
        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.bg());
        int pad = Ui.dp(this, 14.0f);
        root.setPadding(pad, Ui.dp(this, 12.0f), pad, pad);

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
        bar.addView(Ui.text(this, getString(R.string.code_title), 17.0f, Ui.text(), true),
                Ui.lp(0, -2, 1.0f));
        this.save = Ui.chip(this, getString(R.string.common_save), Ui.GOLD);
        this.save.setPadding(Ui.dp(this, 12.0f), Ui.dp(this, 6.0f), Ui.dp(this, 12.0f), Ui.dp(this, 6.0f));
        this.save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                save();
            }
        });
        bar.addView(this.save);
        root.addView(bar);

        this.path = Ui.text(this, file == null ? "" : file.getAbsolutePath(), 10.0f, Ui.GOLD, false);
        this.path.setPadding(0, Ui.dp(this, 4.0f), 0, Ui.dp(this, 6.0f));
        root.addView(this.path);

        this.editor = new EditText(this);
        this.editor.setTextColor(Ui.text());
        this.editor.setHintTextColor(Ui.muted());
        this.editor.setTypeface(Typeface.MONOSPACE);
        this.editor.setTextSize(12.0f);
        this.editor.setGravity(Gravity.TOP | Gravity.START);
        this.editor.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        this.editor.setBackground(Ui.stroke(Ui.surface2(), 0x33D4AF37, Ui.dp(this, 12.0f), Ui.dp(this, 1.0f)));
        int ep = Ui.dp(this, 12.0f);
        this.editor.setPadding(ep, ep, ep, ep);
        this.editor.setHorizontallyScrolling(true);
        this.editor.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (status != null) status.setText("");
            }

            @Override
            public void afterTextChanged(Editable s) {
                ui.removeCallbacks(highlightTask);
                ui.postDelayed(highlightTask, 120L);
            }
        });
        root.addView(this.editor, Ui.lp(-1, 0, 1.0f));

        this.status = Ui.text(this, "", 11.0f, Ui.muted(), false);
        this.status.setPadding(0, Ui.dp(this, 4.0f), 0, Ui.dp(this, 4.0f));
        root.addView(this.status);

        LinearLayout actions = Ui.row(this);
        TextView reload = Ui.button(this, getString(R.string.editor_reload), false);
        reload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                load();
            }
        });
        actions.addView(reload, Ui.lp(0, -2, 1.0f));
        TextView tree = Ui.button(this, getString(R.string.tree_title), false);
        LinearLayout.LayoutParams tlp = Ui.lp(0, -2, 1.0f);
        tlp.leftMargin = Ui.dp(this, 8.0f);
        tree.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(CodeEditorActivity.this, ProjectTreeActivity.class);
                if (project != null) intent.putExtra(ProjectTreeActivity.EXTRA_PROJECT, project.getAbsolutePath());
                startActivity(intent);
            }
        });
        actions.addView(tree, tlp);
        root.addView(actions);

        if (project != null && project.isDirectory()) {
            TextView mapping = Ui.button(this, getString(R.string.code_mapping), false);
            LinearLayout.LayoutParams mlp = Ui.lp(-1, -2);
            mlp.topMargin = Ui.dp(this, 8.0f);
            mapping.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    pickMapping();
                }
            });
            root.addView(mapping, mlp);
        }

        setContentView(root);
        load();
    }

    private void load() {
        if (file == null || !file.isFile()) {
            this.editor.setText("");
            this.path.setText(getString(R.string.editor_missing));
            return;
        }
        this.path.setText(file.getAbsolutePath());
        if (isBinary()) {
            this.editor.setText(getString(R.string.code_binary));
            this.editor.setEnabled(false);
            this.save.setEnabled(false);
            this.status.setText(getString(R.string.code_binary));
            return;
        }
        try {
            byte[] data = ZipUtil.readFile(file);
            this.editor.setText(new String(data, "UTF-8"));
            this.editor.setSelection(0);
            this.status.setText(getString(R.string.code_lines, countLines(new String(data, "UTF-8"))));
            highlight();
        } catch (Throwable t) {
            this.editor.setText(getString(R.string.err_generic, String.valueOf(t)));
            LogManager.e("Editor read failed: " + t);
        }
    }

    private void save() {
        if (file == null || !file.isFile() || isBinary()) return;
        try {
            ZipUtil.writeFile(file, editor.getText().toString());
            this.status.setText(getString(R.string.editor_saved));
            toast(getString(R.string.editor_saved));
            LogManager.i("File saved: " + file.getAbsolutePath());
        } catch (Throwable t) {
            toast(getString(R.string.err_save));
            LogManager.e("Editor save failed: " + t);
        }
    }

    private boolean isBinary() {
        if (file == null) return true;
        String n = file.getName().toLowerCase(java.util.Locale.US);
        return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg")
                || n.endsWith(".webp") || n.endsWith(".gif") || n.endsWith(".ttf")
                || n.endsWith(".otf") || n.endsWith(".woff") || n.endsWith(".woff2")
                || n.endsWith(".so") || n.endsWith(".dex") || n.endsWith(".arsc")
                || n.endsWith(".apk") || n.endsWith(".zip") || n.endsWith(".jar")
                || n.endsWith(".aar") || n.endsWith(".mp3") || n.endsWith(".mp4")
                || n.endsWith(".wav") || n.endsWith(".ogg") || n.endsWith(".p12")
                || n.endsWith(".jks") || n.endsWith(".keystore");
    }

    private void pickMapping() {
        save();
        Intent intent = new Intent(this, FileBrowserActivity.class);
        intent.putExtra("pick", true);
        intent.putExtra("all", true);
        startActivityForResult(intent, REQ_MAPPING);
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != REQ_MAPPING || result != RESULT_OK || data == null) return;
        String path = data.getStringExtra(ConversionService.EXTRA_PATH);
        if (path == null) return;
        final File mapping = new File(path);
        this.status.setText(getString(R.string.code_mapping_running));
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Mapping.Result r = Mapping.apply(project, mapping, null);
                String zipError = null;
                if (r.error == null) {
                    try {
                        ConversionResult last = ConversionService.getLastResult();
                        File zip = last != null && last.zipFile != null ? last.zipFile
                                : new File(project.getParentFile(), project.getName() + "-source.zip");
                        ZipUtil.zipDirectory(project, zip);
                    } catch (Throwable t) {
                        zipError = String.valueOf(t);
                    }
                }
                final String zerr = zipError;
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        status.setText(r.error != null ? r.error
                                : getString(R.string.code_mapping_done, r.line())
                                + (zerr == null ? "" : " · ZIP: " + zerr));
                        if (r.error == null) load();
                    }
                });
            }
        }, "juzoor-mapping").start();
    }

    /* --------------------------------------------------------- highlighting */

    private static final Pattern KEYWORDS = Pattern.compile(
            "\\b(package|import|class|interface|enum|extends|implements|public|private|protected|static|final|void|return|new|if|else|for|while|do|switch|case|break|continue|try|catch|finally|throw|throws|int|long|boolean|float|double|char|byte|short|null|true|false|this|super|fun|val|var|object|when|is|as|in|out|override|open|data|sealed|suspend|inline|internal|companion|const|lateinit|by|where|package|typealias|reified|inline|annotation|synchronized|volatile|transient|native|abstract|instanceof|assert|goto|const)\\b");
    private static final Pattern NUMBERS = Pattern.compile("\\b(0x[0-9a-fA-F]+|\\d+(\\.\\d+)?[fFdDlL]?)\\b");
    private static final Pattern ANNOTATION = Pattern.compile("@[A-Za-z_][A-Za-z0-9_.]*");
    private static final Pattern XML_TAG = Pattern.compile("</?[A-Za-z_][A-Za-z0-9_.:-]*|/?>");
    private static final Pattern STRING = Pattern.compile("\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'");
    private static final Pattern COMMENT = Pattern.compile("//[^\\n]*|/\\*[\\s\\S]*?\\*/|<!--[\\s\\S]*?-->");
    private static final int COLOR_KEYWORD = 0xFFE0B84C;
    private static final int COLOR_STRING = 0xFF8FD16A;
    private static final int COLOR_COMMENT = 0xFF7A8494;
    private static final int COLOR_NUMBER = 0xFF7CC7FF;
    private static final int COLOR_TAG = 0xFFFF9B6C;

    private void highlight() {
        if (editor == null) return;
        Editable e = editor.getText();
        if (e == null) return;
        String text = e.toString();
        if (text.length() > 400 * 1024) return;
        SyntaxSpan[] old = e.getSpans(0, e.length(), SyntaxSpan.class);
        for (SyntaxSpan span : old) e.removeSpan(span);
        String lower = text.toLowerCase(java.util.Locale.US);
        String fileName = file == null ? "" : file.getName().toLowerCase(java.util.Locale.US);
        if (lower.contains("<?xml") || fileName.endsWith(".xml")) {
            paint(e, XML_TAG, COLOR_TAG, text);
            paint(e, STRING, COLOR_STRING, text);
            paint(e, COMMENT, COLOR_COMMENT, text);
            return;
        }
        paint(e, KEYWORDS, COLOR_KEYWORD, text);
        paint(e, ANNOTATION, COLOR_KEYWORD, text);
        paint(e, NUMBERS, COLOR_NUMBER, text);
        paint(e, STRING, COLOR_STRING, text);
        paint(e, COMMENT, COLOR_COMMENT, text);
    }

    private static void paint(Editable e, Pattern p, int color, String text) {
        Matcher m = p.matcher(text);
        while (m.find()) {
            if (m.end() > m.start()) {
                e.setSpan(new SyntaxSpan(color), m.start(), m.end(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
    }

    private static final class SyntaxSpan extends ForegroundColorSpan {
        SyntaxSpan(int color) {
            super(color);
        }
    }

    private static int countLines(String s) {
        int n = 1;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == '\n') n++;
        return n;
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacks(highlightTask);
        super.onDestroy();
    }
}
