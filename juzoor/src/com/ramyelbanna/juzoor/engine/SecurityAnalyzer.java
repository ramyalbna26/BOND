package com.ramyelbanna.juzoor.engine;

import com.ramyelbanna.juzoor.core.LogManager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Static application security testing (SAST) for a recovered application.
 *
 * <p>Everything runs locally: the manifest is inspected, DEX string tables and
 * text assets are searched for secrets, bundled SDKs are matched against an
 * offline tracker database, and weak cryptography is flagged from both DEX
 * constants and native library fingerprints.</p>
 */
public final class SecurityAnalyzer {

    public static final String CRITICAL = "CRITICAL";
    public static final String HIGH = "HIGH";
    public static final String MEDIUM = "MEDIUM";
    public static final String LOW = "LOW";
    public static final String INFO = "INFO";

    public static final class Finding {
        public final String severity;
        public final String category;
        public final String title;
        public final String detail;
        public final String location;

        Finding(String severity, String category, String title, String detail, String location) {
            this.severity = severity;
            this.category = category;
            this.title = title;
            this.detail = detail;
            this.location = location;
        }
    }

    public static final class Report {
        public String packageName = "";
        public int riskScore;
        public int critical;
        public int high;
        public int medium;
        public int low;
        public int info;
        public int filesScanned;
        public int stringsScanned;
        public final List<Finding> findings = new ArrayList<>();
        public final List<String> trackers = new ArrayList<>();
        public final List<String> secrets = new ArrayList<>();
        public File markdown;
        public File json;

        public int total() {
            return critical + high + medium + low + info;
        }

        public String riskLabel() {
            if (riskScore >= 70) return CRITICAL;
            if (riskScore >= 40) return HIGH;
            if (riskScore >= 15) return MEDIUM;
            return LOW;
        }

        public String summary() {
            return total() + " findings, risk " + riskScore + "/100";
        }
    }

    /* ---------------------------------------------------------- tracker DB */

    private static final String[][] TRACKERS = {
            {"com/google/firebase/analytics", "Google Analytics for Firebase", "analytics"},
            {"com/google/firebase/crashlytics", "Firebase Crashlytics", "crash"},
            {"com/google/android/gms/ads", "Google Mobile Ads", "ads"},
            {"com/google/android/gms/measurement", "Google Analytics", "analytics"},
            {"com/google/android/gms/tagmanager", "Google Tag Manager", "analytics"},
            {"com/google/firebase/messaging", "Firebase Cloud Messaging", "messaging"},
            {"com/facebook/appevents", "Facebook Analytics", "analytics"},
            {"com/facebook/ads", "Facebook Audience Network", "ads"},
            {"com/appsflyer", "AppsFlyer", "attribution"},
            {"com/adjust/sdk", "Adjust", "attribution"},
            {"com/onesignal", "OneSignal", "messaging"},
            {"com/amplitude", "Amplitude", "analytics"},
            {"com/mixpanel", "Mixpanel", "analytics"},
            {"com/flurry", "Flurry", "analytics"},
            {"com/unity3d/ads", "Unity Ads", "ads"},
            {"com/applovin", "AppLovin", "ads"},
            {"com/ironsource", "ironSource", "ads"},
            {"com/mopub", "MoPub", "ads"},
            {"com/vungle", "Vungle", "ads"},
            {"com/chartboost", "Chartboost", "ads"},
            {"com/inmobi", "InMobi", "ads"},
            {"com/startapp", "StartApp", "ads"},
            {"com/smaato", "Smaato", "ads"},
            {"com/pubmatic", "PubMatic", "ads"},
            {"com/adcolony", "AdColony", "ads"},
            {"com/bytedance", "ByteDance / Pangle", "ads"},
            {"com/tiktok", "TikTok SDK", "analytics"},
            {"com/braze", "Braze", "engagement"},
            {"com/segment", "Segment", "analytics"},
            {"com/sentry", "Sentry", "crash"},
            {"com/bugsnag", "Bugsnag", "crash"},
            {"com/newrelic", "New Relic", "monitoring"},
            {"com/huawei/hms", "Huawei Mobile Services", "analytics"},
            {"com/yandex", "Yandex AppMetrica", "analytics"},
            {"com/clevertap", "CleverTap", "analytics"},
            {"com/countly", "Countly", "analytics"},
            {"com/kochava", "Kochava", "attribution"},
            {"com/branch", "Branch", "attribution"},
            {"com/singular", "Singular", "attribution"},
            {"com/tenjin", "Tenjin", "attribution"},
            {"com/leanplum", "Leanplum", "engagement"},
            {"com/airship", "Airship", "engagement"},
            {"com/pushwoosh", "Pushwoosh", "messaging"},
            {"com/optimizely", "Optimizely", "experiments"},
            {"com/microsoft/appcenter", "App Center", "crash"},
            {"io/branch", "Branch (io)", "attribution"},
            {"com/google/android/play/core", "Play Core", "distribution"},
    };

    private static final Pattern[] SECRET_PATTERNS = {
            Pattern.compile("AIza[0-9A-Za-z_\\-]{35}"),
            Pattern.compile("AKIA[0-9A-Z]{16}"),
            Pattern.compile("sk_live_[0-9A-Za-z]{20,}"),
            Pattern.compile("gh[pousr]_[A-Za-z0-9]{30,}"),
            Pattern.compile("xox[baprs]-[0-9A-Za-z\\-]{10,}"),
            Pattern.compile("https://[a-z0-9\\-]+\\.firebaseio\\.com"),
            Pattern.compile("-----BEGIN (?:RSA |EC |DSA )?PRIVATE KEY-----"),
            Pattern.compile("(?i)(?:api[_-]?key|secret[_-]?key|access[_-]?token|auth[_-]?token|password|passwd)\\s*[:=]\\s*[\"'][A-Za-z0-9_\\-\\.]{12,}[\"']"),
            Pattern.compile("(?i)bearer\\s+[A-Za-z0-9_\\-\\.]{20,}"),
    };

    private static final String[] CRYPTO_MARKERS = {
            "AES/ECB", "DES/ECB", "DESede", "RC4", "PBEWithMD5AndDES",
            "Cipher.getInstance", "MD5", "SHA-1", "ECB", "NoPadding",
    };

    private static final String[] DANGEROUS_PERMISSIONS = {
            "READ_SMS", "RECEIVE_SMS", "SEND_SMS", "READ_CALL_LOG", "WRITE_CALL_LOG",
            "READ_CONTACTS", "WRITE_CONTACTS", "ACCESS_FINE_LOCATION", "ACCESS_BACKGROUND_LOCATION",
            "RECORD_AUDIO", "CAMERA", "READ_PHONE_STATE", "CALL_PHONE", "MANAGE_EXTERNAL_STORAGE",
            "SYSTEM_ALERT_WINDOW", "REQUEST_INSTALL_PACKAGES", "QUERY_ALL_PACKAGES",
            "READ_EXTERNAL_STORAGE", "WRITE_EXTERNAL_STORAGE", "BODY_SENSORS", "READ_CALENDAR",
    };

    private SecurityAnalyzer() {
    }

    /* -------------------------------------------------------------- public */

    public static Report analyze(File mergedRoot, String decodedManifest, List<File> dexFiles,
                                 List<NativeAnalyzer.Report> natives, List<LibrarySupport.Library> libs,
                                 File analysisDir, LogSink log) {
        Report r = new Report();
        if (analysisDir != null) analysisDir.mkdirs();
        Set<String> strings = new LinkedHashSet<>();

        File mainRoot = null;
        if (analysisDir != null && analysisDir.getParentFile() != null) {
            mainRoot = new File(analysisDir.getParentFile(), "app/src/main");
        }

        /* ---- collect DEX strings and descriptors ---- */
        if (dexFiles != null) {
            for (File dex : dexFiles) {
                try {
                    List<String> all = DexStrings.readStrings(ZipUtil.readFile(dex));
                    r.stringsScanned += all.size();
                    strings.addAll(all);
                    scanTrackers(r, all, mainRoot);
                } catch (Throwable t) {
                    if (log != null) log.log(LogManager.WARN, "Security: cannot read " + dex.getName() + ": " + t);
                }
            }
        }

        /* ---- scan readable text assets for secrets ---- */
        if (mergedRoot != null) {
            scanTextTree(r, mergedRoot, strings);
        }

        /* ---- secrets ---- */
        scanSecrets(r, strings);

        /* ---- manifest ---- */
        if (decodedManifest != null && !decodedManifest.isEmpty()) {
            String pkg = attr(decodedManifest, "package");
            if (pkg != null) r.packageName = pkg;
            scanManifest(r, decodedManifest);
        } else {
            r.findings.add(new Finding(MEDIUM, "manifest", "Manifest unavailable",
                    "The AndroidManifest.xml could not be decoded, so hardening checks were skipped.", "AndroidManifest.xml"));
        }

        /* ---- crypto ---- */
        scanCrypto(r, strings, natives);

        /* ---- libraries / trackers correlation ---- */
        if (libs != null) {
            for (LibrarySupport.Library l : libs) {
                String n = l.name.toLowerCase(Locale.US);
                if (n.contains("analytics") || n.contains("ads") || n.contains("track") || n.contains("crash")) {
                    if (!r.trackers.contains(l.name)) r.trackers.add(l.name);
                }
            }
        }

        /* ---- score ---- */
        for (Finding f : r.findings) {
            if (CRITICAL.equals(f.severity)) r.critical++;
            else if (HIGH.equals(f.severity)) r.high++;
            else if (MEDIUM.equals(f.severity)) r.medium++;
            else if (LOW.equals(f.severity)) r.low++;
            else r.info++;
        }
        int score = r.critical * 25 + r.high * 12 + r.medium * 5 + r.low * 2;
        r.riskScore = Math.min(100, score);

        try {
            writeReports(r, mergedRoot == null ? analysisDir : mergedRoot.getParentFile(), analysisDir);
        } catch (Throwable t) {
            if (log != null) log.log(LogManager.WARN, "Security report failed: " + t);
        }
        if (log != null) {
            log.log(r.riskScore >= 40 ? LogManager.WARN : LogManager.INFO,
                    "Security: " + r.summary() + ", " + r.trackers.size() + " tracker(s)");
        }
        return r;
    }

    /* ------------------------------------------------------------- trackers */

    private static void scanTrackers(Report r, List<String> strings, File mainRoot) {
        Set<String> seen = new LinkedHashSet<>();
        for (String s : strings) {
            if (s.length() < 8 || s.charAt(0) != 'L' || !s.endsWith(";")) continue;
            String desc = s.substring(1, s.length() - 1);
            for (String[] t : TRACKERS) {
                if (desc.startsWith(t[0]) && seen.add(t[1])) {
                    r.trackers.add(t[1]);
                    String location = locateSmali(mainRoot, desc);
                    r.findings.add(new Finding(MEDIUM, "tracker", "Third-party SDK: " + t[1],
                            "Category: " + t[2] + ". Class `" + desc + "` is linked to the exact "
                                    + "Smali file below.", location));
                }
            }
        }
    }

    /** Maps a DEX class descriptor to the real disassembled .smali file. */
    private static String locateSmali(File mainRoot, String descriptor) {
        String rel = descriptor + ".smali";
        if (mainRoot != null && mainRoot.isDirectory()) {
            File[] roots = mainRoot.listFiles();
            if (roots != null) {
                for (File root : roots) {
                    if (!root.isDirectory()) continue;
                    String n = root.getName();
                    if (!(n.equals("smali") || n.startsWith("smali_"))) continue;
                    File candidate = new File(root, rel);
                    if (candidate.isFile()) {
                        File srcParent = mainRoot.getParentFile();
                        File project = srcParent == null ? null : srcParent.getParentFile() == null
                                ? null : srcParent.getParentFile().getParentFile();
                        if (project != null) {
                            String path = project.toURI().relativize(candidate.toURI()).getPath();
                            if (path != null && !path.isEmpty()) return path;
                        }
                        return candidate.getAbsolutePath();
                    }
                }
            }
        }
        return "smali/" + rel + " (predicted)";
    }

    /* -------------------------------------------------------------- secrets */

    private static void scanSecrets(Report r, Set<String> strings) {
        Set<String> seen = new LinkedHashSet<>();
        for (String s : strings) {
            if (s.length() < 8 || s.length() > 512) continue;
            for (Pattern p : SECRET_PATTERNS) {
                Matcher m = p.matcher(s);
                while (m.find()) {
                    String hit = m.group();
                    if (!seen.add(hit)) continue;
                    /* Keep the full value: analysts need to copy it verbatim to
                       rotate/verify the key. */
                    r.secrets.add(hit);
                    r.findings.add(new Finding(HIGH, "secret", "Possible hardcoded credential",
                            "Full value: `" + hit + "` (length " + hit.length()
                                    + "). Rotate it if it is live.",
                            "classes.dex"));
                }
            }
        }
    }

    /* --------------------------------------------------------- text assets */

    private static void scanTextTree(Report r, File root, Set<String> strings) {
        File[] files = root.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                String n = f.getName();
                if ("smali".equals(n) || n.startsWith("smali_") || "jniLibs".equals(n) || "META-INF".equals(n)) continue;
                scanTextTree(r, f, strings);
                continue;
            }
            String name = f.getName().toLowerCase(Locale.US);
            if (!(name.endsWith(".json") || name.endsWith(".xml") || name.endsWith(".txt")
                    || name.endsWith(".properties") || name.endsWith(".js") || name.endsWith(".html")
                    || name.endsWith(".env") || name.endsWith(".yaml") || name.endsWith(".yml"))) continue;
            if (f.length() > 2L * 1024 * 1024) continue;
            try {
                r.filesScanned++;
                String text = new String(ZipUtil.readFile(f), "UTF-8");
                for (String line : text.split("\\r?\\n")) {
                    if (line.length() > 8 && line.length() < 512) strings.add(line.trim());
                }
            } catch (Throwable ignored) {
            }
        }
    }

    /* ------------------------------------------------------------- manifest */

    private static void scanManifest(Report r, String xml) {
        String lower = xml.toLowerCase(Locale.US);
        if (lower.contains("android:debuggable=\"true\"")) {
            r.findings.add(new Finding(HIGH, "manifest", "Debuggable build",
                    "android:debuggable=\"true\" lets anyone attach a debugger and dump memory.", "AndroidManifest.xml"));
        }
        if (lower.contains("android:allowbackup=\"true\"") || !lower.contains("android:allowbackup")) {
            r.findings.add(new Finding(MEDIUM, "manifest", "Backup enabled",
                    "Application data can be extracted with `adb backup` unless allowBackup is false.", "AndroidManifest.xml"));
        }
        if (lower.contains("android:usescleartexttraffic=\"true\"")) {
            r.findings.add(new Finding(HIGH, "manifest", "Cleartext traffic allowed",
                    "HTTP traffic can be intercepted or modified on the network.", "AndroidManifest.xml"));
        }
        if (lower.contains("cleartexttrafficpermitted=\"true\"")) {
            r.findings.add(new Finding(HIGH, "manifest", "Network security config permits cleartext",
                    "A network-security-config explicitly allows unencrypted connections.", "res/xml/network_security_config.xml"));
        }
        if (lower.contains("android:networksecurityconfig")) {
            r.findings.add(new Finding(INFO, "manifest", "Custom network security config",
                    "A network security configuration is present; review its trust anchors.", "AndroidManifest.xml"));
        }

        /* exported components without a permission guard */
        int exported = 0;
        List<String> names = new ArrayList<>();
        String[] tags = {"activity", "activity-alias", "service", "receiver", "provider"};
        for (String tag : tags) {
            int from = 0;
            while (true) {
                int start = lower.indexOf("<" + tag, from);
                if (start < 0) break;
                int end = lower.indexOf('>', start);
                if (end < 0) break;
                String block = lower.substring(start, end);
                if (block.contains("android:exported=\"true\"")) {
                    exported++;
                    String nm = attr(block, "android:name");
                    if (nm != null) names.add(tag + " " + nm);
                    if (!block.contains("android:permission")) {
                        r.findings.add(new Finding(HIGH, "manifest", "Exported " + tag + " without permission",
                                "`" + (nm == null ? "?" : nm) + "` is reachable by any app on the device.", "AndroidManifest.xml"));
                    }
                }
                from = end + 1;
            }
        }
        if (exported > 0) {
            r.findings.add(new Finding(INFO, "manifest", exported + " exported component(s)",
                    joinLimit(names, 12), "AndroidManifest.xml"));
        }

        /* dangerous permissions */
        for (String p : DANGEROUS_PERMISSIONS) {
            if (lower.contains("android.permission." + p.toLowerCase(Locale.US))) {
                r.findings.add(new Finding(INFO, "manifest", "Sensitive permission: " + p,
                        "The app requests a permission that can access private user data.", "AndroidManifest.xml"));
            }
        }
    }

    private static String attr(String block, String name) {
        int p = block.indexOf(name + "=\"");
        if (p < 0) return null;
        int s = p + name.length() + 2;
        int e = block.indexOf('"', s);
        return e > s ? block.substring(s, e) : null;
    }

    private static String joinLimit(List<String> items, int limit) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < items.size() && i < limit; i++) {
            if (i > 0) b.append(", ");
            b.append(items.get(i));
        }
        if (items.size() > limit) b.append(", \u2026 ").append(items.size() - limit).append(" more");
        return b.toString();
    }

    /* --------------------------------------------------------------- crypto */

    private static void scanCrypto(Report r, Set<String> strings, List<NativeAnalyzer.Report> natives) {
        Set<String> seen = new LinkedHashSet<>();
        for (String s : strings) {
            if (s.length() > 120) continue;
            String up = s.toUpperCase(Locale.US);
            for (String marker : CRYPTO_MARKERS) {
                if (up.contains(marker) && seen.add(marker)) {
                    String sev = ("AES/ECB".equals(marker) || "DES/ECB".equals(marker)
                            || "PBEWithMD5AndDES".equals(marker) || "RC4".equals(marker)) ? HIGH : MEDIUM;
                    r.findings.add(new Finding(sev, "crypto", "Weak cryptography: " + marker,
                            "Constant `" + s + "` indicates a deprecated or insecure primitive.", "classes.dex"));
                }
            }
            if ((up.contains("SECRETKEY") || up.contains("AESKEY") || up.contains("ENCRYPTIONKEY"))
                    && (s.length() == 16 || s.length() == 24 || s.length() == 32)) {
                if (seen.add("static-key:" + s)) {
                    r.findings.add(new Finding(HIGH, "crypto", "Static symmetric key",
                            "A " + s.length() * 8 + "-bit key appears to be hardcoded in the DEX.", "classes.dex"));
                }
            }
        }
        if (natives != null) {
            for (NativeAnalyzer.Report n : natives) {
                for (String c : n.crypto) {
                    if (seen.add("so:" + c)) {
                        r.findings.add(new Finding(INFO, "crypto", "Native crypto: " + c,
                                "`" + n.name + "` references this algorithm.", "lib/" + n.abi + "/" + n.name));
                    }
                }
            }
        }
    }

    /* -------------------------------------------------------------- reports */

    private static void writeReports(Report r, File projectRoot, File analysisDir) throws IOException {
        File md = projectRoot == null ? null : new File(projectRoot, "SECURITY.md");
        if (md == null) md = new File(analysisDir, "SECURITY.md");
        File json = new File(analysisDir == null ? projectRoot : analysisDir, "security.json");
        ZipUtil.writeFile(md, markdown(r));
        ZipUtil.writeFile(json, json(r));
        r.markdown = md;
        r.json = json;
    }

    private static String markdown(Report r) {
        StringBuilder b = new StringBuilder(8192);
        b.append("# Security analysis \u2014 Juzoor\n\n");
        b.append("> Static application security testing, performed entirely on this device.\n\n");
        b.append("| Metric | Value |\n|---|---|\n");
        b.append("| Package | `").append(r.packageName).append("` |\n");
        b.append("| Risk score | **").append(r.riskScore).append("/100** (").append(r.riskLabel()).append(") |\n");
        b.append("| Findings | ").append(r.total()).append(" |\n");
        b.append("| Critical / High / Medium / Low / Info | ")
                .append(r.critical).append(" / ").append(r.high).append(" / ")
                .append(r.medium).append(" / ").append(r.low).append(" / ").append(r.info).append(" |\n");
        b.append("| Trackers detected | ").append(r.trackers.size()).append(" |\n");
        b.append("| Strings scanned | ").append(r.stringsScanned).append(" |\n");
        b.append("| Text files scanned | ").append(r.filesScanned).append(" |\n\n");

        b.append("## Trackers and advertising SDKs\n\n");
        if (r.trackers.isEmpty()) b.append("_None detected._\n\n");
        else for (String t : r.trackers) b.append("- ").append(t).append('\n');
        if (!r.trackers.isEmpty()) b.append('\n');

        b.append("## Hardcoded secrets\n\n");
        if (r.secrets.isEmpty()) {
            b.append("_None detected._\n\n");
        } else {
            b.append("```\n");
            for (String secret : r.secrets) b.append(secret).append('\n');
            b.append("```\n\n");
        }

        b.append("## Findings\n\n");
        if (r.findings.isEmpty()) {
            b.append("_No issues found._\n\n");
        } else {
            b.append("| Severity | Category | Title | Location |\n|---|---|---|---|\n");
            for (Finding f : r.findings) {
                b.append("| **").append(f.severity).append("** | ").append(f.category)
                        .append(" | ").append(f.title.replace("|", "\\|"))
                        .append(" | `").append(f.location).append("` |\n");
            }
            b.append('\n');
            for (Finding f : r.findings) {
                if (INFO.equals(f.severity)) continue;
                b.append("### [").append(f.severity).append("] ").append(f.title).append("\n\n");
                b.append(f.detail).append("\n\n");
            }
        }
        b.append("## Recommendations\n\n");
        b.append("1. Remove hardcoded credentials and rotate any key that shipped in a release.\n");
        b.append("2. Set `android:allowBackup=\"false\"` and `android:debuggable=\"false\"` for production.\n");
        b.append("3. Protect exported components with signature-level permissions.\n");
        b.append("4. Replace ECB/DES/RC4/MD5/SHA-1 with AES-GCM and SHA-256.\n");
        b.append("5. Disable cleartext traffic and pin network security configs.\n\n");
        b.append("---\n\n\u00a9 2027 Ramy El-Banna \u2014 Juzoor (\u062c\u0630\u0648\u0631)\u2122\n");
        return b.toString();
    }

    private static String json(Report r) {
        StringBuilder b = new StringBuilder(4096);
        b.append("{\n  \"generator\": \"Juzoor\",\n  \"version\": \"").append(ConversionResult.GENERATOR_VERSION).append("\",\n");
        b.append("  \"package\": \"").append(esc(r.packageName)).append("\",\n");
        b.append("  \"riskScore\": ").append(r.riskScore).append(",\n");
        b.append("  \"risk\": \"").append(r.riskLabel()).append("\",\n");
        b.append("  \"counts\": {\"critical\": ").append(r.critical).append(", \"high\": ").append(r.high)
                .append(", \"medium\": ").append(r.medium).append(", \"low\": ").append(r.low)
                .append(", \"info\": ").append(r.info).append("},\n");
        b.append("  \"stringsScanned\": ").append(r.stringsScanned).append(",\n");
        b.append("  \"filesScanned\": ").append(r.filesScanned).append(",\n");
        b.append("  \"trackers\": [");
        for (int i = 0; i < r.trackers.size(); i++) {
            if (i > 0) b.append(", ");
            b.append('"').append(esc(r.trackers.get(i))).append('"');
        }
        b.append("],\n  \"secrets\": [");
        for (int i = 0; i < r.secrets.size(); i++) {
            if (i > 0) b.append(", ");
            b.append('"').append(esc(r.secrets.get(i))).append('"');
        }
        b.append("],\n  \"findings\": [");
        for (int i = 0; i < r.findings.size(); i++) {
            Finding f = r.findings.get(i);
            if (i > 0) b.append(',');
            b.append("\n    {\"severity\": \"").append(f.severity).append("\", \"category\": \"").append(esc(f.category))
                    .append("\", \"title\": \"").append(esc(f.title)).append("\", \"detail\": \"").append(esc(f.detail))
                    .append("\", \"location\": \"").append(esc(f.location)).append("\"}");
        }
        b.append("\n  ]\n}\n");
        return b.toString();
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }
}
