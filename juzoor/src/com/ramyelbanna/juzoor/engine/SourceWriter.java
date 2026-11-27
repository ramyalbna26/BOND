package com.ramyelbanna.juzoor.engine;

import com.ramyelbanna.juzoor.core.LogManager;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Recovers a navigable Java / Kotlin source tree straight from the DEX class
 * tables.
 *
 * <p>For every class definition Juzoor emits a compilable stub that preserves
 * the real package, class name, superclass, interfaces, field types and method
 * signatures. Method bodies are intentionally replaced with a {@code TODO}
 * marker: the executable logic lives in the disassembled {@code smali} tree,
 * while these stubs give an IDE-friendly skeleton and an API map of the app.</p>
 */
public final class SourceWriter {

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final int MAX_CLASSES = 40000;

    public static final class Stats {
        public int classes;
        public int javaFiles;
        public int kotlinFiles;
        public int methods;
        public int fields;
        public int skipped;

        public String line() {
            return classes + " classes · " + javaFiles + " .java · " + kotlinFiles + " .kt";
        }
    }

    private SourceWriter() {
    }

    public static Stats write(List<File> dexFiles, File javaRoot, File kotlinRoot,
                              boolean kotlin, LogSink log) {
        Stats stats = new Stats();
        Map<String, ClassInfo> classes = new LinkedHashMap<>();
        for (File dex : dexFiles) {
            if (log != null && log.isCancelled()) break;
            try {
                Dex reader = new Dex(ZipUtil.readFile(dex));
                reader.read();
                for (ClassInfo c : reader.classes) {
                    if (c == null || c.name == null) continue;
                    if (classes.size() >= MAX_CLASSES) {
                        stats.skipped++;
                        continue;
                    }
                    if (!classes.containsKey(c.name)) classes.put(c.name, c);
                }
            } catch (Throwable t) {
                if (log != null) log.log(LogManager.WARN, "Source stubs: " + dex.getName() + " skipped (" + t + ")");
            }
        }

        int total = classes.size();
        int index = 0;
        for (ClassInfo c : classes.values()) {
            if (log != null && log.isPaused()) {
                try {
                    Thread.sleep(100L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                continue;
            }
            if (log != null && log.isCancelled()) break;
            index++;
            try {
                if (c == null || c.name == null || c.name.isEmpty()
                        || c.simple == null || c.simple.isEmpty()) {
                    stats.skipped++;
                    continue;
                }
                String rel = c.name.replace('.', '/') + ".java";
                File out = new File(javaRoot, rel);
                File parent = out.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();
                ZipUtil.writeFile(out, c.toJava());
                if (log != null && (index == 1 || index % 25 == 0 || index == total)) {
                    log.file(rel, 87 + (int) (index * 1.0 / Math.max(1, total)));
                }
                stats.javaFiles++;
                stats.classes++;
                stats.methods += c.methods.size();
                stats.fields += c.fields.size();
                if (kotlin && kotlinRoot != null) {
                    String kname = c.name.replace('$', '_');
                    File kout = new File(kotlinRoot, kname.replace('.', '/') + ".kt");
                    File kparent = kout.getParentFile();
                    if (kparent != null && !kparent.exists()) kparent.mkdirs();
                    ZipUtil.writeFile(kout, c.toKotlin());
                    stats.kotlinFiles++;
                }
            } catch (Throwable e) {
                stats.skipped++;
            }
        }
        if (log != null) log.log(LogManager.INFO, "Source stubs recovered: " + stats.line());
        return stats;
    }

    /* --------------------------------------------------------------- model */

    private static final class FieldInfo {
        String name;
        String type;
        int access;
    }

    private static final class MethodInfo {
        String name;
        String ret;
        List<String> params = new ArrayList<>();
        int access;
    }

    private static final class ClassInfo {
        String name;
        String simple;
        String pkg;
        String superName;
        List<String> interfaces = new ArrayList<>();
        List<FieldInfo> fields = new ArrayList<>();
        List<MethodInfo> methods = new ArrayList<>();
        int access;

        boolean isInterface() {
            return (access & 0x0200) != 0;
        }

        boolean isAnnotation() {
            return (access & 0x2000) != 0;
        }

        String toJava() {
            StringBuilder b = new StringBuilder(1024);
            b.append("// Recovered by Juzoor 2.3 — Java stub generated from the DEX class table.\n");
            b.append("// Bodies are intentionally omitted; see app/src/main/smali*/ for bytecode.\n");
            if (pkg != null && !pkg.isEmpty()) b.append("package ").append(pkg).append(";\n");
            b.append("\n");
            b.append("/** Recovered stub for {@code ").append(name).append("}. */\n");
            b.append(javaAccess(access));
            if (isAnnotation()) b.append("@interface ");
            else if (isInterface()) b.append("interface ");
            else b.append("class ");
            b.append(simple);
            if (!isInterface() && superName != null && !superName.equals("java.lang.Object")) {
                b.append(" extends ").append(superName);
            }
            if (!interfaces.isEmpty()) {
                b.append(isInterface() ? " extends " : " implements ");
                for (int i = 0; i < interfaces.size(); i++) {
                    if (i > 0) b.append(", ");
                    b.append(interfaces.get(i));
                }
            }
            b.append(" {\n");

            if (!isInterface() && !isAnnotation()) {
                for (FieldInfo f : fields) {
                    b.append("    ").append(javaMemberAccess(f.access)).append(f.type)
                            .append(' ').append(f.name).append(";\n");
                }
                if (!fields.isEmpty()) b.append('\n');
            }

            for (MethodInfo m : methods) {
                b.append("    ").append(javaMemberAccess(m.access));
                boolean body = !isInterface() && !isAnnotation();
                b.append(m.ret).append(' ').append(m.name).append('(');
                for (int i = 0; i < m.params.size(); i++) {
                    if (i > 0) b.append(", ");
                    b.append(m.params.get(i)).append(" a").append(i);
                }
                if (body) b.append(") {\n        throw new UnsupportedOperationException(\"recovered stub\");\n    }\n\n");
                else b.append(");\n\n");
            }
            b.append("}\n");
            return b.toString();
        }

        String toKotlin() {
            StringBuilder b = new StringBuilder(1024);
            b.append("// Recovered by Juzoor 2.3 — Kotlin stub generated from the DEX class table.\n");
            if (pkg != null && !pkg.isEmpty()) b.append("package ").append(pkg).append("\n");
            b.append("\n");
            b.append("/** Recovered stub for {@code ").append(name).append("}. */\n");
            b.append(isInterface() ? "interface " : "open class ");
            b.append(simple.replace('$', '_')).append(" {\n");
            for (FieldInfo f : fields) {
                String kt = kotlinType(f.type);
                b.append("    var ").append(f.name).append(": ").append(kt)
                        .append(kt.endsWith("?") ? " = null" : " = " + kotlinDefault(f.type)).append('\n');
            }
            if (!fields.isEmpty()) b.append('\n');
            for (MethodInfo m : methods) {
                b.append("    fun ").append(m.name.replace('<', '_').replace('>', '_')).append('(');
                for (int i = 0; i < m.params.size(); i++) {
                    if (i > 0) b.append(", ");
                    b.append("a").append(i).append(": ").append(kotlinType(m.params.get(i)));
                }
                b.append("): ").append(kotlinType(m.ret));
                if (isInterface()) b.append('\n');
                else b.append(" = TODO(\"recovered stub\")\n");
            }
            b.append("}\n");
            return b.toString();
        }
    }

    /* ------------------------------------------------------- java helpers */

    private static String javaAccess(int a) {
        StringBuilder b = new StringBuilder();
        if ((a & 0x0001) != 0) b.append("public ");
        else if ((a & 0x0004) != 0) b.append("protected ");
        else if ((a & 0x0002) != 0) b.append("private ");
        if ((a & 0x0400) != 0 && (a & 0x0200) == 0) b.append("abstract ");
        if ((a & 0x0010) != 0 && (a & 0x0400) == 0) b.append("final ");
        return b.toString();
    }

    private static String javaMemberAccess(int a) {
        StringBuilder b = new StringBuilder();
        if ((a & 0x0001) != 0) b.append("public ");
        else if ((a & 0x0004) != 0) b.append("protected ");
        else if ((a & 0x0002) != 0) b.append("private ");
        if ((a & 0x0008) != 0) b.append("static ");
        return b.toString();
    }

    private static String kotlinDefault(String desc) {
        switch (desc) {
            case "Z": return "false";
            case "B":
            case "S":
            case "I": return "0";
            case "J": return "0L";
            case "F": return "0f";
            case "D": return "0.0";
            case "C": return "'\\u0000'";
            default: return "null";
        }
    }

    private static String kotlinType(String desc) {
        if (desc == null) return "Any?";
        switch (desc) {
            case "V": return "Unit";
            case "Z": return "Boolean";
            case "B": return "Byte";
            case "S": return "Short";
            case "C": return "Char";
            case "I": return "Int";
            case "J": return "Long";
            case "F": return "Float";
            case "D": return "Double";
            default: break;
        }
        if (desc.startsWith("[")) return "Array<" + kotlinType(desc.substring(1)) + ">";
        String n = desc;
        if (n.startsWith("L") && n.endsWith(";")) n = n.substring(1, n.length() - 1).replace('/', '.').replace('$', '_');
        if (n.startsWith("java.lang.") && n.indexOf('.', 10) < 0) n = n.substring(10);
        return n;
    }

    private static String javaType(String desc) {
        if (desc == null) return "Object";
        switch (desc) {
            case "V": return "void";
            case "Z": return "boolean";
            case "B": return "byte";
            case "S": return "short";
            case "C": return "char";
            case "I": return "int";
            case "J": return "long";
            case "F": return "float";
            case "D": return "double";
            default: break;
        }
        int dims = 0;
        while (dims < desc.length() && desc.charAt(dims) == '[') dims++;
        String base;
        if (dims > 0) {
            base = javaType(desc.substring(dims));
        } else if (desc.startsWith("L") && desc.endsWith(";")) {
            base = desc.substring(1, desc.length() - 1).replace('/', '.');
        } else {
            base = desc;
        }
        StringBuilder b = new StringBuilder(base);
        for (int i = 0; i < dims; i++) b.append("[]");
        return b.toString();
    }

    /* ---------------------------------------------------------- dex reader */

    private static final class Dex {
        final byte[] d;
        int stringIdsOff;
        int stringIdsSize;
        int typeIdsOff;
        int typeIdsSize;
        int protoIdsOff;
        int protoIdsSize;
        int fieldIdsOff;
        int fieldIdsSize;
        int methodIdsOff;
        int methodIdsSize;
        int classDefsOff;
        int classDefsSize;
        String[] strings;
        String[] types;
        final List<ClassInfo> classes = new ArrayList<>();

        Dex(byte[] data) {
            this.d = data;
        }

        void read() {
            stringIdsSize = i32(56);
            stringIdsOff = i32(60);
            typeIdsSize = i32(64);
            typeIdsOff = i32(68);
            protoIdsSize = i32(72);
            protoIdsOff = i32(76);
            fieldIdsSize = i32(80);
            fieldIdsOff = i32(84);
            methodIdsSize = i32(88);
            methodIdsOff = i32(92);
            classDefsSize = i32(96);
            classDefsOff = i32(100);

            strings = new String[stringIdsSize];
            types = new String[typeIdsSize];
            for (int i = 0; i < typeIdsSize; i++) {
                int idx = i32(typeIdsOff + i * 4);
                types[i] = string(idx);
            }
            for (int i = 0; i < classDefsSize; i++) {
                int base = classDefsOff + i * 32;
                int classIdx = i32(base);
                if (classIdx < 0 || classIdx >= types.length) continue;
                ClassInfo c = new ClassInfo();
                String raw = types[classIdx];
                if (raw == null || raw.isEmpty()) continue;
                c.name = dotted(raw);
                if (c.name == null || c.name.isEmpty()) continue;
                c.access = i32(base + 4);
                int superIdx = i32(base + 8);
                c.superName = superIdx >= 0 && superIdx < types.length ? dotted(types[superIdx]) : null;
                int ifacesOff = i32(base + 12);
                if (ifacesOff > 0) {
                    int n = i32(ifacesOff);
                    for (int k = 0; k < n; k++) {
                        int t = u16(ifacesOff + 4 + k * 2);
                        if (t >= 0 && t < types.length) {
                            String in = dotted(types[t]);
                            if (in != null && !in.isEmpty()) c.interfaces.add(in);
                        }
                    }
                }
                int classDataOff = i32(base + 24);
                if (classDataOff > 0) readClassData(c, classDataOff);
                int dot = c.name.lastIndexOf('.');
                c.pkg = dot > 0 ? c.name.substring(0, dot) : "";
                c.simple = dot >= 0 ? c.name.substring(dot + 1) : c.name;
                if (c.simple == null || c.simple.isEmpty()) continue;
                classes.add(c);
            }
        }

        void readClassData(ClassInfo c, int off) {
            int[] p = new int[]{off};
            int staticFields = uleb(p);
            int instanceFields = uleb(p);
            int directMethods = uleb(p);
            int virtualMethods = uleb(p);
            int fieldIdx = 0;
            for (int i = 0; i < staticFields + instanceFields; i++) {
                fieldIdx += uleb(p);
                int access = uleb(p);
                if (fieldIdx >= 0 && fieldIdx < fieldIdsSize) {
                    int base = fieldIdsOff + fieldIdx * 8;
                    int typeIdx = u16(base + 2);
                    int nameIdx = i32(base + 4);
                    FieldInfo f = new FieldInfo();
                    f.name = safeName(string(nameIdx));
                    f.type = javaType(typeIdx >= 0 && typeIdx < types.length ? types[typeIdx] : "Ljava/lang/Object;");
                    f.access = access;
                    if (f.name != null) c.fields.add(f);
                }
            }
            int methodIdx = 0;
            for (int i = 0; i < directMethods + virtualMethods; i++) {
                methodIdx += uleb(p);
                int access = uleb(p);
                uleb(p); // code_off — bodies live in the smali tree
                if (methodIdx < 0 || methodIdx >= methodIdsSize) continue;
                int base = methodIdsOff + methodIdx * 8;
                int protoIdx = u16(base + 2);
                int nameIdx = i32(base + 4);
                String name = safeName(string(nameIdx));
                if (name == null) continue;
                if (name.equals("<clinit>")) continue;
                MethodInfo m = new MethodInfo();
                m.access = access;
                m.name = name.equals("<init>") ? c.simple : name;
                if (protoIdx >= 0 && protoIdx < protoIdsSize) {
                    int pb = protoIdsOff + protoIdx * 12;
                    int retType = i32(pb + 4);
                    int paramsOff = i32(pb + 8);
                    m.ret = javaType(retType >= 0 && retType < types.length ? types[retType] : "V");
                    if (paramsOff > 0) {
                        int n = i32(paramsOff);
                        for (int k = 0; k < n; k++) {
                            int t = u16(paramsOff + 4 + k * 2);
                            m.params.add(javaType(t >= 0 && t < types.length ? types[t] : "Ljava/lang/Object;"));
                        }
                    }
                } else {
                    m.ret = "void";
                }
                c.methods.add(m);
            }
        }

        /** Converts a DEX type descriptor (Lcom/foo/Bar;) to a dotted, sanitized class name. */
        static String dotted(String desc) {
            if (desc == null) return null;
            String n = desc;
            while (n.startsWith("[")) n = n.substring(1);
            if (n.startsWith("L") && n.endsWith(";") && n.length() >= 2) {
                n = n.substring(1, n.length() - 1);
            }
            n = n.replace('/', '.');
            StringBuilder b = new StringBuilder(n.length());
            for (int i = 0; i < n.length(); i++) {
                char ch = n.charAt(i);
                if (ch == '.' || ch == '$' || ch == '_' || Character.isJavaIdentifierPart(ch)) {
                    b.append(ch);
                } else {
                    b.append('_');
                }
            }
            String out = b.toString();
            if (out.isEmpty()) return null;
            // A class name segment must not start with a digit.
            String[] parts = out.split("\\.");
            StringBuilder fixed = new StringBuilder(out.length());
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) fixed.append('.');
                String part = parts[i];
                if (!part.isEmpty() && Character.isDigit(part.charAt(0))) fixed.append('_');
                fixed.append(part);
            }
            return fixed.toString();
        }

        String safeName(String s) {
            if (s == null || s.isEmpty()) return null;
            for (int i = 0; i < s.length(); i++) {
                char ch = s.charAt(i);
                if (!(Character.isJavaIdentifierPart(ch) || ch == '<' || ch == '>')) return null;
            }
            return s;
        }

        String string(int idx) {
            if (idx < 0 || idx >= stringIdsSize) return null;
            int off = i32(stringIdsOff + idx * 4);
            int[] p = new int[]{off};
            uleb(p);
            int start = p[0];
            int end = start;
            while (end < d.length && d[end] != 0) end++;
            return new String(d, start, end - start, UTF8);
        }

        int u16(int off) {
            if (off < 0 || off + 1 >= d.length) return 0;
            return (d[off] & 0xff) | ((d[off + 1] & 0xff) << 8);
        }

        int i32(int off) {
            if (off < 0 || off + 3 >= d.length) return 0;
            return (d[off] & 0xff) | ((d[off + 1] & 0xff) << 8)
                    | ((d[off + 2] & 0xff) << 16) | ((d[off + 3] & 0xff) << 24);
        }

        int uleb(int[] p) {
            int result = 0;
            int shift = 0;
            while (p[0] < d.length) {
                int b = d[p[0]++] & 0xff;
                result |= (b & 0x7f) << shift;
                if ((b & 0x80) == 0) break;
                shift += 7;
                if (shift > 35) break;
            }
            return result;
        }
    }
}
