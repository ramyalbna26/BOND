package com.ramyelbanna.juzoor;

import android.app.Application;
import android.content.res.Configuration;
import com.ramyelbanna.juzoor.core.LogManager;
import com.ramyelbanna.juzoor.core.Prefs;
import java.util.Locale;

/* loaded from: /workspace/apk_extract/classes.dex */
public class App extends Application {
    @Override // android.app.Application
    public void onCreate() {
        super.onCreate();
        Prefs.init(this);
        LogManager.init(this);
        LogManager.setAutoSave(Prefs.autoLog());
        applyLocale();
        LogManager.i("Juzoor started — v" + Prefs.VERSION);
    }

    @Override // android.app.Application, android.content.ComponentCallbacks
    public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        applyLocale();
    }

    public void applyLocale() {
        Locale locale = new Locale(Prefs.lang());
        Locale.setDefault(locale);
        Configuration configuration = new Configuration(getResources().getConfiguration());
        configuration.setLocale(locale);
        configuration.fontScale = Prefs.fontScale();
        getResources().updateConfiguration(configuration, getResources().getDisplayMetrics());
    }

    /** Applies the saved 100%–150% font scale to every screen without a restart. */
    public void applyFontScale() {
        Configuration configuration = new Configuration(getResources().getConfiguration());
        configuration.fontScale = Prefs.fontScale();
        getResources().updateConfiguration(configuration, getResources().getDisplayMetrics());
    }
}
