package com.ramyelbanna.juzoor;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.ramyelbanna.juzoor.engine.ConversionEngine;
import com.ramyelbanna.juzoor.engine.ConversionResult;
import com.ramyelbanna.juzoor.ui.Ui;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Preview recovered images and fonts without leaving the device. */
public class AssetsViewerActivity extends BaseActivity {

    public static final String EXTRA_PROJECT = "project";
    private static final int MAX_FILES = 8000;

    private File project;
    private ListView list;
    private AssetAdapter adapter;
    private TextView summary;
    private int mode;

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
        scan();
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
        bar.addView(Ui.text(this, getString(R.string.assets_title), 17.0f, Ui.text(), true),
                Ui.lp(0, -2, 1.0f));
        root.addView(bar);

        TextView where = Ui.text(this, project == null ? getString(R.string.tree_none)
                : project.getAbsolutePath(), 10.0f, Ui.GOLD, false);
        where.setPadding(0, Ui.dp(this, 4.0f), 0, Ui.dp(this, 8.0f));
        root.addView(where);

        LinearLayout tabs = Ui.row(this);
        tabs.addView(chip(getString(R.string.assets_all), 0));
        tabs.addView(chip(getString(R.string.assets_images), 1), Ui.lp(-2, -2));
        tabs.addView(chip(getString(R.string.assets_fonts), 2), Ui.lp(-2, -2));
        root.addView(tabs);

        this.summary = Ui.text(this, "", 10.0f, Ui.muted(), false);
        this.summary.setPadding(0, Ui.dp(this, 8.0f), 0, Ui.dp(this, 4.0f));
        root.addView(this.summary);

        this.list = new ListView(this);
        this.list.setDivider(null);
        this.list.setDividerHeight(0);
        this.list.setCacheColorHint(0);
        this.list.setBackgroundColor(Ui.bg());
        this.adapter = new AssetAdapter();
        this.list.setAdapter(adapter);
        root.addView(this.list, Ui.lp(-1, 0, 1.0f));
        setContentView(root);
    }

    private TextView chip(String text, final int value) {
        LinearLayout.LayoutParams lp = Ui.lp(-2, -2);
        lp.rightMargin = Ui.dp(this, 6.0f);
        TextView chip = Ui.chip(this, text, mode == value ? Ui.GOLD : Ui.muted());
        chip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mode = value;
                buildUi();
                scan();
            }
        });
        return chip;
    }

    private void scan() {
        if (project == null || !project.isDirectory()) {
            summary.setText(getString(R.string.tree_none));
            return;
        }
        summary.setText(getString(R.string.assets_scanning));
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<File> found = new ArrayList<>();
                for (String rel : new String[]{"app/src/main/assets", "app/src/main/res",
                        "app/src/main/recovered", "assets", "res"}) {
                    collect(new File(project, rel), found, 0);
                }
                android.os.Handler h = new Handler(Looper.getMainLooper());
                h.post(new Runnable() {
                    @Override
                    public void run() {
                        adapter.setFiles(found);
                        int images = 0, fonts = 0;
                        for (File f : found) {
                            if (isImage(f)) images++;
                            else if (isFont(f)) fonts++;
                        }
                        summary.setText(getString(R.string.assets_summary, images, fonts, found.size()));
                    }
                });
            }
        }, "juzoor-assets").start();
    }

    private void collect(File dir, List<File> out, int depth) {
        if (dir == null || !dir.isDirectory() || depth > 24 || out.size() >= MAX_FILES) return;
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File f : children) {
            if (out.size() >= MAX_FILES) return;
            if (f.isDirectory()) {
                collect(f, out, depth + 1);
            } else if (isImage(f) || isFont(f)) {
                if (mode == 0 || (mode == 1 && isImage(f)) || (mode == 2 && isFont(f))) {
                    out.add(f);
                }
            }
        }
    }

    private static boolean isImage(File f) {
        String n = f.getName().toLowerCase(Locale.US);
        return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg")
                || n.endsWith(".webp") || n.endsWith(".gif") || n.endsWith(".bmp");
    }

    private static boolean isFont(File f) {
        String n = f.getName().toLowerCase(Locale.US);
        return n.endsWith(".ttf") || n.endsWith(".otf") || n.endsWith(".ttc")
                || n.endsWith(".woff") || n.endsWith(".woff2");
    }

    private void preview(File file) {
        if (isFont(file)) {
            previewFont(file);
        } else {
            previewImage(file);
        }
    }

    private void previewImage(File file) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            int sample = 1;
            while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            Dialog dialog = new Dialog(this);
            LinearLayout box = Ui.column(this);
            box.setBackgroundColor(Ui.bg());
            int pad = Ui.dp(this, 14.0f);
            box.setPadding(pad, pad, pad, pad);
            box.addView(Ui.text(this, file.getName(), 14.0f, Ui.GOLD, true));
            if (bitmap != null) {
                ImageView image = new ImageView(this);
                image.setImageBitmap(bitmap);
                image.setAdjustViewBounds(true);
                image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                box.addView(image, Ui.lp(-1, Ui.dp(this, 360.0f)));
            }
            LinearLayout.LayoutParams lp = Ui.lp(-1, -2);
            lp.topMargin = Ui.dp(this, 6.0f);
            TextView open = Ui.button(this, getString(R.string.code_title), false);
            final File target = file;
            open.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent intent = new Intent(AssetsViewerActivity.this, CodeEditorActivity.class);
                    intent.putExtra(CodeEditorActivity.EXTRA_FILE, target.getAbsolutePath());
                    startActivity(intent);
                }
            });
            box.addView(open, lp);
            dialog.setContentView(box);
            Window w = dialog.getWindow();
            if (w != null) w.setBackgroundDrawableResource(android.R.color.transparent);
            dialog.show();
        } catch (Throwable t) {
            toast(getString(R.string.assets_failed));
        }
    }

    private void previewFont(File file) {
        try {
            Typeface typeface = Typeface.createFromFile(file);
            Dialog dialog = new Dialog(this);
            LinearLayout box = Ui.column(this);
            box.setBackgroundColor(Ui.bg());
            int pad = Ui.dp(this, 14.0f);
            box.setPadding(pad, pad, pad, pad);
            box.addView(Ui.text(this, file.getName(), 14.0f, Ui.GOLD, true));
            TextView preview = Ui.text(this, getString(R.string.assets_font_sample), 28.0f, Ui.text(), false);
            preview.setTypeface(typeface);
            preview.setPadding(0, Ui.dp(this, 18.0f), 0, Ui.dp(this, 18.0f));
            box.addView(preview);
            dialog.setContentView(box);
            Window w = dialog.getWindow();
            if (w != null) w.setBackgroundDrawableResource(android.R.color.transparent);
            dialog.show();
        } catch (Throwable t) {
            toast(getString(R.string.assets_failed));
        }
    }

    private final class AssetAdapter extends BaseAdapter {
        final List<File> files = new ArrayList<>();

        void setFiles(List<File> value) {
            files.clear();
            files.addAll(value);
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return files.size();
        }

        @Override
        public Object getItem(int position) {
            return files.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout row;
            if (convertView == null) {
                row = Ui.row(AssetsViewerActivity.this);
                row.setPadding(Ui.dp(AssetsViewerActivity.this, 10.0f),
                        Ui.dp(AssetsViewerActivity.this, 8.0f),
                        Ui.dp(AssetsViewerActivity.this, 10.0f),
                        Ui.dp(AssetsViewerActivity.this, 8.0f));
                row.setBackground(Ui.round(Ui.surface(), Ui.dp(AssetsViewerActivity.this, 8.0f)));
                LinearLayout.LayoutParams lp = Ui.lp(-1, -2);
                lp.topMargin = Ui.dp(AssetsViewerActivity.this, 4.0f);
                row.setLayoutParams(lp);
                convertView = row;
            } else {
                row = (LinearLayout) convertView;
            }
            row.removeAllViews();
            File f = files.get(position);
            row.addView(Ui.text(AssetsViewerActivity.this, isFont(f) ? "Aa" : "▧", 16.0f,
                    isFont(f) ? Ui.GOLD : Ui.INFO, true));
            LinearLayout column = Ui.column(AssetsViewerActivity.this);
            column.addView(Ui.text(AssetsViewerActivity.this, f.getName(), 12.0f, Ui.text(), false));
            column.addView(Ui.text(AssetsViewerActivity.this, f.getAbsolutePath(), 8.5f, Ui.muted(), false));
            LinearLayout.LayoutParams lp = Ui.lp(0, -2, 1.0f);
            lp.leftMargin = Ui.dp(AssetsViewerActivity.this, 8.0f);
            row.addView(column, lp);
            row.addView(Ui.text(AssetsViewerActivity.this, ConversionEngine.human(f.length()), 9.0f, Ui.muted(), false));
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    preview(f);
                }
            });
            return row;
        }
    }

    @Override
    public void onBackPressed() {
        finish();
    }
}
