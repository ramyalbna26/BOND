package com.ramyelbanna.juzoor;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.ramyelbanna.juzoor.core.LogManager;
import com.ramyelbanna.juzoor.core.Prefs;
import com.ramyelbanna.juzoor.ui.Ui;

/* loaded from: /workspace/apk_extract/classes.dex */
public class AboutActivity extends BaseActivity {
    private static final String CALL = "+201515938166";
    private static final String EMAIL = "RAMYALBNA26@GMAIL.COM";
    private static final String WHATSAPP = "+201030669852";

    @Override // com.ramyelbanna.juzoor.BaseActivity, android.app.Activity
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        buildUi();
    }

    private void buildUi() {
        topBar(getString(R.string.about_title), true);
        LinearLayout card = Ui.card(this);
        card.setGravity(17);
        TextView text = Ui.text(this, "◆", 34.0f, Ui.GOLD, true);
        text.setGravity(17);
        card.addView(text);
        TextView text2 = Ui.text(this, getString(R.string.app_name), 22.0f, Ui.text(), true);
        text2.setGravity(17);
        card.addView(text2);
        TextView text3 = Ui.text(this, getString(R.string.app_tagline), 12.0f, Ui.muted(), false);
        text3.setGravity(17);
        text3.setPadding(0, Ui.dp(this, 6.0f), 0, 0);
        card.addView(text3);
        TextView textVer = Ui.text(this, getString(R.string.settings_version) + " " + versionLine(), 11.0f, Ui.GOLD, false);
        textVer.setGravity(17);
        textVer.setPadding(0, Ui.dp(this, 8.0f), 0, 0);
        card.addView(textVer);
        this.body.addView(card);
        LinearLayout card2 = Ui.card(this);
        LinearLayout.LayoutParams lp = Ui.lp(-1, -2);
        lp.topMargin = Ui.dp(this, 12.0f);
        card2.addView(Ui.text(this, getString(R.string.about_owner), 11.0f, Ui.GOLD, true));
        card2.addView(Ui.text(this, getString(R.string.about_owner_name), 18.0f, Ui.text(), true));
        card2.addView(Ui.text(this, getString(R.string.about_role), 12.0f, Ui.muted(), false));
        this.body.addView(card2, lp);
        TextView text4 = Ui.text(this, getString(R.string.about_contact), 12.0f, Ui.GOLD, true);
        text4.setPadding(0, Ui.dp(this, 20.0f), 0, Ui.dp(this, 8.0f));
        this.body.addView(text4);
        contact(getString(R.string.about_whatsapp), WHATSAPP, "https://wa.me/" + WHATSAPP.replace("+", ""));
        contact(getString(R.string.about_call), CALL, "tel:+201515938166");
        contact(getString(R.string.about_email), EMAIL, "mailto:RAMYALBNA26@GMAIL.COM");
        LinearLayout card3 = Ui.card(this);
        LinearLayout.LayoutParams lp2 = Ui.lp(-1, -2);
        lp2.topMargin = Ui.dp(this, 20.0f);
        TextView text5 = Ui.text(this, getString(R.string.about_copyright_text), 11.0f, Ui.muted(), false);
        text5.setLineSpacing(Ui.dp(this, 4.0f), 1.0f);
        card3.addView(text5);
        this.body.addView(card3, lp2);
        TextView text6 = Ui.text(this, "v" + Prefs.VERSION + "  ·  Juzoor™", 11.0f, Ui.muted(), false);
        text6.setGravity(17);
        text6.setPadding(0, Ui.dp(this, 16.0f), 0, 0);
        this.body.addView(text6);
        TextView button = Ui.button(this, getString(R.string.menu_logcat), false);
        LinearLayout.LayoutParams lp3 = Ui.lp(-1, -2);
        lp3.topMargin = Ui.dp(this, 16.0f);
        button.setOnClickListener(new View.OnClickListener() { // from class: com.ramyelbanna.juzoor.AboutActivity$$ExternalSyntheticLambda0
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                AboutActivity.this.lambda$buildUi$0(view);
            }
        });
        this.body.addView(button, lp3);
    }

    private String versionLine() {
        try {
            android.content.pm.PackageInfo info = getPackageManager()
                    .getPackageInfo(getPackageName(), 0);
            return info.versionName + " (" + info.versionCode + ")";
        } catch (Throwable t) {
            return Prefs.VERSION;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$buildUi$0(View view) {
        open(LogcatActivity.class);
    }

    private void contact(final String str, final String str2, final String str3) {
        LinearLayout row = Ui.row(this);
        row.setBackground(Ui.round(Ui.surface(), Ui.dp(this, 12.0f)));
        int dp = Ui.dp(this, 13.0f);
        row.setPadding(dp, dp, dp, dp);
        LinearLayout column = Ui.column(this);
        column.addView(Ui.text(this, str, 11.0f, Ui.GOLD, true));
        column.addView(Ui.text(this, str2, 14.0f, Ui.text(), false));
        row.addView(column, Ui.lp(0, -2, 1.0f));
        row.addView(Ui.chip(this, "↗", Ui.GOLD));
        row.setClickable(true);
        row.setOnClickListener(new View.OnClickListener() { // from class: com.ramyelbanna.juzoor.AboutActivity$$ExternalSyntheticLambda1
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                AboutActivity.this.lambda$contact$1(str3, str, str2, view);
            }
        });
        LinearLayout.LayoutParams lp = Ui.lp(-1, -2);
        lp.topMargin = Ui.dp(this, 8.0f);
        this.body.addView(row, lp);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$contact$1(String str, String str2, String str3, View view) {
        try {
            startActivity(new Intent("android.intent.action.VIEW", Uri.parse(str)));
            LogManager.i("Contact opened: " + str2);
        } catch (Throwable unused) {
            toast(str3);
        }
    }
}
