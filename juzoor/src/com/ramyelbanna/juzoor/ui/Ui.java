package com.ramyelbanna.juzoor.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.ramyelbanna.juzoor.core.LogManager;
import com.ramyelbanna.juzoor.core.Prefs;

/* loaded from: /workspace/apk_extract/classes.dex */
public final class Ui {
    public static final int BG_DARK = -16250869;
    public static final int BG_LIGHT = -329745;
    public static final int ERROR = -42406;
    public static final int GOLD = -2838729;
    public static final int GOLD_DARK = -7574241;
    public static final int GOLD_LIGHT = -597382;
    public static final int INFO = -10177034;
    public static final int SUCCESS = -12723068;
    public static final int SURFACE_DARK = -15592936;
    public static final int SURFACE_DARK_2 = -15066590;
    public static final int SURFACE_LIGHT = -1;
    public static final int TEXT_DARK = -658970;
    public static final int TEXT_LIGHT = -15264756;
    public static final int TEXT_MUTED = -5659755;
    public static final int TEXT_MUTED_LIGHT = -9739179;
    public static final int WARNING = -18611;

    private Ui() {
    }

    public static boolean dark() {
        return Prefs.dark();
    }

    public static int bg() {
        return dark() ? BG_DARK : BG_LIGHT;
    }

    public static int surface() {
        if (dark()) {
            return SURFACE_DARK;
        }
        return -1;
    }

    public static int surface2() {
        if (dark()) {
            return SURFACE_DARK_2;
        }
        return -922400;
    }

    public static int text() {
        return dark() ? TEXT_DARK : TEXT_LIGHT;
    }

    public static int muted() {
        return dark() ? TEXT_MUTED : TEXT_MUTED_LIGHT;
    }

    public static int dp(Context context, float f) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, f, context.getResources().getDisplayMetrics());
    }

    public static GradientDrawable round(int i, float f) {
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setShape(GradientDrawable.RECTANGLE);
        gradientDrawable.setColor(i);
        gradientDrawable.setCornerRadius(f);
        return gradientDrawable;
    }

    public static GradientDrawable stroke(int i, int i2, float f, int i3) {
        GradientDrawable round = round(i, f);
        round.setStroke(i3, i2);
        return round;
    }

    public static GradientDrawable goldGradient(float f) {
        GradientDrawable gradientDrawable = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{GOLD_LIGHT, GOLD, GOLD_DARK});
        gradientDrawable.setCornerRadius(f);
        return gradientDrawable;
    }

    public static GradientDrawable goldStroke(float f, int i) {
        GradientDrawable gradientDrawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{450146103, 181710647});
        gradientDrawable.setCornerRadius(f);
        gradientDrawable.setStroke(i, 1725214519);
        return gradientDrawable;
    }

    public static void ripple(View view, int i) {
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(i), view.getBackground(), null));
    }

    public static TextView text(Context context, String str, float f, int i, boolean z) {
        TextView textView = new TextView(context);
        textView.setText(str);
        textView.setTextSize(f);
        textView.setTextColor(i);
        if (z) {
            textView.setTypeface(Typeface.DEFAULT_BOLD);
        }
        textView.setLineSpacing(dp(context, 3.0f), 1.0f);
        return textView;
    }

    public static TextView title(Context context, String str) {
        TextView text = text(context, str, 26.0f, text(), true);
        text.setLetterSpacing(0.02f);
        return text;
    }

    public static TextView goldTitle(Context context, String str) {
        TextView text = text(context, str, 26.0f, GOLD, true);
        text.setLetterSpacing(0.02f);
        return text;
    }

    public static TextView label(Context context, String str) {
        return text(context, str, 13.0f, muted(), false);
    }

    public static LinearLayout column(Context context) {
        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        return linearLayout;
    }

    public static LinearLayout row(Context context) {
        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.HORIZONTAL);
        linearLayout.setGravity(16);
        return linearLayout;
    }

    public static LinearLayout card(Context context) {
        LinearLayout column = column(context);
        column.setBackground(goldStroke(dp(context, 18.0f), dp(context, 1.0f)));
        int dp = dp(context, 16.0f);
        column.setPadding(dp, dp, dp, dp);
        return column;
    }

    public static LinearLayout.LayoutParams lp(int i, int i2) {
        return new LinearLayout.LayoutParams(i, i2);
    }

    public static LinearLayout.LayoutParams lp(int i, int i2, float f) {
        return new LinearLayout.LayoutParams(i, i2, f);
    }

    public static void gap(View view, int i) {
        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        if (layoutParams instanceof LinearLayout.LayoutParams) {
            ((LinearLayout.LayoutParams) layoutParams).topMargin = i;
        }
    }

    public static View spacer(Context context, int i) {
        View view = new View(context);
        view.setLayoutParams(new LinearLayout.LayoutParams(dp(context, i), 1));
        return view;
    }

    public static TextView button(Context context, String str, boolean z) {
        TextView textView = new TextView(context);
        textView.setText(str);
        textView.setTextSize(16.0f);
        textView.setTypeface(Typeface.DEFAULT_BOLD);
        textView.setGravity(17);
        int dp = dp(context, 18.0f);
        int dp2 = dp(context, 15.0f);
        textView.setPadding(dp, dp2, dp, dp2);
        if (z) {
            textView.setTextColor(-15462390);
            textView.setBackground(goldGradient(dp(context, 16.0f)));
        } else {
            textView.setTextColor(GOLD);
            textView.setBackground(goldStroke(dp(context, 16.0f), dp(context, 1.0f)));
        }
        textView.setClickable(true);
        textView.setFocusable(true);
        return textView;
    }

    public static TextView chip(Context context, String str, int i) {
        TextView text = text(context, str, 11.0f, i, true);
        int dp = dp(context, 9.0f);
        int dp2 = dp(context, 4.0f);
        text.setPadding(dp, dp2, dp, dp2);
        int i2 = i & 16777215;
        GradientDrawable round = round(570425344 | i2, dp(context, 20.0f));
        round.setStroke(dp(context, 1.0f), i2 | 1426063360);
        text.setBackground(round);
        return text;
    }

    public static View divider(Context context) {
        View view = new View(context);
        view.setBackgroundColor(dark() ? 584363831 : 570425344);
        view.setLayoutParams(new LinearLayout.LayoutParams(-1, Math.max(1, dp(context, 0.7f))));
        return view;
    }

    public static void pad(View view, int i, int i2, int i3, int i4) {
        view.setPadding(i, i2, i3, i4);
    }

    public static int statusBarColor() {
        return dark() ? -16448246 : -790814;
    }

    public static int levelColor(String str) {
        return LogManager.ERROR.equals(str) ? ERROR : LogManager.WARN.equals(str) ? WARNING : LogManager.SUCCESS.equals(str) ? SUCCESS : LogManager.DEBUG.equals(str) ? INFO : GOLD;
    }
}
