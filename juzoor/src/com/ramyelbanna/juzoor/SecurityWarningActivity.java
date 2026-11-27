package com.ramyelbanna.juzoor;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import java.util.Locale;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.ramyelbanna.juzoor.core.LogManager;
import com.ramyelbanna.juzoor.ui.Ui;

/**
 * Red/black legal warning shown when Juzoor detects a debugger, an
 * instrumentation framework or an emulator.  The activity is deliberately
 * independent from {@link BaseActivity} so it cannot recurse into the guard.
 */
public class SecurityWarningActivity extends Activity {

    public static final String EXTRA_REASON = "reason";
    private static final int CLOSE_SECONDS = 12;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private int remaining = CLOSE_SECONDS;
    private TextView countdown;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            remaining--;
            if (countdown != null) {
                countdown.setText(getString(R.string.security_warning_countdown, remaining));
            }
            if (remaining <= 0) {
                close();
            } else {
                handler.postDelayed(this, 1000L);
            }
        }
    };

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        String reason = getIntent() == null ? "unknown" : getIntent().getStringExtra(EXTRA_REASON);
        if (reason == null || reason.isEmpty()) reason = "unknown";
        SecurityGuard.record(this, reason);

        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        window.setBackgroundDrawableResource(android.R.color.black);
        window.setStatusBarColor(0xFF000000);
        window.setNavigationBarColor(0xFF000000);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF000000);
        scroll.setFillViewport(true);
        LinearLayout root = Ui.column(this);
        int pad = Ui.dp(this, 22.0f);
        root.setPadding(pad, Ui.dp(this, 30.0f), pad, pad);
        scroll.addView(root);
        setContentView(scroll);

        TextView icon = Ui.text(this, "⚠️", 54.0f, 0xFFFF3B30, true);
        icon.setGravity(17);
        root.addView(icon, Ui.lp(-1, -2));

        TextView title = Ui.text(this, getString(R.string.security_warning_title), 27.0f, 0xFFFF3B30, true);
        title.setGravity(17);
        title.setPadding(0, Ui.dp(this, 8.0f), 0, Ui.dp(this, 14.0f));
        root.addView(title);

        LinearLayout card = Ui.column(this);
        card.setBackground(Ui.stroke(0xFF16090A, 0xFFFF3B30, Ui.dp(this, 16.0f), Ui.dp(this, 1.0f)));
        int cp = Ui.dp(this, 16.0f);
        card.setPadding(cp, cp, cp, cp);
        TextView legal = Ui.text(this, getString(R.string.security_warning_legal), 15.0f, 0xFFF5E9E9, false);
        legal.setLineSpacing(Ui.dp(this, 4.0f), 1.0f);
        card.addView(legal);
        // Always include the Arabic legal sentence required by the owner, even
        // when the current UI language is English.
        if (!"ar".equals(Locale.getDefault().getLanguage())) {
            TextView arabic = Ui.text(this, getString(R.string.security_warning_ar), 15.0f, 0xFFF5E9E9, false);
            arabic.setPadding(0, Ui.dp(this, 12.0f), 0, 0);
            card.addView(arabic);
        }
        root.addView(card, Ui.lp(-1, -2));

        LinearLayout detail = Ui.column(this);
        detail.setBackground(Ui.round(0xFF16090A, Ui.dp(this, 12.0f)));
        int dp = Ui.dp(this, 13.0f);
        detail.setPadding(dp, dp, dp, dp);
        detail.addView(Ui.text(this, getString(R.string.security_warning_reason), 11.0f, 0xFFFF8A80, true));
        detail.addView(Ui.text(this, reason, 13.0f, 0xFFF5E9E9, false));
        detail.addView(Ui.text(this, getString(R.string.security_warning_logged), 11.0f, 0xFFA9A395, false));
        LinearLayout.LayoutParams dlp = Ui.lp(-1, -2);
        dlp.topMargin = Ui.dp(this, 14.0f);
        root.addView(detail, dlp);

        countdown = Ui.text(this, getString(R.string.security_warning_countdown, remaining), 12.0f, 0xFFFF8A80, true);
        countdown.setGravity(17);
        countdown.setPadding(0, Ui.dp(this, 18.0f), 0, Ui.dp(this, 8.0f));
        root.addView(countdown);

        TextView close = Ui.button(this, getString(R.string.security_warning_exit), false);
        close.setTextColor(0xFFFF3B30);
        close.setBackground(Ui.stroke(0xFF16090A, 0xFFFF3B30, Ui.dp(this, 16.0f), Ui.dp(this, 1.0f)));
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                close();
            }
        });
        root.addView(close, Ui.lp(-1, -2));

        TextView owner = Ui.text(this, getString(R.string.security_warning_owner), 10.5f, 0xFF6B6455, false);
        owner.setGravity(17);
        owner.setPadding(0, Ui.dp(this, 18.0f), 0, 0);
        root.addView(owner);

        handler.postDelayed(tick, 1000L);
        LogManager.e("Security warning displayed: " + reason);
    }

    private void close() {
        handler.removeCallbacksAndMessages(null);
        try {
            finishAffinity();
        } catch (Throwable ignored) {
        }
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    @Override
    public void onBackPressed() {
        // The warning cannot be dismissed with the back key.
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
