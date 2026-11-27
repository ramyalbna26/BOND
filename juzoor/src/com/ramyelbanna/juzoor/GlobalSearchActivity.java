package com.ramyelbanna.juzoor;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.ramyelbanna.juzoor.core.LogManager;
import com.ramyelbanna.juzoor.engine.ConversionResult;
import com.ramyelbanna.juzoor.engine.GlobalSearch;
import com.ramyelbanna.juzoor.engine.ZipUtil;
import com.ramyelbanna.juzoor.ui.Ui;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Global regex search across every recovered file, including native libraries
 * and assets.  Results stream into a recycling list and open in the code editor.
 */
public class GlobalSearchActivity extends BaseActivity {

    public static final String EXTRA_PROJECT = "project";

    private File project;
    private EditText query;
    private TextView regexChip;
    private TextView caseChip;
    private TextView binaryChip;
    private TextView summary;
    private TextView searchButton;
    private SearchAdapter adapter;
    private boolean regex;
    private boolean caseSensitive;
    private boolean includeBinary = true;
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        String path = getIntent() == null ? null : getIntent().getStringExtra(EXTRA_PROJECT);
        ConversionResult r = ConversionService.getLastResult();
        if ((path == null || path.isEmpty()) && r != null && r.outputDir != null) {
            path = r.outputDir.getAbsolutePath();
        }
        this.project = path == null ? null : new File(path);
        buildUi();
    }

    private void buildUi() {
        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.bg());
        int pad = Ui.dp(this, 14.0f);
        root.setPadding(pad, Ui.dp(this, 12.0f), pad, Ui.dp(this, 6.0f));

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
        bar.addView(Ui.text(this, getString(R.string.search_title), 17.0f, Ui.text(), true),
                Ui.lp(0, -2, 1.0f));
        root.addView(bar);

        TextView where = Ui.text(this, project == null ? getString(R.string.search_no_project)
                : project.getAbsolutePath(), 10.0f, Ui.GOLD, false);
        where.setPadding(0, Ui.dp(this, 4.0f), 0, Ui.dp(this, 8.0f));
        root.addView(where);

        this.query = new EditText(this);
        this.query.setHint(getString(R.string.search_hint));
        this.query.setHintTextColor(Ui.muted());
        this.query.setTextColor(Ui.text());
        this.query.setTextSize(14.0f);
        this.query.setSingleLine(true);
        this.query.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        this.query.setOnEditorActionListener(new android.widget.TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(android.widget.TextView view, int actionId, android.view.KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    runSearch();
                    return true;
                }
                return false;
            }
        });
        this.query.setBackground(Ui.round(Ui.surface2(), Ui.dp(this, 10.0f)));
        int qp = Ui.dp(this, 12.0f);
        this.query.setPadding(qp, Ui.dp(this, 9.0f), qp, Ui.dp(this, 9.0f));
        root.addView(this.query, Ui.lp(-1, -2));

        LinearLayout chips = Ui.row(this);
        this.regexChip = toggleChip(getString(R.string.search_regex), regex);
        this.regexChip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                regex = !regex;
                style(regexChip, regex);
            }
        });
        chips.addView(this.regexChip);
        this.caseChip = toggleChip(getString(R.string.search_case), caseSensitive);
        LinearLayout.LayoutParams clp = Ui.lp(-2, -2);
        clp.leftMargin = Ui.dp(this, 6.0f);
        this.caseChip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                caseSensitive = !caseSensitive;
                style(caseChip, caseSensitive);
            }
        });
        chips.addView(this.caseChip, clp);
        this.binaryChip = toggleChip(getString(R.string.search_binary), includeBinary);
        LinearLayout.LayoutParams blp = Ui.lp(-2, -2);
        blp.leftMargin = Ui.dp(this, 6.0f);
        this.binaryChip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                includeBinary = !includeBinary;
                style(binaryChip, includeBinary);
            }
        });
        chips.addView(this.binaryChip, blp);
        LinearLayout.LayoutParams chlp = Ui.lp(-1, -2);
        chlp.topMargin = Ui.dp(this, 8.0f);
        root.addView(chips, chlp);

        LinearLayout actions = Ui.row(this);
        this.searchButton = Ui.button(this, getString(R.string.search_run), true);
        this.searchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                runSearch();
            }
        });
        actions.addView(this.searchButton, Ui.lp(0, -2, 1.0f));
        TextView export = Ui.button(this, getString(R.string.common_share), false);
        LinearLayout.LayoutParams elp = Ui.lp(0, -2, 1.0f);
        elp.leftMargin = Ui.dp(this, 8.0f);
        export.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                share();
            }
        });
        actions.addView(export, elp);
        LinearLayout.LayoutParams alp = Ui.lp(-1, -2);
        alp.topMargin = Ui.dp(this, 8.0f);
        root.addView(actions, alp);

        this.summary = Ui.text(this, "", 10.0f, Ui.muted(), false);
        this.summary.setPadding(0, Ui.dp(this, 6.0f), 0, Ui.dp(this, 4.0f));
        root.addView(this.summary);

        ListView list = new ListView(this);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setCacheColorHint(0);
        list.setBackgroundColor(Ui.bg());
        this.adapter = new SearchAdapter();
        list.setAdapter(this.adapter);
        root.addView(list, Ui.lp(-1, 0, 1.0f));
        setContentView(root);
    }

    private TextView toggleChip(String label, boolean active) {
        TextView chip = Ui.chip(this, label, active ? Ui.GOLD : Ui.muted());
        chip.setPadding(Ui.dp(this, 11.0f), Ui.dp(this, 5.0f),
                Ui.dp(this, 11.0f), Ui.dp(this, 5.0f));
        return chip;
    }

    private void style(TextView chip, boolean active) {
        chip.setTextColor(active ? Ui.GOLD : Ui.muted());
        chip.setBackground(Ui.stroke(Ui.surface2(), active ? Ui.GOLD : 0x33FFFFFF,
                Ui.dp(this, 20.0f), Ui.dp(this, 1.0f)));
    }

    private void runSearch() {
        if (project == null || !project.isDirectory()) {
            summary.setText(getString(R.string.search_no_project));
            return;
        }
        String text = query.getText().toString().trim();
        if (text.isEmpty()) {
            query.setError(getString(R.string.search_need_query));
            return;
        }
        final GlobalSearch.Request request = new GlobalSearch.Request();
        request.query = text;
        request.regex = regex;
        request.caseSensitive = caseSensitive;
        request.includeBinary = includeBinary;
        searchButton.setEnabled(false);
        summary.setText(getString(R.string.search_running));
        adapter.setHits(new ArrayList<GlobalSearch.Hit>());
        new Thread(new Runnable() {
            @Override
            public void run() {
                final GlobalSearch.Report report = GlobalSearch.search(project, request, null);
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        searchButton.setEnabled(true);
                        if (report.error != null) {
                            summary.setText(report.error);
                            return;
                        }
                        adapter.setHits(report.hits);
                        summary.setText(getString(R.string.search_summary, report.hits.size(),
                                report.filesMatched, report.filesScanned)
                                + (report.truncated ? " · " + getString(R.string.search_truncated) : ""));
                        LogManager.i("Global search finished: " + report.hits.size() + " hits");
                    }
                });
            }
        }, "juzoor-search").start();
    }

    private void share() {
        try {
            if (adapter == null || adapter.hits.isEmpty()) {
                toast(getString(R.string.search_empty));
                return;
            }
            StringBuilder b = new StringBuilder();
            b.append("Juzoor global search\n\n");
            for (GlobalSearch.Hit h : adapter.hits) {
                b.append(h.path);
                if (h.line > 0) b.append(':').append(h.line);
                b.append("  ").append(h.text).append('\n');
            }
            File out = new File(getCacheDir(), "juzoor-search.txt");
            ZipUtil.writeFile(out, b.toString());
            startActivity(Intent.createChooser(
                    com.ramyelbanna.juzoor.core.JuzoorFiles.shareIntent(this, out, getString(R.string.search_title)),
                    getString(R.string.search_title)));
        } catch (Throwable t) {
            toast(getString(R.string.err_save));
        }
    }

    private void openHit(GlobalSearch.Hit hit) {
        if (hit == null || project == null) return;
        File file = new File(project, hit.path);
        if (!file.isFile()) {
            toast(getString(R.string.search_file_missing));
            return;
        }
        Intent intent = new Intent(this, CodeEditorActivity.class);
        intent.putExtra(CodeEditorActivity.EXTRA_FILE, file.getAbsolutePath());
        intent.putExtra(CodeEditorActivity.EXTRA_PROJECT, project.getAbsolutePath());
        startActivity(intent);
    }

    private final class SearchAdapter extends BaseAdapter {
        final List<GlobalSearch.Hit> hits = new ArrayList<>();

        void setHits(List<GlobalSearch.Hit> value) {
            hits.clear();
            hits.addAll(value);
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return hits.size();
        }

        @Override
        public Object getItem(int position) {
            return hits.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout card;
            if (convertView == null) {
                card = Ui.column(GlobalSearchActivity.this);
                card.setPadding(Ui.dp(GlobalSearchActivity.this, 10.0f),
                        Ui.dp(GlobalSearchActivity.this, 8.0f),
                        Ui.dp(GlobalSearchActivity.this, 10.0f),
                        Ui.dp(GlobalSearchActivity.this, 8.0f));
                card.setBackground(Ui.round(Ui.surface(), Ui.dp(GlobalSearchActivity.this, 9.0f)));
                LinearLayout.LayoutParams lp = Ui.lp(-1, -2);
                lp.topMargin = Ui.dp(GlobalSearchActivity.this, 5.0f);
                card.setLayoutParams(lp);
                convertView = card;
            } else {
                card = (LinearLayout) convertView;
            }
            card.removeAllViews();
            GlobalSearch.Hit h = hits.get(position);
            LinearLayout top = Ui.row(GlobalSearchActivity.this);
            top.addView(Ui.text(GlobalSearchActivity.this, h.path, 10.0f, Ui.GOLD, false),
                    Ui.lp(0, -2, 1.0f));
            String where = h.binary ? "0x" + Long.toHexString(h.offset) : ":" + h.line;
            top.addView(Ui.text(GlobalSearchActivity.this, where, 9.0f, Ui.muted(), false));
            card.addView(top);
            TextView snippet = Ui.text(GlobalSearchActivity.this, h.text, 11.5f, Ui.text(), false);
            snippet.setTypeface(Typeface.MONOSPACE);
            snippet.setMaxLines(2);
            snippet.setPadding(0, Ui.dp(GlobalSearchActivity.this, 3.0f), 0, 0);
            card.addView(snippet);
            final GlobalSearch.Hit hit = h;
            card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    openHit(hit);
                }
            });
            return card;
        }
    }
}
