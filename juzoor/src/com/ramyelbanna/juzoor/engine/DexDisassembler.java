package com.ramyelbanna.juzoor.engine;

import com.ramyelbanna.juzoor.core.LogManager;
import com.ramyelbanna.juzoor.engine.ZipUtil;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/* loaded from: /workspace/apk_extract/classes.dex */
public final class DexDisassembler {
    private static final String[] OPCODES = {"nop", "move", "move/from16", "move/16", "move-wide", "move-wide/from16", "move-wide/16", "move-object", "move-object/from16", "move-object/16", "move-result", "move-result-wide", "move-result-object", "move-exception", "return-void", "return", "return-wide", "return-object", "const/4", "const/16", "const", "const/high16", "const-wide/16", "const-wide/32", "const-wide", "const-wide/high16", "const-string", "const-string/jumbo", "const-class", "monitor-enter", "monitor-exit", "check-cast", "instance-of", "array-length", "new-instance", "new-array", "filled-new-array", "filled-new-array/range", "fill-array-data", "throw", "goto", "goto/16", "goto/32", "packed-switch", "sparse-switch", "cmpl-float", "cmpg-float", "cmpl-double", "cmpg-double", "cmp-long", "if-eq", "if-ne", "if-lt", "if-ge", "if-gt", "if-le", "if-eqz", "if-nez", "if-ltz", "if-gez", "if-gtz", "if-lez", "unused-3e", "unused-3f", "unused-40", "unused-41", "unused-42", "unused-43", "aget", "aget-wide", "aget-object", "aget-boolean", "aget-byte", "aget-char", "aget-short", "aput", "aput-wide", "aput-object", "aput-boolean", "aput-byte", "aput-char", "aput-short", "iget", "iget-wide", "iget-object", "iget-boolean", "iget-byte", "iget-char", "iget-short", "iput", "iput-wide", "iput-object", "iput-boolean", "iput-byte", "iput-char", "iput-short", "sget", "sget-wide", "sget-object", "sget-boolean", "sget-byte", "sget-char", "sget-short", "sput", "sput-wide", "sput-object", "sput-boolean", "sput-byte", "sput-char", "sput-short", "invoke-virtual", "invoke-super", "invoke-direct", "invoke-static", "invoke-interface", "unused-73", "invoke-virtual/range", "invoke-super/range", "invoke-direct/range", "invoke-static/range", "invoke-interface/range", "unused-79", "unused-7a", "neg-int", "not-int", "neg-long", "not-long", "neg-float", "neg-double", "int-to-long", "int-to-float", "int-to-double", "long-to-int", "long-to-float", "long-to-double", "float-to-int", "float-to-long", "float-to-double", "double-to-int", "double-to-long", "double-to-float", "int-to-byte", "int-to-char", "int-to-short", "add-int", "sub-int", "mul-int", "div-int", "rem-int", "and-int", "or-int", "xor-int", "shl-int", "shr-int", "ushr-int", "add-long", "sub-long", "mul-long", "div-long", "rem-long", "and-long", "or-long", "xor-long", "shl-long", "shr-long", "ushr-long", "add-float", "sub-float", "mul-float", "div-float", "rem-float", "add-double", "sub-double", "mul-double", "div-double", "rem-double", "add-int/2addr", "sub-int/2addr", "mul-int/2addr", "div-int/2addr", "rem-int/2addr", "and-int/2addr", "or-int/2addr", "xor-int/2addr", "shl-int/2addr", "shr-int/2addr", "ushr-int/2addr", "add-long/2addr", "sub-long/2addr", "mul-long/2addr", "div-long/2addr", "rem-long/2addr", "and-long/2addr", "or-long/2addr", "xor-long/2addr", "shl-long/2addr", "shr-long/2addr", "ushr-long/2addr", "add-float/2addr", "sub-float/2addr", "mul-float/2addr", "div-float/2addr", "rem-float/2addr", "add-double/2addr", "sub-double/2addr", "mul-double/2addr", "div-double/2addr", "rem-double/2addr", "add-int/lit16", "rsub-int", "mul-int/lit16", "div-int/lit16", "rem-int/lit16", "and-int/lit16", "or-int/lit16", "xor-int/lit16", "add-int/lit8", "rsub-int/lit8", "mul-int/lit8", "div-int/lit8", "rem-int/lit8", "and-int/lit8", "or-int/lit8", "xor-int/lit8", "shl-int/lit8", "shr-int/lit8", "ushr-int/lit8", "unused-e3", "unused-e4", "unused-e5", "unused-e6", "unused-e7", "unused-e8", "unused-e9", "unused-ea", "unused-eb", "unused-ec", "unused-ed", "unused-ee", "unused-ef", "unused-f0", "unused-f1", "unused-f2", "unused-f3", "unused-f4", "unused-f5", "unused-f6", "unused-f7", "unused-f8", "unused-f9", "invoke-polymorphic", "invoke-polymorphic/range", "invoke-custom", "invoke-custom/range", "const-method-handle", "const-method-type"};
    private static final byte[] SIZE = new byte[256];
    private int classCount;
    private ClassDef[] classDefs;
    private final byte[] d;
    private FieldRef[] fieldRefs;
    private final LogSink log;
    private MethodRef[] methodRefs;
    private Proto[] protos;
    private final File smaliRoot;
    private int[] stringOffsets;
    private String[] strings;
    private int[] typeStringIdx;

    private String encodedValue(int i) {
        return null;
    }

    static {
        for (int i = 0; i < 256; i++) {
            SIZE[i] = 1;
        }
        int[] iArr = {2, 5, 8, 19, 21, 22, 25, 26, 28, 31, 32, 33, 34, 35, 41, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79, 80, 81, 82, 83, 84, 85, 86, 87, 88, 89, 90, 91, 92, 93, 94, 95, 96, 97, 98, 99, 100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 144, 145, 146, 147, 148, 149, 150, 151, 152, 153, 154, 155, 156, 157, 158, 159, 160, 161, 162, 163, 164, 165, 166, 167, 168, 169, 170, 171, 172, 173, 174, 175, 208, 209, 210, 211, 212, 213, 214, 215, 216, 217, 218, 219, 220, 221, 222, 223, 224, 225, 226, 254, 255};
        for (int i2 = 0; i2 < 127; i2++) {
            SIZE[iArr[i2]] = 2;
        }
        int[] iArr2 = {3, 6, 9, 20, 23, 27, 36, 37, 38, 42, 43, 44, 110, 111, 112, 113, 114, 116, 117, 118, 119, 120, 252, 253};
        for (int i3 = 0; i3 < 24; i3++) {
            SIZE[iArr2[i3]] = 3;
        }
        byte[] bArr = SIZE;
        bArr[24] = 5;
        bArr[250] = 4;
        bArr[251] = 4;
    }

    public DexDisassembler(byte[] bArr, File file, LogSink logSink) {
        this.d = bArr;
        this.smaliRoot = file;
        this.log = logSink;
    }

    public int getClassCount() {
        return this.classCount;
    }

    public void disassemble() throws IOException {
        byte[] bArr = this.d;
        if (bArr.length >= 112) {
            if (bArr[0] == 100 && bArr[1] == 101 && bArr[2] == 120) {
                readIds();
                for (ClassDef classDef : this.classDefs) {
                    LogSink logSink = this.log;
                    while (logSink != null && logSink.isPaused()) {
                        if (logSink.isCancelled()) throw new ZipUtil.CancelledException();
                        try {
                            Thread.sleep(100L);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new ZipUtil.CancelledException();
                        }
                    }
                    if (logSink != null && logSink.isCancelled()) {
                        throw new ZipUtil.CancelledException();
                    }
                    try {
                        writeClass(classDef);
                        this.classCount++;
                    } catch (Exception e) {
                        LogSink logSink2 = this.log;
                        if (logSink2 != null) {
                            logSink2.log(LogManager.WARN, "Class partially recovered: " + typeName(classDef.classIdx) + " (" + e.getMessage() + ")");
                        }
                        try {
                            writeStub(classDef, e);
                            this.classCount++;
                        } catch (Exception unused) {
                        }
                    }
                }
                return;
            }
        }
        throw new IOException("Not a DEX file");
    }

    /* JADX WARN: Multi-variable type inference failed */
    private void readIds() {
        int i32 = i32(56);
        int i322 = i32(60);
        this.strings = new String[i32];
        this.stringOffsets = new int[i32];
        for (int i = 0; i < i32; i++) {
            int i323 = i32((i * 4) + i322);
            this.stringOffsets[i] = i323;
            this.strings[i] = readString(i323);
        }
        int i324 = i32(64);
        int i325 = i32(68);
        this.typeStringIdx = new int[i324];
        for (int i2 = 0; i2 < i324; i2++) {
            this.typeStringIdx[i2] = i32((i2 * 4) + i325);
        }
        int i326 = i32(72);
        int i327 = i32(76);
        this.protos = new Proto[i326];
        int i3 = 0;
        while (true) {
            if (i3 >= i326) {
                break;
            }
            Proto proto = new Proto();
            int i4 = (i3 * 12) + i327;
            proto.returnType = i32(i4 + 4);
            proto.params = readTypeList(i32(i4 + 8));
            this.protos[i3] = proto;
            i3++;
        }
        int i328 = i32(80);
        int i329 = i32(84);
        this.fieldRefs = new FieldRef[i328];
        for (int i5 = 0; i5 < i328; i5++) {
            FieldRef fieldRef = new FieldRef();
            int i6 = (i5 * 8) + i329;
            fieldRef.classIdx = u16(i6);
            fieldRef.typeIdx = u16(i6 + 2);
            fieldRef.nameIdx = i32(i6 + 4);
            this.fieldRefs[i5] = fieldRef;
        }
        int i3210 = i32(88);
        int i3211 = i32(92);
        this.methodRefs = new MethodRef[i3210];
        for (int i7 = 0; i7 < i3210; i7++) {
            MethodRef methodRef = new MethodRef();
            int i8 = (i7 * 8) + i3211;
            methodRef.classIdx = u16(i8);
            methodRef.protoIdx = u16(i8 + 2);
            methodRef.nameIdx = i32(i8 + 4);
            this.methodRefs[i7] = methodRef;
        }
        int i3212 = i32(96);
        int i3213 = i32(100);
        this.classDefs = new ClassDef[i3212];
        for (int i9 = 0; i9 < i3212; i9++) {
            ClassDef classDef = new ClassDef();
            int i10 = (i9 * 32) + i3213;
            classDef.classIdx = i32(i10);
            classDef.accessFlags = i32(i10 + 4);
            classDef.superIdx = i32(i10 + 8);
            classDef.interfacesOff = i32(i10 + 12);
            classDef.sourceIdx = i32(i10 + 16);
            classDef.classDataOff = i32(i10 + 24);
            classDef.staticValuesOff = i32(i10 + 28);
            this.classDefs[i9] = classDef;
        }
    }

    private File smaliFile(String typeName) {
        String name = typeName;
        if (name == null || name.length() < 2) {
            name = "Lunknown/Unknown;";
        }
        if (name.startsWith("L") && name.endsWith(";")) {
            name = name.substring(1, name.length() - 1);
        } else {
            name = name.replace(';', '_').replace('[', '_').replace("L", "");
            if (name.isEmpty()) name = "unknown/Unknown";
        }
        StringBuilder clean = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            if (ch == '/' || ch == '$' || ch == '-' || ch == '_' || ch == '.' ||
                    Character.isLetterOrDigit(ch)) {
                clean.append(ch);
            } else {
                clean.append('_');
            }
        }
        return new File(this.smaliRoot, clean.toString() + ".smali");
    }

    private void writeClass(ClassDef classDef) throws IOException {
        int[] iArr;
        String encodedValue;
        String typeName = typeName(classDef.classIdx);
        StringBuilder sb = new StringBuilder(8192);
        sb.append(".class ");
        sb.append(classAccess(classDef.accessFlags));
        sb.append(' ');
        sb.append(typeName);
        sb.append('\n');
        if (classDef.superIdx != -1) {
            sb.append(".super ");
            sb.append(typeName(classDef.superIdx));
            sb.append('\n');
        }
        if (classDef.sourceIdx != -1) {
            sb.append(".source \"");
            sb.append(escape(this.strings[classDef.sourceIdx]));
            sb.append("\"\n");
        }
        int i = 0;
        for (int i2 : readTypeList(classDef.interfacesOff)) {
            sb.append(".implements ");
            sb.append(typeName(i2));
            sb.append('\n');
        }
        ArrayList arrayList = new ArrayList();
        ArrayList arrayList2 = new ArrayList();
        ArrayList arrayList3 = new ArrayList();
        ArrayList arrayList4 = new ArrayList();
        int[] iArr2 = new int[0];
        if (classDef.classDataOff != 0) {
            iArr = iArr2;
            readClassData(classDef.classDataOff, arrayList, arrayList2, arrayList3, arrayList4);
        } else {
            iArr = iArr2;
        }
        int[] readEncodedArray = classDef.staticValuesOff != 0 ? readEncodedArray(classDef.staticValuesOff) : iArr;
        for (Object __o : arrayList) {
            EncodedField encodedField = (EncodedField) __o;
            FieldRef fieldRef = this.fieldRefs[encodedField.fieldIdx];
            sb.append("\n.field ");
            sb.append(fieldAccess(encodedField.accessFlags));
            sb.append(' ');
            sb.append(this.strings[fieldRef.nameIdx]);
            sb.append(':');
            sb.append(typeName(fieldRef.typeIdx));
            if (i < readEncodedArray.length && (encodedValue = encodedValue(readEncodedArray[i])) != null) {
                sb.append(" = ");
                sb.append(encodedValue);
            }
            sb.append('\n');
            i++;
        }
        for (Object __o2 : arrayList2) {
            EncodedField encodedField2 = (EncodedField) __o2;
            FieldRef fieldRef2 = this.fieldRefs[encodedField2.fieldIdx];
            sb.append("\n.field ");
            sb.append(fieldAccess(encodedField2.accessFlags));
            sb.append(' ');
            sb.append(this.strings[fieldRef2.nameIdx]);
            sb.append(':');
            sb.append(typeName(fieldRef2.typeIdx));
            sb.append('\n');
        }
        Iterator<EncodedMethod> it = arrayList3.iterator();
        while (it.hasNext()) {
            writeMethod(sb, it.next());
        }
        Iterator<EncodedMethod> it2 = arrayList4.iterator();
        while (it2.hasNext()) {
            writeMethod(sb, it2.next());
        }
        File file = smaliFile(typeName);
        File parentFile = file.getParentFile();
        if (parentFile != null && !parentFile.exists()) {
            parentFile.mkdirs();
        }
        OutputStreamWriter outputStreamWriter = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8);
        try {
            outputStreamWriter.write(sb.toString());
            outputStreamWriter.close();
        } finally {
        }
    }

    private void writeStub(ClassDef classDef, Exception exc) throws IOException {
        String typeName = typeName(classDef.classIdx);
        StringBuilder sb = new StringBuilder(256);
        sb.append(".class ");
        sb.append(classAccess(classDef.accessFlags));
        sb.append(' ');
        sb.append(typeName);
        sb.append('\n');
        if (classDef.superIdx != -1) {
            sb.append(".super ");
            sb.append(typeName(classDef.superIdx));
            sb.append('\n');
        }
        sb.append("# Partial recovery: ");
        sb.append(exc);
        sb.append('\n');
        File file = smaliFile(typeName);
        File parentFile = file.getParentFile();
        if (parentFile != null && !parentFile.exists()) {
            parentFile.mkdirs();
        }
        OutputStreamWriter outputStreamWriter = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8);
        try {
            outputStreamWriter.write(sb.toString());
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

    private void writeMethod(StringBuilder sb, EncodedMethod encodedMethod) {
        MethodRef methodRef = this.methodRefs[encodedMethod.methodIdx];
        Proto proto = this.protos[methodRef.protoIdx];
        sb.append('\n');
        sb.append(".method ");
        sb.append(methodAccess(encodedMethod.accessFlags));
        sb.append(' ');
        sb.append(this.strings[methodRef.nameIdx]);
        sb.append('(');
        for (int i : proto.params) {
            sb.append(typeName(i));
        }
        sb.append(')');
        sb.append(typeName(proto.returnType));
        sb.append('\n');
        if (encodedMethod.codeOff == 0) {
            sb.append(".end method\n");
            return;
        }
        try {
            int u16 = u16(encodedMethod.codeOff);
            int i2 = 2;
            u16(encodedMethod.codeOff + 2);
            int i32 = i32(encodedMethod.codeOff + 12);
            int i3 = encodedMethod.codeOff + 16;
            sb.append("    .registers ");
            sb.append(u16);
            sb.append('\n');
            sb.append('\n');
            HashSet hashSet = new HashSet();
            collectLabels(i3, i32, hashSet);
            int u162 = u16(encodedMethod.codeOff + 6);
            int i4 = (i32 * 2) + i3;
            if ((i32 & 1) != 1) {
                i2 = 0;
            }
            int i5 = i4 + i2;
            ArrayList<TryBlock> arrayList = new ArrayList();
            if (u162 > 0) {
                int i6 = (u162 * 8) + i5;
                for (int i7 = 0; i7 < u162; i7++) {
                    int i8 = (i7 * 8) + i5;
                    TryBlock tryBlock = new TryBlock();
                    tryBlock.start = i32(i8);
                    tryBlock.count = u16(i8 + 4);
                    parseHandlers(u16(i8 + 6) + i6, tryBlock);
                    arrayList.add(tryBlock);
                    hashSet.add(Integer.valueOf(tryBlock.start));
                    hashSet.add(Integer.valueOf(tryBlock.start + tryBlock.count));
                    Iterator<CatchHandler> it = tryBlock.handlers.iterator();
                    while (it.hasNext()) {
                        hashSet.add(Integer.valueOf(it.next().addr));
                    }
                    if (tryBlock.catchAll >= 0) {
                        hashSet.add(Integer.valueOf(tryBlock.catchAll));
                    }
                }
            }
            int i9 = 0;
            while (i9 < i32) {
                i9 += emitInstruction(i3, i9, hashSet, sb);
            }
            for (TryBlock tryBlock2 : arrayList) {
                String str = "{:L" + hex(tryBlock2.start) + " .. :L" + hex(tryBlock2.start + tryBlock2.count) + "}";
                for (CatchHandler catchHandler : tryBlock2.handlers) {
                    sb.append("    .catch ");
                    sb.append(typeName(catchHandler.typeIdx));
                    sb.append(' ');
                    sb.append(str);
                    sb.append(" :L");
                    sb.append(hex(catchHandler.addr));
                    sb.append('\n');
                }
                if (tryBlock2.catchAll >= 0) {
                    sb.append("    .catchall ");
                    sb.append(str);
                    sb.append(" :L");
                    sb.append(hex(tryBlock2.catchAll));
                    sb.append('\n');
                }
            }
        } catch (Exception e) {
            sb.append("    # body unavailable: ");
            sb.append(e.getMessage());
            sb.append('\n');
        }
        sb.append(".end method\n");
    }

    private void collectLabels(int i, int i2, Set<Integer> set) {
        int i3 = 0;
        while (i3 < i2) {
            int i4 = (i3 * 2) + i;
            int u16 = u16(i4) & 255;
            int u162 = u16(i4) >> 8;
            if (u16 == 40) {
                set.add(Integer.valueOf(((byte) u162) + i3));
            } else if (u16 == 41 || u16 == 50 || u16 == 51 || u16 == 52 || u16 == 53 || u16 == 54 || u16 == 55 || u16 == 56 || u16 == 57 || u16 == 58 || u16 == 59 || u16 == 60 || u16 == 61) {
                set.add(Integer.valueOf(((short) u16(i4 + 2)) + i3));
            } else if (u16 == 42) {
                set.add(Integer.valueOf(i32(i4 + 2) + i3));
            } else if (u16 == 43 || u16 == 44 || u16 == 38) {
                set.add(Integer.valueOf(i32(i4 + 2) + i3));
            }
            i3 += instructionSize(i, i3);
        }
    }

    private int instructionSize(int i, int i2) {
        int i3;
        int i4;
        int i5 = (i2 * 2) + i;
        if (i5 < 0 || (i3 = i5 + 2) > this.d.length) {
            return 1;
        }
        int u16 = u16(i5);
        int i6 = u16 & 255;
        if (i6 != 0) {
            i4 = SIZE[i6];
        } else if (u16 == 256) {
            i4 = (u16(i3) * 2) + 4;
        } else if (u16 == 512) {
            i4 = (u16(i3) * 4) + 2;
        } else {
            if (u16 == 768) {
                int u162 = u16(i3);
                int i32 = i32(i5 + 4);
                if (u162 <= 0 || u162 > 8 || i32 < 0) {
                    return 1;
                }
                long j = (((i32 * u162) + 1) / 2) + 4;
                if (j <= 1048576) {
                    i4 = (int) j;
                }
            }
            i4 = 1;
        }
        if (i4 < 1) {
            i4 = 1;
        }
        if (i2 + i4 > (this.d.length - i) / 2) {
            return 1;
        }
        return i4;
    }

    private int emitInstruction(int i, int i2, Set<Integer> set, StringBuilder sb) {
        String range;
        int i3;
        int i4 = i + (i2 * 2);
        int u16 = u16(i4);
        int i5 = u16 & 255;
        int i6 = u16 >> 8;
        int instructionSize = instructionSize(i, i2);
        if (i5 == 0) {
            if (u16 == 256) {
                return emitPackedSwitch(i4, i2, set, sb);
            }
            if (u16 == 512) {
                return emitSparseSwitch(i4, i2, set, sb);
            }
            if (u16 == 768) {
                return emitArrayData(i4, i2, set, sb);
            }
        }
        if (set.contains(Integer.valueOf(i2))) {
            sb.append(":L");
            sb.append(hex(i2));
            sb.append('\n');
        }
        String str = OPCODES[i5];
        if (i5 == 18) {
            sb.append("    const/4 v");
            sb.append(i6 & 15);
            sb.append(", #");
            sb.append(((i6 >> 4) << 28) >> 28);
            sb.append('\n');
        } else if (i5 == 19 || i5 == 22) {
            sb.append("    ");
            sb.append(str);
            sb.append(" v");
            sb.append(i6);
            sb.append(", #");
            sb.append((int) ((short) u16(i4 + 2)));
            sb.append('\n');
        } else if (i5 == 20) {
            sb.append("    const v");
            sb.append(i6);
            sb.append(", #");
            sb.append(i32(i4 + 2));
            sb.append('\n');
        } else if (i5 == 21) {
            sb.append("    const/high16 v");
            sb.append(i6);
            sb.append(", #0x");
            sb.append(Integer.toHexString(((short) u16(i4 + 2)) << 16));
            sb.append('\n');
        } else if (i5 == 23) {
            sb.append("    const-wide/32 v");
            sb.append(i6);
            sb.append(", #");
            sb.append(i32(i4 + 2));
            sb.append("L\n");
        } else {
            int i7 = 32;
            if (i5 == 24) {
                sb.append("    const-wide v");
                sb.append(i6);
                sb.append(", #0x");
                sb.append(Long.toHexString((i32(i4 + 6) << 32) | (i32(i4 + 2) & 4294967295L)));
                sb.append("L\n");
            } else if (i5 == 25) {
                sb.append("    const-wide/high16 v");
                sb.append(i6);
                sb.append(", #0x");
                sb.append(Long.toHexString(((short) u16(i4 + 2)) << 48));
                sb.append("L\n");
            } else if (i5 == 26 || i5 == 27) {
                int i8 = i4 + 2;
                int u162 = i5 == 26 ? u16(i8) : i32(i8);
                sb.append("    const-string v");
                sb.append(i6);
                sb.append(", \"");
                sb.append(escape(this.strings[u162]));
                sb.append("\"\n");
            } else if (i5 == 28 || i5 == 31 || i5 == 34 || i5 == 254 || i5 == 255) {
                sb.append("    ");
                sb.append(str);
                sb.append(" v");
                sb.append(i6);
                sb.append(", ");
                sb.append(typeName(u16(i4 + 2)));
                sb.append('\n');
            } else if (i5 == 29 || i5 == 30 || i5 == 39) {
                sb.append("    ");
                sb.append(str);
                sb.append(" v");
                sb.append(i6);
                sb.append('\n');
            } else if (i5 == 10 || i5 == 11 || i5 == 12 || i5 == 13) {
                sb.append("    ");
                sb.append(str);
                sb.append(" v");
                sb.append(i6);
                sb.append('\n');
            } else if (i5 == 14) {
                sb.append("    return-void\n");
            } else if (i5 == 15 || i5 == 16 || i5 == 17) {
                sb.append("    ");
                sb.append(str);
                sb.append(" v");
                sb.append(i6);
                sb.append('\n');
            } else {
                if (i5 >= 1) {
                    if (i5 <= 9) {
                        if (i5 == 1 || i5 == 4 || i5 == 7) {
                            int i9 = i6 >> 4;
                            i6 &= 15;
                            i3 = i9;
                        } else if (i5 == 2 || i5 == 5 || i5 == 8) {
                            i3 = u16(i4 + 2);
                        } else {
                            i6 = u16(i4 + 2);
                            i3 = u16(i4 + 4);
                        }
                        sb.append("    ");
                        sb.append(str);
                        sb.append(" v");
                        sb.append(i6);
                        sb.append(", v");
                        sb.append(i3);
                        sb.append('\n');
                    } else {
                        i7 = 32;
                    }
                }
                if (i5 == i7) {
                    sb.append("    instance-of v");
                    sb.append(i6 & 15);
                    sb.append(", v");
                    sb.append(i6 >> 4);
                    sb.append(", ");
                    sb.append(typeName(u16(i4 + 2)));
                    sb.append('\n');
                } else if (i5 == 33) {
                    sb.append("    array-length v");
                    sb.append(i6 & 15);
                    sb.append(", v");
                    sb.append(i6 >> 4);
                    sb.append('\n');
                } else if (i5 == 35) {
                    sb.append("    new-array v");
                    sb.append(i6 & 15);
                    sb.append(", v");
                    sb.append(i6 >> 4);
                    sb.append(", ");
                    sb.append(typeName(u16(i4 + 2)));
                    sb.append('\n');
                } else if (i5 == 36) {
                    int i10 = i4 + 4;
                    int i11 = i4 + 5;
                    int[] iArr = {data(i10) & 15, (data(i10) >> 4) & 15, data(i11) & 15, 15 & (data(i11) >> 4), i6 & 15};
                    sb.append("    filled-new-array ");
                    sb.append(regList(iArr, i6 >> 4));
                    sb.append(", ");
                    sb.append(typeName(u16(i4 + 2)));
                    sb.append('\n');
                } else if (i5 == 37) {
                    sb.append("    filled-new-array/range ");
                    sb.append(range(i6, u16(i4 + 4)));
                    sb.append(", ");
                    sb.append(typeName(u16(i4 + 2)));
                    sb.append('\n');
                } else if (i5 == 38) {
                    sb.append("    fill-array-data v");
                    sb.append(i6);
                    sb.append(", :L");
                    sb.append(hex(i2 + i32(i4 + 2)));
                    sb.append('\n');
                } else if (i5 == 40) {
                    sb.append("    goto :L");
                    sb.append(hex(i2 + ((byte) i6)));
                    sb.append('\n');
                } else if (i5 == 41) {
                    sb.append("    goto/16 :L");
                    sb.append(hex(i2 + ((short) u16(i4 + 2))));
                    sb.append('\n');
                } else if (i5 == 42) {
                    sb.append("    goto/32 :L");
                    sb.append(hex(i2 + i32(i4 + 2)));
                    sb.append('\n');
                } else if (i5 == 43) {
                    sb.append("    packed-switch v");
                    sb.append(i6);
                    sb.append(", :L");
                    sb.append(hex(i2 + i32(i4 + 2)));
                    sb.append('\n');
                } else if (i5 == 44) {
                    sb.append("    sparse-switch v");
                    sb.append(i6);
                    sb.append(", :L");
                    sb.append(hex(i2 + i32(i4 + 2)));
                    sb.append('\n');
                } else if (i5 >= 45 && i5 <= 49) {
                    sb.append("    ");
                    sb.append(str);
                    sb.append(" v");
                    sb.append(i6);
                    sb.append(", v");
                    sb.append((int) data(i4 + 2));
                    sb.append(", v");
                    sb.append((int) data(i4 + 3));
                    sb.append('\n');
                } else if (i5 >= 50 && i5 <= 55) {
                    sb.append("    ");
                    sb.append(str);
                    sb.append(" v");
                    sb.append(i6 & 15);
                    sb.append(", v");
                    sb.append(i6 >> 4);
                    sb.append(", :L");
                    sb.append(hex(i2 + ((short) u16(i4 + 2))));
                    sb.append('\n');
                } else if (i5 >= 56 && i5 <= 61) {
                    sb.append("    ");
                    sb.append(str);
                    sb.append(" v");
                    sb.append(i6);
                    sb.append(", :L");
                    sb.append(hex(i2 + ((short) u16(i4 + 2))));
                    sb.append('\n');
                } else if (i5 >= 68 && i5 <= 81) {
                    sb.append("    ");
                    sb.append(str);
                    sb.append(" v");
                    sb.append(i6);
                    sb.append(", v");
                    sb.append((int) data(i4 + 2));
                    sb.append(", v");
                    sb.append((int) data(i4 + 3));
                    sb.append('\n');
                } else if (i5 >= 82 && i5 <= 95) {
                    FieldRef fieldRef = this.fieldRefs[u16(i4 + 2)];
                    sb.append("    ");
                    sb.append(str);
                    sb.append(" v");
                    sb.append(i6 & 15);
                    sb.append(", v");
                    sb.append(i6 >> 4);
                    sb.append(", ");
                    sb.append(fieldSig(fieldRef));
                    sb.append('\n');
                } else if (i5 >= 96 && i5 <= 109) {
                    FieldRef fieldRef2 = this.fieldRefs[u16(i4 + 2)];
                    sb.append("    ");
                    sb.append(str);
                    sb.append(" v");
                    sb.append(i6);
                    sb.append(", ");
                    sb.append(fieldSig(fieldRef2));
                    sb.append('\n');
                } else if (i5 >= 110 && i5 <= 114) {
                    int i12 = i6 >> 4;
                    int i13 = i6 & 15;
                    int i14 = i4 + 4;
                    int i15 = i4 + 5;
                    int[] iArr2 = {data(i14) & 15, (data(i14) >> 4) & 15, data(i15) & 15, 15 & (data(i15) >> 4), i13};
                    sb.append("    ");
                    sb.append(str);
                    sb.append(' ');
                    sb.append(regList(iArr2, i12));
                    sb.append(", ");
                    sb.append(methodSig(this.methodRefs[u16(i4 + 2)]));
                    sb.append('\n');
                } else if (i5 >= 116 && i5 <= 120) {
                    sb.append("    ");
                    sb.append(str);
                    sb.append(' ');
                    sb.append(range(i6, u16(i4 + 4)));
                    sb.append(", ");
                    sb.append(methodSig(this.methodRefs[u16(i4 + 2)]));
                    sb.append('\n');
                } else if (i5 >= 123 && i5 <= 143) {
                    sb.append("    ");
                    sb.append(str);
                    sb.append(" v");
                    sb.append(i6 & 15);
                    sb.append(", v");
                    sb.append(i6 >> 4);
                    sb.append('\n');
                } else if (i5 >= 144 && i5 <= 175) {
                    sb.append("    ");
                    sb.append(str);
                    sb.append(" v");
                    sb.append(i6);
                    sb.append(", v");
                    sb.append((int) data(i4 + 2));
                    sb.append(", v");
                    sb.append((int) data(i4 + 3));
                    sb.append('\n');
                } else if (i5 >= 176 && i5 <= 207) {
                    sb.append("    ");
                    sb.append(str);
                    sb.append(" v");
                    sb.append(i6 & 15);
                    sb.append(", v");
                    sb.append(i6 >> 4);
                    sb.append('\n');
                } else if (i5 >= 208 && i5 <= 215) {
                    sb.append("    ");
                    sb.append(str);
                    sb.append(" v");
                    sb.append(i6 & 15);
                    sb.append(", v");
                    sb.append(i6 >> 4);
                    sb.append(", #");
                    sb.append((int) ((short) u16(i4 + 2)));
                    sb.append('\n');
                } else if (i5 >= 216 && i5 <= 226) {
                    sb.append("    ");
                    sb.append(str);
                    sb.append(" v");
                    sb.append(i6);
                    sb.append(", v");
                    sb.append((int) data(i4 + 2));
                    sb.append(", #");
                    sb.append((int) data(i4 + 3));
                    sb.append('\n');
                } else if (i5 == 250) {
                    int i16 = i4 + 4;
                    int i17 = i4 + 5;
                    int[] iArr3 = {data(i16) & 15, (data(i16) >> 4) & 15, data(i17) & 15, 15 & (data(i17) >> 4), i6 & 15};
                    sb.append("    invoke-polymorphic ");
                    sb.append(regList(iArr3, i6 >> 4));
                    sb.append(", ");
                    sb.append(methodSig(this.methodRefs[u16(i4 + 2)]));
                    sb.append(", ");
                    sb.append(protoName(u16(i4 + 6)));
                    sb.append('\n');
                } else if (i5 == 251) {
                    sb.append("    invoke-polymorphic/range ");
                    sb.append(range(i6, u16(i4 + 4)));
                    sb.append(", ");
                    sb.append(methodSig(this.methodRefs[u16(i4 + 2)]));
                    sb.append(", ");
                    sb.append(protoName(u16(i4 + 6)));
                    sb.append('\n');
                } else if (i5 == 252 || i5 == 253) {
                    sb.append("    ");
                    sb.append(str);
                    sb.append(" ");
                    if (i5 == 252) {
                        int i18 = i4 + 4;
                        int i19 = i4 + 5;
                        range = regList(new int[]{data(i18) & 15, (data(i18) >> 4) & 15, data(i19) & 15, 15 & (data(i19) >> 4), i6 & 15}, i6 >> 4);
                    } else {
                        range = range(i6, u16(i4 + 4));
                    }
                    sb.append(range);
                    sb.append(", ");
                    sb.append(methodSig(this.methodRefs[u16(i4 + 2)]));
                    sb.append('\n');
                } else {
                    sb.append("    nop # unsupported opcode 0x");
                    sb.append(Integer.toHexString(i5));
                    sb.append('\n');
                }
            }
        }
        return instructionSize;
    }

    private int emitPackedSwitch(int i, int i2, Set<Integer> set, StringBuilder sb) {
        int u16 = u16(i + 2);
        int i32 = i32(i + 4);
        sb.append(":L");
        sb.append(hex(i2));
        sb.append('\n');
        sb.append("    .packed-switch 0x");
        sb.append(Integer.toHexString(i32));
        sb.append('\n');
        for (int i3 = 0; i3 < u16; i3++) {
            sb.append("        :L");
            sb.append(hex(i32(i + 8 + (i3 * 4)) + i2));
            sb.append('\n');
        }
        sb.append("    .end packed-switch\n");
        return (u16 * 2) + 4;
    }

    private int emitSparseSwitch(int i, int i2, Set<Integer> set, StringBuilder sb) {
        int u16 = u16(i + 2);
        sb.append(":L");
        sb.append(hex(i2));
        sb.append('\n');
        sb.append("    .sparse-switch\n");
        for (int i3 = 0; i3 < u16; i3++) {
            int i4 = i + 4;
            int i5 = i3 * 4;
            int i32 = i32(i4 + i5);
            int i322 = i32(i4 + (u16 * 4) + i5);
            sb.append("        0x");
            sb.append(Integer.toHexString(i32));
            sb.append(" -> :L");
            sb.append(hex(i322 + i2));
            sb.append('\n');
        }
        sb.append("    .end sparse-switch\n");
        return (u16 * 4) + 2;
    }

    private int emitArrayData(int i, int i2, Set<Integer> set, StringBuilder sb) {
        int u16 = u16(i + 2);
        int i32 = i32(i + 4);
        if (u16 <= 0 || u16 > 8) {
            u16 = 1;
        }
        int i3 = i + 8;
        long max = Math.max(0, this.d.length - i3);
        if (i32 < 0 || i32 * u16 > max) {
            i32 = (int) (max / u16);
        }
        if (i32 > 65536) {
            i32 = 65536;
        }
        sb.append(":L");
        sb.append(hex(i2));
        sb.append('\n');
        sb.append("    .array-data 0x");
        sb.append(Integer.toHexString(u16));
        sb.append('\n');
        if (u16 <= 0 || u16 > 8) {
            u16 = 1;
        }
        for (int i4 = 0; i4 < i32; i4++) {
            long j = u16;
            if (i3 + (i4 * j) + j > this.d.length) {
                break;
            }
            long j2 = 0;
            for (int i5 = 0; i5 < u16; i5++) {
                j2 |= (this.d[((i4 * u16) + i3) + i5] & 255) << (i5 * 8);
            }
            if (u16 == 8) {
                sb.append("        0x");
                sb.append(Long.toHexString(j2));
                sb.append("L\n");
            } else {
                sb.append("        0x");
                sb.append(Long.toHexString(j2));
                sb.append("t\n");
            }
        }
        sb.append("    .end array-data\n");
        return (((i32 * u16) + 1) / 2) + 4;
    }

    private void readClassData(int i, List<EncodedField> list, List<EncodedField> list2, List<EncodedMethod> list3, List<EncodedMethod> list4) {
        int[] uleb = uleb(i);
        int i2 = uleb[0];
        int[] uleb2 = uleb(uleb[1]);
        int i3 = uleb2[0];
        int[] uleb3 = uleb(uleb2[1]);
        int i4 = uleb3[0];
        int[] uleb4 = uleb(uleb3[1]);
        int i5 = uleb4[0];
        int i6 = uleb4[1];
        int i7 = 0;
        for (int i8 = 0; i8 < i2; i8++) {
            int[] uleb5 = uleb(i6);
            i7 += uleb5[0];
            int[] uleb6 = uleb(uleb5[1]);
            int i9 = uleb6[0];
            i6 = uleb6[1];
            list.add(new EncodedField(i7, i9));
        }
        int i10 = 0;
        for (int i11 = 0; i11 < i3; i11++) {
            int[] uleb7 = uleb(i6);
            i10 += uleb7[0];
            int[] uleb8 = uleb(uleb7[1]);
            int i12 = uleb8[0];
            i6 = uleb8[1];
            list2.add(new EncodedField(i10, i12));
        }
        int i13 = 0;
        for (int i14 = 0; i14 < i4; i14++) {
            int[] uleb9 = uleb(i6);
            i13 += uleb9[0];
            int[] uleb10 = uleb(uleb9[1]);
            int i15 = uleb10[0];
            int[] uleb11 = uleb(uleb10[1]);
            int i16 = uleb11[0];
            i6 = uleb11[1];
            list3.add(new EncodedMethod(i13, i15, i16));
        }
        int i17 = 0;
        for (int i18 = 0; i18 < i5; i18++) {
            int[] uleb12 = uleb(i6);
            i17 += uleb12[0];
            int[] uleb13 = uleb(uleb12[1]);
            int i19 = uleb13[0];
            int[] uleb14 = uleb(uleb13[1]);
            int i20 = uleb14[0];
            i6 = uleb14[1];
            list4.add(new EncodedMethod(i17, i19, i20));
        }
    }

    private int[] readEncodedArray(int i) {
        int[] uleb = uleb(i);
        int i2 = uleb[0];
        int i3 = uleb[1];
        int[] iArr = new int[i2];
        for (int i4 = 0; i4 < i2; i4++) {
            int[] readEncodedValue = readEncodedValue(this.d[i3] & 255, i3 + 1);
            iArr[i4] = readEncodedValue[0];
            i3 = readEncodedValue[1];
        }
        return iArr;
    }

    private int[] readEncodedValue(int i, int i2) {
        if (i2 < 0 || i2 >= this.d.length) {
            return new int[]{0, this.d.length};
        }
        int i3 = (i >> 5) & 7;
        int i4 = i & 31;
        if (i4 == 28) {
            int[] uleb = uleb(i2);
            int i5 = uleb[0];
            int i6 = uleb[1];
            for (int i7 = 0; i7 < i5; i7++) {
                i6 = readEncodedValue(this.d[i6] & 255, i6 + 1)[1];
            }
            return new int[]{0, i6};
        }
        if (i4 == 29) {
            int[] uleb2 = uleb(uleb(i2)[1]);
            int i8 = uleb2[0];
            int i9 = uleb2[1];
            for (int i10 = 0; i10 < i8; i10++) {
                int i11 = uleb(i9)[1];
                i9 = readEncodedValue(this.d[i11] & 255, i11 + 1)[1];
            }
            return new int[]{0, i9};
        }
        long j = 0;
        for (int i12 = 0; i12 < i3 + 1 && i12 < 8; i12++) {
            if (i2 + i12 >= this.d.length) {
                break;
            }
            j |= (long) (this.d[i2 + i12] & 255) << (i12 * 8);
        }
        return new int[]{(int) j, i2 + i3 + 1};
    }

    private void parseHandlers(int i, TryBlock tryBlock) {
        int[] sleb = sleb(i);
        int i2 = sleb[0];
        int i3 = sleb[1];
        int abs = Math.abs(i2);
        for (int i4 = 0; i4 < abs; i4++) {
            int[] uleb = uleb(i3);
            int i5 = uleb[0];
            int[] uleb2 = uleb(uleb[1]);
            int i6 = uleb2[0];
            i3 = uleb2[1];
            tryBlock.handlers.add(new CatchHandler(i5, i6));
        }
        if (i2 <= 0) {
            tryBlock.catchAll = uleb(i3)[0];
        }
    }

    private String typeName(int i) {
        if (i < 0) {
            return "V";
        }
        int[] iArr = this.typeStringIdx;
        if (iArr == null || i >= iArr.length) {
            return "V";
        }
        int sIdx = iArr[i];
        String[] sArr = this.strings;
        if (sArr == null || sIdx < 0 || sIdx >= sArr.length || sArr[sIdx] == null) {
            return "Ljava/lang/Object;";
        }
        return sArr[sIdx];
    }

    private String fieldSig(FieldRef fieldRef) {
        return typeName(fieldRef.classIdx) + "->" + this.strings[fieldRef.nameIdx] + ":" + typeName(fieldRef.typeIdx);
    }

    private String methodSig(MethodRef methodRef) {
        Proto proto = this.protos[methodRef.protoIdx];
        StringBuilder sb = new StringBuilder();
        sb.append(typeName(methodRef.classIdx));
        sb.append("->");
        sb.append(this.strings[methodRef.nameIdx]);
        sb.append('(');
        for (int i : proto.params) {
            sb.append(typeName(i));
        }
        sb.append(')');
        sb.append(typeName(proto.returnType));
        return sb.toString();
    }

    private String protoName(int i) {
        Proto proto = this.protos[i];
        StringBuilder sb = new StringBuilder("(");
        for (int i2 : proto.params) {
            sb.append(typeName(i2));
        }
        sb.append(')');
        sb.append(typeName(proto.returnType));
        return sb.toString();
    }

    private static String regList(int[] iArr, int i) {
        StringBuilder sb = new StringBuilder("{");
        for (int i2 = 0; i2 < i && i2 < iArr.length; i2++) {
            if (i2 > 0) {
                sb.append(", ");
            }
            sb.append('v');
            sb.append(iArr[i2]);
        }
        sb.append('}');
        return sb.toString();
    }

    private static String range(int i, int i2) {
        StringBuilder sb = new StringBuilder("{v");
        sb.append(i2);
        sb.append(" .. v");
        sb.append((i + i2) - 1);
        sb.append("}");
        return sb.toString();
    }

    private int[] readTypeList(int i) {
        if (i == 0) {
            return new int[0];
        }
        int i32 = i32(i);
        int[] iArr = new int[i32];
        for (int i2 = 0; i2 < i32; i2++) {
            iArr[i2] = u16(i + 4 + (i2 * 2));
        }
        return iArr;
    }

    private String readString(int i) {
        int[] uleb = uleb(i);
        int i2 = uleb[0];
        int i3 = uleb[1];
        StringBuilder sb = new StringBuilder(i2);
        while (true) {
            byte[] bArr = this.d;
            int i4 = i3 + 1;
            int i5 = bArr[i3] & 255;
            if (i5 == 0) {
                return sb.toString();
            }
            if (i5 < 128) {
                sb.append((char) i5);
                i3 = i4;
            } else if ((i5 & 224) == 192) {
                if (i4 >= bArr.length) return sb.toString();
                sb.append((char) (((i5 & 31) << 6) | (bArr[i4] & 255 & 63)));
                i3 = i4 + 1;
            } else if ((i5 & 240) == 224) {
                int i6 = i4 + 1;
                if (i6 >= bArr.length) return sb.toString();
                sb.append((char) (((i5 & 15) << 12) | (((bArr[i4] & 255) & 63) << 6) | (bArr[i6] & 255 & 63)));
                i3 = i6 + 1;
            } else {
                sb.append('?');
                i3 = i4;
            }
        }
    }

    private static String escape(String str) {
        if (str == null) return "";
        StringBuilder sb = new StringBuilder(str.length() + 8);
        for (int i = 0; i < str.length(); i++) {
            char charAt = str.charAt(i);
            if (charAt == '\t') {
                sb.append("\\t");
            } else if (charAt == '\n') {
                sb.append("\\n");
            } else if (charAt == '\r') {
                sb.append("\\r");
            } else if (charAt == '\"') {
                sb.append("\\\"");
            } else if (charAt == '\\') {
                sb.append("\\\\");
            } else if (charAt < ' ') {
                sb.append(String.format(Locale.US, "\\u%04x", Integer.valueOf(charAt)));
            } else {
                sb.append(charAt);
            }
        }
        return sb.toString();
    }

    private static String classAccess(int i) {
        StringBuilder sb = new StringBuilder();
        if ((i & 1) != 0) {
            sb.append("public ");
        }
        if ((i & 2) != 0) {
            sb.append("private ");
        }
        if ((i & 4) != 0) {
            sb.append("protected ");
        }
        if ((i & 8) != 0) {
            sb.append("static ");
        }
        if ((i & 16) != 0) {
            sb.append("final ");
        }
        if ((i & 512) != 0) {
            sb.append("interface ");
        }
        if ((i & 1024) != 0) {
            sb.append("abstract ");
        }
        if ((i & 4096) != 0) {
            sb.append("synthetic ");
        }
        if ((i & 8192) != 0) {
            sb.append("annotation ");
        }
        if ((i & 16384) != 0) {
            sb.append("enum ");
        }
        return sb.toString().trim();
    }

    private static String fieldAccess(int i) {
        StringBuilder sb = new StringBuilder();
        if ((i & 1) != 0) {
            sb.append("public ");
        }
        if ((i & 2) != 0) {
            sb.append("private ");
        }
        if ((i & 4) != 0) {
            sb.append("protected ");
        }
        if ((i & 8) != 0) {
            sb.append("static ");
        }
        if ((i & 16) != 0) {
            sb.append("final ");
        }
        if ((i & 64) != 0) {
            sb.append("volatile ");
        }
        if ((i & 128) != 0) {
            sb.append("transient ");
        }
        if ((i & 4096) != 0) {
            sb.append("synthetic ");
        }
        if ((i & 16384) != 0) {
            sb.append("enum ");
        }
        return sb.toString().trim();
    }

    private static String methodAccess(int i) {
        StringBuilder sb = new StringBuilder();
        if ((i & 1) != 0) {
            sb.append("public ");
        }
        if ((i & 2) != 0) {
            sb.append("private ");
        }
        if ((i & 4) != 0) {
            sb.append("protected ");
        }
        if ((i & 8) != 0) {
            sb.append("static ");
        }
        if ((i & 16) != 0) {
            sb.append("final ");
        }
        if ((i & 32) != 0) {
            sb.append("synchronized ");
        }
        if ((i & 64) != 0) {
            sb.append("bridge ");
        }
        if ((i & 128) != 0) {
            sb.append("varargs ");
        }
        if ((i & 256) != 0) {
            sb.append("native ");
        }
        if ((i & 1024) != 0) {
            sb.append("abstract ");
        }
        if ((i & 2048) != 0) {
            sb.append("strictfp ");
        }
        if ((i & 4096) != 0) {
            sb.append("synthetic ");
        }
        if ((i & 65536) != 0) {
            sb.append("constructor ");
        }
        return sb.toString().trim();
    }

    private static String hex(int i) {
        return Integer.toHexString(i);
    }

    private int[] uleb(int i) {
        int i2 = 0;
        int i3 = 0;
        while (true) {
            byte[] bArr = this.d;
            if (i >= bArr.length) {
                break;
            }
            int i4 = i + 1;
            int i5 = bArr[i] & 255;
            i2 |= (i5 & 127) << i3;
            if ((i5 & 128) == 0) {
                i = i4;
                break;
            }
            i3 += 7;
            i = i4;
        }
        return new int[]{i2, i};
    }

    private int[] sleb(int i) {
        int i2;
        int i3;
        int i4 = 0;
        int i5 = 0;
        while (true) {
            i2 = i + 1;
            i3 = this.d[i] & 255;
            i4 |= (i3 & 127) << i5;
            i5 += 7;
            if ((i3 & 128) == 0) {
                break;
            }
            i = i2;
        }
        if (i5 < 32 && (i3 & 64) != 0) {
            i4 |= -(1 << i5);
        }
        return new int[]{i4, i2};
    }

    private int u16(int i) {
        byte[] bArr = this.d;
        return ((bArr[i + 1] & 255) << 8) | (bArr[i] & 255);
    }

    private int i32(int i) {
        byte[] bArr = this.d;
        return ((bArr[i + 3] & 255) << 24) | (bArr[i] & 255) | ((bArr[i + 1] & 255) << 8) | ((bArr[i + 2] & 255) << 16);
    }

    private byte data(int i) {
        return this.d[i];
    }

    private static class Proto {
        int[] params;
        int returnType;

        private Proto() {
        }
    }

    private static class FieldRef {
        int classIdx;
        int nameIdx;
        int typeIdx;

        private FieldRef() {
        }
    }

    private static class MethodRef {
        int classIdx;
        int nameIdx;
        int protoIdx;

        private MethodRef() {
        }
    }

    private static class ClassDef {
        int accessFlags;
        int classDataOff;
        int classIdx;
        int interfacesOff;
        int sourceIdx;
        int staticValuesOff;
        int superIdx;

        private ClassDef() {
        }
    }

    private static class EncodedField {
        final int accessFlags;
        final int fieldIdx;

        EncodedField(int i, int i2) {
            this.fieldIdx = i;
            this.accessFlags = i2;
        }
    }

    private static class EncodedMethod {
        final int accessFlags;
        final int codeOff;
        final int methodIdx;

        EncodedMethod(int i, int i2, int i3) {
            this.methodIdx = i;
            this.accessFlags = i2;
            this.codeOff = i3;
        }
    }

    private static class CatchHandler {
        final int addr;
        final int typeIdx;

        CatchHandler(int i, int i2) {
            this.typeIdx = i;
            this.addr = i2;
        }
    }

    private static class TryBlock {
        int catchAll;
        int count;
        final List<CatchHandler> handlers;
        int start;

        private TryBlock() {
            this.catchAll = -1;
            this.handlers = new ArrayList();
        }
    }
}
