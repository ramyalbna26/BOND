package com.ramyelbanna.juzoor.core;

import android.content.Context;

import com.ramyelbanna.juzoor.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Provides the built-in Juzoor signing key.
 *
 * <p>The key ships as a PKCS#12 resource and is copied into the app's private
 * storage the first time the repackager needs it. It is only meant for local
 * testing and for re-signing builds you own.</p>
 */
public final class KeystoreUtil {

    public static final String ALIAS = "juzoor";
    public static final String PASSWORD = "juzoor";

    private KeystoreUtil() {
    }

    /** Returns the private keystore file, extracting it from resources once. */
    public static File defaultKeystore(Context context) {
        File out = new File(context.getFilesDir(), "juzoor-signing.p12");
        if (out.isFile() && out.length() > 0) return out;
        try {
            InputStream in = context.getResources().openRawResource(R.raw.juzoor_signing);
            FileOutputStream fos = new FileOutputStream(out);
            byte[] buf = new byte[8192];
            int n;
            try {
                while ((n = in.read(buf)) > 0) fos.write(buf, 0, n);
            } finally {
                fos.close();
                in.close();
            }
        } catch (Throwable t) {
            LogManager.e("Could not extract built-in keystore: " + t);
            return null;
        }
        return out;
    }
}
