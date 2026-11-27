package com.ramyelbanna.juzoor;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.ramyelbanna.juzoor.core.LogManager;
import com.ramyelbanna.juzoor.core.Prefs;
import com.ramyelbanna.juzoor.engine.BinaryXml;
import com.ramyelbanna.juzoor.engine.LogSink;
import com.ramyelbanna.juzoor.engine.SecurityAnalyzer;
import com.ramyelbanna.juzoor.engine.SplitBundle;
import com.ramyelbanna.juzoor.engine.ZipUtil;
import com.ramyelbanna.juzoor.ui.Ui;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Standalone static application security testing screen.
 *
 * <p>Pick any APK (or split bundle), unpack it into a private workspace and run
 * the same {@link SecurityAnalyzer} used by the conversion pipeline. The result
 * is rendered as a risk score, severity counters, tracker chips, secret list and
 * per-finding cards, and can be exported as a Markdown report.</p>
 */
public class SecurityActivity extends BaseActivity {

    private static final int REQ_PICK = 41;

    private String path;
    private boolean busy;
    private SecurityAnalyzer.Report report;
    private LinearLayout resultHolder;
    private TextView status;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        buildUi();
    }

    private void buildUi() {
        this.body.removeAllViews();
        topBar(getString(R.string.security_title), true);

        LinearLayout intro = Ui.card(this);
        intro.addView(Ui.text(this, getString(R.string.security_sub), 13.0f, Ui.muted(), false));
        LinearLayout badges = Ui.row(this);
        badges.setPadding(0, Ui.dp(this, 10.0f), 0, 0);
        badges.addView(Ui.chip(this, getString(R.string.home_badge_offline), Ui.SUCCESS));
        badges.addView(Ui.spacer(this, 8));
        badges.addView(Ui.chip(this, getString(R.string.home_engine), Ui.GOLD));
        intro.addView(badges);
        this.body.addView(intro);

        TextView run = Ui.button(this, getString(R.string.security_run), true);
        LinearLayout.LayoutParams lp = Ui.lp(-1, -2);
        lp.topMargin = Ui.dp(this, 14.0f);
        run.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                pickFile();
            }
        });
        this.body.addView(run, lp);

        this.status = Ui.text(this, "", 12.0f, Ui.GOLD, false);
        this.status.setPadding(0, Ui.dp(this, 12.0f), 0, 0);
        this.body.addView(this.status);

        this.resultHolder = Ui.column(this);
        LinearLayout.LayoutParams lpr = Ui.lp(-1, -2);
        lpr.topMargin = Ui.dp(this, 6.0f);
        this.body.addView(this.resultHolder, lpr);

        if (this.report != null) {
            renderReport(this.report);
        } else {
            TextView none = Ui.text(this, getString(R.string.security_none), 13.0f, Ui.muted(), false);
            none.setPadding(0, Ui.dp(this, 14.0f), 0, 0);
            this.resultHolder.addView(none);
        }
    }

    private void pickFile() {
        Intent intent = new Intent(this, FileBrowserActivity.class);
        intent.putExtra("pick", true);
        startActivityForResult(intent, REQ_PICK);
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == REQ_PICK && result == RESULT_OK && data != null) {
            String picked = data.getStringExtra(ConversionService.EXTRA_PATH);
            if (picked != null) {
                this.path = picked;
                scan(new File(picked));
            }
        }
    }

    private void scan(final File apk) {
        if (this.busy) return;
        this.busy = true;
        this.status.setText(getString(R.string.security_scanning) + " " + apk.getName());
        this.resultHolder.removeAllViews();
        LogManager.i("Security scan requested: " + apk.getName());
        new Thread(new Runnable() {
            @Override
            public void run() {
                final SecurityAnalyzer.Report r = runScan(apk);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        busy = false;
                        if (r == null) {
                            status.setText(getString(R.string.security_fail, "unknown error"));
                            return;
                        }
                        report = r;
                        status.setText(getString(R.string.security_done) + " · " + r.summary());
                        renderReport(r);
                    }
                });
            }
        }, "juzoor-sast").start();
    }

    private SecurityAnalyzer.Report runScan(File apk) {
        File work = new File(getCacheDir(), "security");
        try {
            ZipUtil.deleteRecursive(work);
            if (!work.mkdirs()) throw new IllegalStateException("cannot create workspace");
            Sink sink = new Sink();
            SplitBundle.Plan plan = SplitBundle.prepare(apk, work, sink);
            File merged = plan.merged;

            String decodedManifest = null;
            File manifest = new File(plan.baseDir == null ? merged : plan.baseDir, "AndroidManifest.xml");
            if (!manifest.isFile()) manifest = new File(merged, "AndroidManifest.xml");
            if (manifest.isFile()) {
                try {
                    decodedManifest = BinaryXml.decode(ZipUtil.readFile(manifest));
                } catch (Throwable t) {
                    LogManager.w("Security: manifest decode failed: " + t);
                }
            }

            List<File> dexFiles = new ArrayList<File>();
            for (SplitBundle.Part part : plan.parts) dexFiles.addAll(part.dexFiles);

            SecurityAnalyzer.Report r = SecurityAnalyzer.analyze(merged, decodedManifest, dexFiles,
                    null, null, new File(work, "analysis"), sink);
            r.packageName = readPackage(decodedManifest);
            if (r.markdown != null && r.markdown.isFile()) {
                Prefs.setLastSecurityReport(readText(r.markdown));
            }
            Prefs.bumpSecurityScans();
            return r;
        } catch (Throwable t) {
            LogManager.e("Security scan failed: " + t);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    busy = false;
                    status.setText(getString(R.string.security_fail, "see Logcat"));
                }
            });
            return null;
        }
    }

    private static String readPackage(String manifest) {
        if (manifest == null) return "";
        int i = manifest.indexOf("package=\"");
        if (i < 0) return "";
        int start = i + 9;
        int end = manifest.indexOf('"', start);
        return end > start ? manifest.substring(start, end) : "";
    }

    private void renderReport(final SecurityAnalyzer.Report r) {
        this.resultHolder.removeAllViews();

        LinearLayout scoreCard = Ui.card(this);
        LinearLayout top = Ui.row(this);
        top.addView(Ui.chip(this, riskLabel(r.riskLabel()), severityColor(r.riskLabel())), Ui.lp(0, -2, 1.0f));
        top.addView(Ui.text(this, r.riskScore + "/100", 30.0f, severityColor(r.riskLabel()), true));
        scoreCard.addView(top);
        scoreCard.addView(Ui.text(this, getString(R.string.security_score), 11.0f, Ui.muted(), false));
        if (r.packageName != null && !r.packageName.isEmpty()) {
            scoreCard.addView(Ui.text(this, r.packageName, 13.0f, Ui.text(), true));
        }
        scoreCard.addView(Ui.text(this, r.summary(), 12.0f, Ui.muted(), false));

        LinearLayout sev = Ui.row(this);
        sev.setPadding(0, Ui.dp(this, 12.0f), 0, 0);
        addCount(sev, String.valueOf(r.critical), getString(R.string.security_critical), Ui.ERROR);
        addCount(sev, String.valueOf(r.high), getString(R.string.security_high), Ui.WARNING);
        addCount(sev, String.valueOf(r.medium), getString(R.string.security_medium), Ui.GOLD);
        addCount(sev, String.valueOf(r.low), getString(R.string.security_low), Ui.INFO);
        addCount(sev, String.valueOf(r.info), getString(R.string.security_info), Ui.muted());
        scoreCard.addView(sev);

        LinearLayout meta = Ui.row(this);
        meta.setPadding(0, Ui.dp(this, 10.0f), 0, 0);
        meta.addView(Ui.text(this, getString(R.string.security_files_scanned) + ": " + r.filesScanned, 11.0f, Ui.muted(), false), Ui.lp(0, -2, 1.0f));
        meta.addView(Ui.text(this, getString(R.string.security_strings_scanned) + ": " + r.stringsScanned, 11.0f, Ui.muted(), false));
        scoreCard.addView(meta);
        this.resultHolder.addView(scoreCard);

        if (r.total() == 0) {
            TextView clean = Ui.text(this, getString(R.string.security_clean), 13.0f, Ui.SUCCESS, true);
            clean.setPadding(0, Ui.dp(this, 12.0f), 0, 0);
            this.resultHolder.addView(clean);
        }

        if (!r.trackers.isEmpty()) {
            LinearLayout card = Ui.card(this);
            LinearLayout.LayoutParams lp = Ui.lp(-1, -2);
            lp.topMargin = Ui.dp(this, 10.0f);
            card.addView(Ui.text(this, getString(R.string.security_trackers), 13.0f, Ui.GOLD, true));
            LinearLayout chips = Ui.row(this);
            chips.setPadding(0, Ui.dp(this, 8.0f), 0, 0);
            for (String tracker : r.trackers) {
                TextView chip = Ui.chip(this, tracker, Ui.WARNING);
                LinearLayout.LayoutParams clp = Ui.lp(-2, -2);
                clp.rightMargin = Ui.dp(this, 6.0f);
                clp.bottomMargin = Ui.dp(this, 6.0f);
                chips.addView(chip, clp);
            }
            card.addView(chips);
            this.resultHolder.addView(card, lp);
        }

        if (!r.secrets.isEmpty()) {
            LinearLayout card = Ui.card(this);
            LinearLayout.LayoutParams lp = Ui.lp(-1, -2);
            lp.topMargin = Ui.dp(this, 10.0f);
            card.addView(Ui.text(this, getString(R.string.security_secrets), 13.0f, Ui.ERROR, true));
            card.addView(Ui.text(this, getString(R.string.security_secrets_hint), 10.5f, Ui.muted(), false));
            TextView copyAll = Ui.button(this, getString(R.string.common_copy), false);
            LinearLayout.LayoutParams calp = Ui.lp(-1, -2);
            calp.topMargin = Ui.dp(this, 8.0f);
            copyAll.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    StringBuilder all = new StringBuilder();
                    for (String value : r.secrets) all.append(value).append('\n');
                    android.content.ClipboardManager cm =
                            (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (cm != null) {
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("Juzoor secrets", all.toString()));
                        android.widget.Toast.makeText(SecurityActivity.this,
                                getString(R.string.security_secrets_copied, r.secrets.size()),
                                android.widget.Toast.LENGTH_SHORT).show();
                    }
                }
            });
            card.addView(copyAll, calp);
            for (final String secret : r.secrets) {
                TextView line = Ui.text(this, "• " + secret, 11.5f, Ui.muted(), false);
                line.setPadding(0, Ui.dp(this, 3.0f), 0, 0);
                line.setTextIsSelectable(true);
                line.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        android.content.ClipboardManager cm =
                                (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                        if (cm != null) {
                            cm.setPrimaryClip(android.content.ClipData.newPlainText("Juzoor secret", secret));
                            android.widget.Toast.makeText(SecurityActivity.this,
                                    "Copied " + secret.length() + " chars",
                                    android.widget.Toast.LENGTH_SHORT).show();
                        }
                        return true;
                    }
                });
                card.addView(line);
            }
            this.resultHolder.addView(card, lp);
        }

        String[] order = {SecurityAnalyzer.CRITICAL, SecurityAnalyzer.HIGH, SecurityAnalyzer.MEDIUM,
                SecurityAnalyzer.LOW, SecurityAnalyzer.INFO};
        for (String severity : order) {
            List<SecurityAnalyzer.Finding> group = new ArrayList<SecurityAnalyzer.Finding>();
            for (SecurityAnalyzer.Finding f : r.findings) {
                if (severity.equals(f.severity)) group.add(f);
            }
            if (group.isEmpty()) continue;
            TextView header = Ui.text(this, severity + "  ·  " + group.size(), 12.0f, severityColor(severity), true);
            header.setLetterSpacing(0.06f);
            header.setPadding(0, Ui.dp(this, 16.0f), 0, Ui.dp(this, 4.0f));
            this.resultHolder.addView(header);
            for (SecurityAnalyzer.Finding f : group) {
                LinearLayout card = Ui.card(this);
                LinearLayout.LayoutParams lp = Ui.lp(-1, -2);
                lp.topMargin = Ui.dp(this, 6.0f);
                card.addView(Ui.text(this, f.title, 13.5f, Ui.text(), true));
                TextView detail = Ui.text(this, f.detail, 11.5f, Ui.muted(), false);
                detail.setPadding(0, Ui.dp(this, 4.0f), 0, 0);
                card.addView(detail);
                if (f.location != null && !f.location.isEmpty()) {
                    card.addView(Ui.text(this, f.location, 10.5f, Ui.GOLD, false));
                }
                this.resultHolder.addView(card, lp);
            }
        }

        TextView export = Ui.button(this, getString(R.string.security_export), false);
        LinearLayout.LayoutParams lp = Ui.lp(-1, -2);
        lp.topMargin = Ui.dp(this, 18.0f);
        export.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                exportReport(r);
            }
        });
        this.resultHolder.addView(export, lp);
    }

    private void addCount(LinearLayout row, String value, String label, int color) {
        LinearLayout column = Ui.column(this);
        column.setGravity(17);
        column.addView(Ui.text(this, value, 18.0f, color, true));
        column.addView(Ui.text(this, label, 9.5f, Ui.muted(), false));
        row.addView(column, Ui.lp(0, -2, 1.0f));
    }

    private int severityColor(String severity) {
        if (SecurityAnalyzer.CRITICAL.equals(severity)) return Ui.ERROR;
        if (SecurityAnalyzer.HIGH.equals(severity)) return Ui.WARNING;
        if (SecurityAnalyzer.MEDIUM.equals(severity)) return Ui.GOLD;
        if (SecurityAnalyzer.LOW.equals(severity)) return Ui.INFO;
        return Ui.muted();
    }

    private String riskLabel(String label) {
        if (SecurityAnalyzer.CRITICAL.equals(label)) return getString(R.string.security_risk_critical);
        if (SecurityAnalyzer.HIGH.equals(label)) return getString(R.string.security_risk_high);
        if (SecurityAnalyzer.MEDIUM.equals(label)) return getString(R.string.security_risk_medium);
        return getString(R.string.security_risk_low);
    }

    private void exportReport(SecurityAnalyzer.Report r) {
        String text = Prefs.lastSecurityReport();
        if ((text == null || text.isEmpty()) && r.markdown != null && r.markdown.isFile()) {
            text = readText(r.markdown);
        }
        if (text == null || text.isEmpty()) {
            StringBuilder b = new StringBuilder();
            b.append("# Juzoor security report\n\n");
            b.append("Risk score: ").append(r.riskScore).append("/100\n\n");
            for (SecurityAnalyzer.Finding f : r.findings) {
                b.append("- [").append(f.severity).append("] ").append(f.title)
                        .append(" — ").append(f.detail).append('\n');
            }
            text = b.toString();
        }
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.security_share_subject));
        send.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(send, getString(R.string.security_export)));
        LogManager.i("Security report shared");
    }

    private static String readText(File file) {
        StringBuilder b = new StringBuilder();
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
            String line;
            while ((line = br.readLine()) != null) b.append(line).append('\n');
            br.close();
        } catch (Throwable ignored) {
        }
        return b.toString();
    }

    /** Minimal LogSink that forwards to LogManager and never cancels. */
    private static final class Sink implements LogSink {
        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public void log(String level, String message) {
            if (LogManager.ERROR.equals(level)) LogManager.e(message);
            else if (LogManager.WARN.equals(level)) LogManager.w(message);
            else if (LogManager.SUCCESS.equals(level)) LogManager.s(message);
            else if (LogManager.DEBUG.equals(level)) LogManager.d(message);
            else LogManager.i(message);
        }

        @Override
        public void progress(int percent, String step) {
            // standalone scan has no progress bar
        }
    }
}
