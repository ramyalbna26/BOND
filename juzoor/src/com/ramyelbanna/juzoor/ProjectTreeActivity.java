package com.ramyelbanna.juzoor;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.ramyelbanna.juzoor.core.LogManager;
import com.ramyelbanna.juzoor.engine.ConversionEngine;
import com.ramyelbanna.juzoor.engine.ConversionResult;
import com.ramyelbanna.juzoor.ui.Ui;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Lazy project tree.
 *
 * <p>The screen never walks the whole recovered project.  It lists one folder
 * at a time and inserts a folder's immediate children only when the user opens
 * it, so a project with 18,000+ files opens instantly.  The list is backed by
 * a recycling {@link ListView}; pinch-to-zoom changes the row scale and the
 * list pans with a normal drag.</p>
 */
public class ProjectTreeActivity extends BaseActivity {

    public static final String EXTRA_PROJECT = "project";
    public static final String EXTRA_FILE = "file";
    private static final int MAX_CHILDREN = 20000;

    private File project;
    private File root;
    private TreeList list;
    private TreeAdapter adapter;
    private TextView pathLabel;
    private TextView status;
    private float zoom = 1.0f;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        String projectPath = getIntent() == null ? null : getIntent().getStringExtra(EXTRA_PROJECT);
        ConversionResult result = ConversionService.getLastResult();
        if ((projectPath == null || projectPath.isEmpty()) && result != null && result.outputDir != null) {
            projectPath = result.outputDir.getAbsolutePath();
        }
        this.project = projectPath == null ? null : new File(projectPath);
        String filePath = getIntent() == null ? null : getIntent().getStringExtra(EXTRA_FILE);
        this.root = filePath != null && new File(filePath).isDirectory()
                ? new File(filePath) : this.project;
        buildUi();
        if (root != null && root.isDirectory()) loadRoot();
    }

    private void buildUi() {
        LinearLayout rootView = Ui.column(this);
        rootView.setBackgroundColor(Ui.bg());
        int pad = Ui.dp(this, 14.0f);
        rootView.setPadding(pad, Ui.dp(this, 12.0f), pad, Ui.dp(this, 8.0f));

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
        bar.addView(Ui.text(this, getString(R.string.tree_title), 17.0f, Ui.text(), true),
                Ui.lp(0, -2, 1.0f));
        TextView search = Ui.chip(this, getString(R.string.search_title), Ui.GOLD);
        search.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(ProjectTreeActivity.this, GlobalSearchActivity.class);
                if (project != null) intent.putExtra(GlobalSearchActivity.EXTRA_PROJECT, project.getAbsolutePath());
                startActivity(intent);
            }
        });
        bar.addView(search);
        rootView.addView(bar);

        this.pathLabel = Ui.text(this, root == null ? "" : root.getAbsolutePath(), 10.0f, Ui.GOLD, false);
        this.pathLabel.setPadding(0, Ui.dp(this, 4.0f), 0, Ui.dp(this, 6.0f));
        rootView.addView(this.pathLabel);

        if (project == null || !project.isDirectory()) {
            this.status = Ui.text(this, getString(R.string.tree_none), 13.0f, Ui.muted(), false);
            rootView.addView(this.status);
            setContentView(rootView);
            return;
        }

        LinearLayout tools = Ui.row(this);
        TextView zoomOut = Ui.chip(this, "A−", Ui.GOLD);
        zoomOut.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setZoom(zoom - 0.1f);
            }
        });
        tools.addView(zoomOut);
        TextView zoomIn = Ui.chip(this, "A+", Ui.GOLD);
        LinearLayout.LayoutParams zlp = Ui.lp(-2, -2);
        zlp.leftMargin = Ui.dp(this, 6.0f);
        zoomIn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setZoom(zoom + 0.1f);
            }
        });
        tools.addView(zoomIn, zlp);
        TextView rootButton = Ui.chip(this, getString(R.string.tree_root), Ui.GOLD);
        LinearLayout.LayoutParams rlp = Ui.lp(-2, -2);
        rlp.leftMargin = Ui.dp(this, 6.0f);
        rootButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                loadRoot();
            }
        });
        tools.addView(rootButton, rlp);
        TextView assets = Ui.chip(this, getString(R.string.assets_title), Ui.GOLD);
        LinearLayout.LayoutParams alp = Ui.lp(-2, -2);
        alp.leftMargin = Ui.dp(this, 6.0f);
        assets.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(ProjectTreeActivity.this, AssetsViewerActivity.class);
                intent.putExtra(AssetsViewerActivity.EXTRA_PROJECT, project.getAbsolutePath());
                startActivity(intent);
            }
        });
        tools.addView(assets, alp);
        rootView.addView(tools);

        this.status = Ui.text(this, "", 10.0f, Ui.muted(), false);
        this.status.setPadding(0, Ui.dp(this, 6.0f), 0, Ui.dp(this, 4.0f));
        rootView.addView(this.status);

        this.list = new TreeList(this);
        this.list.setDivider(null);
        this.list.setDividerHeight(0);
        this.list.setCacheColorHint(0);
        this.list.setBackgroundColor(Ui.bg());
        this.list.setOnScaleListener(new TreeList.ScaleListener() {
            @Override
            public void onScale(float factor) {
                setZoom(zoom * factor);
            }
        });
        this.adapter = new TreeAdapter();
        this.list.setAdapter(this.adapter);
        rootView.addView(this.list, Ui.lp(-1, 0, 1.0f));
        setContentView(rootView);
    }

    private void loadRoot() {
        if (root == null || !root.isDirectory()) return;
        List<Node> nodes = new ArrayList<>();
        addChildren(nodes, root, 0);
        adapter.setNodes(nodes);
        pathLabel.setText(root.getAbsolutePath());
        status.setText(getString(R.string.tree_lazy, nodes.size()));
        list.setSelection(0);
    }

    private void toggle(Node node) {
        if (node == null || !node.dir) {
            openFile(node);
            return;
        }
        int index = adapter.nodes.indexOf(node);
        if (index < 0) return;
        if (node.expanded) {
            int end = index + 1;
            while (end < adapter.nodes.size() && adapter.nodes.get(end).depth > node.depth) end++;
            for (int i = end - 1; i > index; i--) adapter.nodes.remove(i);
            node.expanded = false;
            node.loaded = false;
            adapter.notifyDataSetChanged();
            return;
        }
        List<Node> children = new ArrayList<>();
        addChildren(children, node.file, node.depth + 1);
        adapter.nodes.addAll(index + 1, children);
        node.expanded = true;
        node.loaded = true;
        adapter.notifyDataSetChanged();
        status.setText(getString(R.string.tree_lazy, adapter.nodes.size()));
    }

    private void addChildren(List<Node> out, File dir, int depth) {
        File[] files = dir.listFiles();
        if (files == null) return;
        List<File> list = new ArrayList<>(Arrays.asList(files));
        list.sort(new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });
        int count = 0;
        for (File f : list) {
            if (count++ >= MAX_CHILDREN) break;
            Node n = new Node();
            n.file = f;
            n.name = f.getName();
            n.dir = f.isDirectory();
            n.depth = depth;
            out.add(n);
        }
    }

    private void openFile(Node node) {
        if (node == null || node.dir) return;
        Intent intent = new Intent(this, CodeEditorActivity.class);
        intent.putExtra(CodeEditorActivity.EXTRA_FILE, node.file.getAbsolutePath());
        if (project != null) intent.putExtra(CodeEditorActivity.EXTRA_PROJECT, project.getAbsolutePath());
        startActivity(intent);
        LogManager.d("Tree opened: " + node.file.getAbsolutePath());
    }

    private void setZoom(float value) {
        zoom = Math.max(0.75f, Math.min(1.6f, value));
        adapter.notifyDataSetChanged();
        status.setText(String.format(Locale.US, "%.0f%% · %s", zoom * 100, getString(R.string.tree_lazy, adapter.getCount())));
    }

    private static final class Node {
        File file;
        String name;
        boolean dir;
        boolean expanded;
        boolean loaded;
        int depth;
    }

    private final class TreeAdapter extends BaseAdapter {
        final List<Node> nodes = new ArrayList<>();

        void setNodes(List<Node> value) {
            nodes.clear();
            nodes.addAll(value);
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return nodes.size();
        }

        @Override
        public Object getItem(int position) {
            return nodes.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout row;
            ViewHolder holder;
            if (convertView == null) {
                row = Ui.row(ProjectTreeActivity.this);
                row.setPadding(Ui.dp(ProjectTreeActivity.this, 8.0f),
                        Ui.dp(ProjectTreeActivity.this, 7.0f),
                        Ui.dp(ProjectTreeActivity.this, 8.0f),
                        Ui.dp(ProjectTreeActivity.this, 7.0f));
                row.setBackground(Ui.round(Ui.surface(), Ui.dp(ProjectTreeActivity.this, 8.0f)));
                holder = new ViewHolder();
                holder.arrow = Ui.text(ProjectTreeActivity.this, "", 14.0f, Ui.GOLD, true);
                row.addView(holder.arrow);
                holder.name = Ui.text(ProjectTreeActivity.this, "", 13.0f, Ui.text(), false);
                holder.name.setTypeface(Typeface.MONOSPACE);
                LinearLayout.LayoutParams nlp = Ui.lp(0, -2, 1.0f);
                nlp.leftMargin = Ui.dp(ProjectTreeActivity.this, 6.0f);
                row.addView(holder.name, nlp);
                holder.meta = Ui.text(ProjectTreeActivity.this, "", 9.0f, Ui.muted(), false);
                row.addView(holder.meta);
                row.setTag(holder);
                convertView = row;
            } else {
                holder = (ViewHolder) convertView.getTag();
            }
            Node n = nodes.get(position);
            holder.name.setText(n.name);
            holder.name.setTextSize(13.0f * zoom);
            holder.meta.setTextSize(9.0f * zoom);
            holder.arrow.setText(n.dir ? (n.expanded ? "▾" : "▸") : "·");
            holder.arrow.setTextSize(14.0f * zoom);
            holder.meta.setText(n.dir ? getString(R.string.fm_folder) : ConversionEngine.human(n.file.length()));
            int left = Ui.dp(ProjectTreeActivity.this, 8.0f) + Ui.dp(ProjectTreeActivity.this, 14.0f) * Math.min(16, n.depth);
            convertView.setPadding(left, Ui.dp(ProjectTreeActivity.this, 7.0f),
                    Ui.dp(ProjectTreeActivity.this, 8.0f), Ui.dp(ProjectTreeActivity.this, 7.0f));
            convertView.setBackground(Ui.round(n.dir ? Ui.surface2() : Ui.surface(),
                    Ui.dp(ProjectTreeActivity.this, 8.0f)));
            convertView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    toggle(n);
                }
            });
            return convertView;
        }
    }

    private static final class ViewHolder {
        TextView arrow;
        TextView name;
        TextView meta;
    }

    /** A ListView that understands the two-finger pinch gesture. */
    private static final class TreeList extends ListView {
        interface ScaleListener {
            void onScale(float factor);
        }

        private ScaleListener listener;
        private final ScaleGestureDetector detector;

        TreeList(Context context) {
            super(context);
            detector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override
                public boolean onScale(ScaleGestureDetector d) {
                    if (listener != null) {
                        listener.onScale(d.getScaleFactor());
                        return true;
                    }
                    return false;
                }
            });
        }

        void setOnScaleListener(ScaleListener l) {
            this.listener = l;
        }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            detector.onTouchEvent(ev);
            return super.onTouchEvent(ev);
        }

        @Override
        public boolean performClick() {
            super.performClick();
            return true;
        }
    }
}
