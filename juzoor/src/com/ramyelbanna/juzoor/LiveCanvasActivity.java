package com.ramyelbanna.juzoor;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.ramyelbanna.juzoor.core.LogManager;
import com.ramyelbanna.juzoor.engine.ConversionResult;
import com.ramyelbanna.juzoor.ui.Ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Live decompilation canvas.
 *
 * <p>Every file reported by the engine is streamed into a zoomable, pannable
 * canvas with its own progress bar.  The screen listens to the same
 * {@link ConversionService} that powers the notification and the log, so it can
 * pause, resume or cancel without stopping the foreground service.</p>
 */
public class LiveCanvasActivity extends BaseActivity implements ConversionService.Listener {

    private static final int REQ_PICK = 51;

    private String path;
    private ProgressBar bar;
    private TextView pct;
    private TextView step;
    private TextView files;
    private TextView status;
    private TextView pauseButton;
    private TextView resultButton;
    private LiveCanvasView canvas;
    private boolean finished;
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        this.path = getIntent() == null ? null : getIntent().getStringExtra(ConversionService.EXTRA_PATH);
        buildUi();
        ConversionService.addListener(this);
        if (ConversionService.isRunning()) {
            onProgress(ConversionService.getProgress(), ConversionService.getStep());
            this.files.setText(getString(R.string.canvas_files) + ": " + ConversionService.getFileCount());
        } else if (this.path != null) {
            startConversion(this.path);
        }
    }

    private void buildUi() {
        topBar(getString(R.string.canvas_title), true);

        LinearLayout intro = Ui.card(this);
        intro.addView(Ui.text(this, getString(R.string.canvas_sub), 13.0f, Ui.muted(), false));
        LinearLayout badges = Ui.row(this);
        badges.setPadding(0, Ui.dp(this, 10.0f), 0, 0);
        badges.addView(Ui.chip(this, getString(R.string.home_badge_offline), Ui.SUCCESS));
        badges.addView(Ui.spacer(this, 8));
        badges.addView(Ui.chip(this, getString(R.string.home_engine), Ui.GOLD));
        intro.addView(badges);
        this.body.addView(intro);

        if (this.path == null && !ConversionService.isRunning()) {
            TextView pick = Ui.button(this, getString(R.string.home_open_file), true);
            LinearLayout.LayoutParams plp = Ui.lp(-1, -2);
            plp.topMargin = Ui.dp(this, 12.0f);
            pick.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent intent = new Intent(LiveCanvasActivity.this, FileBrowserActivity.class);
                    intent.putExtra("pick", true);
                    startActivityForResult(intent, REQ_PICK);
                }
            });
            this.body.addView(pick, plp);
        }

        LinearLayout card = Ui.card(this);
        LinearLayout.LayoutParams lp = Ui.lp(-1, -2);
        lp.topMargin = Ui.dp(this, 12.0f);
        this.body.addView(card, lp);

        LinearLayout head = Ui.row(this);
        this.files = Ui.text(this, getString(R.string.canvas_files) + ": 0", 12.0f, Ui.GOLD, true);
        head.addView(this.files, Ui.lp(0, -2, 1.0f));
        this.status = Ui.text(this, getString(R.string.canvas_waiting), 11.0f, Ui.muted(), false);
        head.addView(this.status);
        card.addView(head);

        LinearLayout row = Ui.row(this);
        row.setPadding(0, Ui.dp(this, 10.0f), 0, 0);
        this.bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        this.bar.setMax(100);
        this.bar.setProgress(0);
        row.addView(this.bar, Ui.lp(0, Ui.dp(this, 8.0f), 1.0f));
        this.pct = Ui.text(this, "0%", 14.0f, Ui.GOLD, true);
        this.pct.setPadding(Ui.dp(this, 12.0f), 0, 0, 0);
        row.addView(this.pct);
        card.addView(row);

        this.step = Ui.text(this, "…", 12.0f, Ui.muted(), false);
        this.step.setPadding(0, Ui.dp(this, 8.0f), 0, 0);
        card.addView(this.step);

        this.canvas = new LiveCanvasView(this);
        LinearLayout.LayoutParams clp = Ui.lp(-1, Ui.dp(this, 430.0f));
        clp.topMargin = Ui.dp(this, 12.0f);
        this.canvas.setBackground(Ui.stroke(Ui.surface2(), 0x33D4AF37, Ui.dp(this, 16.0f), Ui.dp(this, 1.0f)));
        this.body.addView(this.canvas, clp);

        TextView hint = Ui.text(this, getString(R.string.canvas_hint), 11.0f, Ui.muted(), false);
        hint.setPadding(0, Ui.dp(this, 8.0f), 0, 0);
        this.body.addView(hint);

        LinearLayout controls = Ui.row(this);
        LinearLayout.LayoutParams ctr = Ui.lp(-1, -2);
        ctr.topMargin = Ui.dp(this, 12.0f);
        this.body.addView(controls, ctr);
        this.pauseButton = Ui.button(this, getString(R.string.canvas_pause), false);
        this.pauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                togglePause();
            }
        });
        controls.addView(this.pauseButton, Ui.lp(0, -2, 1.0f));
        TextView cancel = Ui.button(this, getString(R.string.progress_cancel), false);
        LinearLayout.LayoutParams cl = Ui.lp(0, -2, 1.0f);
        cl.leftMargin = Ui.dp(this, 8.0f);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ConversionService.cancel();
                status.setText(getString(R.string.progress_cancelling));
                canvas.setRunning(false);
            }
        });
        controls.addView(cancel, cl);

        LinearLayout tools = Ui.row(this);
        LinearLayout.LayoutParams tlp = Ui.lp(-1, -2);
        tlp.topMargin = Ui.dp(this, 8.0f);
        this.body.addView(tools, tlp);
        TextView logcat = Ui.button(this, getString(R.string.log_title), false);
        logcat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                open(LogcatActivity.class);
            }
        });
        tools.addView(logcat, Ui.lp(0, -2, 1.0f));
        TextView settings = Ui.button(this, getString(R.string.settings_title), false);
        LinearLayout.LayoutParams sl = Ui.lp(0, -2, 1.0f);
        sl.leftMargin = Ui.dp(this, 8.0f);
        settings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                open(SettingsActivity.class);
            }
        });
        tools.addView(settings, sl);

        this.resultButton = Ui.button(this, getString(R.string.canvas_open_result), true);
        LinearLayout.LayoutParams rlp = Ui.lp(-1, -2);
        rlp.topMargin = Ui.dp(this, 12.0f);
        this.resultButton.setVisibility(View.GONE);
        this.resultButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(LiveCanvasActivity.this, ResultActivity.class));
            }
        });
        this.body.addView(this.resultButton, rlp);
    }

    private void startConversion(String file) {
        Intent intent = new Intent(this, ConversionService.class);
        intent.setAction(ConversionService.ACTION_START);
        intent.putExtra(ConversionService.EXTRA_PATH, file);
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void togglePause() {
        boolean now;
        if (ConversionService.isPaused()) {
            ConversionService.resume();
            now = false;
            status.setText(getString(R.string.canvas_resumed));
        } else {
            ConversionService.pause();
            now = true;
            status.setText(getString(R.string.canvas_paused));
        }
        pauseButton.setText(getString(now ? R.string.canvas_resume : R.string.canvas_pause));
        canvas.setPaused(now);
    }

    @Override
    public void onProgress(final int percent, final String stage) {
        ui.post(new Runnable() {
            @Override
            public void run() {
                bar.setProgress(percent);
                pct.setText(percent + "%");
                step.setText(stage);
                canvas.setStage(stage);
            }
        });
    }

    @Override
    public void onLog(final String level, final String message) {
        ui.post(new Runnable() {
            @Override
            public void run() {
                canvas.addLog(level, message);
            }
        });
    }

    @Override
    public void onFile(final String file, final int percent) {
        ui.post(new Runnable() {
            @Override
            public void run() {
                canvas.addFile(file, percent);
                files.setText(getString(R.string.canvas_files) + ": " + ConversionService.getFileCount());
                status.setText(file);
            }
        });
    }

    @Override
    public void onFinished(final ConversionResult result) {
        if (finished) return;
        finished = true;
        ui.post(new Runnable() {
            @Override
            public void run() {
                canvas.setRunning(false);
                bar.setProgress(100);
                pct.setText("100%");
                if (result != null && result.success) {
                    step.setText(getString(R.string.canvas_finished));
                    resultButton.setVisibility(View.VISIBLE);
                    status.setText(result.classCount + " " + getString(R.string.result_classes)
                            + " · " + result.fileCount + " " + getString(R.string.result_files));
                } else {
                    step.setText(getString(R.string.canvas_cancelled));
                    status.setText(result != null && result.error != null ? result.error : "");
                }
                pauseButton.setEnabled(false);
            }
        });
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == REQ_PICK && result == RESULT_OK && data != null) {
            String picked = data.getStringExtra(ConversionService.EXTRA_PATH);
            if (picked != null) {
                path = picked;
                startConversion(picked);
            }
        }
    }

    @Override
    protected void onDestroy() {
        ConversionService.removeListener(this);
        super.onDestroy();
    }

    /* ------------------------------------------------------------ canvas view */

    private static final class LiveCanvasView extends View {
        private static final int ROW = 54;
        private static final int MAX_NODES = 700;
        private final List<Node> nodes = new ArrayList<Node>();
        private final List<Line> logs = new ArrayList<Line>();
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint monoPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final GestureDetector gestures;
        private final ScaleGestureDetector scaleDetector;
        private float scale = 1.0f;
        private float panX;
        private float panY;
        private boolean autoFollow = true;
        private boolean running = true;
        private boolean paused;
        private String stage = "";
        private long started = System.currentTimeMillis();

        private static final class Node {
            final String path;
            final String name;
            final int depth;
            final String kind;
            int percent;
            long time;

            Node(String path, String name, int depth, int percent, long time, String kind) {
                this.path = path;
                this.name = name;
                this.depth = depth;
                this.percent = percent;
                this.time = time;
                this.kind = kind;
            }
        }

        private static final class Line {
            final String level;
            final String text;
            final long time;

            Line(String level, String text, long time) {
                this.level = level;
                this.text = text;
                this.time = time;
            }
        }

        LiveCanvasView(Context context) {
            super(context);
            setFocusable(true);
            setClickable(true);
            paint.setStrokeWidth(1.0f);
            textPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));
            monoPaint.setTypeface(Typeface.MONOSPACE);
            gestures = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onDown(MotionEvent e) {
                    ViewGroup parent = (ViewGroup) getParent();
                    if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
                    return true;
                }

                @Override
                public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
                    if (e1 != null && e1.getPointerCount() > 1) return false;
                    panX -= dx;
                    panY -= dy;
                    autoFollow = false;
                    invalidate();
                    return true;
                }

                @Override
                public boolean onDoubleTap(MotionEvent e) {
                    scale = 1.0f;
                    panX = 0;
                    panY = 0;
                    autoFollow = true;
                    invalidate();
                    return true;
                }
            });
            scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override
                public boolean onScale(ScaleGestureDetector detector) {
                    float before = scale;
                    scale = Math.max(0.5f, Math.min(3.0f, scale * detector.getScaleFactor()));
                    float ratio = scale / before;
                    // Keep the pinch focus stable while scaling.
                    panX = (panX + detector.getFocusX()) * ratio - detector.getFocusX();
                    panY = (panY + detector.getFocusY()) * ratio - detector.getFocusY();
                    autoFollow = false;
                    invalidate();
                    return true;
                }
            });
        }

        void addFile(String path, int percent) {
            if (path == null || path.isEmpty()) return;
            synchronized (nodes) {
                if (!nodes.isEmpty()) {
                    Node last = nodes.get(nodes.size() - 1);
                    if (last.path.equals(path)) {
                        last.percent = percent;
                        last.time = System.currentTimeMillis();
                        invalidate();
                        return;
                    }
                }
                Node n = new Node(path, shortName(path), depthOf(path), percent, System.currentTimeMillis(), kindOf(path));
                nodes.add(n);
                while (nodes.size() > MAX_NODES) nodes.remove(0);
            }
            if (autoFollow) invalidate();
        }

        void addLog(String level, String message) {
            synchronized (logs) {
                logs.add(new Line(level, message, System.currentTimeMillis()));
                while (logs.size() > 7) logs.remove(0);
            }
            invalidate();
        }

        void setStage(String stage) {
            this.stage = stage == null ? "" : stage;
            invalidate();
        }

        void setRunning(boolean running) {
            this.running = running;
            invalidate();
        }

        void setPaused(boolean paused) {
            this.paused = paused;
            invalidate();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            scaleDetector.onTouchEvent(event);
            gestures.onTouchEvent(event);
            if (event.getActionMasked() == MotionEvent.ACTION_UP
                    || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                ViewGroup parent = (ViewGroup) getParent();
                if (parent != null) parent.requestDisallowInterceptTouchEvent(false);
                if (event.getActionMasked() == MotionEvent.ACTION_UP) performClick();
            }
            return true;
        }

        @Override
        public boolean performClick() {
            super.performClick();
            return true;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            canvas.drawColor(Ui.dark() ? 0xFF0C0C10 : 0xFFF6F2E8);
            drawGrid(canvas, w, h);

            synchronized (nodes) {
                int content = nodes.size() * ROW + 70;
                float base = autoFollow && content > h ? h - content - 12 : 18;
                float ty = base + panY;
                canvas.save();
                canvas.scale(scale, scale, w / 2.0f, h / 2.0f);
                canvas.translate(panX, ty);
                for (int i = 0; i < nodes.size(); i++) {
                    drawNode(canvas, nodes.get(i), i, w);
                }
                canvas.restore();
            }

            drawFooter(canvas, w, h);
            if (running) postInvalidateOnAnimation();
        }

        private void drawGrid(Canvas canvas, int w, int h) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Ui.dark() ? 0x14D4AF37 : 0x0AD4AF37);
            for (int x = 0; x < w; x += 32) {
                for (int y = 0; y < h; y += 32) canvas.drawCircle(x, y, 0.9f, paint);
            }
        }

        private void drawNode(Canvas canvas, Node n, int index, int w) {
            float y = index * ROW;
            float x = 14 + Math.min(14, n.depth) * 15;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.0f);
            paint.setColor(Ui.dark() ? 0x33D4AF37 : 0x33A07C18);
            if (index > 0) {
                canvas.drawLine(x - 8, y - ROW / 2.0f, x - 8, y + ROW / 2.0f, paint);
                canvas.drawLine(x - 8, y, x + 2, y, paint);
            }
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(colorFor(n.kind));
            rect.set(x + 2, y - 11, x + 14, y + 1);
            canvas.drawRoundRect(rect, 2.4f, 2.4f, paint);

            textPaint.setTextSize(sp(11.5f));
            textPaint.setColor(Ui.text());
            textPaint.setFakeBoldText(false);
            canvas.drawText(n.name, x + 22, y - 2, textPaint);

            float barX = x + 22;
            float barY = y + 6;
            float barW = Math.min(210, Math.max(90, w - barX - 70));
            paint.setColor(Ui.dark() ? 0x33FFFFFF : 0x20000000);
            rect.set(barX, barY, barX + barW, barY + 4.5f);
            canvas.drawRoundRect(rect, 2.5f, 2.5f, paint);
            float fraction;
            if (n.percent >= 0) {
                fraction = Math.max(0.02f, Math.min(1f, n.percent / 100.0f));
            } else {
                fraction = 0.18f + 0.30f * (float) Math.abs(Math.sin((System.currentTimeMillis() - n.time) / 700.0 + index));
            }
            paint.setColor(colorFor(n.kind));
            rect.set(barX, barY, barX + barW * fraction, barY + 4.5f);
            canvas.drawRoundRect(rect, 2.5f, 2.5f, paint);

            monoPaint.setTextSize(sp(8.5f));
            monoPaint.setColor(n.percent >= 0 ? Ui.GOLD : Ui.muted());
            String label = n.percent >= 0 ? n.percent + "%" : "…";
            canvas.drawText(label, barX + barW + 8, barY + 4, monoPaint);
        }

        private void drawFooter(Canvas canvas, int w, int h) {
            float panelH = 78;
            paint.setColor(Ui.dark() ? 0xE608080B : 0xE6FAF7EF);
            rect.set(0, h - panelH, w, h);
            canvas.drawRect(rect, paint);
            paint.setColor(Ui.dark() ? 0x33D4AF37 : 0x33A07C18);
            canvas.drawLine(0, h - panelH, w, h - panelH, paint);

            monoPaint.setTextSize(sp(9.5f));
            monoPaint.setColor(Ui.muted());
            String head = (paused ? "❚❚ " : "▶ ") + stage + "   ·   zoom "
                    + String.format(Locale.US, "%.2f", scale) + "×";
            canvas.drawText(trim(head, 84), 10, h - panelH + 15, monoPaint);

            synchronized (logs) {
                int start = Math.max(0, logs.size() - 3);
                for (int i = start; i < logs.size(); i++) {
                    Line l = logs.get(i);
                    monoPaint.setColor(levelColor(l.level));
                    canvas.drawText(trim(l.text, 92), 10, h - panelH + 30 + (i - start) * 15, monoPaint);
                }
            }
        }

        private int levelColor(String level) {
            if (LogManager.ERROR.equals(level)) return 0xFFFF5A5A;
            if (LogManager.WARN.equals(level)) return 0xFFFFB74D;
            if (LogManager.SUCCESS.equals(level)) return 0xFF3DDC84;
            if (LogManager.DEBUG.equals(level)) return 0xFF64B5F6;
            return Ui.GOLD;
        }

        private int colorFor(String kind) {
            if ("dex".equals(kind)) return Ui.GOLD;
            if ("java".equals(kind)) return Ui.INFO;
            if ("native".equals(kind)) return Ui.WARNING;
            if ("asset".equals(kind)) return Ui.SUCCESS;
            if ("res".equals(kind)) return 0xFFB58CFF;
            return Ui.muted();
        }

        private float sp(float value) {
            return value * getResources().getDisplayMetrics().scaledDensity;
        }

        private static String shortName(String path) {
            int slash = path.lastIndexOf('/');
            return slash >= 0 ? path.substring(slash + 1) : path;
        }

        private static int depthOf(String path) {
            int depth = 0;
            for (int i = 0; i < path.length(); i++) if (path.charAt(i) == '/') depth++;
            return depth;
        }

        private static String kindOf(String path) {
            String n = path.toLowerCase(Locale.US);
            if (n.endsWith(".dex") || n.contains("smali")) return "dex";
            if (n.endsWith(".java") || n.endsWith(".kt")) return "java";
            if (n.endsWith(".so")) return "native";
            if (n.startsWith("assets/") || n.contains("/assets/")) return "asset";
            if (n.startsWith("res/") || n.contains("/res/")) return "res";
            return "other";
        }

        private static String trim(String s, int max) {
            if (s == null) return "";
            return s.length() <= max ? s : "…" + s.substring(s.length() - max + 1);
        }
    }
}
