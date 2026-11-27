package com.ramyelbanna.juzoor.engine;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts the numeric values emitted by binary-XML decoding back into the
 * symbolic references and flag names that aapt2 and Android Studio expect.
 * Without this step a recovered manifest links raw resource IDs such as
 * {@code @0x7f030000} and numeric flag masks such as {@code 0x6a0}, which makes
 * an otherwise correct project fail to build.
 */
public final class ManifestFixer {

    private static final Pattern RES_ID = Pattern.compile("@0x([0-9a-fA-F]{8})");
    private static final Pattern NUMERIC_ATTR = Pattern.compile(
            "(?:(android:)?)([A-Za-z0-9_]+)\\s*=\\s*\"(0x[0-9a-fA-F]+|-?[0-9]+)\"");

    private interface Mapper {
        String map(int value);
    }

    private static final String[] CONFIG_CHANGES = {
            "mcc", "mnc", "locale", "touchscreen", "keyboard", "keyboardHidden",
            "navigation", "orientation", "screenLayout", "uiMode", "screenSize",
            "smallestScreenSize", "layoutDirection", "density", "fontScale",
            "colorMode", "grammaticalGender", "fontWeightAdjustment"
    };

    private static final String[] ORIENTATIONS = {
            "landscape", "portrait", "user", "behind", "sensor", "nosensor",
            "sensorLandscape", "sensorPortrait", "reverseLandscape", "reversePortrait",
            "fullSensor", "userLandscape", "userPortrait", "fullUser", "locked"
    };

    private static final String[] LAUNCH_MODES = {"standard", "singleTop", "singleTask", "singleInstance", "singleInstancePerTask"};

    private static final String[] BOOLEAN_ATTRS = {"enabled", "exported", "allowBackup", "supportsRtl",
            "debuggable", "directBootAware", "hardwareAccelerated", "largeHeap",
            "usesCleartextTraffic", "requestLegacyExternalStorage", "extractNativeLibs",
            "resizeableActivity", "persistent", "isolatedProcess", "multiprocess",
            "grantUriPermissions", "visibleToInstantApps", "allowClearUserData",
            "allowTaskReparenting", "clearTaskOnLaunch", "finishOnTaskLaunch", "noHistory",
            "reparent", "stopWithTask", "testOnly"};

    private static final String[] FOREGROUND_TYPES = {"dataSync", "mediaPlayback", "phoneCall",
            "location", "connectedDevice", "mediaProjection", "camera", "microphone",
            "health", "remoteMessaging", "systemExempted", "shortService", "fileManagement"};

    private ManifestFixer() {
    }

    public static String fix(String xml, ResourceTable table) {
        if (xml == null || xml.isEmpty()) return xml;
        String out = resolveIds(xml, table);
        out = transform(out, "configChanges", new Mapper() {
            @Override
            public String map(int value) {
                return flags(value, CONFIG_CHANGES);
            }
        });
        out = transform(out, "windowSoftInputMode", new Mapper() {
            @Override
            public String map(int value) {
                return softInput(value);
            }
        });
        out = transform(out, "screenOrientation", new Mapper() {
            @Override
            public String map(int value) {
                return value == -1 ? "unspecified" : enumValue(value, ORIENTATIONS);
            }
        });
        out = transform(out, "launchMode", new Mapper() {
            @Override
            public String map(int value) {
                return enumValue(value, LAUNCH_MODES);
            }
        });
        out = transform(out, "installLocation", new Mapper() {
            @Override
            public String map(int value) {
                return enumValue(value, new String[]{"auto", "internalOnly", "preferExternal"});
            }
        });
        out = transform(out, "foregroundServiceType", new Mapper() {
            @Override
            public String map(int value) {
                return flags(value, FOREGROUND_TYPES);
            }
        });
        out = transform(out, "protectionLevel", new Mapper() {
            @Override
            public String map(int value) {
                return protection(value);
            }
        });
        out = transform(out, "gravity", new Mapper() {
            @Override
            public String map(int value) {
                return gravity(value);
            }
        });
        out = transform(out, "layout_gravity", new Mapper() {
            @Override
            public String map(int value) {
                return gravity(value);
            }
        });
        out = transform(out, "orientation", new Mapper() {
            @Override
            public String map(int value) {
                return enumValue(value, new String[]{"horizontal", "vertical"});
            }
        });
        out = transform(out, "visibility", new Mapper() {
            @Override
            public String map(int value) {
                if (value == 0) return "visible";
                if (value == 4) return "invisible";
                if (value == 8) return "gone";
                return null;
            }
        });
        out = transform(out, "textStyle", new Mapper() {
            @Override
            public String map(int value) {
                if (value == 0) return "normal";
                if (value == 1) return "bold";
                if (value == 2) return "italic";
                if (value == 3) return "bold|italic";
                return null;
            }
        });
        out = transform(out, "scaleType", new Mapper() {
            @Override
            public String map(int value) {
                return enumValue(value, new String[]{"matrix", "fitXY", "fitStart", "fitCenter",
                        "fitEnd", "center", "centerCrop", "centerInside"});
            }
        });
        out = transform(out, "ellipsize", new Mapper() {
            @Override
            public String map(int value) {
                return enumValue(value, new String[]{"none", "start", "middle", "end", "marquee"});
            }
        });
        for (String attr : new String[]{"layout_width", "layout_height", "width", "height"}) {
            out = transform(out, attr, new Mapper() {
                @Override
                public String map(int value) {
                    if (value == -1) return "match_parent";
                    if (value == -2) return "wrap_content";
                    if (value == -3) return "fill_parent";
                    return null;
                }
            });
        }
        for (String attr : BOOLEAN_ATTRS) {
            out = transform(out, attr, new Mapper() {
                @Override
                public String map(int value) {
                    if (value == 0) return "false";
                    if (value == 1 || value == -1) return "true";
                    return null;
                }
            });
        }
        return out;
    }

    private static String resolveIds(String xml, ResourceTable table) {
        if (table == null) return xml;
        Matcher m = RES_ID.matcher(xml);
        StringBuffer sb = new StringBuffer(xml.length());
        while (m.find()) {
            String replacement = m.group();
            try {
                String ref = table.referenceFor((int) Long.parseLong(m.group(1), 16));
                if (ref != null && !ref.isEmpty()) replacement = ref;
            } catch (Throwable ignored) {
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Rewrites every numeric occurrence of one attribute independently, so a
     * manifest with several activities keeps their individual flags.
     */
    private static String transform(String xml, String attr, Mapper mapper) {
        Matcher m = NUMERIC_ATTR.matcher(xml);
        StringBuffer sb = new StringBuffer(xml.length());
        while (m.find()) {
            String name = m.group(2);
            if (!attr.equals(name)) {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group()));
                continue;
            }
            String raw = m.group(3);
            String replacement = null;
            try {
                int value = raw.startsWith("0x") || raw.startsWith("0X")
                        ? (int) Long.parseLong(raw.substring(2), 16) : Integer.parseInt(raw);
                replacement = mapper.map(value);
            } catch (Throwable ignored) {
            }
            if (replacement == null) {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group()));
            } else {
                String prefix = m.group(1) == null ? "" : m.group(1);
                m.appendReplacement(sb, Matcher.quoteReplacement(prefix + name + "=\"" + replacement + "\""));
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String flags(int value, String[] names) {
        if (value < 0) {
            if (value == -1) {
                StringBuilder all = new StringBuilder();
                for (String n : names) {
                    if (all.length() > 0) all.append('|');
                    all.append(n);
                }
                return all.toString();
            }
            return null;
        }
        if (value == 0) return "0";
        StringBuilder b = new StringBuilder();
        int known = 0;
        for (int i = 0; i < names.length; i++) {
            int bit = 1 << i;
            known |= bit;
            if ((value & bit) != 0) {
                if (b.length() > 0) b.append('|');
                b.append(names[i]);
            }
        }
        if ((value & ~known) != 0) return null;
        return b.length() == 0 ? "0" : b.toString();
    }

    private static String enumValue(int value, String[] names) {
        return value >= 0 && value < names.length ? names[value] : null;
    }

    private static String softInput(int value) {
        if (value < 0) return null;
        String state;
        switch (value & 0x0F) {
            case 0: state = "stateUnspecified"; break;
            case 1: state = "stateUnchanged"; break;
            case 2: state = "stateHidden"; break;
            case 3: state = "stateAlwaysHidden"; break;
            case 4: state = "stateVisible"; break;
            case 5: state = "stateAlwaysVisible"; break;
            default: return null;
        }
        String adjust;
        switch (value & 0xF0) {
            case 0: adjust = "adjustUnspecified"; break;
            case 0x10: adjust = "adjustResize"; break;
            case 0x20: adjust = "adjustPan"; break;
            case 0x30: adjust = "adjustNothing"; break;
            default: return null;
        }
        return state + "|" + adjust;
    }

    private static String gravity(int value) {
        switch (value) {
            case 0x01: return "center_horizontal";
            case 0x03: return "left";
            case 0x05: return "right";
            case 0x07: return "fill_horizontal";
            case 0x08: return "clip_horizontal";
            case 0x10: return "center_vertical";
            case 0x11: return "center";
            case 0x30: return "top";
            case 0x50: return "bottom";
            case 0x70: return "fill_vertical";
            case 0x77: return "fill";
            case 0x80: return "clip_vertical";
            case 0x00800003: return "start";
            case 0x00800005: return "end";
            default: return null;
        }
    }

    private static String protection(int value) {
        if (value < 0) return null;
        StringBuilder b = new StringBuilder();
        int base = value & 0x0F;
        if (base == 0) b.append("normal");
        else if (base == 1) b.append("dangerous");
        else if (base == 2) b.append("signature");
        else if (base == 3) b.append("signatureOrSystem");
        else return null;
        if ((value & 0x10) != 0) b.append("|privileged");
        if ((value & 0x20) != 0) b.append("|development");
        return b.toString();
    }
}
