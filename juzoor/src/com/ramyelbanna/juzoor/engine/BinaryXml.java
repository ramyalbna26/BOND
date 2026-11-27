package com.ramyelbanna.juzoor.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/* loaded from: /workspace/apk_extract/classes.dex */
public final class BinaryXml {
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private static final int RES_STRING_POOL_TYPE = 1;
    private static final int RES_XML_CDATA_TYPE = 260;
    private static final int RES_XML_END_ELEMENT_TYPE = 259;
    private static final int RES_XML_END_NAMESPACE_TYPE = 257;
    private static final int RES_XML_RESOURCE_MAP_TYPE = 384;
    private static final int RES_XML_START_ELEMENT_TYPE = 258;
    private static final int RES_XML_START_NAMESPACE_TYPE = 256;
    private static final int RES_XML_TYPE = 3;
    private final byte[] data;
    private StringPool strings;
    private int[] resMap = new int[0];
    private final StringBuilder out = new StringBuilder(65536);
    private final List<String> nsStack = new ArrayList();
    private int indent = 0;
    private final List<ElementInfo> elementStack = new ArrayList();

    private BinaryXml(byte[] bArr) {
        this.data = bArr;
    }

    public static String decode(byte[] bArr) {
        BinaryXml binaryXml = new BinaryXml(bArr);
        binaryXml.parse();
        return binaryXml.out.toString();
    }

    private void parse() {
        this.out.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        int u16 = u16(0);
        if (u16 != RES_XML_TYPE) {
            throw new IllegalArgumentException("Not AXML: 0x" + Integer.toHexString(u16));
        }
        int u162 = u16(2);
        int i32 = i32(4);
        while (true) {
            int i = u162 + 8;
            if (i > i32 || i > this.data.length) {
                return;
            }
            int u163 = u16(u162);
            int u164 = u16(u162 + 2);
            int i322 = i32(u162 + 4);
            if (i322 <= 0) {
                return;
            }
            int i2 = u162 + i322;
            byte[] bArr = this.data;
            if (i2 > bArr.length) {
                return;
            }
            if (u163 != RES_STRING_POOL_TYPE) {
                if (u163 == RES_XML_RESOURCE_MAP_TYPE) {
                    int i3 = (i322 - u164) / 4;
                    this.resMap = new int[i3];
                    for (int i4 = 0; i4 < i3; i4 += RES_STRING_POOL_TYPE) {
                        this.resMap[i4] = i32(u162 + u164 + (i4 * 4));
                    }
                } else {
                    switch (u163) {
                        case RES_XML_START_NAMESPACE_TYPE /* 256 */:
                            int i323 = i32(u162 + 16);
                            int i324 = i32(u162 + 20);
                            StringPool stringPool = this.strings;
                            String str = stringPool == null ? "" : stringPool.get(i323);
                            StringPool stringPool2 = this.strings;
                            String str2 = stringPool2 != null ? stringPool2.get(i324) : "";
                            this.nsStack.add(str + "=" + str2);
                            break;
                        case RES_XML_END_NAMESPACE_TYPE /* 257 */:
                            if (this.nsStack.isEmpty()) {
                                break;
                            } else {
                                List<String> list = this.nsStack;
                                list.remove(list.size() - RES_STRING_POOL_TYPE);
                                break;
                            }
                        case RES_XML_START_ELEMENT_TYPE /* 258 */:
                            startElement(u162, u164);
                            break;
                        case RES_XML_END_ELEMENT_TYPE /* 259 */:
                            endElement(u162, u164);
                            break;
                        case RES_XML_CDATA_TYPE /* 260 */:
                            int i325 = i32(u162 + 16);
                            StringPool stringPool3 = this.strings;
                            if (stringPool3 != null) {
                                String str3 = stringPool3.get(i325);
                                if (str3.isEmpty()) {
                                    break;
                                } else {
                                    if (!this.elementStack.isEmpty()) {
                                        List<ElementInfo> list2 = this.elementStack;
                                        list2.get(list2.size() - RES_STRING_POOL_TYPE).hasChildren = true;
                                    }
                                    this.out.append(escapeText(str3));
                                    break;
                                }
                            } else {
                                break;
                            }
                    }
                }
            } else {
                this.strings = new StringPool(bArr, u162);
            }
            u162 = i2;
        }
    }

    private void startElement(int i, int i2) {
        String typedValue;
        StringPool stringPool;
        StringPool stringPool2;
        StringPool stringPool3;
        StringPool stringPool4;
        int i3 = i + i2;
        i32(i3);
        int i32 = i32(i3 + 4);
        int u16 = u16(i3 + 8);
        int u162 = u16(i3 + 10);
        int u163 = u16(i3 + 12);
        StringPool stringPool5 = this.strings;
        String str = stringPool5 == null ? "?" : stringPool5.get(i32);
        this.out.append("\n");
        indent();
        StringBuilder sb = this.out;
        sb.append('<');
        sb.append(str);
        int i4 = 0;
        if (this.elementStack.isEmpty()) {
            for (String str2 : this.nsStack) {
                int indexOf = str2.indexOf(61);
                String substring = indexOf > 0 ? str2.substring(i4, indexOf) : "";
                if (indexOf > 0) {
                    str2 = str2.substring(indexOf + RES_STRING_POOL_TYPE);
                }
                this.out.append("\n");
                indentPlus(2);
                if (substring.isEmpty()) {
                    StringBuilder sb2 = this.out;
                    sb2.append("xmlns=\"");
                    sb2.append(escapeAttr(str2));
                    sb2.append('\"');
                } else {
                    StringBuilder sb3 = this.out;
                    sb3.append("xmlns:");
                    sb3.append(substring);
                    sb3.append("=\"");
                    sb3.append(escapeAttr(str2));
                    sb3.append('\"');
                }
                i4 = 0;
            }
        }
        int i5 = i3 + u16;
        for (int i6 = 0; i6 < u163; i6 += RES_STRING_POOL_TYPE) {
            int i7 = (i6 * u162) + i5;
            int i322 = i32(i7);
            int i323 = i32(i7 + 4);
            int i324 = i32(i7 + 8);
            int i8 = this.data[i7 + 15] & 255;
            int i325 = i32(i7 + 16);
            String str3 = (i322 < 0 || (stringPool4 = this.strings) == null) ? "" : stringPool4.get(i322);
            String str4 = (i323 < 0 || (stringPool3 = this.strings) == null) ? "" : stringPool3.get(i323);
            if (str4.isEmpty()) {
                str4 = fallbackAttrName(i6, i323);
            }
            if (i8 == RES_XML_TYPE && (stringPool2 = this.strings) != null) {
                typedValue = stringPool2.get(i325);
            } else if (i324 >= 0 && (stringPool = this.strings) != null) {
                typedValue = stringPool.get(i324);
            } else {
                typedValue = typedValue(i8, i325);
            }
            this.out.append("\n");
            indentPlus(2);
            if (ANDROID_NS.equals(str3)) {
                this.out.append("android:");
            } else if (!str3.isEmpty()) {
                StringBuilder sb4 = this.out;
                sb4.append(str3);
                sb4.append(':');
            }
            StringBuilder sb5 = this.out;
            sb5.append(str4);
            sb5.append("=\"");
            sb5.append(escapeAttr(typedValue));
            sb5.append('\"');
        }
        this.out.append('>');
        if (!this.elementStack.isEmpty()) {
            List<ElementInfo> list = this.elementStack;
            list.get(list.size() - RES_STRING_POOL_TYPE).hasChildren = true;
        }
        this.elementStack.add(new ElementInfo(str));
        this.indent += RES_STRING_POOL_TYPE;
    }

    private static class ElementInfo {
        boolean hasChildren;
        final String name;

        ElementInfo(String str) {
            this.name = str;
        }
    }

    private void endElement(int i, int i2) {
        ElementInfo remove;
        int length;
        int i32 = i32(i + i2 + 4);
        StringPool stringPool = this.strings;
        String str = stringPool == null ? "?" : stringPool.get(i32);
        if (this.elementStack.isEmpty()) {
            remove = null;
        } else {
            remove = this.elementStack.remove(this.elementStack.size() - 1);
        }
        int i3 = this.indent;
        if (i3 > 0) {
            this.indent = i3 - 1;
        }
        if (remove != null && !remove.hasChildren && (length = this.out.length()) > 0) {
            int i4 = length - 1;
            if (this.out.charAt(i4) == '>') {
                this.out.setLength(i4);
                this.out.append("/>");
                return;
            }
        }
        this.out.append("\n");
        indent();
        StringBuilder sb = this.out;
        sb.append("</");
        sb.append(str);
        sb.append('>');
    }

    private String fallbackAttrName(int i, int i2) {
        if (i2 >= 0) {
            int[] iArr = this.resMap;
            if (i2 < iArr.length) {
                String str = AttrNames.get(iArr[i2]);
                if (str != null) {
                    return str;
                }
                return "attr_0x" + String.format("%08x", Integer.valueOf(this.resMap[i2]));
            }
        }
        return "attr" + i;
    }

    private String typedValue(int i, int i2) {
        switch (i) {
            case 0:
                return "null";
            case RES_STRING_POOL_TYPE /* 1 */:
                return "@0x" + String.format("%08x", Integer.valueOf(i2));
            case 2:
                return "@*0x" + String.format("%08x", Integer.valueOf(i2));
            case RES_XML_TYPE /* 3 */:
                StringPool stringPool = this.strings;
                return stringPool == null ? "" : stringPool.get(i2);
            case 4:
                return Float.toString(Float.intBitsToFloat(i2));
            case 5:
                return complexToDimension(i2);
            case 6:
                return complexToFraction(i2);
            case 7:
                return "0x" + Integer.toHexString(i2);
            case 8:
                return Integer.toString(i2);
            case 9:
                return "0x" + Integer.toHexString(i2);
            case 10:
                return Integer.toString(i2);
            case 11:
                return Boolean.toString(i2 != 0);
            case 12:
                return Integer.toString(i2);
            case 13:
                return Integer.toString(i2);
            case 14:
                return Integer.toString(i2);
            case 15:
                return Integer.toString(i2);
            case 16:
                return Integer.toString(i2);
            case 17:
                return "0x" + Integer.toHexString(i2);
            case 18:
                return i2 != 0 ? "true" : "false";
            case 19:
            case 20:
            case 21:
            case 22:
                return Integer.toString(i2);
            case 23:
                return Integer.toString(i2);
            case 24:
                return "0x" + Integer.toHexString(i2);
            case 25:
            case 26:
            case 27:
            default:
                return "0x" + Integer.toHexString(i2);
            case 28:
            case 29:
            case 30:
            case 31:
                return "#" + String.format("%08x", Integer.valueOf(i2));
        }
    }

    static String complexToDimension(int i) {
        float f;
        float f2;
        float f3;
        int i2 = i & 15;
        int i3 = (i >> 4) & RES_XML_TYPE;
        if (i3 == 0) {
            f3 = i >> 8;
        } else if (i3 == RES_STRING_POOL_TYPE) {
            f3 = Float.intBitsToFloat(i & (-256));
        } else {
            if (i3 == 2) {
                f = i >> 8;
                f2 = 128.0f;
            } else {
                f = i >> 8;
                f2 = 32768.0f;
            }
            f3 = f / f2;
        }
        String str = i2 != 0 ? i2 != RES_STRING_POOL_TYPE ? i2 != 2 ? i2 != RES_XML_TYPE ? i2 != 4 ? i2 != 5 ? "" : "mm" : "in" : "pt" : "sp" : "dp" : "px";
        return trimFloat(f3) + str;
    }

    static String complexToFraction(int i) {
        int i2 = i & 15;
        float f = (i >> 8) / 32768.0f;
        if (i2 == RES_STRING_POOL_TYPE) {
            return trimFloat(f * 100.0f) + "%";
        }
        if (i2 == 2) {
            return trimFloat(f * 100.0f) + "%p";
        }
        return trimFloat(f);
    }

    static String trimFloat(float f) {
        long j = (long) f;
        return f == ((float) j) ? Long.toString(j) : String.format(Locale.US, "%.6f", Float.valueOf(f)).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private void indent() {
        for (int i = 0; i < this.indent; i += RES_STRING_POOL_TYPE) {
            this.out.append("    ");
        }
    }

    private void indentPlus(int i) {
        for (int i2 = 0; i2 < this.indent + i; i2 += RES_STRING_POOL_TYPE) {
            this.out.append("    ");
        }
    }

    private static String escapeAttr(String str) {
        return str.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String escapeText(String str) {
        return str.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private int u16(int i) {
        byte[] bArr = this.data;
        return ((bArr[i + RES_STRING_POOL_TYPE] & 255) << 8) | (bArr[i] & 255);
    }

    private int i32(int i) {
        byte[] bArr = this.data;
        return ((bArr[i + RES_XML_TYPE] & 255) << 24) | (bArr[i] & 255) | ((bArr[i + RES_STRING_POOL_TYPE] & 255) << 8) | ((bArr[i + 2] & 255) << 16);
    }
}
