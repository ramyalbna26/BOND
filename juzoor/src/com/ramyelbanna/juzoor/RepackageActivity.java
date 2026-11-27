package com.ramyelbanna.juzoor;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.ramyelbanna.juzoor.core.JuzoorFiles;
import com.ramyelbanna.juzoor.core.KeystoreUtil;
import com.ramyelbanna.juzoor.core.LogManager;
import com.ramyelbanna.juzoor.core.Prefs;
import com.ramyelbanna.juzoor.engine.LogSink;
import com.ramyelbanna.juzoor.engine.Repackager;
import com.ramyelbanna.juzoor.ui.Ui;

import java.io.File;

/**
 * Local APK repackaging workbench.
 *
 * <p>Select an APK, optionally patch manifest flags, replace constant strings in
 * {@code classes*.dex} and inject a file, then rebuild the archive and sign it
 * with v1 / v2 / v3 schemes using the built-in or a user supplied keystore.</p>
 */
public class RepackageActivity extends BaseActivity {

    private static final int REQ_APK = 51;
    private static final int REQ_KEY = 52;
    private static final int REQ_INJECT = 53;

    interface Cb {
        void on(boolean value);
    }

    private String inputPath;
    private String injectPath;
    private File keystore;
    private boolean busy;

    private boolean v1 = Prefs.signV1();
    private boolean v2 = Prefs.signV2();
    private boolean v3 = Prefs.signV3();
    private boolean patchDebuggable;
    private boolean patchBackup;
    private boolean patchCleartext;

    private TextView inputLabel;
    private TextView keystoreLabel;
    private TextView injectLabel;
    private TextView status;
    private TextView logView;
    private EditText outputName;
    private EditText alias;
    private EditText password;
    private EditText oldString;
    private EditText newString;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        this.keystore = KeystoreUtil.defaultKeystore(this);
        buildUi();
    }

    private void buildUi() {
        this.body.removeAllViews();
        topBar(getString(R.string.repackage_title), true);

        LinearLayout intro = Ui.card(this);
        intro.addView(Ui.text(this, getString(R.string.repackage_sub), 13.0f, Ui.muted(), false));
        LinearLayout badges = Ui.row(this);
        badges.setPadding(0, Ui.dp(this, 10.0f), 0, 0);
        badges.addView(Ui.chip(this, getString(R.string.home_badge_offline), Ui.SUCCESS));
        badges.addView(Ui.spacer(this, 8));
        badges.addView(Ui.chip(this, "apksig v1·v2·v3", Ui.GOLD));
        intro.addView(badges);
        this.body.addView(intro);

        TextView pick = Ui.button(this, getString(R.string.repackage_pick), true);
        LinearLayout.LayoutParams lp = Ui.lp(-1, -2);
        lp.topMargin = Ui.dp(this, 14.0f);
        pick.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                pick(REQ_APK, "apk");
            }
        });
        this.body.addView(pick, lp);

        this.inputLabel = Ui.text(this, getString(R.string.repackage_input) + ": —", 12.0f, Ui.muted(), false);
        this.inputLabel.setPadding(0, Ui.dp(this, 8.0f), 0, 0);
        this.body.addView(this.inputLabel);

        header(getString(R.string.repackage_output));
        this.outputName = field("app-juzoor.apk", false);
        this.body.addView(this.outputName);

        header(getString(R.string.repackage_keystore));
        this.keystoreLabel = Ui.text(this, keystoreText(), 12.0f, Ui.text(), false);
        this.keystoreLabel.setPadding(0, 0, 0, Ui.dp(this, 8.0f));
        this.body.addView(this.keystoreLabel);
        TextView chooseKey = Ui.button(this, getString(R.string.repackage_keystore_pick), false);
        chooseKey.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                pick(REQ_KEY, "all");
            }
        });
        this.body.addView(chooseKey);

        this.alias = field(Prefs.keystoreAlias(), false);
        this.alias.setHint(getString(R.string.repackage_alias));
        this.body.addView(this.alias);
        this.password = field(Prefs.keystorePass(), true);
        this.password.setHint(getString(R.string.repackage_password));
        this.body.addView(this.password);

        header(getString(R.string.repackage_patch));
        addToggle(getString(R.string.repackage_v1), v1, new Cb() {
            @Override
            public void on(boolean value) {
                v1 = value;
                Prefs.setSignV1(value);
            }
        });
        addToggle(getString(R.string.repackage_v2), v2, new Cb() {
            @Override
            public void on(boolean value) {
                v2 = value;
                Prefs.setSignV2(value);
            }
        });
        addToggle(getString(R.string.repackage_v3), v3, new Cb() {
            @Override
            public void on(boolean value) {
                v3 = value;
                Prefs.setSignV3(value);
            }
        });
        addToggle(getString(R.string.repackage_patch_debuggable), patchDebuggable, new Cb() {
            @Override
            public void on(boolean value) {
                patchDebuggable = value;
            }
        });
        addToggle(getString(R.string.repackage_patch_backup), patchBackup, new Cb() {
            @Override
            public void on(boolean value) {
                patchBackup = value;
            }
        });
        addToggle(getString(R.string.repackage_patch_cleartext), patchCleartext, new Cb() {
            @Override
            public void on(boolean value) {
                patchCleartext = value;
            }
        });

        header(getString(R.string.repackage_smali));
        TextView note = Ui.text(this, getString(R.string.repackage_smali_note), 11.5f, Ui.muted(), false);
        note.setPadding(0, 0, 0, Ui.dp(this, 8.0f));
        this.body.addView(note);
        this.oldString = field("", false);
        this.oldString.setHint(getString(R.string.repackage_old));
        this.body.addView(this.oldString);
        this.newString = field("", false);
        this.newString.setHint(getString(R.string.repackage_new));
        this.body.addView(this.newString);

        header(getString(R.string.repackage_inject));
        this.injectLabel = Ui.text(this, "—", 12.0f, Ui.muted(), false);
        this.injectLabel.setPadding(0, 0, 0, Ui.dp(this, 8.0f));
        this.body.addView(this.injectLabel);
        TextView chooseInject = Ui.button(this, getString(R.string.repackage_select_file), false);
        chooseInject.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                pick(REQ_INJECT, "all");
            }
        });
        this.body.addView(chooseInject);

        TextView build = Ui.button(this, getString(R.string.repackage_build), true);
        LinearLayout.LayoutParams lpb = Ui.lp(-1, -2);
        lpb.topMargin = Ui.dp(this, 22.0f);
        build.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                rebuild();
            }
        });
        this.body.addView(build, lpb);

        this.status = Ui.text(this, "", 12.0f, Ui.GOLD, false);
        this.status.setPadding(0, Ui.dp(this, 12.0f), 0, 0);
        this.body.addView(this.status);

        header(getString(R.string.repackage_log));
        this.logView = Ui.text(this, "", 10.5f, Ui.muted(), false);
        this.logView.setTypeface(Typeface.MONOSPACE);
        this.logView.setTextIsSelectable(true);
        this.logView.setBackground(Ui.round(Ui.surface(), Ui.dp(this, 10.0f)));
        int pad = Ui.dp(this, 10.0f);
        this.logView.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams lpl = Ui.lp(-1, -2);
        lpl.topMargin = Ui.dp(this, 6.0f);
        this.body.addView(this.logView, lpl);
    }

    private String keystoreText() {
        if (this.keystore == null) return getString(R.string.repackage_no_keystore);
        if (this.keystore.getName().equals("juzoor-signing.p12")) {
            return getString(R.string.repackage_keystore_default);
        }
        return this.keystore.getAbsolutePath();
    }

    private void pick(int request, String filter) {
        Intent intent = new Intent(this, FileBrowserActivity.class);
        intent.putExtra("pick", true);
        intent.putExtra("filter", filter);
        startActivityForResult(intent, request);
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null) return;
        String path = data.getStringExtra(ConversionService.EXTRA_PATH);
        if (path == null) return;
        if (request == REQ_APK) {
            this.inputPath = path;
            File in = new File(path);
            this.inputLabel.setText(getString(R.string.repackage_input) + ": " + in.getName());
            String base = in.getName();
            if (base.toLowerCase().endsWith(".apk")) base = base.substring(0, base.length() - 4);
            this.outputName.setText(base + "-juzoor.apk");
        } else if (request == REQ_KEY) {
            File key = new File(path);
            String lower = key.getName().toLowerCase();
            if (!lower.endsWith(".p12") && !lower.endsWith(".jks") && !lower.endsWith(".keystore")) {
                toast(getString(R.string.repackage_no_keystore));
                return;
            }
            this.keystore = key;
            this.keystoreLabel.setText(keystoreText());
        } else if (request == REQ_INJECT) {
            this.injectPath = path;
            this.injectLabel.setText(getString(R.string.repackage_injected, new File(path).getName()));
        }
    }

    private void rebuild() {
        if (this.busy) return;
        if (this.inputPath == null) {
            toast(getString(R.string.repackage_need_input));
            return;
        }
        if (this.keystore == null || !this.keystore.isFile()) {
            toast(getString(R.string.repackage_no_keystore));
            return;
        }
        final File in = new File(this.inputPath);
        String name = this.outputName.getText().toString().trim();
        if (name.isEmpty()) name = "app-juzoor.apk";
        if (!name.toLowerCase().endsWith(".apk")) name = name + ".apk";
        final File out = new File(in.getParentFile(), name);

        final Repackager.Options o = new Repackager.Options();
        o.v1 = this.v1;
        o.v2 = this.v2;
        o.v3 = this.v3;
        o.minSdk = 26;
        o.patchDebuggable = this.patchDebuggable;
        o.patchBackup = this.patchBackup;
        o.patchCleartext = this.patchCleartext;
        o.keystore = this.keystore;
        o.alias = this.alias.getText().toString().trim();
        if (o.alias.isEmpty()) o.alias = KeystoreUtil.ALIAS;
        o.storePass = this.password.getText().toString();
        o.keyPass = o.storePass;
        String oldValue = this.oldString.getText().toString();
        String newValue = this.newString.getText().toString();
        if (!oldValue.isEmpty() && !newValue.isEmpty()) {
            o.stringPatches.add(new String[]{oldValue, newValue});
        }
        if (this.injectPath != null) {
            o.injectFile = new File(this.injectPath);
            o.injectPath = "assets/" + o.injectFile.getName();
        }

        this.busy = true;
        this.status.setText(getString(R.string.repackage_running));
        this.logView.setText("");
        LogManager.i("Repackaging " + in.getName() + " → " + out.getName());
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Repackager.Result result = Repackager.run(in, out, o, new Sink());
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        busy = false;
                        showResult(result);
                    }
                });
            }
        }, "juzoor-repack").start();
    }

    private void showResult(final Repackager.Result result) {
        StringBuilder b = new StringBuilder();
        for (String line : result.log) b.append("• ").append(line).append('\n');
        if (!result.success) b.append("✗ ").append(result.error);
        this.logView.setText(b.toString());
        if (!result.success) {
            this.status.setText(getString(R.string.repackage_fail, result.error));
            LogManager.e("Repackage failed: " + result.error);
            return;
        }
        this.status.setText(getString(R.string.repackage_done) + " · " + result.summary());
        this.status.setTextColor(Ui.SUCCESS);

        LinearLayout actions = Ui.row(this);
        LinearLayout.LayoutParams lp = Ui.lp(-1, -2);
        lp.topMargin = Ui.dp(this, 12.0f);
        TextView share = Ui.button(this, getString(R.string.repackage_share), true);
        share.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    startActivity(JuzoorFiles.shareIntent(RepackageActivity.this, result.output,
                            getString(R.string.repackage_share)));
                } catch (Throwable t) {
                    toast(getString(R.string.result_no_handler));
                }
            }
        });
        actions.addView(share, Ui.lp(0, -2, 1.0f));
        TextView install = Ui.button(this, getString(R.string.repackage_install), false);
        LinearLayout.LayoutParams lpi = Ui.lp(0, -2, 1.0f);
        lpi.leftMargin = Ui.dp(this, 8.0f);
        install.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(JuzoorFiles.stage(RepackageActivity.this, result.output),
                            "application/vnd.android.package-archive");
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                } catch (Throwable t) {
                    toast(getString(R.string.result_no_handler));
                }
            }
        });
        actions.addView(install, lpi);
        this.body.addView(actions, lp);
    }

    private void header(String text) {
        TextView header = Ui.text(this, text, 12.0f, Ui.GOLD, true);
        header.setLetterSpacing(0.08f);
        LinearLayout.LayoutParams lp = Ui.lp(-1, -2);
        lp.topMargin = Ui.dp(this, 20.0f);
        lp.bottomMargin = Ui.dp(this, 8.0f);
        this.body.addView(header, lp);
    }

    private EditText field(String value, boolean secret) {
        EditText edit = new EditText(this);
        edit.setText(value);
        edit.setTextSize(13.0f);
        edit.setTextColor(Ui.text());
        edit.setHintTextColor(Ui.muted());
        edit.setSingleLine(true);
        edit.setBackground(Ui.stroke(Ui.surface(), Ui.GOLD_DARK, Ui.dp(this, 10.0f), Ui.dp(this, 1.0f)));
        int pad = Ui.dp(this, 10.0f);
        edit.setPadding(pad, pad, pad, pad);
        if (secret) edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        LinearLayout.LayoutParams lp = Ui.lp(-1, -2);
        lp.topMargin = Ui.dp(this, 6.0f);
        edit.setLayoutParams(lp);
        return edit;
    }

    private void addToggle(String label, boolean initial, final Cb cb) {
        LinearLayout row = Ui.row(this);
        row.setBackground(Ui.round(Ui.surface(), Ui.dp(this, 12.0f)));
        int dp = Ui.dp(this, 12.0f);
        row.setPadding(dp, dp, dp, dp);
        row.addView(Ui.text(this, label, 13.0f, Ui.text(), false), Ui.lp(0, -2, 1.0f));
        final boolean[] state = {initial};
        final TextView chip = Ui.text(this, initial ? getString(R.string.settings_on) : getString(R.string.settings_off),
                11.0f, initial ? Ui.SUCCESS : Ui.muted(), true);
        chip.setPadding(Ui.dp(this, 12.0f), Ui.dp(this, 5.0f), Ui.dp(this, 12.0f), Ui.dp(this, 5.0f));
        chip.setBackground(Ui.stroke(Ui.surface2(), initial ? Ui.SUCCESS : 872415231,
                Ui.dp(this, 20.0f), Ui.dp(this, 1.0f)));
        row.addView(chip);
        row.setClickable(true);
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                boolean next = !state[0];
                state[0] = next;
                chip.setText(next ? getString(R.string.settings_on) : getString(R.string.settings_off));
                chip.setTextColor(next ? Ui.SUCCESS : Ui.muted());
                chip.setBackground(Ui.stroke(Ui.surface2(), next ? Ui.SUCCESS : 872415231,
                        Ui.dp(RepackageActivity.this, 20.0f), Ui.dp(RepackageActivity.this, 1.0f)));
                cb.on(next);
            }
        });
        LinearLayout.LayoutParams lp = Ui.lp(-1, -2);
        lp.topMargin = Ui.dp(this, 8.0f);
        this.body.addView(row, lp);
    }

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
            else LogManager.i(message);
        }

        @Override
        public void progress(int percent, String step) {
        }
    }
}
