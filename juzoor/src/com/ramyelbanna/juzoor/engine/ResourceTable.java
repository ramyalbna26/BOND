package com.ramyelbanna.juzoor.engine;

import com.ramyelbanna.juzoor.core.LogManager;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

/* loaded from: /workspace/apk_extract/classes.dex */
public final class ResourceTable {
    private static final int RES_TABLE_PACKAGE_TYPE = 512;
    private static final int RES_TABLE_TYPE = 2;
    private static final int RES_TABLE_TYPE_TYPE = 513;
    private final byte[] data;
    private StringPool globalPool;
    private final List<ResEntry> entries = new ArrayList();
    private final Map<Long, ResEntry> byId = new LinkedHashMap();
    private final Map<Integer, String> packageNames = new LinkedHashMap();
    private final Map<String, String> filePaths = new LinkedHashMap();

    public static class MapItem {
        public int data;
        public int dataType;
        public int name;
    }

    public static class ResEntry {
        public boolean complex;
        public String config;
        public int data;
        public int dataType;
        public int entryId;
        public String key;
        public final List<MapItem> maps = new ArrayList();
        public int parent;
        public int pkgId;
        public int typeId;
        public String typeName;
    }

    private static long id(int i, int i2, int i3) {
        return ((i2 & 255) << 16) | ((i & 255) << 24) | (i3 & 65535);
    }

    private static boolean isLocaleChar(int i) {
        return (i >= 97 && i <= 122) || (i >= 65 && i <= 90) || (i >= 48 && i <= 57);
    }

    private static String pluralQuantity(int i) {
        switch (i) {
            case 16777220:
                return "other";
            case 16777221:
                return "zero";
            case 16777222:
                return "one";
            case 16777223:
                return "two";
            case 16777224:
                return "few";
            case 16777225:
                return "many";
            default:
                return null;
        }
    }

    public ResourceTable(byte[] bArr) {
        this.data = bArr;
    }

    public int getEntryCount() {
        return this.entries.size();
    }

    public List<ResEntry> getEntries() {
        return this.entries;
    }

    public void parse() {
        int i;
        if (u16(0) != RES_TABLE_TYPE) {
            throw new IllegalArgumentException("Not an ARSC table");
        }
        int u16 = u16(RES_TABLE_TYPE);
        int i32 = i32(4);
        while (true) {
            int i2 = u16 + 8;
            if (i2 > i32 || i2 > this.data.length) {
                break;
            }
            int u162 = u16(u16);
            int u163 = u16(u16 + RES_TABLE_TYPE);
            int i322 = i32(u16 + 4);
            if (i322 <= 0 || (i = u16 + i322) > this.data.length) {
                break;
            }
            if (u162 == RES_TABLE_PACKAGE_TYPE) {
                parsePackage(u16, u163, i322);
            }
            u16 = i;
        }
        for (ResEntry resEntry : this.entries) {
            this.byId.put(Long.valueOf(id(resEntry.pkgId, resEntry.typeId, resEntry.entryId)), resEntry);
        }
        buildFilePaths();
    }

    /**
     * Maps the resource file path stored in the table (which R8 may flatten to
     * {@code res/0K.xml}) back to {@code <type>[-<config>]/<name>.<ext>}.
     */
    private void buildFilePaths() {
        for (ResEntry e : this.entries) {
            if (e.complex || e.dataType != 3) continue;
            String value;
            try {
                value = stringValue(e.data);
            } catch (Throwable t) {
                continue;
            }
            if (value == null || !value.startsWith("res/")) continue;
            int dot = value.lastIndexOf('.');
            String ext = dot >= 0 ? value.substring(dot) : "";
            String dir = e.typeName + (e.config == null || e.config.isEmpty() ? "" : "-" + e.config);
            String dest = dir + "/" + e.key + ext;
            if (!filePaths.containsKey(value)) filePaths.put(value, dest);
        }
    }

    /** Returns the decoded destination path for a source path inside res/. */
    public String filePathFor(String sourcePath) {
        return filePaths.get(sourcePath);
    }

    private void parsePackage(int i, int i2, int i3) {
        int i4;
        int i32 = i32(i + 8);
        this.packageNames.put(Integer.valueOf(i32), readUtf16Fixed(i + 12, 128));
        int i322 = i32(i + 268);
        int i323 = i32(i + 276);
        int i324 = i2 >= 288 ? i32(i + 284) : 0;
        StringPool stringPool = new StringPool(this.data, i322 + i);
        StringPool stringPool2 = new StringPool(this.data, i323 + i);
        int i5 = i2 + i;
        int i6 = i + i3;
        int i7 = i5;
        while (i7 + 8 <= i6) {
            int u16 = u16(i7);
            int u162 = u16(i7 + RES_TABLE_TYPE);
            int i325 = i32(i7 + 4);
            if (i325 <= 0 || (i4 = i325 + i7) > i6) {
                return;
            }
            if (u16 == RES_TABLE_TYPE_TYPE) {
                parseType(i7, u162, i32, stringPool, stringPool2, i324);
            }
            i7 = i4;
        }
    }

    /* JADX WARN: Multi-variable type inference failed */
    private void parseType(int i, int i2, int i3, StringPool stringPool, StringPool stringPool2, int i4) {
        int i5;
        int i6;
        int i7;
        byte[] bArr = this.data;
        int i8 = bArr[i + 8] & 255;
        int i9 = bArr[i + 9] & 255;
        int i32 = i32(i + 12);
        int i322 = i32(i + 16);
        String configQualifier = configQualifier(i + 20);
        String str = stringPool.get((i8 - 1) - i4);
        if (str == null || str.isEmpty()) {
            str = "unknown";
        }
        boolean z = true;
        if ((i9 & 1) != 0) {
            return;
        }
        int i10 = i + i2;
        int i11 = 0;
        while (i11 < i32) {
            int i323 = i32((i11 * 4) + i10);
            if (i323 != -1 && (i6 = (i5 = i + i322 + i323) + 8) <= this.data.length) {
                int u16 = u16(i5 + RES_TABLE_TYPE);
                int i324 = i32(i5 + 4);
                ResEntry resEntry = new ResEntry();
                resEntry.pkgId = i3;
                resEntry.typeId = i8;
                resEntry.entryId = i11;
                resEntry.typeName = str;
                resEntry.key = stringPool2.get(i324);
                resEntry.config = configQualifier;
                if ((u16 & 1) != 0) {
                    resEntry.complex = z;
                    resEntry.parent = i32(i6);
                    int i325 = i32(i5 + 12);
                    int i12 = i5 + 16;
                    int i13 = 0;
                    while (i13 < i325) {
                        int i14 = i12 + 12;
                        if (i14 > this.data.length) {
                            break;
                        }
                        MapItem mapItem = new MapItem();
                        mapItem.name = i32(i12);
                        mapItem.dataType = this.data[i12 + 7] & 255;
                        mapItem.data = i32(i12 + 8);
                        resEntry.maps.add(mapItem);
                        i13++;
                        i10 = i10;
                        i12 = i14;
                    }
                    i7 = i10;
                } else {
                    i7 = i10;
                    int i15 = i6 + 8;
                    byte[] bArr2 = this.data;
                    if (i15 <= bArr2.length) {
                        resEntry.dataType = bArr2[i6 + 3] & 255;
                        resEntry.data = i32(i6 + 4);
                    }
                }
                this.entries.add(resEntry);
            } else {
                i7 = i10;
            }
            i11++;
            i10 = i7;
            z = true;
        }
    }

    /** Resolves a resource id back to {@code @type/name} for manifest repair. */
    public String referenceFor(int id) {
        ResEntry resEntry = this.byId.get(Long.valueOf(id & 4294967295L));
        if (resEntry != null) {
            return "@" + resEntry.typeName + "/" + resEntry.key;
        }
        String framework = FrameworkRes.get(id);
        if (framework == null) return null;
        int slash = framework.indexOf('/');
        if (slash <= 0) return null;
        String type = framework.substring(0, slash);
        String name = framework.substring(slash + 1);
        if ("style".equals(type)) name = name.replace('_', '.');
        return "@android:" + type + "/" + name;
    }

    public void writeValues(File file, LogSink logSink) throws IOException {
        File valuesDir = new File(file, "values");
        valuesDir.mkdirs();
        writePublic(new File(valuesDir, "public.xml"));
        java.util.TreeMap<String, java.util.TreeMap<String, java.util.List<ResEntry>>> treeMap = new java.util.TreeMap<>();
        for (ResEntry e : this.entries) {
            if (e.typeName.equals("attr") || e.typeName.equals("id") || e.typeName.equals("declare-styleable")) continue;
            java.util.TreeMap<String, java.util.List<ResEntry>> byType = treeMap.get(e.config);
            if (byType == null) { byType = new java.util.TreeMap<>(); treeMap.put(e.config, byType); }
            java.util.List<ResEntry> list = byType.get(e.typeName);
            if (list == null) { list = new java.util.ArrayList<>(); byType.put(e.typeName, list); }
            list.add(e);
        }
        int written = 0;
        for (java.util.Map.Entry<String, java.util.TreeMap<String, java.util.List<ResEntry>>> entry : treeMap.entrySet()) {
            String config = entry.getKey();
            File dir = config.isEmpty() ? valuesDir : new File(file, "values-" + config);
            dir.mkdirs();
            for (java.util.Map.Entry<String, java.util.List<ResEntry>> e2 : entry.getValue().entrySet()) {
                String type = e2.getKey();
                String fileName = fileNameFor(type);
                if (fileName == null) continue;
                StringBuilder sb = new StringBuilder(4096);
                sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n");
                boolean any = false;
                for (ResEntry re : e2.getValue()) {
                    if (renderEntry(sb, re, type)) any = true;
                }
                sb.append("</resources>\n");
                if (any) { writeUtf8(new File(dir, fileName), sb.toString()); written++; }
            }
        }
        if (logSink != null) logSink.log(LogManager.INFO, "Resource table: " + this.entries.size() + " entries, " + written + " values files");
    }

    private String fileNameFor(String str) {
        str.hashCode();
        switch (str) {
            case "fraction":
                return "fractions.xml";
            case "string-array":
            case "array":
            case "integer-array":
                return "arrays.xml";
            case "string":
                return "strings.xml";
            case "plurals":
                return "plurals.xml";
            case "bool":
                return "bools.xml";
            case "color":
                return "colors.xml";
            case "dimen":
                return "dimens.xml";
            case "style":
                return "styles.xml";
            case "integer":
                return "integers.xml";
            default:
                return null;
        }
    }

    /* JADX WARN: Can't fix incorrect switch cases order, some code will duplicate */
    /* JADX WARN: Code restructure failed: missing block: B:38:0x00a7, code lost:


        if (r20.equals("color") == false) goto L28;
     */
    /* JADX WARN: Failed to restore switch over string. Please report as a decompilation issue */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
    */
    private boolean renderEntry(StringBuilder sb, ResEntry resEntry, String str) {
        String value;
        String value2;
        String reference;
        String xmlName = xmlName(resEntry.key);
        if (xmlName.isEmpty()) {
            return false;
        }
        if (!resEntry.complex) {
            char c = 3;
            if (resEntry.dataType == 3) {
                String stringValue = stringValue(resEntry.data);
                if (stringValue.startsWith("res/") || stringValue.startsWith("assets/")) {
                    return false;
                }
                if (!str.equals("string") && !str.equals("color")) {
                    return false;
                }
                sb.append("    <");
                sb.append(str);
                sb.append(" name=\"");
                sb.append(xmlName);
                sb.append("\">");
                sb.append(escapeString(stringValue));
                sb.append("</");
                sb.append(str);
                sb.append(">\n");
                return true;
            }
            String value3 = value(resEntry.dataType, resEntry.data, false);
            if (value3 == null) {
                return false;
            }
            str.hashCode();
            switch (str.hashCode()) {
                case -1653751294:
                    if (str.equals("fraction")) {
                        c = 0;
                        break;
                    }
                    c = 65535;
                    break;
                case -891985903:
                    if (str.equals("string")) {
                        c = 1;
                        break;
                    }
                    c = 65535;
                    break;
                case 3029738:
                    if (str.equals("bool")) {
                        c = 2;
                        break;
                    }
                    c = 65535;
                    break;
                case 94842723:
                    break;
                case 95588145:
                    if (str.equals("dimen")) {
                        c = 4;
                        break;
                    }
                    c = 65535;
                    break;
                case 1958052158:
                    if (str.equals("integer")) {
                        c = 5;
                        break;
                    }
                    c = 65535;
                    break;
                default:
                    c = 65535;
                    break;
            }
            switch (c) {
                case 0:
                case 1:
                case RES_TABLE_TYPE /* 2 */:
                case 3:
                case 4:
                case 5:
                    sb.append("    <");
                    sb.append(str);
                    sb.append(" name=\"");
                    sb.append(xmlName);
                    sb.append("\">");
                    sb.append(value3);
                    sb.append("</");
                    sb.append(str);
                    sb.append(">\n");
                    return true;
            }
            return false;
        }
        if (str.equals("style")) {
            sb.append("    <style name=\"");
            sb.append(xmlName);
            sb.append('\"');
            if (resEntry.parent != 0 && (reference = reference(resEntry.parent, true)) != null) {
                sb.append(" parent=\"");
                sb.append(reference);
                sb.append('\"');
            }
            sb.append(">\n");
            for (MapItem mapItem : resEntry.maps) {
                String attrName = attrName(mapItem.name);
                if (attrName != null && (value2 = value(mapItem.dataType, mapItem.data, true)) != null) {
                    sb.append("        <item name=\"");
                    sb.append(attrName);
                    sb.append("\">");
                    sb.append(value2);
                    sb.append("</item>\n");
                }
            }
            sb.append("    </style>\n");
            return true;
        }
        if (str.equals("plurals")) {
            sb.append("    <plurals name=\"");
            sb.append(xmlName);
            sb.append("\">\n");
            for (MapItem mapItem2 : resEntry.maps) {
                String pluralQuantity = pluralQuantity(mapItem2.name);
                if (pluralQuantity != null && (value = value(mapItem2.dataType, mapItem2.data, true)) != null) {
                    sb.append("        <item quantity=\"");
                    sb.append(pluralQuantity);
                    sb.append("\">");
                    sb.append(value);
                    sb.append("</item>\n");
                }
            }
            sb.append("    </plurals>\n");
            return true;
        }
        if (!str.equals("array") && !str.equals("string-array") && !str.equals("integer-array")) {
            return false;
        }
        sb.append("    <");
        sb.append(str);
        sb.append(" name=\"");
        sb.append(xmlName);
        sb.append("\">\n");
        for (MapItem mapItem3 : resEntry.maps) {
            String value4 = value(mapItem3.dataType, mapItem3.data, true);
            if (value4 != null) {
                sb.append("        <item>");
                sb.append(value4);
                sb.append("</item>\n");
            }
        }
        sb.append("    </");
        sb.append(str);
        sb.append(">\n");
        return true;
    }

    private void writePublic(File file) throws IOException {
        StringBuilder sb = new StringBuilder(65536);
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n");
        for (ResEntry resEntry : this.entries) {
            if (resEntry.key != null && !resEntry.key.isEmpty()) {
                sb.append("    <public type=\"");
                sb.append(resEntry.typeName);
                sb.append("\" name=\"");
                sb.append(xmlName(resEntry.key));
                sb.append("\" id=\"0x");
                sb.append(String.format(Locale.US, "%08x", Long.valueOf(id(resEntry.pkgId, resEntry.typeId, resEntry.entryId))));
                sb.append("\" />\n");
            }
        }
        sb.append("</resources>\n");
        writeUtf8(file, sb.toString());
    }

    private String stringValue(int i) {
        if (this.globalPool == null) {
            this.globalPool = new StringPool(this.data, u16(RES_TABLE_TYPE));
        }
        return this.globalPool.get(i);
    }

    private String value(int i, int i2, boolean z) {
        switch (i) {
            case 0:
                return "@null";
            case 1:
            case 7:
                return reference(i2, z);
            case RES_TABLE_TYPE /* 2 */:
            case 8:
                return "?" + reference(i2, z);
            case 3:
                return escapeString(stringValue(i2));
            case 4:
                return BinaryXml.trimFloat(Float.intBitsToFloat(i2));
            case 5:
                return BinaryXml.complexToDimension(i2);
            case 6:
                return BinaryXml.complexToFraction(i2);
            default:
                switch (i) {
                    case 16:
                        return Integer.toString(i2);
                    case 17:
                        return "0x" + Integer.toHexString(i2);
                    case 18:
                        return i2 != 0 ? "true" : "false";
                    default:
                        switch (i) {
                            case 28:
                            case 29:
                            case 30:
                            case 31:
                                return "#" + String.format(Locale.US, "%08x", Integer.valueOf(i2));
                            default:
                                return "0x" + Integer.toHexString(i2);
                        }
                }
        }
    }

    private String reference(int i, boolean z) {
        String str;
        int indexOf;
        ResEntry resEntry = this.byId.get(Long.valueOf(i & 4294967295L));
        if (resEntry != null) {
            String str2 = resEntry.pkgId != 1 ? "" : "android:";
            if (z && resEntry.typeName.equals("attr")) {
                return str2 + resEntry.key;
            }
            return "@" + str2 + resEntry.typeName + "/" + resEntry.key;
        }
        if ((i >>> 24) != 1 || (str = FrameworkRes.get(i)) == null || (indexOf = str.indexOf(47)) <= 0) {
            return null;
        }
        String substring = str.substring(0, indexOf);
        String substring2 = str.substring(indexOf + 1);
        if (z && substring.equals("attr")) {
            return "android:" + substring2;
        }
        if (substring.equals("style")) {
            // android.R.style field names use '_' where XML uses '.'.
            substring2 = substring2.replace('_', '.');
        }
        return "@android:" + substring + "/" + substring2;
    }

    private String attrName(int i) {
        int indexOf;
        ResEntry resEntry = this.byId.get(Long.valueOf(i & 4294967295L));
        if (resEntry != null && resEntry.typeName.equals("attr")) {
            return (resEntry.pkgId != 1 ? "" : "android:") + resEntry.key;
        }
        if ((i >>> 24) != 1) {
            return null;
        }
        String str = FrameworkRes.get(i);
        if (str != null && (indexOf = str.indexOf(47)) > 0) {
            return "android:" + str.substring(indexOf + 1);
        }
        String str2 = AttrNames.get(i);
        if (str2 == null) {
            return null;
        }
        return "android:" + str2;
    }

    private static String xmlName(String str) {
        if (str == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(str.length());
        for (int i = 0; i < str.length(); i++) {
            char charAt = str.charAt(i);
            if (Character.isLetterOrDigit(charAt) || charAt == '_' || charAt == '.' || charAt == '-') {
                sb.append(charAt);
            } else {
                sb.append('_');
            }
        }
        return sb.toString();
    }

    static String escapeString(String str) {
        StringBuilder sb = new StringBuilder(str.length() + 16);
        for (int i = 0; i < str.length(); i++) {
            char charAt = str.charAt(i);
            if (charAt == '\t') {
                sb.append("\\t");
            } else if (charAt == '\n') {
                sb.append("\\n");
            } else if (charAt == '\"') {
                sb.append("\\\"");
            } else if (charAt == '<') {
                sb.append("&lt;");
            } else if (charAt == '>') {
                sb.append("&gt;");
            } else if (charAt == '&') {
                sb.append("&amp;");
            } else if (charAt == '\'') {
                sb.append("\\'");
            } else if (charAt < ' ') {
                sb.append(' ');
            } else {
                sb.append(charAt);
            }
        }
        String sb2 = sb.toString();
        if (!sb2.startsWith("@") && !sb2.startsWith("?")) {
            return sb2;
        }
        return "\\" + sb2;
    }

    String configQualifier(int i) {
        int i32 = i32(i);
        ArrayList arrayList = new ArrayList();
        int u16 = u16(i + 4);
        int u162 = u16(i + 6);
        if (u16 != 0) {
            arrayList.add("mcc" + u16);
        }
        if (u162 != 0) {
            arrayList.add("mnc" + u162);
        }
        byte[] bArr = this.data;
        String lowerCase = asciiLocale(bArr[i + 8], bArr[i + 9]).toLowerCase(Locale.US);
        byte[] bArr2 = this.data;
        String upperCase = asciiLocale(bArr2[i + 10], bArr2[i + 11]).toUpperCase(Locale.US);
        if (!lowerCase.isEmpty()) {
            arrayList.add(lowerCase);
            if (!upperCase.isEmpty()) {
                arrayList.add("r" + upperCase);
            }
        }
        if (i32 >= 32) {
            int u163 = u16(i + 30);
            if (u163 != 0) {
                arrayList.add("sw" + u163 + "dp");
            }
            int u164 = u16(i + 32);
            int u165 = u16(i + 34);
            if (u164 != 0) {
                arrayList.add("w" + u164 + "dp");
            }
            if (u165 != 0) {
                arrayList.add("h" + u165 + "dp");
            }
        }
        if (i32 >= 30) {
            int i2 = this.data[i + 28] & 255;
            int i3 = (i2 >> 6) & 3;
            if (i3 == 1) {
                arrayList.add("ldltr");
            } else if (i3 == RES_TABLE_TYPE) {
                arrayList.add("ldrtl");
            }
            int i4 = i2 & 15;
            if (i4 == 1) {
                arrayList.add("small");
            } else if (i4 == RES_TABLE_TYPE) {
                arrayList.add("normal");
            } else if (i4 == 3) {
                arrayList.add("large");
            } else if (i4 == 4) {
                arrayList.add("xlarge");
            }
            int i5 = (i2 >> 4) & 3;
            if (i5 == 1) {
                arrayList.add("notlong");
            } else if (i5 == RES_TABLE_TYPE) {
                arrayList.add("long");
            }
            int i6 = this.data[i + 29] & 255;
            int i7 = i6 & 15;
            if (i7 == RES_TABLE_TYPE) {
                arrayList.add("desk");
            } else if (i7 == 3) {
                arrayList.add("car");
            } else if (i7 == 4) {
                arrayList.add("television");
            } else if (i7 == 5) {
                arrayList.add("appliance");
            } else if (i7 == 6) {
                arrayList.add("watch");
            } else if (i7 == 7) {
                arrayList.add("vrheadset");
            }
            int i8 = (i6 >> 4) & 15;
            if (i8 == 1) {
                arrayList.add("notnight");
            } else if (i8 == RES_TABLE_TYPE) {
                arrayList.add("night");
            }
            int i9 = this.data[i + 12] & 255;
            if (i9 == 1) {
                arrayList.add("port");
            } else if (i9 == RES_TABLE_TYPE) {
                arrayList.add("land");
            } else if (i9 == 3) {
                arrayList.add("square");
            }
            int u166 = u16(i + 14);
            if (u166 != 0) {
                arrayList.add(densityName(u166));
            }
            int i10 = this.data[i + 13] & 255;
            if (i10 == 1) {
                arrayList.add("notouch");
            } else if (i10 == RES_TABLE_TYPE) {
                arrayList.add("stylus");
            } else if (i10 == 3) {
                arrayList.add("finger");
            }
            int i11 = this.data[i + 16] & 255;
            if (i11 == 1) {
                arrayList.add("keysexposed");
            } else if (i11 == RES_TABLE_TYPE) {
                arrayList.add("keyshidden");
            } else if (i11 == 3) {
                arrayList.add("keyssoft");
            }
            int i12 = this.data[i + 17] & 255;
            if (i12 == 1) {
                arrayList.add("navexposed");
            } else if (i12 == RES_TABLE_TYPE) {
                arrayList.add("navhidden");
            }
            int i13 = this.data[i + 18] & 255 & 3;
            if (i13 == 1) {
                arrayList.add("nonav");
            } else if (i13 == RES_TABLE_TYPE) {
                arrayList.add("dpad");
            } else if (i13 == 3) {
                arrayList.add("trackball");
            } else if (i13 == 4) {
                arrayList.add("wheel");
            }
            int u167 = u16(i + 24);
            if (u167 != 0) {
                arrayList.add("v" + u167);
            }
        }
        return ResourceTable$$ExternalSyntheticBackport0.m("-", arrayList);
    }

    private static String asciiLocale(byte b, byte b2) {
        int i = b & 255;
        int i2 = b2 & 255;
        if (i == 0 || i2 == 0 || i >= 128 || i2 >= 128 || !isLocaleChar(i) || !isLocaleChar(i2)) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append((char) i);
        sb.append((char) i2);
        return sb.toString();
    }

    private static String densityName(int i) {
        if (i == 120) {
            return "ldpi";
        }
        if (i == 160) {
            return "mdpi";
        }
        if (i == 213) {
            return "tvdpi";
        }
        if (i == 240) {
            return "hdpi";
        }
        if (i == 320) {
            return "xhdpi";
        }
        if (i == 480) {
            return "xxhdpi";
        }
        if (i == 640) {
            return "xxxhdpi";
        }
        switch (i) {
            case 65534:
                return "anydpi";
            case 65535:
                return "nodpi";
            default:
                return i + "dpi";
        }
    }

    private String readUtf16Fixed(int i, int i2) {
        StringBuilder sb = new StringBuilder();
        for (int i3 = 0; i3 < i2; i3++) {
            int u16 = u16((i3 * RES_TABLE_TYPE) + i);
            if (u16 == 0) {
                break;
            }
            sb.append((char) u16);
        }
        return sb.toString();
    }

    private static void writeUtf8(File file, String str) throws IOException {
        File parentFile = file.getParentFile();
        if (parentFile != null && !parentFile.exists()) {
            parentFile.mkdirs();
        }
        OutputStreamWriter outputStreamWriter = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8);
        try {
            outputStreamWriter.write(str);
            outputStreamWriter.close();
        } catch (Throwable th) {
            try {
                outputStreamWriter.close();
            } catch (Throwable th2) {
                th.addSuppressed(th2);
            }
            throw th;
        }
    }

    private int u16(int i) {
        byte[] bArr = this.data;
        return ((bArr[i + 1] & 255) << 8) | (bArr[i] & 255);
    }

    private int i32(int i) {
        byte[] bArr = this.data;
        return ((bArr[i + 3] & 255) << 24) | (bArr[i] & 255) | ((bArr[i + 1] & 255) << 8) | ((bArr[i + RES_TABLE_TYPE] & 255) << 16);
    }
}
