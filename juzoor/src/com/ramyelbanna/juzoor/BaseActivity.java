package com.ramyelbanna.juzoor;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.ramyelbanna.juzoor.core.Prefs;
import com.ramyelbanna.juzoor.ui.Ui;

/* loaded from: /workspace/apk_extract/classes.dex */
public abstract class BaseActivity extends Activity {
    protected LinearLayout body;
    private float appliedFontScale = 1.0f;
    private String appliedLang = "";

    @Override // android.app.Activity
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        Prefs.init(this);
        String threat = SecurityGuard.inspect(this);
        if (threat != null) {
            Intent warning = new Intent(this, SecurityWarningActivity.class);
            warning.putExtra(SecurityWarningActivity.EXTRA_REASON, threat);
            warning.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(warning);
            finish();
            return;
        }
        appliedFontScale = Prefs.fontScale();
        appliedLang = Prefs.lang();
        ((App) getApplication()).applyLocale();
        Window window = getWindow();
        window.setBackgroundDrawableResource(android.R.color.transparent);
        window.getDecorView().setBackgroundColor(Ui.bg());
        window.addFlags(Integer.MIN_VALUE);
        window.setStatusBarColor(Ui.statusBarColor());
        window.setNavigationBarColor(Ui.bg());
        if (Ui.dark()) {
            window.getDecorView().setSystemUiVisibility(0);
        } else {
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
        this.body = Ui.column(this);
        int dp = Ui.dp(this, 18.0f);
        this.body.setPadding(dp, Ui.dp(this, 14.0f), dp, Ui.dp(this, 32.0f));
        if (scrollBody()) {
            ScrollView scrollView = new ScrollView(this);
            scrollView.setFillViewport(true);
            scrollView.setBackgroundColor(Ui.bg());
            scrollView.addView(this.body, new FrameLayout.LayoutParams(-1, -2));
            setContentView(scrollView);
        } else {
            // Screens with a weighted, self-scrolling list must fill the
            // viewport; nesting that list in a ScrollView would measure every
            // row and defeat recycling.
            this.body.setBackgroundColor(Ui.bg());
            setContentView(this.body, new FrameLayout.LayoutParams(-1, -1));
        }
    }

    /** Override to build a viewport-filling body instead of a scrolling page. */
    protected boolean scrollBody() {
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Rebuild the current screen when the user changed the font slider in
        // Settings, so every visible label picks up the new size immediately.
        if (Math.abs(Prefs.fontScale() - appliedFontScale) > 0.001f) {
            appliedFontScale = Prefs.fontScale();
        appliedLang = Prefs.lang();
            recreate();
        }
    }

    protected LinearLayout topBar(String str, boolean z) {
        LinearLayout row = Ui.row(this);
        if (z) {
            TextView text = Ui.text(this, "‹", 30.0f, Ui.GOLD, true);
            text.setPadding(Ui.dp(this, 6.0f), 0, Ui.dp(this, 14.0f), 0);
            text.setOnClickListener(new View.OnClickListener() { // from class: com.ramyelbanna.juzoor.BaseActivity$$ExternalSyntheticLambda0
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    BaseActivity.this.lambda$topBar$0(view);
                }
            });
            row.addView(text);
        }
        row.addView(Ui.text(this, str, 18.0f, Ui.text(), true), Ui.lp(0, -2, 1.0f));
        row.setPadding(0, 0, 0, Ui.dp(this, 10.0f));
        this.body.addView(row, Ui.lp(-1, -2));
        return row;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$topBar$0(View view) {
        finish();
    }

    protected void open(Class<?> cls) {
        startActivity(new Intent(this, cls));
    }

    protected void toast(String str) {
        Toast.makeText(this, str, Toast.LENGTH_SHORT).show();
    }
}
