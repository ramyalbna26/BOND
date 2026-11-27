package com.ramyelbanna.juzoor;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.ramyelbanna.juzoor.core.LogManager;
import com.ramyelbanna.juzoor.engine.ConversionResult;
import com.ramyelbanna.juzoor.engine.LiveCanvasFeed;
import com.ramyelbanna.juzoor.ui.Ui;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Locale;

/**
 * Live decompilation screen.
 *
 * <p>All live events — log lines and extracted file names — share one
 * {@link RecyclerView}.  A {@link ListAdapter} with {@link DiffUtil} updates
 * only the rows whose content changed, and the feed keeps at most
 * {@link LiveCanvasFeed#MAX_ROWS} rows in memory.  The progress header lives
 * outside the list, so scrolling never moves it.  Auto-scroll pauses as soon
 * as the user scrolls away from the end and resumes at the bottom.</p>
 */
public class LiveCanvasActivity extends BaseActivity implements ConversionService.Listener {

    private static final int REQ_PICK = 51;
    private static final long FLUSH_MS = 80L;
    private static final int MAX_PENDING = 96;

    private String path;
    private ProgressBar bar;
    private TextView pct;
    private TextView step;
    private TextView files;
    private TextView status;
    private TextView currentPath;
    private TextView pauseButton;
    private TextView resultButton;
    private TextView followHint;
    private RecyclerView list;
    private EventAdapter adapter;
    private final LiveCanvasFeed feed = new LiveCanvasFeed();
    private final ArrayDeque<Pending> pending = new ArrayDeque<Pending>();
    private boolean autoFollow = true;
    private boolean finished;
    private boolean destroyed;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable flushTick = new Runnable() {
        @Override
        public void run() {
            flushPending();
        }
    };

    private static final class Pending {
        final boolean file;
        final String level;
        final String text;
        final int percent;

        Pending(boolean file, String level, String text, int percent) {
            this.file = file;
            this.level = level;
            this.text = text;
            this.percent = percent;
        }
    }

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        this.path = getIntent() == null ? null : getIntent().getStringExtra(ConversionService.EXTRA_PATH);
        buildUi();
        ConversionService.addListener(this);
        feed.addLog(LogManager.INFO, getString(R.string.canvas_waiting));
        submitFeed();
        if (ConversionService.isRunning()) {
            onProgress(ConversionService.getProgress(), ConversionService.getStep());
            String current = ConversionService.getCurrentFile();
            if (current != null) showFile(current);
            this.files.setText(getString(R.string.canvas_files) + ": " + ConversionService.getFileCount());
        } else if (this.path != null) {
            startConversion(this.path);
        }
    }

    @Override
    protected boolean scrollBody() {
        // The live list owns the scroll gesture.  Wrapping it in the base
        // ScrollView would measure every row and remove its recycling win.
        return false;
    }

    private void buildUi() {
        this.body.setPadding(Ui.dp(this, 14.0f), Ui.dp(this, 8.0f),
                Ui.dp(this, 14.0f), Ui.dp(this, 10.0f));
        topBar(getString(R.string.canvas_title), true);

        LinearLayout head = Ui.card(this);
        head.setPadding(Ui.dp(this, 12.0f), Ui.dp(this, 10.0f),
                Ui.dp(this, 12.0f), Ui.dp(this, 10.0f));
        this.body.addView(head, Ui.lp(-1, -2));

        LinearLayout counters = Ui.row(this);
        this.files = Ui.text(this, getString(R.string.canvas_files) + ": 0", 13.0f, Ui.GOLD, true);
        counters.addView(this.files);
        this.status = Ui.text(this, getString(R.string.canvas_waiting), 11.0f, Ui.muted(), false);
        this.status.setSingleLine(true);
        this.status.setEllipsize(TextUtils.TruncateAt.END);
        this.status.setGravity(Gravity.END);
        LinearLayout.LayoutParams statusLp = Ui.lp(0, -2, 1.0f);
        statusLp.leftMargin = Ui.dp(this, 8.0f);
        counters.addView(this.status, statusLp);
        head.addView(counters);

        LinearLayout progress = Ui.row(this);
        progress.setPadding(0, Ui.dp(this, 8.0f), 0, 0);
        this.bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        this.bar.setMax(100);
        this.bar.setProgress(0);
        progress.addView(this.bar, Ui.lp(0, Ui.dp(this, 8.0f), 1.0f));
        this.pct = Ui.text(this, "0%", 14.0f, Ui.GOLD, true);
        this.pct.setPadding(Ui.dp(this, 12.0f), 0, 0, 0);
        progress.addView(this.pct);
        head.addView(progress);

        this.step = Ui.text(this, "…", 11.0f, Ui.muted(), false);
        this.step.setSingleLine(true);
        this.step.setEllipsize(TextUtils.TruncateAt.END);
        this.step.setPadding(0, Ui.dp(this, 4.0f), 0, 0);
        head.addView(this.step);

        this.currentPath = Ui.text(this, "", 10.0f, Ui.GOLD, false);
        this.currentPath.setTypeface(Typeface.MONOSPACE);
        this.currentPath.setSingleLine(true);
        this.currentPath.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        this.currentPath.setPadding(0, Ui.dp(this, 3.0f), 0, 0);
        head.addView(this.currentPath);

        this.adapter = new EventAdapter();
        this.list = new RecyclerView(this);
        this.list.setLayoutManager(new LinearLayoutManager(this));
        this.list.setAdapter(this.adapter);
        this.list.setHasFixedSize(true);
        this.list.setItemAnimator(null);
        this.list.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        this.list.setBackground(Ui.stroke(Ui.surface2(), 0x33D4AF37,
                Ui.dp(this, 16.0f), Ui.dp(this, 1.0f)));
        int listPad = Ui.dp(this, 6.0f);
        this.list.setPadding(listPad, listPad, listPad, listPad);
        this.list.setClipToPadding(false);
        this.list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    autoFollow = false;
                } else if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    autoFollow = atBottom();
                }
                updateFollowHint();
            }

            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (dy == 0) return;
                if (atBottom()) {
                    autoFollow = true;
                } else if (recyclerView.getScrollState() == RecyclerView.SCROLL_STATE_DRAGGING) {
                    autoFollow = false;
                }
                updateFollowHint();
            }
        });
        LinearLayout.LayoutParams listLp = Ui.lp(-1, 0, 1.0f);
        listLp.topMargin = Ui.dp(this, 8.0f);
        this.body.addView(this.list, listLp);

        this.followHint = Ui.text(this, getString(R.string.canvas_follow_on),
                10.0f, Ui.muted(), false);
        this.followHint.setSingleLine(true);
        this.followHint.setEllipsize(TextUtils.TruncateAt.END);
        this.followHint.setPadding(0, Ui.dp(this, 4.0f), 0, 0);
        this.followHint.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                autoFollow = true;
                scrollToLatest();
                updateFollowHint();
            }
        });
        this.body.addView(this.followHint);

        if (this.path == null && !ConversionService.isRunning()) {
            TextView pick = Ui.button(this, getString(R.string.home_open_file), true);
            LinearLayout.LayoutParams plp = Ui.lp(-1, -2);
            plp.topMargin = Ui.dp(this, 8.0f);
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

        LinearLayout controls = Ui.row(this);
        LinearLayout.LayoutParams ctr = Ui.lp(-1, -2);
        ctr.topMargin = Ui.dp(this, 8.0f);
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
        rlp.topMargin = Ui.dp(this, 8.0f);
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
    }

    @Override
    public void onProgress(final int percent, final String stage) {
        ui.post(new Runnable() {
            @Override
            public void run() {
                if (bar == null || destroyed) return;
                bar.setProgress(percent);
                pct.setText(percent + "%");
                step.setText(stage == null ? "" : stage);
            }
        });
    }

    @Override
    public void onLog(final String level, final String message) {
        enqueue(new Pending(false, level, message, -1));
    }

    @Override
    public void onFile(String file, int percent) {
        enqueue(new Pending(true, "", file, percent));
    }

    private void enqueue(Pending item) {
        if (destroyed || item.text == null || item.text.isEmpty()) return;
        synchronized (pending) {
            // A burst larger than this is already in the Logcat file.  Keeping
            // a hard bound here stops callback floods from growing the heap.
            if (pending.size() >= MAX_PENDING) pending.removeFirst();
            pending.addLast(item);
        }
        ui.removeCallbacks(flushTick);
        ui.postDelayed(flushTick, FLUSH_MS);
    }

    private void flushPending() {
        if (adapter == null) return;
        boolean changed = false;
        synchronized (pending) {
            while (!pending.isEmpty()) {
                Pending item = pending.removeFirst();
                if (item.file) {
                    feed.addFile(item.text, item.percent);
                    showFile(item.text);
                } else {
                    feed.addLog(item.level, item.text);
                }
                changed = true;
            }
        }
        if (changed) submitFeed();
    }

    private void showFile(String file) {
        if (currentPath == null) return;
        currentPath.setText(file);
        files.setText(getString(R.string.canvas_files) + ": " + ConversionService.getFileCount());
    }

    private void submitFeed() {
        if (adapter == null) return;
        List<LiveCanvasFeed.Entry> snapshot = feed.snapshot();
        adapter.submitList(snapshot, new Runnable() {
            @Override
            public void run() {
                if (autoFollow) scrollToLatest();
            }
        });
    }

    private void scrollToLatest() {
        if (list == null || adapter == null) return;
        int count = adapter.getItemCount();
        if (count > 0) list.scrollToPosition(count - 1);
    }

    private boolean atBottom() {
        if (list == null || adapter == null) return true;
        RecyclerView.LayoutManager manager = list.getLayoutManager();
        if (!(manager instanceof LinearLayoutManager)) return true;
        LinearLayoutManager linear = (LinearLayoutManager) manager;
        int last = linear.findLastVisibleItemPosition();
        return last >= adapter.getItemCount() - 1;
    }

    private void updateFollowHint() {
        if (followHint == null) return;
        followHint.setText(getString(autoFollow ? R.string.canvas_follow_on : R.string.canvas_follow_paused));
        followHint.setTextColor(autoFollow ? Ui.muted() : Ui.GOLD);
    }

    @Override
    public void onFinished(final ConversionResult result) {
        if (finished || destroyed) return;
        finished = true;
        ui.post(new Runnable() {
            @Override
            public void run() {
                flushPending();
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
        destroyed = true;
        ui.removeCallbacks(flushTick);
        ConversionService.removeListener(this);
        super.onDestroy();
    }

    /* ------------------------------------------------------------- list rows */

    private static final DiffUtil.ItemCallback<LiveCanvasFeed.Entry> DIFF =
            new DiffUtil.ItemCallback<LiveCanvasFeed.Entry>() {
                @Override
                public boolean areItemsTheSame(LiveCanvasFeed.Entry oldItem,
                                               LiveCanvasFeed.Entry newItem) {
                    return oldItem.id == newItem.id;
                }

                @Override
                public boolean areContentsTheSame(LiveCanvasFeed.Entry oldItem,
                                                  LiveCanvasFeed.Entry newItem) {
                    return oldItem.equals(newItem);
                }
            };

    private static final class EventHolder extends RecyclerView.ViewHolder {
        final TextView icon;
        final TextView message;
        final TextView meta;

        EventHolder(LinearLayout row, TextView icon, TextView message, TextView meta) {
            super(row);
            this.icon = icon;
            this.message = message;
            this.meta = meta;
        }
    }

    /**
     * One lightweight holder for both row kinds.  File and log rows share the
     * same fixed height, so switching typeface or ellipsis never changes the
     * page layout.  File paths are monospace and middle-ellipsized; log text
     * uses the normal sans-serif face and end-ellipsis.
     */
    private final class EventAdapter extends ListAdapter<LiveCanvasFeed.Entry, EventHolder> {

        EventAdapter() {
            super(DIFF);
            setHasStableIds(true);
        }

        @Override
        public long getItemId(int position) {
            return getItem(position).id;
        }

        @Override
        public EventHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            Context context = parent.getContext();
            LinearLayout row = Ui.row(context);
            row.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 38.0f)));
            row.setPadding(Ui.dp(context, 10.0f), 0, Ui.dp(context, 8.0f), 0);

            TextView icon = Ui.text(context, "", 14.5f, Ui.GOLD, true);
            icon.setGravity(Gravity.CENTER);
            icon.setIncludeFontPadding(false);
            row.addView(icon, new LinearLayout.LayoutParams(
                    Ui.dp(context, 24.0f), ViewGroup.LayoutParams.MATCH_PARENT));

            TextView message = Ui.text(context, "", 12.5f, Ui.text(), false);
            message.setSingleLine(true);
            message.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            message.setIncludeFontPadding(false);
            message.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(message, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));

            TextView meta = Ui.text(context, "", 9.5f, Ui.muted(), true);
            meta.setSingleLine(true);
            meta.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            meta.setIncludeFontPadding(false);
            row.addView(meta, new LinearLayout.LayoutParams(
                    Ui.dp(context, 42.0f), ViewGroup.LayoutParams.MATCH_PARENT));

            return new EventHolder(row, icon, message, meta);
        }

        @Override
        public void onBindViewHolder(EventHolder holder, int position) {
            LiveCanvasFeed.Entry entry = getItem(position);
            holder.itemView.setBackgroundColor(position % 2 == 0
                    ? (Ui.dark() ? 0x08FFFFFF : 0x08000000)
                    : 0x00000000);
            if (entry.kind == LiveCanvasFeed.Kind.FILE) {
                holder.icon.setText("✓");
                holder.icon.setTextColor(fileColor(entry.text));
                holder.message.setText(entry.text);
                holder.message.setTypeface(Typeface.MONOSPACE);
                holder.message.setEllipsize(TextUtils.TruncateAt.MIDDLE);
                holder.message.setTextColor(Ui.text());
                holder.meta.setText(entry.percent >= 0
                        ? String.format(Locale.US, "%d%%", entry.percent) : "");
                holder.meta.setTextColor(Ui.GOLD);
            } else {
                holder.icon.setText(symbolFor(entry.level));
                holder.icon.setTextColor(Ui.levelColor(entry.level));
                holder.message.setText(entry.text);
                holder.message.setTypeface(Typeface.SANS_SERIF);
                holder.message.setEllipsize(TextUtils.TruncateAt.END);
                holder.message.setTextColor(LogManager.ERROR.equals(entry.level)
                        ? Ui.ERROR : Ui.text());
                holder.meta.setText(levelLabel(entry.level));
                holder.meta.setTextColor(Ui.levelColor(entry.level));
            }
        }
    }

    private static String symbolFor(String level) {
        if (LogManager.ERROR.equals(level)) return "✗";
        if (LogManager.WARN.equals(level)) return "⚠";
        if (LogManager.SUCCESS.equals(level)) return "✓";
        if (LogManager.DEBUG.equals(level)) return "·";
        return "•";
    }

    private static String levelLabel(String level) {
        if (LogManager.ERROR.equals(level)) return "ERR";
        if (LogManager.WARN.equals(level)) return "WARN";
        if (LogManager.SUCCESS.equals(level)) return "OK";
        if (LogManager.DEBUG.equals(level)) return "DBG";
        return "INFO";
    }

    private static int fileColor(String path) {
        String name = path == null ? "" : path.toLowerCase(Locale.US);
        if (name.endsWith(".dex") || name.contains("smali")) return Ui.GOLD;
        if (name.endsWith(".java") || name.endsWith(".kt")) return Ui.INFO;
        if (name.endsWith(".so")) return Ui.WARNING;
        if (name.startsWith("assets/") || name.contains("/assets/")) return Ui.SUCCESS;
        if (name.startsWith("res/") || name.contains("/res/")) return 0xFFB58CFF;
        return Ui.GOLD;
    }
}
