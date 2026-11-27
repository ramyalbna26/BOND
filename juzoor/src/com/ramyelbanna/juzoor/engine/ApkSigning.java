package com.ramyelbanna.juzoor.engine;

import com.android.apksig.ApkSigner;
import com.android.apksig.ApkVerifier;
import com.ramyelbanna.juzoor.core.LogManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Thin, Android-friendly wrapper around Google's {@code apksig} library.
 *
 * <p>Only the {@code com.android.apksig} package is bundled with Juzoor (the
 * command line front-end and the Conscrypt provider are stripped at build
 * time), which keeps the APK small while giving full v1 / v2 / v3 signing and
 * verification on the device.</p>
 */
public final class ApkSigning {

    public static final class SignOptions {
        public boolean v1 = true;
        public boolean v2 = true;
        public boolean v3 = true;
        public int minSdk = 21;
        public File keystore;
        public String storePass = "juzoor";
        public String keyPass = "juzoor";
        public String alias = "juzoor";
        public String signerName = "Juzoor";
    }

    public static final class SignResult {
        public boolean success;
        public String error = "";
        public String signer = "";
        public boolean verifiedV1;
        public boolean verifiedV2;
        public boolean verifiedV3;
        public int schemeCount;

        public String describe() {
            StringBuilder b = new StringBuilder();
            if (verifiedV1) b.append("v1 ");
            if (verifiedV2) b.append("v2 ");
            if (verifiedV3) b.append("v3 ");
            if (b.length() == 0) b.append("unsigned");
            return b.toString().trim();
        }
    }

    private ApkSigning() {
    }

    public static SignResult sign(File input, File output, SignOptions o, LogSink log) {
        SignResult r = new SignResult();
        try {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            InputStream in = new FileInputStream(o.keystore);
            try {
                ks.load(in, o.storePass.toCharArray());
            } finally {
                in.close();
            }
            if (!ks.containsAlias(o.alias)) {
                r.error = "Alias not found in keystore: " + o.alias;
                return r;
            }
            PrivateKey key = (PrivateKey) ks.getKey(o.alias, o.keyPass.toCharArray());
            java.security.cert.Certificate[] chain = ks.getCertificateChain(o.alias);
            if (key == null || chain == null || chain.length == 0) {
                r.error = "Keystore entry has no private key / certificate chain";
                return r;
            }
            List<X509Certificate> certs = new ArrayList<>();
            for (java.security.cert.Certificate c : chain) {
                if (c instanceof X509Certificate) certs.add((X509Certificate) c);
            }
            ApkSigner.SignerConfig cfg = new ApkSigner.SignerConfig.Builder(o.signerName, key, certs).build();
            ApkSigner signer = new ApkSigner.Builder(Collections.singletonList(cfg))
                    .setInputApk(input)
                    .setOutputApk(output)
                    .setV1SigningEnabled(o.v1)
                    .setV2SigningEnabled(o.v2)
                    .setV3SigningEnabled(o.v3)
                    .setMinSdkVersion(o.minSdk)
                    .setOtherSignersSignaturesPreserved(false)
                    .build();
            signer.sign();
            r.success = true;
            r.signer = certs.get(0).getSubjectDN().getName();
            verify(output, r, log);
            if (log != null) log.log(LogManager.SUCCESS, "APK signed (" + r.describe() + ") → " + output.getName());
        } catch (Throwable t) {
            r.success = false;
            r.error = String.valueOf(t);
            if (log != null) log.log(LogManager.ERROR, "Signing failed: " + t);
        }
        return r;
    }

    public static void verify(File apk, SignResult r, LogSink log) {
        try {
            ApkVerifier.Result v = new ApkVerifier.Builder(apk).build().verify();
            r.verifiedV1 = v.isVerifiedUsingV1Scheme();
            r.verifiedV2 = v.isVerifiedUsingV2Scheme();
            r.verifiedV3 = v.isVerifiedUsingV3Scheme();
            r.schemeCount = (r.verifiedV1 ? 1 : 0) + (r.verifiedV2 ? 1 : 0) + (r.verifiedV3 ? 1 : 0);
            if (log != null) {
                log.log(v.isVerified() ? LogManager.SUCCESS : LogManager.WARN,
                        "Signature verification: " + (v.isVerified() ? "OK" : "FAILED") + " [" + r.describe() + "]");
            }
        } catch (Throwable t) {
            if (log != null) log.log(LogManager.WARN, "Verification error: " + t);
        }
    }
}
