package com.ramyelbanna.juzoor;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.ramyelbanna.juzoor.core.LogManager;
import com.ramyelbanna.juzoor.engine.ConversionEngine;
import com.ramyelbanna.juzoor.engine.SplitBundle;
import com.ramyelbanna.juzoor.ui.Ui;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;

/* loaded from: /workspace/apk_extract/classes.dex */
public class FileBrowserActivity extends BaseActivity {
    private static final int REQ_STORAGE = 7;
    private File current;
    private String filter;
    private boolean allFiles;
    private LinearLayout list;
    private TextView pathLabel;

    @Override // com.ramyelbanna.juzoor.BaseActivity, android.app.Activity
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        this.filter = getIntent().getStringExtra("filter");
        this.allFiles = getIntent().getBooleanExtra("all", false);
        this.current = Environment.getExternalStorageDirectory();
        buildUi();
        if (hasAccess()) {
            load(this.current);
        } else {
            requestAccess();
        }
    }

    private void buildUi() {
        this.body.removeAllViews();
        topBar(getString(R.string.fm_title), true);
        LinearLayout card = Ui.card(this);
        TextView text = Ui.text(this, "", 12.0f, Ui.GOLD, true);
        this.pathLabel = text;
        card.addView(text);
        LinearLayout row = Ui.row(this);
        row.setPadding(0, Ui.dp(this, 10.0f), 0, 0);
        TextView button = Ui.button(this, getString(R.string.fm_up), false);
        button.setOnClickListener(new View.OnClickListener() { // from class: com.ramyelbanna.juzoor.FileBrowserActivity$$ExternalSyntheticLambda3
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                FileBrowserActivity.this.lambda$buildUi$0(view);
            }
        });
        row.addView(button, Ui.lp(0, -2, 1.0f));
        TextView button2 = Ui.button(this, getString(R.string.fm_root), false);
        LinearLayout.LayoutParams lp = Ui.lp(0, -2, 1.0f);
        lp.leftMargin = Ui.dp(this, 8.0f);
        button2.setOnClickListener(new View.OnClickListener() { // from class: com.ramyelbanna.juzoor.FileBrowserActivity$$ExternalSyntheticLambda4
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                FileBrowserActivity.this.lambda$buildUi$1(view);
            }
        });
        row.addView(button2, lp);
        LinearLayout.LayoutParams lp2 = Ui.lp(-1, -2);
        lp2.topMargin = Ui.dp(this, 8.0f);
        card.addView(row, lp2);
        this.body.addView(card);
        this.list = Ui.column(this);
        LinearLayout.LayoutParams lp3 = Ui.lp(-1, -2);
        lp3.topMargin = Ui.dp(this, 12.0f);
        this.body.addView(this.list, lp3);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$buildUi$0(View view) {
        File file = this.current;
        File parentFile = file == null ? null : file.getParentFile();
        if (parentFile != null) {
            load(parentFile);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$buildUi$1(View view) {
        load(Environment.getExternalStorageDirectory());
    }

    private boolean hasAccess() {
        return Build.VERSION.SDK_INT >= 30 ? Environment.isExternalStorageManager() : Build.VERSION.SDK_INT < 23 || checkSelfPermission("android.permission.READ_EXTERNAL_STORAGE") == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    private void requestAccess() {
        if (Build.VERSION.SDK_INT >= 30) {
            LinearLayout card = Ui.card(this);
            card.addView(Ui.text(this, getString(R.string.fm_permission_title), 15.0f, Ui.text(), true));
            card.addView(Ui.text(this, getString(R.string.fm_permission_msg), 13.0f, Ui.muted(), false));
            TextView button = Ui.button(this, getString(R.string.fm_grant), true);
            LinearLayout.LayoutParams lp = Ui.lp(-1, -2);
            lp.topMargin = Ui.dp(this, 12.0f);
            button.setOnClickListener(new View.OnClickListener() { // from class: com.ramyelbanna.juzoor.FileBrowserActivity$$ExternalSyntheticLambda0
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    FileBrowserActivity.this.lambda$requestAccess$2(view);
                }
            });
            card.addView(button, lp);
            LinearLayout.LayoutParams lp2 = Ui.lp(-1, -2);
            lp2.topMargin = Ui.dp(this, 12.0f);
            this.body.addView(card, lp2);
            return;
        }
        if (Build.VERSION.SDK_INT >= 23) {
            requestPermissions(new String[]{"android.permission.READ_EXTERNAL_STORAGE"}, REQ_STORAGE);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$requestAccess$2(View view) {
        try {
            startActivity(new Intent("android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION", Uri.parse("package:" + getPackageName())));
        } catch (Exception unused) {
            startActivity(new Intent("android.settings.MANAGE_ALL_FILES_ACCESS_PERMISSION"));
        }
    }

    @Override // android.app.Activity
    public void onRequestPermissionsResult(int i, String[] strArr, int[] iArr) {
        super.onRequestPermissionsResult(i, strArr, iArr);
        if (i == REQ_STORAGE) {
            if (iArr.length > 0 && iArr[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                load(this.current);
            } else {
                LogManager.w("Storage permission denied");
                toast(getString(R.string.fm_denied));
            }
        }
    }

    @Override // android.app.Activity
    protected void onResume() {
        File file;
        super.onResume();
        if (!hasAccess() || (file = this.current) == null) {
            return;
        }
        load(file);
    }

    private void load(File file) {
        if (file == null) {
            return;
        }
        this.current = file;
        this.pathLabel.setText(file.getAbsolutePath());
        this.list.removeAllViews();
        File[] listFiles = file.listFiles();
        if (listFiles == null) {
            this.list.addView(Ui.text(this, getString(R.string.fm_empty), 13.0f, Ui.muted(), false));
            return;
        }
        ArrayList arrayList = new ArrayList();
        ArrayList arrayList2 = new ArrayList();
        for (File file2 : listFiles) {
            if (!file2.isHidden() || file2.getName().startsWith(".")) {
                if (file2.isDirectory()) {
                    arrayList.add(file2);
                } else if (matches(file2)) {
                    arrayList2.add(file2);
                }
            }
        }
        Collections.sort(arrayList, new Comparator() { // from class: com.ramyelbanna.juzoor.FileBrowserActivity$$ExternalSyntheticLambda1
            @Override // java.util.Comparator
            public final int compare(Object obj, Object obj2) {
                int compareToIgnoreCase;
                compareToIgnoreCase = ((File) obj).getName().compareToIgnoreCase(((File) obj2).getName());
                return compareToIgnoreCase;
            }
        });
        Collections.sort(arrayList2, new Comparator() { // from class: com.ramyelbanna.juzoor.FileBrowserActivity$$ExternalSyntheticLambda2
            @Override // java.util.Comparator
            public final int compare(Object obj, Object obj2) {
                int compareToIgnoreCase;
                compareToIgnoreCase = ((File) obj).getName().compareToIgnoreCase(((File) obj2).getName());
                return compareToIgnoreCase;
            }
        });
        if (arrayList.isEmpty() && arrayList2.isEmpty()) {
            this.list.addView(Ui.text(this, getString(R.string.fm_empty), 13.0f, Ui.muted(), false));
        }
        Iterator it = arrayList.iterator();
        while (it.hasNext()) {
            this.list.addView(rowFor((File) it.next(), true));
        }
        Iterator it2 = arrayList2.iterator();
        while (it2.hasNext()) {
            this.list.addView(rowFor((File) it2.next(), false));
        }
    }

    private boolean matches(File file) {
        if (this.allFiles) return file.isFile();
        String lowerCase = file.getName().toLowerCase(java.util.Locale.US);
        String str = this.filter;
        if (str != null && !str.isEmpty()) {
            if (this.filter.equals("apk+")) {
                return lowerCase.endsWith(".apk+") || lowerCase.endsWith(".apks") || lowerCase.endsWith(".apkm") || lowerCase.endsWith(".xapk");
            }
            return lowerCase.endsWith("." + this.filter);
        }
        return SplitBundle.isSupported(lowerCase);
    }

    private LinearLayout rowFor(final File file, final boolean z) {
        String human;
        LinearLayout row = Ui.row(this);
        row.setBackground(Ui.round(z ? Ui.surface2() : Ui.surface(), Ui.dp(this, 12.0f)));
        int dp = Ui.dp(this, 13.0f);
        row.setPadding(dp, dp, dp, dp);
        String str = z ? "▸" : "◈";
        int i = Ui.GOLD;
        row.addView(Ui.text(this, str, 16.0f, z ? Ui.muted() : Ui.GOLD, true));
        LinearLayout column = Ui.column(this);
        LinearLayout.LayoutParams lp = Ui.lp(0, -2, 1.0f);
        lp.leftMargin = Ui.dp(this, 10.0f);
        column.addView(Ui.text(this, file.getName(), 14.0f, Ui.text(), false));
        if (z) {
            human = getString(R.string.fm_folder);
        } else {
            human = ConversionEngine.human(file.length());
        }
        column.addView(Ui.text(this, human, 11.0f, Ui.muted(), false));
        row.addView(column, lp);
        if (!z) {
            String badge = allFiles ? extensionOf(file.getName()) : SplitBundle.badge(file.getName());
            if (!"APK".equals(badge)) {
                i = Ui.GOLD_LIGHT;
            }
            row.addView(Ui.chip(this, badge, i));
        }
        LinearLayout.LayoutParams lp2 = Ui.lp(-1, -2);
        lp2.topMargin = Ui.dp(this, 6.0f);
        row.setLayoutParams(lp2);
        row.setClickable(true);
        row.setOnClickListener(new View.OnClickListener() { // from class: com.ramyelbanna.juzoor.FileBrowserActivity$$ExternalSyntheticLambda5
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                FileBrowserActivity.this.lambda$rowFor$5(z, file, view);
            }
        });
        return row;
    }

    private static String extensionOf(String name) {
        int dot = name == null ? -1 : name.lastIndexOf('.');
        if (dot < 0 || dot + 1 >= name.length()) return "FILE";
        String ext = name.substring(dot + 1).toUpperCase(java.util.Locale.US);
        return ext.length() > 6 ? ext.substring(0, 6) : ext;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$rowFor$5(boolean z, File file, View view) {
        if (z) {
            load(file);
            return;
        }
        LogManager.i("Selected file: " + file.getName() + " (" + file.length() + " bytes)");
        if (getIntent().getBooleanExtra("pick", false)) {
            Intent result = new Intent();
            result.putExtra(ConversionService.EXTRA_PATH, file.getAbsolutePath());
            setResult(RESULT_OK, result);
            finish();
            return;
        }
        Intent intent = new Intent(this, (Class<?>) ConfirmActivity.class);
        intent.putExtra(ConversionService.EXTRA_PATH, file.getAbsolutePath());
        startActivity(intent);
    }
}
